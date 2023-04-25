package org.example.services.videoproducer;

import org.example.TimeUtils;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.*;
import org.example.services.videoproducer.codec.CodecCreator;
import org.example.services.videoproducer.codec.VideoFrameConsumer;
import org.example.services.videoproducer.codec.VideoFrameTypes;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decorator
 * it receives videframe from videoproducer, save last 20 - 30
 * Also it converts each frame to videoFramePacket and assign version
 * on client request it returns lack blocks
 */
public class CachedVideoProducer implements AutoCloseable, ServicePacketAcceptor {
    private final static Logger logger = LoggerFactory.getLogger(CachedVideoProducer.class);
    private OutgoingPacketCarrier outgoingPacketCarrier;
    private VideoProducer videoProducer;
    private final AtomicInteger videoFramesVersion = new AtomicInteger(0);
    private final Map<Integer, VideoFramePacket[]> frames = new ConcurrentHashMap<>();
    private final Map<VideoFramePacket, AtomicLong> lastSentTime =new ConcurrentHashMap<>();
    private final int RESEND_MS_PERIOD = 20;
    private final int CACHE_SIZE = 13;  //25 frames / second
    private VideoHeaderPacket videoHeaderPacket;
    private final static int SEND_HEADER_EVERY_SECONDS = 5;
    private boolean timeToSendHeader;
    private TimersManager timersManager;
    private SoftTimer headerTimer;

    public CachedVideoProducer(OutgoingPacketCarrier outgoingPacketCarrier, ImageProducer imageProducer, VideoConfig videoConfig,
                               TimersManager timersManager, CodecCreator codecCreator) {
        this.videoProducer = new VideoProducer(imageProducer, videoConfig, videoFrameConsumer, timersManager, codecCreator);
        this.outgoingPacketCarrier = outgoingPacketCarrier;
        this.timersManager = timersManager;
        headerTimer = timersManager.addTimer(SEND_HEADER_EVERY_SECONDS * 1000, true, () -> {
            synchronized (this) {
                timeToSendHeader = true;
            }
        });
    }

    private VideoFrameConsumer videoFrameConsumer = videoFrame -> {
        int newId = videoFramesVersion.incrementAndGet();
        if (VideoFrameTypes.VideoFrameTypeIDR.equals(videoFrame.getVideoFrameType())) {
            ImageSize imageSize = videoProducer.getActualImageSize();
            //take a few bytes from IDR packet from 0 0 0 1 0x67 .... to 0 0 0 0
            byte[] header = VideoHeaderPacket.copyHeaderFromIdrOrNull(videoFrame.getFrameData());
            if (header != null) {
                videoHeaderPacket = new VideoHeaderPacket(imageSize.getWidth(), imageSize.getHeight(),
                        header, 0, header.length);
            }
        }
        synchronized (this) {
            if (timeToSendHeader) {
                timeToSendHeader = false;
                if (videoHeaderPacket != null) {
                    outgoingPacketCarrier.packetWasBorn(videoHeaderPacket, logId -> {
                        logger.debug("video frame header was sent {}", logId);
                    });
                }
            }
        }

        boolean IFrame = VideoFrameTypes.VideoFrameTypeIDR.equals(videoFrame.getVideoFrameType())
                || VideoFrameTypes.VideoFrameTypeI.equals(videoFrame.getVideoFrameType());

        final VideoFramePacket[] videoFramePackets = VideoFramePacket.split(videoFrame.getFrameData(),
                newId, IFrame);
        frames.put(newId, videoFramePackets);
        for (VideoFramePacket videFramePart : videoFramePackets) {
            outgoingPacketCarrier.packetWasBorn(videFramePart, logId -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("video frame part was sent {}, logId {}", videFramePart.getDescription(), logId);
                }
            });
            lastSentTime.put(videFramePart, new AtomicLong(TimeUtils.nowMs()));
        }

        removeOldPackets(newId);
    };

    private void removeOldPackets(int newId) {
        Set<Integer> oldIds = frames.keySet().stream()
                .filter(id -> id < newId - CACHE_SIZE)
                .collect(Collectors.toSet());
        oldIds.forEach(id ->{
            VideoFramePacket[] framePackets=frames.get(id);
            for (VideoFramePacket videoFramePacket : framePackets) {
                lastSentTime.remove(videoFramePacket);
            }
            frames.remove(id);
        });
    }

    @Override
    public void close() throws Exception {
        if (headerTimer != null) {
            timersManager.removeTimer(headerTimer);
            headerTimer = null;
        }
        videoProducer.close();
    }

    @Override
    public Set<PacketTypes> getAcceptPacketTypes() {
        Set<PacketTypes> set = new HashSet<>();
        set.add(PacketTypes.VideoLacksRequest);
        set.add(PacketTypes.VideoFramesSet);
        return set;
    }

    @Override
    public void accept(PacketReference packet, Integer logId) {
        if (packet.getPacketType()==PacketTypes.VideoFramesSet){
            activeSetNotify(packet, logId);
        } else if (packet.getPacketType()==PacketTypes.VideoLacksRequest){
            lacksRequestHandle(packet, logId);
        }
    }

    private void activeSetNotify(PacketReference packet, Integer logId) {
        Map<Integer, VideoFramePacket[]> framesCopy = Collections.unmodifiableMap(this.frames);
        Function<VideoFramePacket, Void> send = (videoFramePart) -> {
            if (timeToResend(videoFramePart)) {
                outgoingPacketCarrier.packetWasBorn(videoFramePart, (outcomingLogId) ->
                        logger.info("Marker#{} Resend since absent in active set. Logs {}/{} Frame id {} part {}",
                                FramesActiveSet.getMarkerFromBuf(packet.getDataReference()), logId, outcomingLogId,
                                videoFramePart.getId(), videoFramePart.getIndex())
                );
                updateResendTime(videoFramePart);
            }else{
                logger.debug("Marker#{} Resend ignored Frame id {} part {}",
                        FramesActiveSet.getMarkerFromBuf(packet.getDataReference()),
                        videoFramePart.getId(), videoFramePart.getIndex());
            }
            return null;
        };

        int lastSuccessId = FramesActiveSet.getLastSuccessId(packet.getDataReference());
        if (logger.isDebugEnabled()){
            logger.debug("Marker#{} Received active frames set packet with last success frame Id {}",
                    FramesActiveSet.getMarkerFromBuf(packet.getDataReference()), lastSuccessId);
        }

        Map<Integer, Set<Integer>> activeSet = FramesActiveSet.getActiveSet(packet.getDataReference());
        //send next after lastSuccessId frame parts
        int nextFrame = lastSuccessId + 1;

        if (framesCopy.containsKey(nextFrame)){
            //may be next frame contains only 1 part and it was not received
            if (!activeSet.containsKey(nextFrame)){
                Arrays.stream(framesCopy.get(nextFrame)).forEach(send::apply);
            }else{
                //send only lacks
                Set<Integer> existParts = activeSet.get(nextFrame);
                Arrays.stream(framesCopy.get(nextFrame))
                .filter(part-> !existParts.contains(part))
                .forEach(send::apply);
            }
        }
    }

    private void lacksRequestHandle(PacketReference packet, Integer logId) {
        Map<Integer, Set<Integer>> lacksRequest = LacksRequest.getLacks(packet.getDataReference());
        Function<VideoFramePacket, Void> send = (videoFramePart) -> {
            if (timeToResend(videoFramePart)) {
                outgoingPacketCarrier.packetWasBorn(videoFramePart, (outcomingLogId) ->
                        logger.info("Marker#{} Lacks resend. Logs {}/{} Frame id {} part {}",
                                LacksRequest.getMarkerFromBuf(packet.getDataReference()), logId, outcomingLogId,
                                videoFramePart.getId(), videoFramePart.getIndex())
                );
                updateResendTime(videoFramePart);
            }else{
                logger.debug("Marker#{} Resend ignored Frame id {} part {}",
                        LacksRequest.getMarkerFromBuf(packet.getDataReference()),
                        videoFramePart.getId(), videoFramePart.getIndex());
            }
            return null;
        };
        if (logger.isDebugEnabled()){
            logger.debug("Marker#{} Received lack request packet with {} frame/s",
                    LacksRequest.getMarkerFromBuf(packet.getDataReference()), lacksRequest.keySet()
            .stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        for (int id : lacksRequest.keySet()) {
            Set<Integer> parts = lacksRequest.get(id);
            VideoFramePacket[] frames = this.frames.get(id);
            if (frames != null) {
                //return all parts
                if (parts.isEmpty()) {
                    for (int partIndex = 0; partIndex < frames.length; partIndex++) {
                        send.apply(frames[partIndex]);
                    }
                } else {
                    for (int partIndex : parts) {
                        send.apply(frames[partIndex]);
                    }
                }
            } else {
                logger.warn("There are not cached parts for frame {}, logId {}", id, logId);
            }
        }
    }

    private boolean timeToResend(VideoFramePacket videoFramePacket){
        AtomicLong ms = lastSentTime.get(videoFramePacket);
        if(ms!=null) {
            return TimeUtils.elapsed(RESEND_MS_PERIOD, ms.get());
        }
        //already gone
        return false;
    }

    private void updateResendTime(VideoFramePacket videoFramePacket){
        AtomicLong ms = lastSentTime.get(videoFramePacket);
        if(ms!=null){
            ms.set(TimeUtils.nowMs());
        }
    }
}
