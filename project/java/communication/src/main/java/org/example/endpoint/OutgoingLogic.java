package org.example.endpoint;

import org.example.CommonConfig;
import org.example.TimeUtils;
import org.example.communication.DataPipe;
import org.example.communication.logging.PostLogger;
import org.example.packets.AbstractPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * collects packets to send
 * 1 remember age of creation (of packet)
 * 2 analyze size for each
 * 3 combine small packets and send in a bunch [packet1, packet2, ...]
 * PacketReceiver will parse this sequence on another side
 */
public class OutgoingLogic implements OutgoingPacketCarrier, AutoCloseable {
    private static final int AWAIT_TIMEOUT = 50;
    private static final int QUEUE_CAPACITY = 300;
    private final static Logger logger = LoggerFactory.getLogger(OutgoingLogic.class);
    private DataPipe dataPipe;
    private boolean align;

    private final Object lock = new Object();
    private final LinkedList<PacketToSend> packetsToSend = new LinkedList<>();
    private Thread sendingThread;

    public OutgoingLogic(DataPipe dataPipe, boolean align) {
        this.dataPipe = dataPipe;
        this.align = align;
        sendingThread = new Thread(sendRunnable, "BunchPacking");
        sendingThread.setDaemon(true);
        sendingThread.start();
    }

    @Override
    public void packetWasBorn(AbstractPacket brandNew, PostLogger postLogger) {
        PacketToSend newOne = new PacketToSend(brandNew, postLogger);
        synchronized (lock) {
            if (packetsToSend.size() < QUEUE_CAPACITY) {
                packetsToSend.add(newOne);
                logger.debug("Packet enqueued for sending {}", newOne.brandNew.getDescription());
            } else {
                logger.warn("Outgoing queue full. (slow network may be)");
            }
            lock.notify();
        }
    }

    public void reset() {
        synchronized (lock) {
            packetsToSend.clear();
        }
    }

    private boolean isEmpty() {
        synchronized (lock) {
            return packetsToSend.isEmpty();
        }
    }

    private Runnable sendRunnable = () -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (isEmpty()) {
                    synchronized (lock) {
                        lock.wait(AWAIT_TIMEOUT);
                    }
                } else {
                    sendPendingPackets();
                }
            } catch (InterruptedException ignored) {
                return;
            } catch (Exception e) {
                logger.error("on sendingPackets", e);
            }
        }
    };

    private void sendPendingPackets() throws InterruptedException {
        BunchToSend bunch = getBunchToSend();

        Collection<PostLogger> loggers = bunch.targets.stream()
                .map(packetToSend -> packetToSend.postLogger)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        PostLogger compositeLogger = logId -> {
            loggers.forEach(logger -> {
                logger.logAttempHappen(logId);
            });
        };

        int bunchSize = align ? AbstractPacket.semicolon(bunch.totalSize) : bunch.totalSize;
        byte[] bunchBuf = new byte[bunchSize];
        if (logger.isDebugEnabled()) {
            fillBufferWithDebug(bunch, bunchBuf);
        } else {
            fillBuffer(bunch, bunchBuf);
        }
        dataPipe.sendData(bunchBuf, 0, bunchSize, compositeLogger);
        synchronized (lock) {
            packetsToSend.removeAll(bunch.targets);
        }
    }

    private void fillBufferWithDebug(BunchToSend bunch, byte[] bunchBuf) {
        int offset = 0;
        StringBuilder debugInfo = new StringBuilder();
        for (PacketToSend packet : bunch.targets) {
            packet.brandNew.toArray(bunchBuf, offset, packet.size);
            debugInfo.append(String.format("offset %04x size %d type %02x, Description: %s \n", offset, packet.size,
                    packet.brandNew.getPacketType(), packet.brandNew.getDescription()));
            offset += packet.size;
        }
        logger.debug("Sending in the bunchBuffer[{}] packets: \n {}", bunchBuf.length, debugInfo);
    }


    private void fillBuffer(BunchToSend bunch, byte[] bunchBuf) {
        int offset = 0;
        for (PacketToSend packet : bunch.targets) {
            packet.brandNew.toArray(bunchBuf, offset, packet.size);
            offset += packet.size;
        }
    }


    private BunchToSend getBunchToSend() {
        List<PacketToSend> candidates;
        synchronized (lock) {
            candidates = new ArrayList<>(packetsToSend);
        }
        //packets which we are going to send at once
        List<PacketToSend> targets = new ArrayList<>(candidates.size());

        int totalSize = 0;
        for (PacketToSend packet : candidates) {
            //when we split VideoFrame, we use PACKET_SIZE_PREFERRED, so we have space for some all packet else
            if (totalSize + packet.size < CommonConfig.PACKET_SIZE_MAX) {
                targets.add(packet);
                totalSize += packet.size;
            }
        }
        if (targets.isEmpty()) {
            PacketToSend oneTarget = candidates.get(0);
            targets.add(oneTarget);
        }

        return new BunchToSend(targets, totalSize);
    }


    @Override
    public void close() throws Exception {
        sendingThread.interrupt();
        synchronized (lock) {
            lock.notify();
        }
        sendingThread.join();
        reset();
    }

    private static class BunchToSend {
        final List<PacketToSend> targets;
        final int totalSize;

        private BunchToSend(List<PacketToSend> targets, int totalSize) {
            this.targets = targets;
            this.totalSize = totalSize;
        }
    }

    private static class PacketToSend implements Comparable<PacketToSend> {
        final AbstractPacket brandNew;
        final PostLogger postLogger;
        final long creationTime;
        final int size;

        private PacketToSend(AbstractPacket brandNew, PostLogger postLogger) {
            this.brandNew = brandNew;
            this.postLogger = postLogger;
            this.creationTime = TimeUtils.nowMs();
            this.size = brandNew.calculateSize();
        }

        @Override
        public int compareTo(PacketToSend o) {
            return Long.compare(creationTime, o.creationTime);
        }
    }
}
