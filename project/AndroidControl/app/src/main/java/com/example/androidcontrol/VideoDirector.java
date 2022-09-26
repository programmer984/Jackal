package com.example.androidcontrol;

import android.content.Context;
import android.view.View;

import com.example.androidcontrol.video.Decoder;
import com.example.androidcontrol.video.FrameFreeListener;
import com.example.androidcontrol.video.ImageFrame;
import com.example.androidcontrol.video.VideoRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VideoDirector {
    private Logger logger = LoggerFactory.getLogger(VideoDirector.class);

    private int width = 320;
    private int height = 240;

    private Decoder decoder;
    private final VideoRenderer videoRenderer;
    private final Queue<ImageFrame> backwardQueue = new ConcurrentLinkedQueue<>();

    private FrameFreeListener freeListener = frame -> {
        backwardQueue.add(frame);
    };


    public VideoDirector(Context context){
        this.videoRenderer = new VideoRenderer(context, freeListener);

        backwardQueue.add(new ImageFrame(context, width, height));
        backwardQueue.add(new ImageFrame(context, width, height));
        backwardQueue.add(new ImageFrame(context, width, height));

        try {

            decoder = new Decoder((offset, length, buf) -> {
                ImageFrame frame = backwardQueue.poll();
                if (frame != null) {
                    long start = System.currentTimeMillis();
                    frame.transformYuvToBitmap(buf, offset);
                    long stop = System.currentTimeMillis();
                    videoRenderer.enqueueFrame(frame);
                    long delta = stop-start;
                    if (delta>10){
                        logger.warn("bitmap creation took more than 10ms {}", delta);
                    }
                }
            }, width, height);
        } catch (IOException e) {
            logger.error("Videodirector initialization", e);
        }
    }

    public View getVideoRenderer() {
        return videoRenderer;
    }


    public void enqueueVideoFrame(byte[] buf, int offset, int length){
        try {
            decoder.enqueueFrame(buf, offset, length);
        } catch (IOException e) {
            logger.error("enqueue videoframe", e);
        }
    }
}
