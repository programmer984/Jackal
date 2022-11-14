package org.example.packetsReceiver;

@FunctionalInterface
public interface OnePacketConsumer {
    void accept(byte[] data, int offset, int size, Integer logId);
}
