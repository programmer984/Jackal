package com.example.androidcontrol.video;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;

import java.util.concurrent.atomic.AtomicReference;

public class VideoRenderer extends View {

    private AtomicReference<ImageFrame> frameToDraw = new AtomicReference<>();
    private FrameFreeListener frameFreeListener;

    public VideoRenderer(Context context, FrameFreeListener frameFreeListener) {
        super(context);
        this.frameFreeListener = frameFreeListener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        ImageFrame frame = frameToDraw.get();
        if (frame != null) {
            long start = System.currentTimeMillis();
            canvas.drawRect(0, 0, frame.getScaledWidth(), frame.getScaledHeight(), frame.paint);
            long stop = System.currentTimeMillis();
            frameFreeListener.frameFree(frame);
        }
    }

    public void enqueueFrame(ImageFrame frame) {
        ImageFrame prevFrame = frameToDraw.getAndSet(frame);
        postInvalidate();

        if (prevFrame != null && !prevFrame.equals(frame)) {
            frameFreeListener.frameFree(prevFrame);
        }
    }

}
