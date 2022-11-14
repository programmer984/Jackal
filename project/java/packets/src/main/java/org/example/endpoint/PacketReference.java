package org.example.endpoint;

import org.example.packets.PacketTypes;

/**
 * reference to checked packet
 * [data] could be any size
 * you must refer from offset
 */
public class PacketReference {
    private final byte[] data;
    private final int offset;
    private final int size;
    private final PacketTypes packetType;

    public PacketReference(byte[] data, int offset, int size) {
        this.data = data;
        this.offset = offset;
        this.size = size;
        packetType = PacketTypes.get(data[offset]);
    }

    public byte[] getData() {
        return data;
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public PacketTypes getPacketType() {
        return packetType;
    }
}
