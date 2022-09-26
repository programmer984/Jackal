package org.example.serviceComponents.packets;

import org.example.Utils;

/**
 * 00 00 00 01 sps 00 00 00 01 pps
 */
public class VideoHeaderPacket extends AbstractPacket {

    private final byte[] header;
    private static final byte[] dataHeader = new byte[]{0, 0, 0, 1};

    public VideoHeaderPacket(byte[] header) {
        super(PacketTypes.VideoHeader);
        this.header = header;
    }

    public static byte[] getHeader(byte[] buf, int packetOffset) {
        int size = getSize(buf, packetOffset) - TLC_LENGTH;
        return Utils.copyBytes(buf, packetOffset + BODY_OFFSET, size);
    }

    public static byte[] searchH264Header(byte[] buf) {
        int offset = 0;
        while (offset < buf.length) {
            Integer index = Utils.searchSequense(buf, offset, dataHeader);
            if (index == null) {
                return null;
            }

            if (buf.length > index + 1 && h264HeaderLikeRight(buf, index)) {
                Integer endOffset = Utils.searchSequense(buf, index + 16, dataHeader);
                if (endOffset == null) {
                    return null;
                }
                int size = endOffset - index;
                if (size < 50) {
                    return Utils.copyBytes(buf, index, size);
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

    @Override
    public byte[] toArray() {
        byte[] buf = new byte[TLC_LENGTH + header.length];
        setTypeAndSize(buf, buf.length);
        Utils.bufToBuf(header, buf, BODY_OFFSET);
        setCrc(buf);
        return buf;
    }
}
