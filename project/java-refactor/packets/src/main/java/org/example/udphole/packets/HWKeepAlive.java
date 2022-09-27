package org.example.udphole.packets;

import org.example.udphole.ByteUtils;
import org.example.udphole.TimeUtils;

public class HWKeepAlive extends AbstractPacket {
    private final int bodyLength = 8;

    public HWKeepAlive() {
        super(PacketTypes.HWKeepAlive);
    }

    @Override
    public byte[] toArray() {
        byte[] buf = new byte[TLC_LENGTH + bodyLength];
        setTypeAndSize(buf);
        ByteUtils.u64ToBuf(TimeUtils.nowMs(), buf, BODY_OFFSET);
        setCrc(buf);
        return buf;
    }
}
