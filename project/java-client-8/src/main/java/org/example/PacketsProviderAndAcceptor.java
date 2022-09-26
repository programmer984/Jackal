package org.example;

public interface PacketsProviderAndAcceptor {
    void onIncomingPacket();
    PacketOut getKeepAlive();
    boolean fastCheck(byte[] buf);
}
