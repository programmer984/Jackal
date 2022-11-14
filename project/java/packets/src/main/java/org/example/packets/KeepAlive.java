package org.example.packets;

import org.example.ByteUtils;
import org.example.TimeUtils;

public class KeepAlive extends AbstractPacket {
    private final int bodyLength = 8;
    static final int minimumSize = 12;
    private final long timestamp=TimeUtils.nowMs();
    public KeepAlive() {
        super(PacketTypes.KeepAlive);
    }

    @Override
    public int calculateSize() {
        return TLC_LENGTH + bodyLength;
    }

    @Override
    public void toArray(byte[] buf, int offset, int calculatedSize) {
        ByteUtils.u64ToBuf(timestamp, buf, offset + BODY_OFFSET);
        setTypeAndSize(buf, offset, calculatedSize);
        setCrc(buf, offset, calculatedSize);
    }
}
