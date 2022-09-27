package com.example.dataprovider.service;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.example.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Codec {

    // Used to load the 'myapplication' library on application startup.
    static {
        System.loadLibrary("openh264");
        System.loadLibrary("encoder");
    }

    private static final String tag = "encoder-java";
    private Logger logger = LoggerFactory.getLogger(Codec.class);
    private DataAcceptor dataAcceptor;
    private final ParcelFileDescriptor nativeRead;
    private final ParcelFileDescriptor javaWrite;
    private final ParcelFileDescriptor javaRead;
    private final ParcelFileDescriptor nativeWrite;
    private final ParcelFileDescriptor.AutoCloseOutputStream outputStream;
    private ParcelFileDescriptor.AutoCloseInputStream inputStream;
    private volatile boolean shouldWork = true;
    private final byte[] readBuffer = new byte[1024 * 1024];
    private int yuvValidateSize;
    private final byte startMarker = 0x45;
    private final static int readHeaderSize = 2 + 4;
    private final byte[] byteTimestamp = new byte[8];
    private final Thread readThread;

    public Codec(VideoConfig params, DataAcceptor dataAcceptor) throws IOException {
        this.dataAcceptor = dataAcceptor;
        yuvValidateSize = params.getWidth() * params.getHeight() * 3 / 2;

        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createPipe();
        nativeRead = pair[0];
        javaWrite = pair[1];
        pair = ParcelFileDescriptor.createPipe();
        javaRead = pair[0];
        nativeWrite = pair[1];

        init(params.getWidth(), params.getHeight(), params.getMaxFrameRate(), params.getTargetBitrate(),
                nativeRead.getFd(), nativeWrite.getFd());

        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(javaWrite);
        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(javaRead);

        readThread = new Thread(reader, "readerThread");
        readThread.setDaemon(true);
        readThread.start();
    }

    native void init(int width, int height,
                     int maxFrameRate, int targetBitrate,
                     int pipeIn, int pipeOut);

    native void disposeLowLevel();

    public synchronized void enqueueYUVImage(byte[] data, int dataSize, long timestamp) throws IOException {
        if (dataSize == yuvValidateSize) {
            outputStream.write(startMarker);
            longtoBytes(timestamp, byteTimestamp);
            outputStream.write(byteTimestamp);
            outputStream.write(data, 0, dataSize);
            outputStream.flush();
        } else {
            Log.e(tag, String.format("Wrong data size %d expected %d", dataSize, yuvValidateSize));
        }
    }

    private static void longtoBytes(long src, byte[] dst) {
        dst[7] = (byte) ((src >> 56) & 0xff);
        dst[6] = (byte) ((src >> 48) & 0xff);
        dst[5] = (byte) ((src >> 40) & 0xff);
        dst[4] = (byte) ((src >> 32) & 0xff);
        dst[3] = (byte) ((src >> 24) & 0xff);
        dst[2] = (byte) ((src >> 16) & 0xff);
        dst[1] = (byte) ((src >> 8) & 0xff);
        dst[0] = (byte) ((src) & 0xff);
    }

    private static int bytesToInt(byte[] src, int offset) {
        return Utils.bufToI32(src, offset);
    }

    /*
        while (1) {
        int readCount = read(pipeIn, framePtr + offset, packetSize == 0 ? VideoFrameHeaderSize :
                                                        packetSize - offset);
        offset += readCount;
        //check header read
        if (bodySize == 0 && framePtr[0] == VideoFrameMarker) {
            if (offset >= VideoFrameHeaderSize) {
                memcpy((byte *) &bodySize, framePtr + 1, VideoFrameBodySize);
                packetSize = bodySize + VideoFrameHeaderSize;
            } else {
                continue;
            }
        }
        if (offset == packetSize) {
            break;
        }
    }
     */
    Runnable reader = () -> {
        // 0x45, frameType, bodySize(4), data
        int packetSize = 0;
        int bodySize = 0;
        int offset = 0;

        while (shouldWork) {
            try {
                int dataRead = inputStream.read(readBuffer, offset, bodySize == 0 ? readHeaderSize : packetSize - offset);
                offset += dataRead;
                if (readBuffer[0] == startMarker) {
                    if (bodySize == 0 && offset >= readHeaderSize) {
                        bodySize = bytesToInt(readBuffer, 2);
                        packetSize = bodySize + readHeaderSize;
                    }
                } else {
                    //clear
                    inputStream.read(readBuffer, 0, readBuffer.length);
                    packetSize = 0;
                    bodySize = 0;
                    offset = 0;
                    continue;
                }

                if (offset == packetSize && offset > 0) {
                    dataAcceptor.onDataReady(VideoFrameTypes.of(readBuffer[1]), readBuffer, readHeaderSize, bodySize);
                    packetSize = 0;
                    bodySize = 0;
                    offset = 0;
                }
            } catch (Exception e) {
                logger.error("codec read loop", e);
            }
        }
    };


    public void dispose() {
        shouldWork=false;
        disposeLowLevel();
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
        readThread.interrupt();
    }
}
