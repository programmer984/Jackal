package com.example.androidcontrol.video;

@FunctionalInterface
public interface FrameFreeListener {
    void frameFree(YUVImage frame);
}
