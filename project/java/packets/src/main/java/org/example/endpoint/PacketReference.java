package org.example.endpoint;

import org.example.DataReference;
import org.example.packets.PacketTypes;

/**
 * reference to checked packet
 * [data] could be greater or equal to length
 * you must refer from offset
 */
public class PacketReference {
    private final DataReference dataReference;
    private final PacketTypes packetType;

    public PacketReference(byte[] data, int offset, int size) {
        this.dataReference=new DataReference(data, offset, size);
        packetType = PacketTypes.get(data[offset]);
    }

    public PacketReference(DataReference dataReference){
        this.dataReference=dataReference;
        packetType = PacketTypes.get(getData()[getOffset()]);
    }

    public byte[] getData() {
        return dataReference.getBuf();
    }

    public int getOffset() {
        return dataReference.getOffset();
    }

    public int getSize() {
        return dataReference.getLength();
    }

    public DataReference getDataReference() {
        return dataReference;
    }

    public PacketTypes getPacketType() {
        return packetType;
    }
}
