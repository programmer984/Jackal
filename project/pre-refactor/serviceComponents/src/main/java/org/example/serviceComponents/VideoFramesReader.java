package org.example.serviceComponents;

import org.example.PacketOut;
import org.example.Utils;
import org.example.serviceComponents.packets.LacksRequest;
import org.example.serviceComponents.packets.VideoFramePacket;
import org.example.serviceComponents.packets.VideoHeaderPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Reads asnychroniously incoming parts of videoframes
 * sort, request lacks, sending to outoutVideoStream (vlc, gstreamer, rtsp server, etc...)
 */
public class VideoFramesReader implements Closeable {
    private int width;
    private int height;
    private VideoStreamAcceptor acceptor;
    private OutgoingSender packetsSender;

    private static final Logger logger
            = LoggerFactory.getLogger(VideoFramesReader.class);

    private final Object lock = new Object();
    private final AtomicBoolean awaiting = new AtomicBoolean(false);
    private final AtomicBoolean shouldWork = new AtomicBoolean(true);
    private final AtomicBoolean pipeCreating = new AtomicBoolean(false);
    //incoming queue like 1 2 5 4 9 6
    private final Map<Integer, videoFramePackets> pendingPackets = new ConcurrentHashMap<>();
    private final Set<Integer> sentPackets = Collections.synchronizedSet(new HashSet<>());
    private volatile byte[] header;
    private volatile boolean headerSent;
    private static final int AWAIT_MS = 200;
    //1.5 Mbps if we have 10 packets with size 1000bytes
    // we should start be in trouble in 67ms
    private static final int BYTES_PER_MS = 150;
    private long lacksRequested;
    //frequency - how often we can request lacks
    private static final int LACK_REQUEST_PERIOD = 30;
    //how many times we can request lacks for this ID
    private static final int MAX_LACKS_REQUEST_TIMES = 1;
    //private volatile int lastSentId = 0;
    private AtomicInteger lastSentId = new AtomicInteger(0);
    private static final int SENT_PACKETS_HOLD_COUNT = 20;
    //we sent something and lastSentId set
    private volatile boolean chainStarted;
    private Thread reorderThread;

    public VideoFramesReader(int width, int height, VideoStreamAcceptor acceptor, OutgoingSender packetsSender) {
        this.width = width;
        this.height = height;
        acceptor.configureVideoAcceptor(width, height);
        this.acceptor = acceptor;
        this.packetsSender = packetsSender;

        reorderThread = new Thread(pipeRunnable, "reorderThread");
        reorderThread.setDaemon(true);
        reorderThread.start();
    }


    public void setHeader(byte[] packets, int packetOffset, int logId) {
        this.header = VideoHeaderPacket.getHeader(packets, packetOffset);
        logger.debug("Header set logId {}, {}", logId, Utils.toHexString(packets, packets.length));
    }

    /**
     * Does not block
     * @param packets
     * @param packetOffset
     * @param logId
     * @throws IOException
     */
    public void addFrame(byte[] packets, int packetOffset, int logId) throws IOException {
        packetDecorator p = new packetDecorator(packets, packetOffset, logId);
        if (this.header == null) {
            log(new videoFramePackets(p), "no header");
        } else if (sentPackets.contains(p.id)) {
            log(new videoFramePackets(p), "already sent");
        } else if (p.id > lastSentId.get()) {
            pendingPackets.computeIfAbsent(p.id, (id) -> new videoFramePackets(p))
                    .addPart(p);
            pipeNotify();
        } else {
            log(new videoFramePackets(p), "too-old or CRC incorrect");
        }

    }


    @Override
    public void close() throws IOException {
        shouldWork.set(false);
        pipeNotify();
    }

    private void pipeWait() throws InterruptedException {
        synchronized (lock) {
            awaiting.set(true);
            lock.wait();
            awaiting.set(false);
        }
    }

    private void pipeNotify() {
        if (awaiting.get()) {
            synchronized (lock) {
                if (awaiting.get()) {
                    lock.notify();
                }
            }
        }
    }

    private Runnable pipeRunnable = () -> {
        while (shouldWork.get()) {
            try {
                if (pendingPackets.isEmpty()) {
                    pipeWait();
                } else {
                    sendPacketsChainV3Smooth();
                    if (clearOldPackets()) {
                        chainStarted = false;
                    }
                    requestLackParts();
                }
                Thread.sleep(10);
            } catch (Exception e) {
                logger.error("main video loop", e);
            }
        }
    };

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
        if (!lackPartsOverAllVersions.isEmpty() && Utils.elapsed(LACK_REQUEST_PERIOD, lacksRequested)) {
            LacksRequest lacksRequest = new LacksRequest(lackPartsOverAllVersions);
            String description = lacksRequest.getDescription();
            packetsSender.send(new PacketOut(lacksRequest.toArray(), logId -> {
                logger.debug("LogID: {}. requesting lacks {}", logId, description);
            }));
            lacksRequested = Utils.nowMs();
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
            } else if (veryFirst.id == lastSentId.get() + 1) {
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
                    if (veryFirst != null && veryFirst.id == lastSentId.get() + 1
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
            packet.writeOut(acceptor);
            log(packet, "sent");
        }

        pendingPackets.remove(packet.id);
        if (lastSentId.get() < packet.id) {
            lastSentId.set(packet.id);
        }
    }

    private boolean clearOldPackets() {
        List<Integer> removeList = pendingPackets.values().stream().filter(p -> {
            if (p.isOld() || p.id < lastSentId.get()) {
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
                .filter(id -> id < lastSentId.get() - SENT_PACKETS_HOLD_COUNT)
                .collect(Collectors.toSet()));
        return haveSomeToDelete;
    }


    private void log(videoFramePackets p, String action) {
        String logIds = p.parts.stream().map(p1 -> String.valueOf(p1.logId))
                .collect(Collectors.joining(",", "", ""));
        String partIndexes = p.parts.stream().map(p1 -> String.valueOf(p1.partIndex))
                .collect(Collectors.joining(",", "", ""));
        long now = Utils.nowMs();
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
        volatile long creationTimestamp = Utils.nowMs();
        boolean isIframe;
        private final Set<packetDecorator> parts = Collections.synchronizedSet(new HashSet<>());
        int lacksRequestedTimes;

        public videoFramePackets(packetDecorator some) {
            this.id = some.id;
            this.partsCount = some.partsCount;
            approxSize = some.approxSize;
        }

        public void writeOut(VideoStreamAcceptor acceptor) {
            try {
                if (!headerSent) {
                    acceptor.writeVideoHeader(header, 0, header.length);
                    headerSent = true;
                }
                List<packetDecorator> packetParts = parts.stream()
                        .sorted(Comparator.comparingInt(packetDecorator::getIndex))
                        .collect(Collectors.toList());
                for (packetDecorator packetDecorator : packetParts) {
                    acceptor.writeVideoFrame(packetDecorator.id, packetDecorator.partIndex, packetDecorator.partsCount,
                            packetDecorator.buf, packetDecorator.videoDataOffset, packetDecorator.partLength);
                }
            } catch (Exception e) {
                logger.error("video piping", e);
            }
        }

        public void addPart(packetDecorator part) {
            parts.add(part);
            if (part.partIndex == 0) {
                if (VideoFramePacket.isIframe(part.buf, part.packetOffset)) {
                    logger.debug("I-Frame id {}, logId {}", part.id, part.logId);
                    isIframe = true;
                }
            }
            if (!isIframe && part.frameConfig.iFrame) {
                logger.warn("I-frame directly set, but it is not found by analyzer");
            }
            if (isIframe && !part.frameConfig.iFrame) {
                logger.warn("I-frame was found by analyzer, but defined directly by encoder");
            }
            isIframe = isIframe || part.frameConfig.iFrame;
        }

        public boolean isComplete() {
            return parts.size() >= partsCount;
        }

        boolean isOld() {
            return Utils.elapsed(AWAIT_MS, creationTimestamp);
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
            if (Utils.elapsed(rquiredMs, creationTimestamp)) {
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
        final int videoDataOffset;
        final int partLength;
        final int id;
        final byte[] buf;
        final int logId;
        final int packetOffset;
        final int partIndex;
        final int partsCount;
        final int approxSize;
        final VideoFramePacket.FrameConfig frameConfig;

        packetDecorator(byte[] buf, int packetOffset, int logId) {
            this.buf = buf;
            this.logId = logId;
            this.packetOffset = packetOffset;
            videoDataOffset = VideoFramePacket.getDataOffset(packetOffset);
            partLength = VideoFramePacket.getVideFrameSize(buf, packetOffset);
            id = VideoFramePacket.getPacketId(buf, packetOffset);
            partIndex = VideoFramePacket.getPartIndex(buf, packetOffset);
            partsCount = VideoFramePacket.getPartsCount(buf, packetOffset);
            approxSize = VideoFramePacket.getApproxSize(buf, packetOffset);
            frameConfig = VideoFramePacket.getFrameConfig(buf, packetOffset);
            if (id < 0) {
                logger.error("wrong id {} logId {} version dump {}", id, logId, Utils.toHexString(buf, VideoFramePacket.ID_OFFSET, 4));
            }
        }

        int getIndex() {
            return partIndex;
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
