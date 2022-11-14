package org.example.packetsReceiver;

/**
 * Threadsafe
 * Suitable for UDP packets,
 * One UDP packet must contain integer (even, semicolon) amount of packets (
 */
public class PacketReceiverOneShot extends AbstractPacketsReceiver{

    public PacketReceiverOneShot(ProtocolHandler protocolHandler, OnePacketConsumer packetConsumer) {
        this.protocolHandler = protocolHandler;
        this.packetConsumer = packetConsumer;
    }

    @Override
    public void onNewDataReceived(byte[] data, int offsetFinal, int sizeFinal, Integer logId) {
        searchPacketsAndPush(data, offsetFinal, sizeFinal, logId);
    }
}
