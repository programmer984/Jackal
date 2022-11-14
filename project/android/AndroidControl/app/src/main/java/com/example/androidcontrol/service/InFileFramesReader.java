package com.example.androidcontrol.service;

import com.example.androidcontrol.VideoDirector;
import com.example.androidcontrol.video.Decoder;

import org.example.ByteUtils;
import org.example.DataReference;
import org.example.packetsReceiver.PacketRecevingResult;
import org.example.packetsReceiver.PacketRecevingResultStates;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.packetsReceiver.ProtocolHandler;
import org.example.services.DistributionService;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class InFileFramesReader implements AutoCloseable {
    private static final int bufSize = 100000;
    private static final int TLC_LENGTH = 8;
    private static final int BODY_LENGTH_OFFSET = 4;
    private static final int BODY_OFFSET = TLC_LENGTH;
    private byte[] token = new byte[]{0x45, 0x45, 0x47};
    private final Logger logger = LoggerFactory.getLogger(InFileFramesReader.class);
    private VideoDirector director;
    private InputStream inputStream;
    private TimersManager timersManager;
    private final Object timerLock=new Object();
    private SoftTimer softTimer;
    private Thread thread;
    private int pipingFrameIndex;

    private List<DataReference> foundVideoFrames = new ArrayList<>();


    private ProtocolHandler framesAnalizer = new ProtocolHandler() {
        PacketRecevingResult result = new PacketRecevingResult();

        @Override
        public int findRelativeStartPosition(byte[] bytes, int offset, int length) {
            return ByteUtils.searchSequense(bytes, offset, token);
        }

        @Override
        public int getBytesCountForRequiredForStartSearch() {
            return token.length;
        }

        @Override
        public PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length) {
            result.setResultState(PacketRecevingResultStates.INCOMPLETE);
            if (length >= TLC_LENGTH) {
                int bodySize = ByteUtils.bufToI32(data, offset + BODY_LENGTH_OFFSET);
                if (length >= bodySize + TLC_LENGTH) {
                    result.setResultState(PacketRecevingResultStates.COMPLETE);
                    result.setSize(bodySize + TLC_LENGTH);
                }
            }
            return result;
        }

        @Override
        public int getApproximatePacketSize(byte[] bytes, int i, int i1) {
            return 1000;
        }

        @Override
        public void resetReceivingState() {

        }
    };

    private Runnable reader = () -> {
        try {
            PacketsReceiverStreamCollector packetsReceiverStreamCollector = new PacketsReceiverStreamCollector(
                    timersManager, framesAnalizer, () -> {
                return 2;
            }, (buf, offset, length, logId) -> {
                byte[] copy = ByteUtils.copyBytes(buf, offset + TLC_LENGTH, length - TLC_LENGTH);
                foundVideoFrames.add(new DataReference(copy));
            }, bufSize);

            int tmpBufferSize = 1024;
            byte[] tmpBuffer = new byte[tmpBufferSize];
            int readCount = 0;
            do {
                readCount = inputStream.read(tmpBuffer, 0, tmpBufferSize);
                if (readCount > 0) {
                    packetsReceiverStreamCollector.onNewDataReceived(tmpBuffer, 0, readCount);
                }
            } while (readCount > 0 && !Thread.currentThread().isInterrupted());
            inputStream.close();

            director.configureVideoAcceptor(320, 240);
            director.writeVideoHeader(foundVideoFrames.get(0));
            synchronized (timerLock) {
                softTimer = timersManager.addTimer(1000 / 25, true, () -> {
                    DataReference nextFrame = foundVideoFrames.get(pipingFrameIndex++);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sending frame from a file {} {}", pipingFrameIndex, nextFrame.getLength());
                    }
                    director.writeVideoFrame(pipingFrameIndex, 0, 1, nextFrame);
                    if (pipingFrameIndex == foundVideoFrames.size()) {
                        pipingFrameIndex = 0;
                    }
                }, "PreloadFramesPusher");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    };


    public InFileFramesReader(VideoDirector director, InputStream inputStream, TimersManager timersManager) {
        this.director = director;
        this.inputStream = inputStream;
        this.timersManager = timersManager;
    }


    public void startReading() {
        thread = new Thread(reader);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() throws Exception {
        if (thread != null) {
            thread.interrupt();
        }
        if (inputStream != null) {
            inputStream.close();
        }
        if (thread != null) {
            thread.join();
        }
        synchronized (timerLock) {
            if (softTimer != null) {
                timersManager.removeTimer(softTimer);
                softTimer = null;
            }
        }
        foundVideoFrames.clear();
        thread = null;
        inputStream = null;
    }
}
