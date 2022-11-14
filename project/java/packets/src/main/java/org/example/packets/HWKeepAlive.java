package org.example.packets;

import org.example.ByteUtils;
import org.example.TimeUtils;

public class HWKeepAlive extends AbstractPacket {
    private final int bodyLength = 8;

    public HWKeepAlive() {
        super(PacketTypes.HWKeepAlive);
    }

    @Override
    public int calculateSize() {
        return TLC_LENGTH + bodyLength;
    }

    @Override
    public void toArray(byte[] buf, int offset, int calculatedSize) {
        ByteUtils.u64ToBuf(TimeUtils.nowMs(), buf, offset+BODY_OFFSET);
        setTypeAndSize(buf, offset, calculatedSize);
        setCrc(buf, offset, calculatedSize);
    }
}
