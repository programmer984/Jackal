package org.example.serviceComponents.imageCreating;

/**
    All image parts have the same let/top offset related to source bitmap
    equal width and height
 */
public class ImagePartsConfiguration {
    private final int version;
    private final int offsetX, offsetY;
    private final int width, height;
    private final int imageWidth;

    public ImagePartsConfiguration(int version, int offsetX, int offsetY, int partWidth, int partHeight, int imageWidth) {
        this.version = version;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = partWidth;
        this.height = partHeight;
        this.imageWidth = imageWidth;
    }


    public int getVersion() {
        return version;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getImageWidth() { return imageWidth; }

}
