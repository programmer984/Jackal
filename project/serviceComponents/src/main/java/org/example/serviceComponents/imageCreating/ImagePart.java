package org.example.serviceComponents.imageCreating;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class ImagePart implements Iterable<Byte> {
    //from left top
    private final int col, row;
    private final byte[] bitmap;
    private final ImagePartsConfiguration configuration;

    public ImagePart(int col, int row, byte[] commonBitmap, ImagePartsConfiguration configuration) {
        this.col = col;
        this.row = row;
        this.bitmap = commonBitmap;
        this.configuration = configuration;
    }

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public Stream<Byte> getByteStream() {
        return getIndexesStream()
                .mapToObj(i -> bitmap[i]);
    }

    @Override
    public Iterator<Byte> iterator() {
        List<Integer> indexes = new ArrayList<>();
        getIndexesStream().forEach(i -> indexes.add(i));
        return new Iterator<Byte>() {
            int index = 0;
            @Override
            public boolean hasNext() {
                return index < indexes.size();
            }

            @Override
            public Byte next() {
                return bitmap[indexes.get(index++)];
            }
        };
    }

    private IntStream getIndexesStream() {
        int offset = getMyCellOffset();
        return IntStream.iterate(offset, i -> i += configuration.getImageWidth())
                .limit(configuration.getHeight())
                .flatMap(value -> IntStream.range(value, value + configuration.getWidth()));
    }


    private int getMyCellOffset() {
        //base offset
        int offset = (configuration.getOffsetY() * configuration.getImageWidth()) + configuration.getOffsetX();
        // plus offset of the row
        offset += (row * (configuration.getHeight() * configuration.getImageWidth()));
        // plus offset of the cell
        offset += (col * configuration.getWidth());
        return offset;
    }

}
