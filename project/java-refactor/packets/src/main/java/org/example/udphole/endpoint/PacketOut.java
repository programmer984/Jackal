package org.example.udphole.endpoint;

import org.example.udphole.packets.AbstractPacket;

public class PacketOut {
    private final AbstractPacket packet;
    private final PostLogger postLogger;

    public PacketOut(AbstractPacket packet) {
        this.packet = packet;
        this.postLogger = null;
    }
    public PacketOut(AbstractPacket packet, PostLogger postLogger) {
        this.packet = packet;
        this.postLogger = postLogger;
    }

    public AbstractPacket getPacket() {
        return packet;
    }

    public PostLogger getPostLogger() {
        return postLogger;
    }

}
