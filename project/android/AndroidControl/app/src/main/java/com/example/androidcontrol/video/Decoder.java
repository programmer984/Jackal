package com.example.androidcontrol.video;

import android.os.ParcelFileDescriptor;


import org.example.ByteUtils;
import org.example.DataReference;
import org.example.YUVUtils;
import org.example.packetsReceiver.OnePacketConsumer;
import org.example.packetsReceiver.PacketsReceiverStreamCollector;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/*
accepts h264 frame
returns YUV images
 */
public class Decoder implements AutoCloseable {

    static {
        System.loadLibrary("openh264");
        System.loadLibrary("decoder");
    }

    private final Logger logger = LoggerFactory.getLogger(Decoder.class);

    private ImageAcceptor imageAcceptor;
    private final ParcelFileDescriptor nativeRead;
    private final ParcelFileDescriptor javaWrite;
    private final ParcelFileDescriptor javaRead;
    private final ParcelFileDescriptor nativeWrite;
    private final ParcelFileDescriptor.AutoCloseOutputStream outputStream;
    private ParcelFileDescriptor.AutoCloseInputStream inputStream;

    private final byte[] readBuffer = new byte[1024];
    private final static int headerLength = 8;
    private final byte[] startMarker = new byte[]{0x35, 0x11, (byte) 0x89, 0x14};
    private final byte[] sizeBuf = new byte[4];


    private int yuvValidateSize;
    private PacketsReceiverStreamCollector packetsReceiver;
    private int with;
    private int height;
    private Thread readThread;

    public Decoder(ImageAcceptor imageAcceptor, int width, int height, TimersManager timersManager) throws IOException {
        this.with = width;
        this.height = height;
        this.imageAcceptor = imageAcceptor;
        this.yuvValidateSize = YUVUtils.calculateBufferSize(width, height);


        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createPipe();
        nativeRead = pair[0];
        javaWrite = pair[1];
        pair = ParcelFileDescriptor.createPipe();
        javaRead = pair[0];
        nativeWrite = pair[1];

        initDecoder(width, height, nativeRead.getFd(), nativeWrite.getFd());

        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(javaWrite);
        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(javaRead);

        packetsReceiver = new PacketsReceiverStreamCollector(timersManager, new CodecNativeProtocolHandler(yuvValidateSize),
                () -> 1, onePacketConsumer, CodecNativeProtocolHandler.incomingHeaderLength + yuvValidateSize, 10);

        readThread = new Thread(reader, "readerThread");
        readThread.setDaemon(true);
        readThread.start();
    }

    native void initDecoder(int width, int height, int pipeIn, int pipeOut);

    native void destroyDecoder();

    public synchronized void enqueueFrame(DataReference dataReference) throws IOException {
        ByteUtils.i32ToBuf(dataReference.getLength() + headerLength, sizeBuf, 0);
        outputStream.write(startMarker);
        outputStream.write(sizeBuf);
        outputStream.write(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength());
        outputStream.flush();
    }


    private OnePacketConsumer onePacketConsumer = (buf, offset, length, logId) -> {
        imageAcceptor.onImageDecoded(buf, CodecNativeProtocolHandler.incomingHeaderLength, yuvValidateSize);
    };

    private final Runnable reader = () -> {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int dataRead = inputStream.read(readBuffer, 0, readBuffer.length);
                packetsReceiver.onNewDataReceived(readBuffer, 0, dataRead);
            }  catch (Exception e) {
                logger.error("during image reading", e);
            }
        }
    };

    @Override
    public void close() throws Exception {
        if (readThread!=null){
            readThread.interrupt();
            readThread = null;
        }
        if (inputStream!=null){
            destroyDecoder();
            synchronized (this){
                outputStream.write(new byte[]{0,0,0,0});
                outputStream.flush();
            }
            outputStream.close();
            inputStream.close();
            inputStream = null;
            Thread.sleep(10);
        }
        logger.info("Decoder destroyed");
    }

    public boolean isConfiguredBy(int width, int height) {
        return this.with == width && this.height == height;
    }

}
