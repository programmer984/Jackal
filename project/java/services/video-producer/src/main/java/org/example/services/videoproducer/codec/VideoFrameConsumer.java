package org.example.services.videoproducer.codec;

@FunctionalInterface
public interface VideoFrameConsumer {
    void accept(VideoFrame videoFrame);
}
