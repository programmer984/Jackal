package org.example.communication.logging;

import org.example.PathUtils;
import org.example.packets.LogFilePacketMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class FileSystemPacketsLogger implements DataLogger, Runnable {
    private static final Logger logger
            = LoggerFactory.getLogger(FileSystemPacketsLogger.class);
    private AtomicInteger counter = new AtomicInteger();
    private static final int maxFileSize = 10 * 1024 * 1024;
    private static final int maxQueueSize = 100;
    private Thread pipeThread;
    private final BlockingQueue<packetsBunch> packetsQueue = new LinkedBlockingDeque<>(maxQueueSize);
    private String currentPath;
    private final String baseDir;
    private Object joinLock = new Object();


    public FileSystemPacketsLogger(String baseDir) {
        this.baseDir = baseDir;
        pipeThread = new Thread(this, "PacketsLogger");
        pipeThread.setDaemon(true);
        pipeThread.start();
    }

    @Override
    public Integer addIncomingBunch(byte[] data, int offset, int length) {
        if (packetsQueue.size() < maxQueueSize) {
            packetsBunch packetsBunch = new packetsBunch(Direction.IN, data, offset, length);
            if (packetsQueue.offer(packetsBunch)) {
                return packetsBunch.number;
            }
            logger.warn("incoming log queue full {}", packetsBunch.number);
            return null;
        }
        logger.warn("incoming log queue full");
        return null;
    }

    @Override
    public void addOutgoingBunch(byte[] data, int offset, int length, PostLogger postLogger) {
        if (packetsQueue.size() < maxQueueSize) {
            packetsBunch packetsBunch = new packetsBunch(Direction.OUT, data, offset, length, postLogger);
            if (!packetsQueue.offer(packetsBunch)) {
                logger.warn("outgoing log queue full {}", packetsBunch.number);
                if (postLogger != null) {
                    postLogger.logAttempHappen(null);
                }
            }
        } else {
            logger.warn("outgoing log queue full");
            if (postLogger != null) {
                postLogger.logAttempHappen(null);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return packetsQueue.isEmpty();
    }

    @Override
    public void join() throws InterruptedException {
        while (!isEmpty() && pipeThread.isAlive()) {
            synchronized (joinLock) {
                joinLock.wait();
            }
        }
    }

    @Override
    public void run() {
        FileOutputStream fileOutputStream = null;
        int fileNumber = 1;
        int bytesWritten = 0;
        byte[] markerBuf = new byte[32];
        try {
            while (Thread.currentThread().isAlive()) {
                packetsBunch p = packetsQueue.take();

                if (currentPath == null) {
                    Date date = new Date();
                    currentPath = PathUtils.resolve(baseDir,
                            String.format("%d-%d", date.getHours(), date.getMinutes()));
                    new File(currentPath).mkdirs();
                }
                try {
                    //create output file
                    if (fileOutputStream == null) {
                        File f = new File(PathUtils.resolve(currentPath, String.format("%d.bin", fileNumber++)));
                        fileOutputStream = new FileOutputStream(f);
                    } else if (bytesWritten >= maxFileSize) {
                        fileOutputStream.close();
                        File f = new File(PathUtils.resolve(currentPath, String.format("%d.bin", fileNumber++)));
                        fileOutputStream = new FileOutputStream(f);
                        bytesWritten = 0;
                    }

                    //writting marker of the packet
                    int logMarkerSize = p.fillLogMarkerBuf(markerBuf);
                    fileOutputStream.write(markerBuf, 0, logMarkerSize);
                    bytesWritten += logMarkerSize;

                    //writing actual packet
                    fileOutputStream.write(p.value, p.offset, p.length);
                    bytesWritten += p.length;

                    //log if there is attached logger
                    if (p.postLogger != null) {
                        p.postLogger.logAttempHappen(p.number);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //required for buffers reusing (notify about we done)
                if (isEmpty()) {
                    synchronized (joinLock) {
                        joinLock.notify();
                    }
                }
            }
        } catch (InterruptedException ignored) {

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    logger.error("Logger disposing", e);
                }
            }
        }
    }


    private enum Direction {
        IN,
        OUT
    }

    private class packetsBunch {
        Direction direction;
        int number;
        byte[] value;
        int offset;
        int length;
        PostLogger postLogger;
        LogFilePacketMarker marker;

        public packetsBunch(Direction direction, byte[] value, int offset, int length) {
            this(direction, value, offset, length, null);
        }

        public packetsBunch(Direction direction, byte[] value, int offset, int length, PostLogger postLogger) {
            this.direction = direction;
            this.number = counter.addAndGet(1);
            this.value = value;
            this.offset = offset;
            this.length = length;
            this.postLogger = postLogger;
            this.marker = new LogFilePacketMarker(number, direction.name());
        }

        public int fillLogMarkerBuf(byte[] buf) {
            int writtenSize = marker.calculateSize();
            marker.toArray(buf, 0, writtenSize);
            return writtenSize;
        }
    }
}
