package com.example.androidcontrol.video;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class VideoRenderer extends View {

    private AtomicReference<YUVImage> frameToDraw = new AtomicReference<>();
    private FrameFreeListener frameFreeListener;
    private final Logger logger = LoggerFactory.getLogger(VideoRenderer.class);
    private static final int jankTime = 16;
    public VideoRenderer(Context context, FrameFreeListener frameFreeListener) {
        super(context);
        this.frameFreeListener = frameFreeListener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        YUVImage image = frameToDraw.get();
        if (logger.isDebugEnabled()) {
            logger.debug("onDraw with {}", image);
        }
        if (image != null) {
            long start = System.currentTimeMillis();
            canvas.translate(image.getOffsetX(), image.getOffsetY());
            canvas.drawRect(0,0,
                    image.getScaledWidth(), image.getScaledHeight(), image.getPaint());
            long stop = System.currentTimeMillis();
            stop-=start;
            if (stop>jankTime){
                logger.warn("Slow rendering {}ms", stop);
            }
            frameFreeListener.frameFree(image);
        }
    }

    public void enqueueFrame(YUVImage frame) {
        YUVImage prevFrame = frameToDraw.getAndSet(frame);
        postInvalidate();

        if (prevFrame != null && !prevFrame.equals(frame)) {
            frameFreeListener.frameFree(prevFrame);
        }
    }

}
