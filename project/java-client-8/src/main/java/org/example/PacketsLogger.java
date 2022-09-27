package org.example;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class PacketsLogger implements Runnable {
    private static final Logger logger
            = LoggerFactory.getLogger("PacketsLogger");
    private AtomicInteger counter = new AtomicInteger();
    private static final int maxQueueSize = 100;
    private Thread pipeThread;
    private BlockingQueue<packetsBunch> packetsQueue = new LinkedBlockingDeque<>(maxQueueSize);
    private volatile String currentPath;

    private boolean shouldLog = Configuration.logPackets;
    private String baseDir = Configuration.packetsDir;

    public PacketsLogger() {
        if (shouldLog) {
            pipeThread = new Thread(this, "PacketsLogger");
            pipeThread.setDaemon(true);
            pipeThread.start();
        }
    }

    public int addIncomingBunch(byte[] data) {
        if (shouldLog && packetsQueue.size() < maxQueueSize) {
            packetsBunch packetsBunch = new packetsBunch(Direction.IN, data);
            packetsQueue.add(packetsBunch);
            return packetsBunch.number;
        }
        return -1;
    }

    public int addOutgoingBunch(PacketOut packetOut) {
        if (shouldLog && packetsQueue.size() < maxQueueSize) {
            packetsBunch packetsBunch = new packetsBunch(Direction.OUT, packetOut.getData(), packetOut.getPostLogger());
            packetsQueue.add(packetsBunch);
            return packetsBunch.number;
        }
        if (packetOut.getPostLogger()!=null){
            try{
                packetOut.getPostLogger().onFileWasWritten(-1);
            }catch (Exception ex){
                logger.error("during logging", ex);
            }
        }
        return -1;
    }

    public void reset() {
        currentPath = null;
    }

    @Override
    public void run() {
        try {
            while (Thread.currentThread().isAlive()) {
                packetsBunch p = packetsQueue.take();

                String path = currentPath;
                if (path == null) {
                    Date date = new Date();
                    path = FilenameUtils.concat(baseDir, String.format("%d-%d", date.getHours(), date.getMinutes()));
                    new File(path).mkdirs();
                    currentPath = path;
                }

                File f = new File(FilenameUtils.concat(path, String.format("%08d - %s.bin", p.number, p.direction)));
                try (FileOutputStream fileOutputStream = new FileOutputStream(f)) {
                    fileOutputStream.write(p.value);
                    if (p.postLogger != null) {
                        p.postLogger.onFileWasWritten(p.number);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
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
        PostLogger postLogger;

        public packetsBunch(Direction direction, byte[] value) {
            this(direction, value, null);
        }

        public packetsBunch(Direction direction, byte[] value, PostLogger postLogger) {
            this.direction = direction;
            this.number = counter.addAndGet(1);
            this.value = value;
            this.postLogger = postLogger;
        }
    }
}
