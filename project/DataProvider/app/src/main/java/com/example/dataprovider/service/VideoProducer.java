package com.example.dataprovider.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class VideoProducer {
    private ImageProducer imageProducer;
    private Codec codec;
    private final static byte videoFrameHeaderStart = 0x45;
    private final static int videoFrameHeaderSize = 2;
    private VideoConfig config;
    private Thread samplerThread;
    private int frameRateMs;
    private Logger logger = LoggerFactory.getLogger("VideoProducer");
    private volatile boolean shouldWork = true;
    private DataAcceptor videoFrameAcceptor;

    public VideoProducer(ImageProducer imageProducer, VideoConfig config, DataAcceptor videoFrameAcceptor) {
        this.videoFrameAcceptor = videoFrameAcceptor;
        this.imageProducer = imageProducer;
        this.config = config;

        frameRateMs = 1000 / config.maxFrameRate;


        samplerThread = new Thread(sampler, "samplerThread");
        samplerThread.setDaemon(true);
        samplerThread.start();
        logger.debug("VideoProducer created");
    }



    private Runnable sampler = () -> {

        try {
            long startSendingMs = 0;
            long stopSendingMs = 0;
            while (shouldWork) {
                startSendingMs = System.currentTimeMillis();
                YUVImage image = imageProducer.getNextImage();
                if (codec == null && imageProducer.isInitialized()) {
                    config.setWidth(imageProducer.getWidth());
                    config.setHeight(imageProducer.getHeight());
                    if (imageProducer.getWidth() != config.getWidth() ||
                            imageProducer.getHeight() != config.getHeight()) {
                        logger.warn("Difference between image size and codec config {}/{} {}/{}",
                                imageProducer.getWidth(), config.getWidth(),
                                imageProducer.getHeight(), config.getHeight());
                        config.setWidth(imageProducer.getWidth());
                        config.setHeight(imageProducer.getHeight());
                    }
                    codec = new Codec(config, videoFrameAcceptor);
                } else if (image != null && codec != null) {
                    codec.enqueueYUVImage(image.buffer, image.buffer.length, image.timestamp);
                    stopSendingMs = System.currentTimeMillis();
                }
                if (image != null) {
                    imageProducer.freeImage(image);
                }
                if (stopSendingMs > startSendingMs) {
                    long deltaMs = frameRateMs - (stopSendingMs - startSendingMs);
                    if (deltaMs > 0) {
                        Thread.sleep(deltaMs);
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error("In loop", e);
        }
    };


    public void dispose() {
        shouldWork = false;
        codec.dispose();
        samplerThread.interrupt();
        imageProducer.dispose();
        logger.debug("VideoProducer disposed");
    }
}
