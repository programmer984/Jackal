package org.example.communication.logging;

import org.example.PathUtils;
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
            packetsQueue.add(packetsBunch);
            return packetsBunch.number;
        }
        logger.warn("incoming queue full");
        return null;
    }

    @Override
    public void addOutgoingBunch(byte[] data, int offset, int length, PostLogger postLogger) {
        if (packetsQueue.size() < maxQueueSize) {
            packetsBunch packetsBunch = new packetsBunch(Direction.OUT, data, offset, length, postLogger);
            packetsQueue.add(packetsBunch);
        } else {
            logger.warn("incoming queue full");
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
        try {
            while (Thread.currentThread().isAlive()) {
                packetsBunch p = packetsQueue.take();

                if (currentPath == null) {
                    Date date = new Date();
                    currentPath = PathUtils.resolve(baseDir,
                            String.format("%d-%d", date.getHours(), date.getMinutes()));
                    new File(currentPath).mkdirs();
                }

                File f = new File(PathUtils.resolve(currentPath, String.format("%08d - %s.bin", p.number, p.direction)));
                try (FileOutputStream fileOutputStream = new FileOutputStream(f)) {
                    fileOutputStream.write(p.value, p.offset, p.length);
                    if (p.postLogger != null) {
                        p.postLogger.logAttempHappen(p.number);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (isEmpty()) {
                    synchronized (joinLock) {
                        joinLock.notify();
                    }
                }
            }
        } catch (InterruptedException ignored) {

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
        }
    }
}
