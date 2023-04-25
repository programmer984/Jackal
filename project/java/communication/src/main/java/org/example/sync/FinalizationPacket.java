package org.example.sync;

import org.example.ByteUtils;

/**
 * start[3], size[1], type[1], ms[2]
 */
class FinalizationPacket extends BasePacket {

    public static final int MS_OFFSET = BODY_OFFSET;
    public static final int HEADER_SIZE = 2;

    private final int msLeft;

    public FinalizationPacket(int msLeft) {
        this.msLeft = msLeft;
    }

    public int getMsLeft() {
        return msLeft;
    }

    public int calculateSize(){
        return TLC_SIZE + HEADER_SIZE;
    }

    public void toArray(byte[] buf, int offset) {
        int size = calculateSize();
        ByteUtils.bufToBuf(start, 0, start.length, buf, offset);
        ByteUtils.u8ToBuf(size, buf, offset + SIZE_OFFSET);
        ByteUtils.u8ToBuf(PacketTypes.COUNTDOWN.value(), buf, offset + TYPE_OFFSET);
        ByteUtils.u16ToBuf(msLeft, buf, offset + MS_OFFSET);
    }

    public static FinalizationPacket fromIncomingData(byte[] buf, int offset) {
        int msLeft = ByteUtils.bufToU16(buf, offset + MS_OFFSET);
        return new FinalizationPacket(msLeft);
    }

}
