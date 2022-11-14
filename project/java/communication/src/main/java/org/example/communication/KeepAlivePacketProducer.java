package org.example.communication;

@FunctionalInterface
public interface KeepAlivePacketProducer {
    byte[] createKeepAlive();
}
