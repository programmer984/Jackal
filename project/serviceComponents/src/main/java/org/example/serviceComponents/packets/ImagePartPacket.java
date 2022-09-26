package org.example.serviceComponents.packets;

import org.example.Utils;
import org.example.serviceComponents.imageCreating.ImagePart;
import org.example.serviceComponents.imageCreating.ImagePartsConfiguration;
import org.example.serviceComponents.imageParsing.ImageCell;

import java.util.Iterator;

/**
 * 4 - version
 * 1 - row
 * 1 - col
 * 2 - width
 * 2 - height
 * . - data
 */
public class ImagePartPacket extends AbstractPacket {

    private ImagePart image;
    private ImagePartsConfiguration configuration;


    public ImagePartPacket(ImagePart imagePart, ImagePartsConfiguration configuration) {
        super(PacketTypes.ImagePart);
        this.image = imagePart;
        this.configuration = configuration;
    }

    @Override
    public byte[] toArray() {
        int dataSize = configuration.getWidth() * configuration.getHeight();
        byte[] buf = new byte[TLC_LENGTH + 4 + 1 + 1 + 2 + 2 + dataSize];
        setTypeAndSize(buf);
        int offset = BODY_OFFSET;
        Utils.i32ToBuf(configuration.getVersion(), buf, offset);
        offset += 4;
        Utils.u8ToBuf(image.getRow(), buf, offset++);
        Utils.u8ToBuf(image.getCol(), buf, offset++);
        Utils.u16ToBuf(configuration.getWidth(), buf, offset);
        offset += 2;
        Utils.u16ToBuf(configuration.getHeight(), buf, offset);
        offset += 2;
        Iterator<Byte> byteIterator = image.iterator();
        while (byteIterator.hasNext()) {
            buf[offset++] = byteIterator.next();
        }
        setCrc(buf);
        return buf;
    }

    public static ImageCell fromPacket(byte[] buf, int offset) {
        offset += BODY_OFFSET;
        int version = Utils.bufToI32(buf, offset);
        offset += 4;
        int row = Utils.bufToU8(buf, offset++);
        int col = Utils.bufToU8(buf, offset++);
        int width = Utils.bufToU16(buf, offset);
        offset += 2;
        int height = Utils.bufToU16(buf, offset);
        offset += 2;
        return new ImageCell(version, width, height, row, col, offset, buf);
    }

}
