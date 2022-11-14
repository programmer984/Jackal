package org.example.services.videoproducer;

import org.example.TimeUtils;
import org.example.YUVUtils;

/**
 * reference to YUV formatted image (bitmap)
 */
public class YUVImage implements Comparable<YUVImage> {
    private long timestamp = TimeUtils.nowMs();
    private final int width;
    private final int height;
    private final int dataSize;
    private final byte[] buffer;

    /**
     * buffer.length = width*height + width*height/4
     */
    public YUVImage(int width, int height) {
        this.width = width;
        this.height = height;
        this.dataSize = YUVUtils.calculateBufferSize(width, height);
        this.buffer = new byte[dataSize];
    }


    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getDataSize() {
        return dataSize;
    }



    @Override
    public int compareTo(YUVImage yuvImage) {
        return Long.compare(this.timestamp, yuvImage.timestamp);
    }
}