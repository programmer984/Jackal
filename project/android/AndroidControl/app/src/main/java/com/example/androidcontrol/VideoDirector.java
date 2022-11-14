package com.example.androidcontrol;

import android.content.Context;
import android.view.View;

import com.example.androidcontrol.video.Decoder;
import com.example.androidcontrol.video.FrameFreeListener;
import com.example.androidcontrol.video.ImageAcceptor;
import com.example.androidcontrol.video.YUVImage;
import com.example.androidcontrol.video.VideoRenderer;

import org.example.ByteUtils;
import org.example.DataReference;
import org.example.services.videoconsumer.VideoStreamAcceptor;
import org.example.softTimer.TimersManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VideoDirector implements VideoStreamAcceptor, AutoCloseable {
    private Logger logger = LoggerFactory.getLogger(VideoDirector.class);

    private final Context context;
    private final TimersManager timersManager;
    private final Object decoderSync=new Object();
    private Decoder decoder;
    private VideoRenderer videoRenderer;
    private boolean headerWasSent;
    private final Queue<YUVImage> backwardQueue = new ConcurrentLinkedQueue<>();
    private final static int maximumFrameSize = 200000;
    private final byte[] tmpBuf = new byte[maximumFrameSize];
    private int tmpOffset;
    private Size containerSize;

    private FrameFreeListener freeListener = frame -> {
        backwardQueue.add(frame);
    };

    private ImageAcceptor imageAcceptor = (buf, offset, length) -> {
        YUVImage frame = backwardQueue.poll();
        if (logger.isDebugEnabled()) {
            logger.debug("Received new YUV image from decoder and we are going to put to {}", frame);
        }
        if (frame != null) {
            long start = System.currentTimeMillis();
            if (offset!=0){
                buf = ByteUtils.copyBytes(buf, offset, length);
            }
            frame.transformYuvToBitmap(buf, 0);
            long stop = System.currentTimeMillis();
            videoRenderer.enqueueFrame(frame);
            long delta = stop - start;
            if (delta > 10) {
                logger.warn("bitmap creation took more than 10ms {}", delta);
            }
        }
    };

    public VideoDirector(Context context, int width, int height, TimersManager timersManager) {
        this.context = context;
        this.timersManager = timersManager;
        containerSize = new Size(width, height);
        this.videoRenderer = new VideoRenderer(context, freeListener);
    }


    public View getVideoRenderer() {
        return videoRenderer;
    }

    synchronized boolean isHeaderWasSent() {
        return headerWasSent;
    }

    synchronized void setHeaderWasSent(boolean headerWasSent) {
        this.headerWasSent = headerWasSent;
    }

    @Override
    public void configureVideoAcceptor(int width, int height) {
        try {
            if (decoder != null && !decoder.isConfiguredBy(width, height)) {
                decoder.close();
                decoder=null;
                backwardQueue.clear();
            }
            if (decoder==null){
                Size imageSize = new Size(width, height);
                backwardQueue.add(new YUVImage(context, imageSize, containerSize));
                backwardQueue.add(new YUVImage(context, imageSize, containerSize));
                backwardQueue.add(new YUVImage(context, imageSize, containerSize));
                synchronized (decoderSync) {
                    decoder = new Decoder(imageAcceptor, width, height, timersManager);
                }
                setHeaderWasSent(false);
            }
            // else skip
        } catch (Exception e) {
            logger.error("Videodirector initialization", e);
        }
    }

    @Override
    public void writeVideoHeader(DataReference dataReference) throws Exception {
        if (!isHeaderWasSent()) {
            decoder.enqueueFrame(dataReference);
            setHeaderWasSent(true);
        }
    }

    @Override
    public void writeVideoFrame(int id, int partIndex, int partsCount, DataReference dataReference) throws Exception {
        try {
            if (partsCount == 1) {
                decoder.enqueueFrame(dataReference);
                tmpOffset = 0;
            } else {
                //collect all parts
                ByteUtils.bufToBuf(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength(),
                        tmpBuf, tmpOffset);
                tmpOffset += dataReference.getLength();
                //last part copied
                if (partIndex == partsCount - 1) {
                    decoder.enqueueFrame(new DataReference(tmpBuf, 0, tmpOffset));
                    tmpOffset = 0;
                }
            }
        } catch (IOException e) {
            logger.error("enqueue videoframe", e);
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (decoderSync) {
            if (decoder != null) {
                decoder.close();
                decoder = null;
            }
        }
    }
}
