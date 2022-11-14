package com.example.androidcontrol.video;

@FunctionalInterface
public interface ImageAcceptor {
    void onImageDecoded(int offset, int length, byte[] buf);
}
