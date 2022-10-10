package com.example.dataprovider.service;

public class VideoConfig {
    int width;
    int height;
    int maxFrameRate;
    int targetBitrate;

    public VideoConfig(int width, int height) {
        this.width = width;
        this.height = height;
        this.maxFrameRate = 24;
        this.targetBitrate = 1500000;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
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