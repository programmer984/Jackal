package org.example.services.videoproducer.codec;

public interface CodecCreator {
    Codec createCodec(int width, int height, VideoFrameConsumer videoFrameConsumer);
}
