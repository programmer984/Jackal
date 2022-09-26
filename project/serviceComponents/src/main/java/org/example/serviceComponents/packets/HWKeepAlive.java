package org.example.serviceComponents.packets;

import org.example.Utils;

public class HWKeepAlive extends AbstractPacket {
    private final int bodyLength = 8;

    public HWKeepAlive() {
        super(PacketTypes.HWKeepAlive);
    }

    @Override
    public byte[] toArray() {
        byte[] buf = new byte[TLC_LENGTH + bodyLength];
        setTypeAndSize(buf);
        Utils.u64ToBuf(Utils.nowMs(), buf, BODY_OFFSET);
        setCrc(buf);
        return buf;
    }
}
