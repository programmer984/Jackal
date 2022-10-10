package org.example.serviceComponents.imageParsing;

import java.util.Arrays;
import java.util.Iterator;

public class ImageCell implements Iterable<Byte> {
    /**
     * @param offset - the bitmap data offset in data
     */
    public ImageCell(int version, int width, int height,
                     int row, int col, int offset, byte[] data) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.row = row;
        this.col = col;
        this.offset = offset;
        this.size = width * height;
        this.data = data;
    }

    private final int version, width, height, row, col, offset, size;
    private final byte[] data;

    public int getVersion() {
        return version;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }


    public byte getAtIndex(int index) {
        return data[offset + index];
    }

    @Override
    public Iterator<Byte> iterator() {
        return new Iterator<Byte>() {
            int index = 0;
            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public Byte next() {
                return data[offset + index++];
            }
        };
    }

    public byte[] getBitmap() {
        return Arrays.copyOfRange(data, offset, offset + size);
    }
}
