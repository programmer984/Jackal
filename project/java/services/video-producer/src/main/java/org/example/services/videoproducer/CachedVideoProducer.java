package org.example.services.videoproducer;

import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.LacksRequest;
import org.example.packets.PacketTypes;
import org.example.packets.VideoFramePacket;
import org.example.packets.VideoHeaderPacket;
import org.example.services.videoproducer.codec.CodecCreator;
import org.example.services.videoproducer.codec.VideoFrameConsumer;
import org.example.services.videoproducer.codec.VideoFrameTypes;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Decorator
 * it receives videframe from videoproducer, save last 2 - 3
 * Also it converts each frame to videoFramePacket and assign version
 * on client request it returns lack blocks
 */
public class CachedVideoProducer implements AutoCloseable, ServicePacketAcceptor {
    private final static Logger logger = LoggerFactory.getLogger(CachedVideoProducer.class);
    private OutgoingPacketCarrier outgoingPacketCarrier;
    private VideoProducer videoProducer;
    private final AtomicInteger videoFramesVersion = new AtomicInteger(0);
    private final Map<Integer, VideoFramePacket[]> lacks = new ConcurrentHashMap<>();
    private final int CACHE_SIZE = 3;
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
            videoHeaderPacket = new VideoHeaderPacket(imageSize.getWidth(), imageSize.getHeight(),
                    videoFrame.getFrameData(), 0, videoFrame.getFrameData().length);
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

        VideoFramePacket[] videoFramePackets = VideoFramePacket.split(videoFrame.getFrameData(),
                newId, IFrame);
        lacks.put(newId, videoFramePackets);
        for (VideoFramePacket videFramePart : videoFramePackets) {
            outgoingPacketCarrier.packetWasBorn(videFramePart, logId ->
                    logger.debug("video frame part was sent {}, logId {}", videFramePart.getDescription(), logId));
        }

        removeOldPackets(newId);
    };

    private void removeOldPackets(int newId) {
        Set<Integer> oldIds = lacks.keySet().stream()
                .filter(id -> id < newId - CACHE_SIZE)
                .collect(Collectors.toSet());
        oldIds.forEach(lacks::remove);
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
        return set;
    }

    @Override
    public void accept(PacketReference packet, Integer logId) {
        Map<Integer, Set<Integer>> lacksRequest = LacksRequest.getLacks(packet.getData(), packet.getOffset());
        for (int id : lacksRequest.keySet()) {
            Set<Integer> parts = lacksRequest.get(id);
            VideoFramePacket[] frames = lacks.get(id);
            if (frames != null) {
                for (int part : parts) {
                    VideoFramePacket videFramePart = frames[part];
                    outgoingPacketCarrier.packetWasBorn(videFramePart, (outcomingLogId) ->
                            logger.info("Incoming logId {} LogID: {}. lack resend for id {} part {}", logId, outcomingLogId, id, part)
                    );
                }
            } else {
                logger.warn("There are not cached parts for logId {}", logId);
            }
        }
    }
}
