package org.example.endpoint;

import org.example.packets.PacketTypes;

import java.util.Set;

/**
 * Some service should implement this
 */
public interface ServicePacketAcceptor {
    /**
     *  when we receive packet from data channel we know that it is for video rendering
     *  for example and use accept "methdod" on video module
     * @return set of packet types which this acceptor may handle
     */
    Set<PacketTypes> getAcceptPacketTypes();
    void accept(PacketReference packet, Integer logId);
}


