package org.example.serviceComponents.packets;

import org.example.Utils;

import java.nio.ByteBuffer;

public class KeepAlive extends AbstractPacket {
    private final int bodyLength = 8;

    public KeepAlive() {
        super(PacketTypes.KeepAlive);
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
