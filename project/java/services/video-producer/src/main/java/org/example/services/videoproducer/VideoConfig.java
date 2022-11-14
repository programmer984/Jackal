package org.example.services.videoproducer;

public class VideoConfig {
    private ImageSize desiredSize;
    private int maxFrameRate;
    private int targetBitrate;

    public VideoConfig(ImageSize desiredSize) {
        this.desiredSize = desiredSize;
        this.maxFrameRate = 24;
        this.targetBitrate = 1500000;
    }

    public ImageSize getDesiredSize() {
        return desiredSize;
    }

    public void setDesiredSize(ImageSize desiredSize) {
        this.desiredSize = desiredSize;
    }

    public int getMaxFrameRate() {
        return maxFrameRate;
    }

    public void setMaxFrameRate(int maxFrameRate) {
        this.maxFrameRate = maxFrameRate;
    }

    public int getTargetBitrate() {
        return targetBitrate;
    }

    public void setTargetBitrate(int targetBitrate) {
        this.targetBitrate = targetBitrate;
    }
}