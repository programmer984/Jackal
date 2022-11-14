package org.example.endpoint;

/**
 * incoming pipe data from data channel
 */
@FunctionalInterface
public interface IncomingPacketAcceptor {
    void accept(PacketReference packet, Integer logId);
}
