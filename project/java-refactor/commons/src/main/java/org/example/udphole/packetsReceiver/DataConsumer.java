package org.example.udphole.packetsReceiver;

@FunctionalInterface
public interface DataConsumer {
    void accept(byte[] data, int offset, int size);
}
