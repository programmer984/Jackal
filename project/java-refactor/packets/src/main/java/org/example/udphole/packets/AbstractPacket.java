package org.example.udphole.packets;

import org.example.udphole.ByteUtils;

public abstract class AbstractPacket {
    final byte packetType;
    public static final int BODY_OFFSET = 3;
    //type(1), length(2) and crc(1) summary length
    public static final int TLC_LENGTH = 4;

    public AbstractPacket(int packetType) {
        this.packetType = (byte) packetType;
    }

    protected void setTypeAndSize(byte[] buf) {
        setTypeAndSize(buf, buf.length);
    }

    protected void setTypeAndSize(byte[] buf, int size) {
        buf[0] = packetType;
        ByteUtils.u16ToBuf(size, buf, 1);
    }

    protected static void setCrc(byte[] buf) {
        setCrc(buf, buf.length);
    }

    public static int getPacketType(byte[] buf) {
        return getPacketType(buf, 0);
    }

    public static int getPacketType(byte[] buf, int offset) {
        return buf[offset] & 0xFF;
    }

    protected static void setCrc(byte[] buf, int size) {
        byte crc = ByteUtils.calculateCrc(buf, size);
        buf[size - 1] = crc;
    }

    public static int getSize(byte[] buf, int offset) {
        if (buf.length - offset > 3) {
            return ByteUtils.bufToU16(buf, offset + 1);
        }
        return 0;
    }

    public static boolean checkCRC(byte[] buf, int offset) {
        int size = getSize(buf, offset);
        if (buf.length >= size) {
            byte crc = buf[size - 1];
            byte crc2 = ByteUtils.calculateCrc(buf, offset, size);
            return crc == crc2;
        }
        return false;
    }

    public static int semicolon(int packetSize) {
        return CryptoUtils.getPaddedSize(packetSize);
    }

    public abstract byte[] toArray();

    public String getDescription(){
        return "no-description";
    }
}
