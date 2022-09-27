package org.example.udphole.endpoint;

import org.example.udphole.packets.AbstractPacket;

@FunctionalInterface
public interface PacketAcceptor {
    void packetReceived(AbstractPacket packet);
}
