package org.example.services.videoconsumer;

import org.example.ByteUtils;
import org.example.DataReference;
import org.example.packets.VideoHeaderPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class FramesSink implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(FramesSink.class);
    public static final int VIDEO_FRAMES_QUEUE_SIZE = 15;

    private final BlockingQueue<VideoFramePackets> framesQueue = new LinkedBlockingDeque<>(VIDEO_FRAMES_QUEUE_SIZE);
    private final Thread writingThread;

    private final VideoStreamAcceptor acceptor;
    private VideoHeaderPacket header;
    private boolean headerSent;


    public FramesSink(VideoStreamAcceptor acceptor) {
        this.acceptor = acceptor;
        writingThread = new Thread(this, "Piping videoFrame");
        writingThread.setDaemon(true);
        writingThread.start();
    }


    public void setHeader(byte[] packets, int packetOffset, Integer logId) {
        synchronized (this) {
            VideoHeaderPacket newHeader = VideoHeaderPacket.fromBuf(packets, packetOffset);
            if (this.header == null || !VideoHeaderPacket.dimensionEquals(newHeader, this.header)) {
                headerSent = false;
            }
            this.header = newHeader;
            notify();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Header set logId {}, w:h {} {} {}", logId, header.getWidth(), header.getHeight(),
                    ByteUtils.toHexString(packets, packetOffset, Math.min(packets.length - packetOffset, 20)));
        }
    }

    public void enqueue(final VideoFramePackets frame) {
        if (framesQueue.size()<VIDEO_FRAMES_QUEUE_SIZE){
            try {
                framesQueue.put(frame);
            } catch (InterruptedException ignored) {
            }
        }else{
            logger.warn("VideoFrame was dropped due queue full {}", frame);
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (this) {
                if (header == null) {
                    try {
                        //sleep 1
                        wait();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                if (!headerSent && header != null) {
                    try {
                        acceptor.configureVideoAcceptor(header.getWidth(), header.getHeight());
                        acceptor.writeVideoHeader(new DataReference(header.getHeaderBuf(), header.getHeaderOffset(), header.getHeaderLength()));
                        headerSent = true;
                    } catch (Exception e) {
                        logger.error("During header setup ", e);
                    }
                }
            }
            if (headerSent) {
                try {
                    //sleep 2
                    VideoFramePackets frame = framesQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        writeOut(frame);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            } else if (!framesQueue.isEmpty()) {
                logger.warn("framesQueue {} items cleared due header absent", framesQueue.size());
                framesQueue.clear();
            }
        }
    }

    private void writeOut(final VideoFramePackets frame) {
        try {

            List<PacketDecorator> packetParts = frame.getParts().stream()
                    .sorted(Comparator.comparingInt(PacketDecorator::getIndex))
                    .collect(Collectors.toList());

            for (PacketDecorator packetDecorator : packetParts) {
                try {
                    acceptor.writeVideoFrame(packetDecorator.id, packetDecorator.partIndex, packetDecorator.partsCount,
                            new DataReference(packetDecorator.getBuffer(), packetDecorator.getVideDataOffset(),
                                    packetDecorator.getVideDataLength()));
                } catch (Exception e) {
                    logger.error("During frame send {} ", packetDecorator.toString(), e);
                }
            }
        } catch (Exception e) {
            logger.error("video piping", e);
        }
    }


    public void close() {
        if (writingThread.isAlive()){
            writingThread.interrupt();
            framesQueue.clear();
        }
    }
}
