package org.example.services.videoproducer;

import org.example.Dispatcher;
import org.example.services.videoproducer.codec.Codec;
import org.example.services.videoproducer.codec.CodecCreator;
import org.example.services.videoproducer.codec.VideoFrameConsumer;
import org.example.softTimer.SoftTimer;
import org.example.softTimer.TimerCallback;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * periodically (framerate) takes images from image producer and convert to video frame using Codec
 */
public class VideoProducer implements AutoCloseable {
    private final static Logger logger = LoggerFactory.getLogger(VideoProducer.class);
    private TimersManager timersManager;
    private ImageProducer imageProducer;
    private VideoConfig videoConfig;
    private SoftTimer nextFrameTimer;
    private Dispatcher dispatcher;
    private CodecCreator codecCreator;
    private VideoFrameConsumer videoFrameConsumer;
    private Codec codec;
    private ImageSize actualSize;

    public VideoProducer(ImageProducer imageProducer, VideoConfig videoConfig,
                         VideoFrameConsumer videoFrameConsumer, TimersManager timersManager, CodecCreator codecCreator) {
        this.imageProducer = imageProducer;
        this.videoFrameConsumer = videoFrameConsumer;
        this.timersManager = timersManager;
        this.codecCreator = codecCreator;
        this.videoConfig = videoConfig;
        dispatcher = new Dispatcher(1, "ImageToFrameConvertThread");
        nextFrameTimer = timersManager.addTimer(1000 / videoConfig.getMaxFrameRate(), true, nextFrameInvoke);
    }


    private Runnable createNextFrame = () -> {
        YUVImage nextImage = imageProducer.getFreshImageOrNull();
        try {
            //if there is next image and acceptor of the
            if (nextImage != null && videoFrameConsumer != null) {
                if (codec == null) {
                    codec = codecCreator.createCodec(nextImage.getWidth(), nextImage.getHeight(), videoFrameConsumer);
                    setActualImageSize(new ImageSize(nextImage.getWidth(), nextImage.getHeight()));
                }
                codec.enqueueYUVImage(nextImage);
            }
        } catch (IOException e) {
            logger.error("During yuvImage enqueue", e);
        } finally {
            if (nextImage != null) {
                imageProducer.freeImage(nextImage);
            }
        }
    };

    private TimerCallback nextFrameInvoke = () -> {
        if (!dispatcher.isEmpty()) {
            logger.warn("too high frame rate for this codec");
            return;
        }
        dispatcher.submitBlocking(createNextFrame);
    };


    @Override
    public void close() throws Exception {
        if (nextFrameTimer != null) {
            timersManager.removeTimer(nextFrameTimer);
            nextFrameTimer = null;
        }
        if (codec != null) {
            codec.close();
            codec = null;
        }
        dispatcher.close();
        timersManager = null;
        dispatcher = null;
    }

    public synchronized ImageSize getActualImageSize() {
        return actualSize;
    }

    private synchronized void setActualImageSize(ImageSize actualSize) {
        this.actualSize = actualSize;
    }
}
