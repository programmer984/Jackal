package org.example.services.videoproducer.codec;

import org.example.*;
import org.example.communication.logging.DataLogger;
import org.example.communication.logging.FileSystemPacketsLogger;
import org.example.communication.logging.NoLogger;
import org.example.packetsReceiver.*;
import org.example.services.videoproducer.YUVImage;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * converts YUV image to video frame
 * send YUVImage with following format (through Outputstream unix pipe)
 * START_TOKEN[3], timestamp[8], body[uvValidateSize]
 * receive VideoFrame back with following format (through InputStream unix pipe)
 * START_TOKEN[3], frameType[1], bodySize[4], body[bodySize]
 */
public abstract class Codec implements AutoCloseable {

    private final static Logger logger = LoggerFactory.getLogger(Codec.class);
    private final int videFrameMaxSize = 1024 * 1024;

    private VideoFrameConsumer videoFrameConsumer;
    protected OutputStream outputStream;
    protected InputStream inputStream;

    private ProtocolHandler receivingVideFrameProtocolHandler = new CodecNativeProtocolHandler(videFrameMaxSize);
    private final int yuvValidateSize;
    private final byte[] byteTimestamp = new byte[8];
    private TimersManager timersManager;
    private Thread readThread;
    private static final int READY_FRAMES_QUEUE_SIZE = 2;
    private Dispatcher readyFramesSendDispatcher = new Dispatcher(READY_FRAMES_QUEUE_SIZE, "readyFramesSendDispatcher");
    private DataLogger nativeLogger;

    public Codec(int width, int height, VideoFrameConsumer videoFrameConsumer, TimersManager timersManager) throws Exception {
        this.videoFrameConsumer = videoFrameConsumer;
        yuvValidateSize = YUVUtils.calculateBufferSize(width, height);
        this.timersManager = timersManager;
        if (CommonConfig.logCodecPackets) {
            nativeLogger = new FileSystemPacketsLogger(PathUtils.resolve(CommonConfig.packetsDir, "native-codec"));
        } else {
            nativeLogger = new NoLogger();
        }
    }

    protected void startReadReadyFrameThread() {
        readThread = new Thread(reader, "readerThread");
        readThread.setDaemon(true);
        readThread.start();
    }


    public void enqueueYUVImage(YUVImage image) throws IOException {
        if (readThread == null || !readThread.isAlive()) {
            logger.warn("images writing to outputstream but not reading back (memory overflow about to happen). call startReadReadyFrameThread()");
        }
        if (image.getDataSize() == yuvValidateSize) {
            outputStream.write(CodecNativeProtocolHandler.START_TOKEN, 0, CodecNativeProtocolHandler.START_TOKEN.length);
            ByteUtils.u64ToBuf(image.getTimestamp(), byteTimestamp, 0);
            outputStream.write(byteTimestamp);
            outputStream.write(image.getBuffer(), 0, image.getDataSize());
            outputStream.flush();
        } else {
            logger.error("Wrong data size {} expected {}", image.getDataSize(), yuvValidateSize);
        }
    }

    private OnePacketConsumer pipeFrameConsumer = (data, offset, size, logId) -> {
        if (!readyFramesSendDispatcher.isFull()) {
            VideoFrameTypes type = VideoFrameTypes.of(data[offset + CodecNativeProtocolHandler.FRAME_TYPE_OFFSET]);
            int bodySize = ByteUtils.bufToI32(data, offset + CodecNativeProtocolHandler.BODY_LENGTH_OFFSET);
            byte[] body = new byte[bodySize];
            //copy packetReceiver's shared buffer
            ByteUtils.bufToBuf(data, CodecNativeProtocolHandler.BODY_OFFSET, bodySize, body, 0);
            final VideoFrame readyFrame = new VideoFrame(type, body);
            logger.debug("Videoframe ready on logId {}", logId);
            readyFramesSendDispatcher.submitBlocking(() -> videoFrameConsumer.accept(readyFrame));
        } else {
            logger.warn("Ready videoFrames receiving slow");
        }
    };

    private final CommunicationDriver communicationDriver = () -> {
        //  100/ms
        return 100;
    };

    private Runnable reader = () -> {
        boolean currentBufIs1 = false;
        final byte[] readBuffer1 = new byte[10000];
        final byte[] readBuffer2 = new byte[10000];
        final PacketsReceiverStreamCollector packetsReceiver = new PacketsReceiverStreamCollector(timersManager, receivingVideFrameProtocolHandler,
                communicationDriver, pipeFrameConsumer, videFrameMaxSize, 10);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                byte[] readBuffer = currentBufIs1 ? readBuffer2 : readBuffer1;
                currentBufIs1 = !currentBufIs1;

                int dataRead = inputStream.read(readBuffer, 0, readBuffer.length);
                Integer logId = nativeLogger.addIncomingBunch(readBuffer, 0, dataRead);
                long startMs = TimeUtils.nowMs();
                packetsReceiver.onNewDataReceived(readBuffer, 0, dataRead, logId);
                nativeLogger.join();
                long stopMs = TimeUtils.nowMs();
                logger.debug("Data pushing and join took {} ms", stopMs - startMs);
            } catch (Exception e) {
                logger.error("codec read loop", e);
            }
        }
    };


    protected abstract void disposeLowLevel();

    public void close() throws Exception {
        readThread.interrupt();
        try {
            disposeLowLevel();
        } catch (Exception e) {
            logger.error("native dispose", e);
        }
        try {
            inputStream.close();
        } catch (IOException e) {
            logger.error("codec dispose", e);
        }
        try {
            outputStream.close();
        } catch (IOException e) {
            logger.error("codec dispose", e);
        }
    }
}
