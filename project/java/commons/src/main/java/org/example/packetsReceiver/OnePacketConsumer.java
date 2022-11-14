package org.example.packetsReceiver;



@FunctionalInterface
public interface OnePacketConsumer {
    /**
     * handle checked from incoming stream packet
     * @param data data
     * @param offset offset
     * @param size size
     * @param logId log id
     */
    void accept(byte[] data, int offset, int size, Integer logId);
}
