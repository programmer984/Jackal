package org.example.udphole.endpoint;

@FunctionalInterface
public interface PacketProducer {
    void packetBorn(PacketOut packet);
}
