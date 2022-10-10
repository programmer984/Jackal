package com.example.dataprovider.service;

class YUVImage implements Comparable<YUVImage> {
    volatile long timestamp;
    byte[] buffer;

    @Override
    public int compareTo(YUVImage yuvImage) {
        return Long.compare(this.timestamp, yuvImage.timestamp);
    }
}