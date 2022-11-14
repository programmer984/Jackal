package com.example.dataprovider.hw;

import org.example.DataReference;
import org.example.endpoint.PacketReference;
import org.example.endpoint.ServicePacketAcceptor;
import org.example.packets.PacketTypes;

import java.util.HashSet;
import java.util.Set;

public class HwRemoteControlPacketsAcceptor implements ServicePacketAcceptor {
    private static Set<PacketTypes> packetTypes = new HashSet<>();

    static {
        packetTypes.add(PacketTypes.HWDoMove);
    }

    private Hm10Pipe outgoingEndpoint;

    public HwRemoteControlPacketsAcceptor(Hm10Pipe outgoingEndpoint) {
        this.outgoingEndpoint = outgoingEndpoint;
    }

    @Override
    public Set<PacketTypes> getAcceptPacketTypes() {
        return packetTypes;
    }

    @Override
    public void accept(PacketReference packetReference, Integer logId) {
        if (packetReference.getPacketType() == PacketTypes.HWDoMove) {
            DataReference data = packetReference.getDataReference();
            outgoingEndpoint.sendData(data.getBuf(), data.getOffset(), data.getLength(), null);
        }
    }
}
