package org.example.serviceComponents.imageCreating;

class RawImage {
    final byte[] rawData;
    final int width;
    final int height;

    public RawImage(byte[] rawData, int width, int height) {
        this.rawData = rawData;
        this.width = width;
        this.height = height;
    }
}
