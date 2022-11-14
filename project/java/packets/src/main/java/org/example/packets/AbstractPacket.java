package org.example.packets;

import org.example.ByteUtils;

public abstract class AbstractPacket {
    private final byte packetType;
    public static final int BODY_OFFSET = 3;
    //type(1), length(2) and crc(1) summary length
    public static final int TLC_LENGTH = 4;

    public AbstractPacket(PacketTypes packetType) {
        this.packetType = (byte) packetType.getNumber();
    }

    public static int getPacketType(byte[] buf) {
        return getPacketType(buf, 0);
    }

    public static int getPacketType(byte[] buf, int offset) {
        return buf[offset] & 0xFF;
    }

    protected static void setCrc(byte[] buf, int packetOffset, int packetSize) {
        byte crc = ByteUtils.calculateCrc(buf, packetOffset, packetSize - 1);
        buf[packetOffset + packetSize - 1] = crc;
    }

    public static int getSize(byte[] buf, int offset) {
        if (buf.length - offset > 3) {
            return ByteUtils.bufToU16(buf, offset + 1);
        }
        return 0;
    }

    public static boolean checkCRC(byte[] buf, int packetStartOffset) {
        int size = getSize(buf, packetStartOffset);
        if (buf.length >= size) {
            byte crc = buf[size - 1];
            byte crc2 = ByteUtils.calculateCrc(buf, packetStartOffset, size - 1);
            return crc == crc2;
        }
        return false;
    }

    public static int semicolon(int packetSize) {
        return CryptoUtils.getPaddedSize(packetSize);
    }

    public byte getPacketType() {
        return packetType;
    }

    @Deprecated
    protected void setTypeAndSize(byte[] buf) {
        setTypeAndSize(buf, 0, buf.length);
    }

    protected void setTypeAndSize(byte[] buf, int packetOffset, int size) {
        buf[packetOffset] = packetType;
        ByteUtils.u16ToBuf(size, buf, packetOffset + 1);
    }


    public byte[] toArray(boolean semicolon) {
        int originalSize = calculateSize();
        int packetSize = semicolon ? semicolon(originalSize) : originalSize;
        byte[] buf = new byte[packetSize];
        toArray(buf, 0, originalSize);
        return buf;
    }

    /**
     * calculate clean size (without semicolon)
     *
     * @return TLC+
     */
    public abstract int calculateSize();

    /**
     * write to buf starting from offset
     *
     * @param calculatedSize already calculated in calculateSize() size of the packet
     */
    public abstract void toArray(byte[] buf, int offset, int calculatedSize);

    public void toArray(byte[] buf, int offset) {
        toArray(buf, offset, calculateSize());
    }

    public String getDescription() {
        return "no-description";
    }


}
