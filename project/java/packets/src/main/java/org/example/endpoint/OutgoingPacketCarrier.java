package org.example.endpoint;

import org.example.communication.logging.PostLogger;
import org.example.packets.AbstractPacket;

/**
 * session layer
 * Some data channel should implement this
 * for example udp data sender
 */
@FunctionalInterface
public interface OutgoingPacketCarrier {
    /**
     * when new videoframe is ready, videomodule invokes this
     * @param brandNew
     * @param postLogger
     */
    void packetWasBorn(AbstractPacket brandNew, PostLogger postLogger);
}
