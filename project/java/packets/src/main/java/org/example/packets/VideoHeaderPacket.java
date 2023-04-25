package org.example.packets;

import org.example.ByteUtils;

/**
 * width[2], height[2], 00 00 00 01 sps 00 00 00 01 pps
 */
public class VideoHeaderPacket extends AbstractPacket {

    public static final int WH_LENGTH = 4;
    public static final int WIDTH_OFFSET = BODY_OFFSET;
    public static final int HEIGHT_OFFSET = WIDTH_OFFSET + 2;
    public static final int VIDEO_HEADER_OFFSET = HEIGHT_OFFSET + 2;
    private static final byte[] marker = new byte[]{0, 0, 0, 1};
    private static final int MINIMUM_HEADER_SIZE = 22;
    private static final int MAXIMUM_HEADER_SIZE = 30;
    private final int width;
    private final int height;
    private final byte[] headerBuf;
    private final int headerOffset;
    private final int headerLength;


    public VideoHeaderPacket(int width, int height, byte[] headerBuf, int headerOffset, int headerLength) {
        super(PacketTypes.VideoHeader);
        this.width = width;
        this.height = height;
        this.headerBuf = headerBuf;
        this.headerOffset = headerOffset;
        this.headerLength = headerLength;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getHeaderBuf() {
        return headerBuf;
    }

    public int getHeaderOffset() {
        return headerOffset;
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public static VideoHeaderPacket fromBuf(byte[] someBuf, int packetOffset) {
        int headerSize = getSize(someBuf, packetOffset) - TLC_LENGTH - WH_LENGTH;

        int width = ByteUtils.bufToU16(someBuf, packetOffset + WIDTH_OFFSET);
        int height = ByteUtils.bufToU16(someBuf, packetOffset + HEIGHT_OFFSET);

        return new VideoHeaderPacket(width, height, someBuf, packetOffset + VIDEO_HEADER_OFFSET, headerSize);
    }

    public static byte[] copyHeaderFromIdrOrNull(byte[] idr) {
        if (idr.length >= MINIMUM_HEADER_SIZE && ByteUtils.searchSequense(idr, 0, marker) == 0) {
            if ((idr[4] & 0x0F) == 7) {
                //skip first marker (skip first 0 0 0 1 marker)
                int nextBlockPosition = ByteUtils.searchSequense(idr, MINIMUM_HEADER_SIZE, idr.length - MINIMUM_HEADER_SIZE,
                        marker, 0, marker.length);
                if (nextBlockPosition > 0 && nextBlockPosition < MAXIMUM_HEADER_SIZE) {
                    return ByteUtils.copyBytes(idr, 0, nextBlockPosition + MINIMUM_HEADER_SIZE);
                }
            }
        }
        return null;
    }

    /*
    private static final byte[] dataHeader = new byte[]{0, 0, 0, 1};
    public static byte[] searchH264Header(byte[] buf) {
        int offset = 0;
        while (offset < buf.length) {
            Integer index = ByteUtils.searchSequense(buf, offset, dataHeader);
            if (index == null) {
                return null;
            }

            if (buf.length > index + 1 && h264HeaderLikeRight(buf, index)) {
                Integer endOffset = ByteUtils.searchSequense(buf, index + 16, dataHeader);
                if (endOffset == null) {
                    return null;
                }
                int size = endOffset - index;
                if (size < 50) {
                    return ByteUtils.copyBytes(buf, index, size);
                }
            }
            offset = index;
        }
        return null;
    }

    // 00 00 00 01 67
    public static boolean h264HeaderLikeRight(byte[] buf, int offset) {
        return (buf[offset + 4] & 0x0F) == 7;
    }
*/


    @Override
    public void toArray(byte[] buf, int offset, int calculatedSize) {
        ByteUtils.u16ToBuf(width, buf, offset + WIDTH_OFFSET);
        ByteUtils.u16ToBuf(height, buf, offset + HEIGHT_OFFSET);
        ByteUtils.bufToBuf(headerBuf, getHeaderOffset(), getHeaderLength(), buf, offset + VIDEO_HEADER_OFFSET);
        setTypeAndSize(buf, offset, calculatedSize);
        setCrc(buf, offset, calculatedSize);
    }

    @Override
    public int calculateSize() {
        return TLC_LENGTH + WH_LENGTH + headerLength;
    }


    public static boolean dimensionEquals(VideoHeaderPacket h1, VideoHeaderPacket h2){
        return h1.getWidth()==h2.getWidth() && h1.getHeight() == h2.getHeight();
    }
}
