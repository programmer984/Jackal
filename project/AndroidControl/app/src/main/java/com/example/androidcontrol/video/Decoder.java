package com.example.androidcontrol.video;

import android.os.ParcelFileDescriptor;
import android.util.Log;


import java.io.IOException;


/*
accepts h264 frame
returns YUV images
 */
public class Decoder {

    static {
        System.loadLibrary("openh264");
        System.loadLibrary("decoder");
    }

    private static final String tag = "decoder-java";

    private ImageAcceptor imageAcceptor;
    private final ParcelFileDescriptor nativeRead;
    private final ParcelFileDescriptor javaWrite;
    private final ParcelFileDescriptor javaRead;
    private final ParcelFileDescriptor nativeWrite;
    private final ParcelFileDescriptor.AutoCloseOutputStream outputStream;
    private ParcelFileDescriptor.AutoCloseInputStream inputStream;
    private volatile boolean shouldWork = true;
    private byte[] readBuffer;
    private final byte[] size = new byte[2];
    private int yuvValidateSize;
    private final byte startMarker = 0x45;

    public Decoder(ImageAcceptor imageAcceptor, int width, int height) throws IOException {
        this.imageAcceptor = imageAcceptor;
        this.yuvValidateSize = width * height * 3 / 2;
        readBuffer = new byte[yuvValidateSize];

        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createPipe();
        nativeRead = pair[0];
        javaWrite = pair[1];
        pair = ParcelFileDescriptor.createPipe();
        javaRead = pair[0];
        nativeWrite = pair[1];

        initDecoder(width, height, nativeRead.getFd(), nativeWrite.getFd());

        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(javaWrite);
        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(javaRead);

        Thread readThread = new Thread(reader, "readerThread");
        readThread.setDaemon(true);
        readThread.start();
    }

    native void initDecoder(int width, int height, int pipeIn, int pipeOut);

    public synchronized void enqueueFrame(byte[] data, int offset, int dataSize) throws IOException {
        outputStream.write(startMarker);
        shortToBytes(dataSize, size);
        outputStream.write(size);
        outputStream.write(data, offset, dataSize);
        outputStream.flush();
    }

    private static void shortToBytes(int src, byte[] dst) {
        dst[1] = (byte) ((src >> 8) & 0xff);
        dst[0] = (byte) ((src) & 0xff);
    }

    private final Runnable reader = () -> {
        int offset = 0;
        while (shouldWork) {
            try {
                int dataRead = inputStream.read(readBuffer, offset, readBuffer.length - offset);
                offset += dataRead;
                if (yuvValidateSize == offset) {
                    imageAcceptor.onImageDecoded(0, yuvValidateSize, readBuffer);
                    offset = 0;
                } else {
                    Log.e(tag, String.format("wrong yuv read at once %d", dataRead));
                }
            } catch (IOException e) {
                e.printStackTrace();
                shouldWork = false;
            }
        }
    };

}
