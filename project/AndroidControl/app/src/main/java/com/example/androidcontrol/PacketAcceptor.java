package com.example.androidcontrol;

import org.example.serviceComponents.packets.AbstractPacket;

@FunctionalInterface
public interface PacketAcceptor {
    void takePacket(AbstractPacket packet);
}
