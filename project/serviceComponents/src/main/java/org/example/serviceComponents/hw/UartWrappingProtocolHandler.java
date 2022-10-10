package org.example.serviceComponents.hw;

import org.example.Utils;
import org.example.serviceComponents.packetsReceiver.*;
import org.example.utils.ByteUtils;

/**
 * Recognize incoming packet as with extra header [0x69, ...] to standard system packet
 * //start(1), type(1), length(2), body(length), crc(1)
 */
public class UartWrappingProtocolHandler implements ProtocolHandler {
    private static final byte DEFAULT_START = 0x69;
    public static final byte START_TOKEN_SIZE = 1;
    public static final int MAXIMUM_PACKET_SIZE = 19;
    private final byte[] startToken;
    private final static int BODY_OFFSET = 4;
    private final int LENGTH_OFFSET = 2;
    private final static int CRC_LENGTH = 1;
    private final static int MINIMUM_PACKET_SIZE = 5;

    public UartWrappingProtocolHandler() {
        this(DEFAULT_START);
    }

    public UartWrappingProtocolHandler(byte startTokenValue) {
        startToken = new byte[]{startTokenValue};
    }

    public byte getStartToken() {
        return startToken[0];
    }

    @Override
    public int findStartPosition(byte[] data, int offset, int length) {
        return ByteUtils.searchSequense(data, offset, length, startToken, 0, START_TOKEN_SIZE);
    }

    @Override
    public int getBytesCountForRequiredForStartSearch() {
        return START_TOKEN_SIZE;
    }

    @Override
    public PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length) {
        PacketRecevingResult result = new PacketRecevingResult();

        if (length < BODY_OFFSET) { //we can not calculate packetSize at this moment
            result.setResultState(PacketRecevingResultStates.INCOMPLETE);
        } else {
            int packetSize = Utils.bufToU16(data, offset + LENGTH_OFFSET);
            if (packetSize > MAXIMUM_PACKET_SIZE || packetSize < MINIMUM_PACKET_SIZE) {
                result.setResultState(PacketRecevingResultStates.TRASH);
            } else if (length >= packetSize + START_TOKEN_SIZE) {
                int calculatedCRC = calculateCRC(data, offset + START_TOKEN_SIZE,
                        packetSize - CRC_LENGTH);
                int packetCRC = data[offset + START_TOKEN_SIZE + packetSize - CRC_LENGTH];

                if (calculatedCRC == packetCRC) {
                    result.setResultState(PacketRecevingResultStates.COMPLETE);
                } else {
                    result.setResultState(PacketRecevingResultStates.TRASH);
                }

                result.setSize(packetSize + START_TOKEN_SIZE);
            } else {
                result.setResultState(PacketRecevingResultStates.INCOMPLETE);
            }
        }
        return result;
    }

    byte calculateCRC(byte[] data, int offset, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            crc += data[i + offset];
        }
        return (byte) crc;
    }

    @Override
    public int getApproximatePacketSize(byte[] data, int offset, int length) {
        if (length >= LENGTH_OFFSET + 2) {
            return Utils.bufToU16(data, offset + LENGTH_OFFSET);
        }
        return MAXIMUM_PACKET_SIZE;
    }

    @Override
    public void resetReceivingState() {

    }
}
