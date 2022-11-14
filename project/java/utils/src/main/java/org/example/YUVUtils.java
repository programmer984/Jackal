package org.example;

public class YUVUtils {
    public static int calculateBufferSize(int width, int height) {
        int colorBlockSize = width / 2 * height / 2;
        return width * height + (colorBlockSize + colorBlockSize);
    }
}
