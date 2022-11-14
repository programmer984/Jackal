package com.example.androidcontrol.video;

@FunctionalInterface
public interface ImageAcceptor {
    void onImageDecoded(byte[] buf, int offset, int length);
}
