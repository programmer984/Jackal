package org.example.sync;

import org.example.ByteUtils;
import org.example.DataReference;

/**
 * start[3], size[1], type[1], body[size-TLC_SIZE]
 */
public abstract class BasePacket {
    public static final int TLC_SIZE = 5;
    public static final byte[] start = new byte[]{0x27, 0x28, 0x44};
    public static final int SIZE_OFFSET = 3;
    public static final int TYPE_OFFSET = 4;
    public static final int BODY_OFFSET = 5;

    public abstract int calculateSize();

    public abstract void toArray(byte[] buf, int offset);

    public static PacketTypes checkAndGetPacketType(DataReference data){
        if (data.getLength()>TLC_SIZE && ByteUtils.searchSequense(data.getBuf(), data.getOffset(), start)==0){
            int value = ByteUtils.bufToU8(data.getBuf(), data.getOffset()+TYPE_OFFSET);
            return PacketTypes.forValue(value);
        }
        return PacketTypes.UNKNOWN;
    }
}
