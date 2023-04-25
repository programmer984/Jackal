package org.example.services.videoconsumer;


import org.example.Dispatcher;
import org.example.TimeUtils;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.FramesActiveSet;
import org.example.packets.LacksRequest;
import org.example.packets.PacketTypes;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads asnychroniously incoming parts of videoframes
 * sort, request lacks, sending to outoutVideoStream (vlc, gstreamer, rtsp server, etc...)
 */
public class VideoFramesCollector implements AutoCloseable, ServicePacketAcceptor {
    private static final Logger logger = LoggerFactory.getLogger(VideoFramesCollector.class);
    //half second. CachedVideoProducer also holds 13 frames
    private static final int AWAIT_MS = 500;
    //between last success enqueue to FramesSink and now
    private static final int LACK_REQUEST_PERIOD = 20;
    private static final int ACTIVE_FRAMES_SET_REQUEST_PERIOD = 20;

    private static final int REINIT_SECONDS = 3;

    private final FramesSink sink;
    private final OutgoingPacketCarrier outgoingPacketCarrier;
    private final Dispatcher dispatcher = new Dispatcher(300, "Frame collecting");
    private final SoftTimer activeSetTimer;
    private final TimersManager timersManager;

    private final TreeSet<VideoFramePackets> pendingFrames = new TreeSet<>();
    //incomplete P frames which we received before complete I frame
    private final Set<VideoFramePackets> obsoleteFrames = new HashSet<>();
    //old P or I frames
    private final Set<VideoFramePackets> oldFrames = new HashSet<>();
    private final Set<VideoFramePackets> successFrames = new HashSet<>();

    private int lastDroppedId;
    private int lastEnqueuedId;
    //enqueued to FramesSink
    private long lastEnqueueTime;
    private long lastLacksRequestTime;
    private int marker;


    public VideoFramesCollector(VideoStreamAcceptor acceptor, OutgoingPacketCarrier outgoingPacketCarrier, TimersManager timersManager) {
        this.sink = new FramesSink(acceptor);
        this.outgoingPacketCarrier = outgoingPacketCarrier;
        this.timersManager = timersManager;
        activeSetTimer = timersManager.addTimer(ACTIVE_FRAMES_SET_REQUEST_PERIOD, true, this::activeFramesSetSend, "activeFramesSetSender");
    }

    @Override
    public void close() throws Exception {
        timersManager.removeTimer(activeSetTimer);
        dispatcher.close();
        sink.close();
    }

    @Override
    public Set<PacketTypes> getAcceptPacketTypes() {
        Set<PacketTypes> set = new HashSet<>();
        set.add(PacketTypes.VideoFrame);
        set.add(PacketTypes.VideoHeader);
        return set;
    }

    @Override
    public void accept(PacketReference packet, Integer logId) {
        dispatcher.submitBlocking(() -> {
            if (packet.getPacketType() == PacketTypes.VideoHeader) {
                sink.setHeader(packet.getData(), packet.getOffset(), logId);
            } else if (packet.getPacketType() == PacketTypes.VideoFrame) {
                try {
                    addFrame(packet, logId);
                } catch (IOException e) {
                    logger.error("during frame part add", e);
                }
            }
        });
    }

    private void activeFramesSetSend() {
        if (dispatcher.isEmpty()) {
            dispatcher.submitBlocking(() -> {
                Map<Integer, Set<Integer>> activeFrames = new HashMap<>();
                pendingFrames.forEach(frame -> {
                    activeFrames.put(frame.getId(), frame.getParts()
                            .stream()
                            .map(PacketDecorator::getIndex)
                            .collect(Collectors.toSet()));
                });

                if (!activeFrames.isEmpty()) {
                    final FramesActiveSet activeSetPacket = new FramesActiveSet(marker++, lastEnqueuedId, activeFrames);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Marker#{} informing", activeSetPacket.getMarker());
                    }
                    outgoingPacketCarrier.packetWasBorn(activeSetPacket,
                            logId ->
                            {
                                if (logger.isDebugEnabled()) {
                                   logger.debug("Marker#{} LogID: {}. informing active set {}", activeSetPacket.getMarker(),
                                           logId, activeSetPacket.getDescription());
                                }
                            }
                    );
                }
            });
        }
    }

    /**
     * Does not block
     *
     * @param logId saved buffer to some storage @see FileSystemPacketsLogger
     * @throws IOException
     */
    private void addFrame(PacketReference packet, Integer logId) throws IOException {
        PacketDecorator p = new PacketDecorator(packet, logId);
        if (p.id <= lastEnqueuedId) {
            logger.debug("already sent {}", p.id);
        } else if (p.id <= lastDroppedId) {
            logger.warn("in blacklist {}", p.id);
        } else {
            boolean wasAdded = false;
            for (VideoFramePackets frame : pendingFrames) {
                if (frame.getId() == p.id) {
                    frame.addPart(p);
                    wasAdded = true;
                    break;
                }
            }
            if (!wasAdded) {
                VideoFramePackets frame = new VideoFramePackets(p);
                pendingFrames.add(frame);
                frame.addPart(p);
                if (logger.isDebugEnabled()) {
                    logger.debug("frame created {}", frame);
                }
            }
            check();
        }

        //if sender was rebooted, its versions should be less than ours
        //reset state
        if (TimeUtils.elapsedSeconds(REINIT_SECONDS, lastEnqueueTime)) {
            lastEnqueuedId = 0;
            lastDroppedId = 0;
        }
    }


    private void check() {
        try {
            if (!pendingFrames.isEmpty()) {
                obsoleteFrames.clear();
                oldFrames.clear();
                successFrames.clear();
                Integer queuedIFrame = null;
                for (VideoFramePackets frame : pendingFrames) {
                    if (isOld(frame)) {
                        oldFrames.add(frame);
                    }
                    //some complete P or I frame
                    else if (frame.isComplete() && ((frame.getId() - lastEnqueuedId == 1)
                            || frame.isIframe)) {
                        enqueue(frame);
                        successFrames.add(frame);
                        if (frame.isIframe) {
                            queuedIFrame = frame.getId();
                        }
                    } else {
                        obsoleteFrames.add(frame);
                    }
                }

                pendingFrames.removeAll(successFrames);

                if (!oldFrames.isEmpty()) {
                    pendingFrames.removeAll(oldFrames);
                    drop(String.format("dropping old frames %d", oldFrames.size()),
                            oldFrames);
                }
                if (!obsoleteFrames.isEmpty() && queuedIFrame != null) {
                    pendingFrames.removeAll(obsoleteFrames);
                    drop(String.format("dropping %d obsolete frames since I-frame %d has been appeared", obsoleteFrames.size(), queuedIFrame),
                            obsoleteFrames);
                }
                //request lacks if nothing enqueued
                if (successFrames.isEmpty() && TimeUtils.elapsed(LACK_REQUEST_PERIOD, lastLacksRequestTime)) {
                    requestLackParts();
                }
            }
        } catch (Exception e) {
            logger.error("main video loop", e);
        }
    }

    private boolean isOld(VideoFramePackets frame) {
        return TimeUtils.elapsed(AWAIT_MS, frame.creationTimestamp);
    }

    private void drop(String reason, Collection<VideoFramePackets> frames) {
        if (logger.isWarnEnabled()) {
            logger.warn(reason);
            frames.forEach(frame -> {
                logger.warn("{}", frame.toString());
            });
        }
        frames.forEach(frame -> {
            if (frame.getId() > lastDroppedId) {
                lastDroppedId = frame.getId();
            }
        });
        frames.clear();
    }

    private void enqueue(final VideoFramePackets frame) {
        sink.enqueue(frame);
        if (logger.isDebugEnabled()) {
            logger.debug("Successfully enqueued {}", frame);
        }
        lastEnqueueTime = TimeUtils.nowMs();
        lastEnqueuedId = frame.id;
    }


    private void requestLackParts() {
        final int awaitingNextId = lastEnqueuedId + 1;
        pendingFrames.stream().findFirst().ifPresent(firstFrame -> {
            Map<Integer, Set<Integer>> lacks = new HashMap<>();
            //if we did not receive next frame at all (my be it was small and not splitted)
            if (firstFrame.getId() != awaitingNextId) {
                lacks.put(awaitingNextId, Collections.emptySet());
            } else {
                lacks.put(firstFrame.getId(), firstFrame.getLacks());
            }

            if (!lacks.isEmpty()) {
                LacksRequest lacksRequest = new LacksRequest(marker++, lacks);
                String description = lacksRequest.getDescription();
                if (logger.isDebugEnabled()) {
                    logger.debug("Marker#{} Requesting lacks {}", lacksRequest.getMarker(), description);
                }
                outgoingPacketCarrier.packetWasBorn(lacksRequest,
                        logId ->
                        {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Marker#{} LogID: {}. requesting lacks {}", lacksRequest.getMarker(), logId, description);
                            }
                        }
                );
                lastLacksRequestTime = TimeUtils.nowMs();
            }
        });

    }

}
