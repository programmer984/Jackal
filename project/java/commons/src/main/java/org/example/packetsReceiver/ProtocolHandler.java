package org.example.packetsReceiver;

public interface ProtocolHandler {


    /**
     *     //find potential start of the packet
     *     //returns offset from offset (if offset is 3 and found in data[4], returns 1
     *     //-1 not found
     * @param data
     * @param offset
     * @param length
     * @return
     */
    int findRelativeStartPosition(byte[] data, int offset, int length);

    int getBytesCountForRequiredForStartSearch();

    /**
     *     //return packet size if found
     *     //data -some big buffer (it may contains previous packet or something)
     *     //you must care only about data between offset and (offset+length) of data
     * @param data
     * @param offset
     * @param length
     * @return
     */
    PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length);

    /**
     * //predict or calculate currently receiving packet size (in bytes)
     * @param data
     * @param offset
     * @param length
     * @return
     */
    int getApproximatePacketSize(byte[] data, int offset, int length);

    //if receiving timeout happened (for example)
    void resetReceivingState();
}
