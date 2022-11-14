package org.example.serviceComponents.hw;


public interface HardwarePipe {
    //HardwarePipe(DataConsumer packetConsumer){};
    boolean isOpen();
    void sendPacket(byte[] packet);
}
