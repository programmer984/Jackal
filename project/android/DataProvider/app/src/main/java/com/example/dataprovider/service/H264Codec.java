package com.example.dataprovider.service;

import android.os.ParcelFileDescriptor;
import org.example.services.videoproducer.codec.Codec;
import org.example.services.videoproducer.codec.VideoFrameConsumer;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class H264Codec extends Codec {

    // Used to load the 'myapplication' library on application startup.
    static {
        System.loadLibrary("openh264");
        System.loadLibrary("encoder");
    }

    private static final String tag = "encoder-java";
    private Logger logger = LoggerFactory.getLogger(H264Codec.class);

    private final ParcelFileDescriptor nativeRead;
    private final ParcelFileDescriptor javaWrite;
    private final ParcelFileDescriptor javaRead;
    private final ParcelFileDescriptor nativeWrite;


    public H264Codec(int width, int height, int maxFrameRate, int targetBitrate,
                     VideoFrameConsumer videoFrameConsumer, TimersManager timersManager) throws Exception {
        super(width, height, videoFrameConsumer, timersManager);

        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createPipe();
        nativeRead = pair[0];
        javaWrite = pair[1];
        pair = ParcelFileDescriptor.createPipe();
        javaRead = pair[0];
        nativeWrite = pair[1];

        init(width, height, maxFrameRate, targetBitrate,
                nativeRead.getFd(), nativeWrite.getFd());

        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(javaWrite);
        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(javaRead);

        startReadReadyFrameThread();
    }

    native void init(int width, int height,
                     int maxFrameRate, int targetBitrate,
                     int pipeIn, int pipeOut);

    protected native void disposeLowLevel();


}
