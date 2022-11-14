package org.example.services.videoconsumer;


import org.example.ByteUtils;
import org.example.DataReference;
import org.example.Dispatcher;
import org.example.TimeUtils;
import org.example.endpoint.OutgoingPacketCarrier;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.LacksRequest;
import org.example.packets.PacketTypes;
import org.example.packets.VideoFramePacket;
import org.example.packets.VideoHeaderPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads asnychroniously incoming parts of videoframes
 * sort, request lacks, sending to outoutVideoStream (vlc, gstreamer, rtsp server, etc...)
 */
public class VideoFramesReader implements AutoCloseable, ServicePacketAcceptor {
    private static final Logger logger = LoggerFactory.getLogger(VideoFramesReader.class);


    private VideoStreamAcceptor acceptor;
    private OutgoingPacketCarrier outgoingPacketCarrier;

    private final Dispatcher dispatcher = new Dispatcher(300, "Frame collecting");
    private final Dispatcher acceptorDispatcher = new Dispatcher(3, "Piping videoFrame");

    //incoming queue like 1 2 5 4 9 6
    private final Map<Integer, videoFramePackets> pendingPackets = new HashMap<>();
    private final Set<Integer> sentPackets = new HashSet<>();
    private VideoHeaderPacket header;
    private boolean headerSent;

    private static final int AWAIT_MS = 200;
    //1.5 Mbps if we have 10 packets with size 1000bytes
    // we should start be in trouble in 67ms
    private static final int BYTES_PER_MS = 150;
    private long lacksRequestedTime;
    //if between 2 packets enqueue more than CHECK_PAUSE_INTERVAL - time to check frame ready
    private static final int CHECK_PAUSE_INTERVAL = 5;
    //frequency - how often we can request lacks
    private static final int LACK_REQUEST_PERIOD = 30;
    //how many times we can request lacks for this ID
    private static final int MAX_LACKS_REQUEST_TIMES = 1;


    private static final int REINIT_SECONDS = 3;

    private int lastSentId;
    private static final int SENT_PACKETS_HOLD_COUNT = 20;
    //we sent something and lastSentId set
    private boolean chainStarted;
    private long lastEnqueueTime;

    public VideoFramesReader(VideoStreamAcceptor acceptor, OutgoingPacketCarrier outgoingPacketCarrier) {
        this.acceptor = acceptor;
        this.outgoingPacketCarrier = outgoingPacketCarrier;
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
                setHeader(packet.getData(), packet.getOffset(), logId);
            } else if (packet.getPacketType() == PacketTypes.VideoFrame) {
                try {
                    addFrame(packet, logId);
                } catch (IOException e) {
                    logger.error("during frame part add", e);
                }
            }
        });
    }


    void setHeader(byte[] packets, int packetOffset, Integer logId) {
        this.header = VideoHeaderPacket.fromBuf(packets, packetOffset);
        acceptor.configureVideoAcceptor(header.getWidth(), header.getHeight());
        logger.debug("Header set logId {}, w:h {} {} {}", logId, header.getWidth(), header.getHeight(),
                ByteUtils.toHexString(packets, packetOffset, Math.min(packets.length - packetOffset, 20)));

    }

    /**
     * Does not block
     *
     * @param logId saved buffer to some storage @see FileSystemPacketsLogger
     * @throws IOException
     */
    void addFrame(PacketReference packet, Integer logId) throws IOException {
        packetDecorator p = new packetDecorator(packet, logId);
        if (this.header == null) {
            logger.debug("no header {}", logId);
        } else if (sentPackets.contains(p.id)) {
            logger.debug("already sent {}", p.id);
        } else if (p.id < lastSentId) {
            //reset state
            if (TimeUtils.elapsedSeconds(REINIT_SECONDS, lastEnqueueTime)) {
                lastSentId = 0;
                header = null;
            }
        } else if (p.id > lastSentId) {
            long prevEnqueueTime = lastEnqueueTime;
            pendingPackets.computeIfAbsent(p.id, (id) -> new videoFramePackets(p))
                    .addPart(p);
            lastEnqueueTime = TimeUtils.nowMs();

            //if 90% of the current frame received OR next frame receiving started - call check
            if (lastEnqueueTime - prevEnqueueTime >= CHECK_PAUSE_INTERVAL
                    || pendingPackets.size() > 1
                    || dispatcher.isEmpty()) {// have some free ti
                check();
            }
        } else {
            log(new videoFramePackets(p), "too-old or CRC incorrect");
        }

    }


    @Override
    public void close() throws Exception {
        dispatcher.close();
        acceptorDispatcher.close();
    }


    private void check() {
        try {
            if (!pendingPackets.isEmpty()) {
                sendPacketsChainV3Smooth();
                if (clearOldPackets()) {
                    chainStarted = false;
                }
                //requestLackParts();
            }
        } catch (Exception e) {
            logger.error("main video loop", e);
        }
    }

    private void requestLackParts() {
        List<videoFramePackets> packets = pendingPackets.values().stream().sorted()
                .collect(Collectors.toList());

        Map<Integer, Set<Integer>> lackPartsOverAllVersions = new HashMap<>();
        for (videoFramePackets packet : packets) {
            if (packet.highlyLikelyHasLack() && packet.lacksRequestedTimes < MAX_LACKS_REQUEST_TIMES) {
                final Set<Integer> lacks = packet.getLacks();
                if (!lacks.isEmpty()) {
                    lackPartsOverAllVersions.put(packet.id, lacks);
                }
            }
        }
        if (!lackPartsOverAllVersions.isEmpty() && TimeUtils.elapsed(LACK_REQUEST_PERIOD, lacksRequestedTime)) {
            LacksRequest lacksRequest = new LacksRequest(lackPartsOverAllVersions);
            String description = lacksRequest.getDescription();
            outgoingPacketCarrier.packetWasBorn(lacksRequest, logId ->
                    logger.debug("LogID: {}. requesting lacks {}", logId, description)
            );
            lacksRequestedTime = TimeUtils.nowMs();
        }
    }

    private void sendPacketsChainV3Smooth() {
        List<videoFramePackets> packets = pendingPackets.values().stream().sorted()
                .collect(Collectors.toList());

        if (packets.isEmpty()) {
            return;
        }

        videoFramePackets veryFirst = packets.get(0);
        if (veryFirst.isComplete()) {
            if (!chainStarted) {
                chainStarted = true;
                sendPacketAndRemove(veryFirst);
            } else if (veryFirst.id == lastSentId + 1) {
                // if it is expected next packet id+1 or first became old
                //it is our start of chain
                sendPacketAndRemove(veryFirst);
                //send rest
                while (true) {
                    packets = pendingPackets.values().stream().sorted()
                            .collect(Collectors.toList());
                    if (packets.isEmpty()) {
                        break;
                    }
                    videoFramePackets youngIframe = searchCompleteYoungIframe(packets);
                    if (youngIframe != null) {
                        //remove all old packets
                        packets.stream().filter(p -> p.id < youngIframe.id)
                                .forEach(p -> {
                                    log(p, "young I-frame present");
                                    pendingPackets.remove(p.id);
                                });
                        //send this i frame and continue
                        sendPacketAndRemove(youngIframe);
                        break;
                    }
                    veryFirst = packets.get(0);
                    if (veryFirst != null && veryFirst.id == lastSentId + 1
                            && veryFirst.isComplete()) {
                        sendPacketAndRemove(veryFirst);
                    } else {
                        break;
                    }
                }
            } else {
                //await incoming udp packets
            }
        }
    }

    private videoFramePackets searchCompleteYoungIframe(List<videoFramePackets> packets) {
        for (int i = packets.size() - 1; i >= 0; i--) {
            videoFramePackets p = packets.get(i);
            if (p.isComplete() && p.isIframe) {
                return p;
            }
        }
        return null;
    }


    private void sendPacketAndRemove(videoFramePackets packet) {
        if (!sentPackets.contains(packet.id)) {
            sentPackets.add(packet.id);
            packet.writeOut();
            log(packet, "sent");
        }

        pendingPackets.remove(packet.id);
        if (lastSentId < packet.id) {
            lastSentId = packet.id;
        }
    }

    private boolean clearOldPackets() {
        List<Integer> removeList = pendingPackets.values().stream().filter(p -> {
            if (p.isOld() || p.id < lastSentId) {
                log(p, "removed");
                return true;
            }
            return false;
        }).map(videoFramePackets::getId).collect(Collectors.toList());
        boolean haveSomeToDelete = !removeList.isEmpty();
        if (haveSomeToDelete) {
            sentPackets.addAll(removeList);
            removeList.forEach(pendingPackets::remove);
        }
        //clean sent Set
        sentPackets.removeAll(sentPackets.stream()
                .filter(id -> id < lastSentId - SENT_PACKETS_HOLD_COUNT)
                .collect(Collectors.toSet()));
        return haveSomeToDelete;
    }


    private void log(videoFramePackets p, String action) {
        String logIds = p.parts.stream().map(p1 -> String.valueOf(p1.logId))
                .collect(Collectors.joining(",", "", ""));
        String partIndexes = p.parts.stream().map(p1 -> String.valueOf(p1.partIndex))
                .collect(Collectors.joining(",", "", ""));
        long now = TimeUtils.nowMs();
        String text = String.format("videoFrame version %d, complete %s, parts %s/%d, action %s, logIds %s, delta %d ",
                p.id, p.isComplete(), partIndexes, p.partsCount, action, logIds, now - p.creationTimestamp);
        if (action.contains("sent")) {
            logger.debug(text);
        } else {
            logger.warn(text);
        }
    }


    private class videoFramePackets implements Comparable<videoFramePackets> {
        final int id;
        final int partsCount;
        final int approxSize;
        volatile long creationTimestamp = TimeUtils.nowMs();
        boolean isIframe;
        private final Set<packetDecorator> parts = Collections.synchronizedSet(new HashSet<>());
        int lacksRequestedTimes;

        public videoFramePackets(packetDecorator some) {
            this.id = some.id;
            this.partsCount = some.partsCount;
            approxSize = some.approxSize;
        }

        public void writeOut() {
            try {
                if (!headerSent) {
                    acceptorDispatcher.submitBlocking(() -> {
                        try {
                            acceptor.writeVideoHeader(new DataReference(header.getHeaderBuf(), header.getHeaderOffset(), header.getHeaderLength()));
                        } catch (Exception e) {
                            logger.error("During header setup ", e);
                        }
                    });
                    headerSent = true;
                }
                List<packetDecorator> packetParts = parts.stream()
                        .sorted(Comparator.comparingInt(packetDecorator::getIndex))
                        .collect(Collectors.toList());
                acceptorDispatcher.submitBlocking(() -> {
                    for (packetDecorator packetDecorator : packetParts) {
                        try {
                            acceptor.writeVideoFrame(packetDecorator.id, packetDecorator.partIndex, packetDecorator.partsCount,
                                    new DataReference(packetDecorator.getBuffer(), packetDecorator.getVideDataOffset(),
                                            packetDecorator.getVideDataLength()));
                        } catch (Exception e) {
                            logger.error("During frame send {} ", packetDecorator.toString(), e);
                        }
                    }
                });
            } catch (Exception e) {
                logger.error("video piping", e);
            }
        }

        public void addPart(packetDecorator part) {
            parts.add(part);
            isIframe = part.frameConfig.iFrame;
        }

        public boolean isComplete() {
            return parts.size() >= partsCount;
        }

        boolean isOld() {
            return TimeUtils.elapsed(AWAIT_MS, creationTimestamp);
        }

        public int getId() {
            return id;
        }

        @Override
        public int compareTo(videoFramePackets o) {
            return Integer.compare(id, o.id);
        }

        public boolean highlyLikelyHasLack() {
            if (isComplete()) {
                return false;
            }
            int rquiredMs = approxSize * 1000 / BYTES_PER_MS;
            if (TimeUtils.elapsed(rquiredMs, creationTimestamp)) {
                return true;
            }
            return false;
        }

        public Set<Integer> getLacks() {
            Set<Integer> lackParts = new HashSet<>();
            Set<Integer> presentPartsId = new HashSet<>(parts).stream()
                    .map(packetDecorator::getIndex).collect(Collectors.toSet());
            for (int i = 0; i < partsCount; i++) {
                if (presentPartsId.contains(i)) {
                    continue;
                }
                lackParts.add(i);
            }
            return lackParts;
        }
    }


    private class packetDecorator {
        private final PacketReference packet;
        private final int videoDataOffset;
        private final int videoDataLength;

        final int id;
        final Integer logId;

        final int partIndex;
        final int partsCount;
        final int approxSize;
        final VideoFramePacket.FrameConfig frameConfig;

        packetDecorator(PacketReference packet, Integer logId) {
            this.packet = packet;
            this.logId = logId;
            final int packetOffset = packet.getOffset();
            final byte[] buf = packet.getData();
            videoDataOffset = VideoFramePacket.getVideoDataOffset(packetOffset);
            videoDataLength = VideoFramePacket.getVideFrameSize(buf, packetOffset);
            id = VideoFramePacket.getPacketId(buf, packetOffset);
            partIndex = VideoFramePacket.getPartIndex(buf, packetOffset);
            partsCount = VideoFramePacket.getPartsCount(buf, packetOffset);
            approxSize = VideoFramePacket.getApproxSize(buf, packetOffset);
            frameConfig = VideoFramePacket.getFrameConfig(buf, packetOffset);
            if (id < 0) {
                logger.error("wrong id {} logId {} version dump {}", id, logId, ByteUtils.toHexString(buf, VideoFramePacket.ID_OFFSET, 4));
            }
        }

        int getIndex() {
            return partIndex;
        }

        public byte[] getBuffer() {
            return packet.getData();
        }

        public int getPacketOffset() {
            return packet.getOffset();
        }

        public int getVideDataOffset() {
            return videoDataOffset;
        }

        public int getVideDataLength() {
            return videoDataLength;
        }

        @Override
        public String toString() {
            return "packetDecorator{" +
                    "videoDataOffset=" + videoDataOffset +
                    ", id=" + id +
                    ", logId=" + logId +
                    ", videoDataOffset=" + videoDataOffset +
                    ", partIndex=" + partIndex +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            packetDecorator that = (packetDecorator) o;

            return partIndex == that.partIndex;
        }

        @Override
        public int hashCode() {
            return partIndex;
        }


    }

}
