package com.example.dataprovider.service;

@FunctionalInterface
public interface DataAcceptor {
    void onDataReady(VideoFrameTypes frameType, byte[] frameData, int offset, int frameSize);
}
