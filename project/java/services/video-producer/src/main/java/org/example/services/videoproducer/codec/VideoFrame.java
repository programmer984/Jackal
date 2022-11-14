package org.example.services.videoproducer.codec;

public class VideoFrame {
    private final VideoFrameTypes videoFrameType;
    private final byte[] frameData;

    public VideoFrame(VideoFrameTypes videoFrameType, byte[] frameData) {
        this.videoFrameType = videoFrameType;
        this.frameData = frameData;
    }

    public VideoFrameTypes getVideoFrameType() {
        return videoFrameType;
    }

    public byte[] getFrameData() {
        return frameData;
    }
}
