package org.example.udphole.packetsReceiver;

public interface ProtocolHandler {
    //find potential start of the packet
    //-1 not found
    int findStartPosition(byte[] data, int offset, int length);

    int getBytesCountForRequiredForStartSearch();

    //return packet size if found
    PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length);

    //predict or calculate currently receiving packet size (in bytes)
    int getApproximatePacketSize(byte[] data, int offset, int length);

    //if receiving timeout happened (for example)
    void resetReceivingState();
}
