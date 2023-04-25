package org.example.protocol;

import org.example.ByteUtils;
import org.example.packets.AbstractPacket;
import org.example.packets.PacketTypes;
import org.example.packetsReceiver.PacketRecevingResult;
import org.example.packetsReceiver.PacketRecevingResultStates;
import org.example.packetsReceiver.ProtocolHandler;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * //type(1), length(2), body(length), crc(1)
 */
public class InternalProtocolHandler implements ProtocolHandler {

    public static final int START_TOKEN_SIZE = 1;
    public static final int HEADER_SIZE = 3;

    private static final Set<Byte> knownPacketTypes = Arrays.stream(PacketTypes.values())
            .map(PacketTypes::getNumberAsByte)
            .collect(Collectors.toSet());

    @Override
    public int findRelativeStartPosition(byte[] data, int offset, int length) {
        for (int i = 0; i <  length; i++) {
            if (knownPacketTypes.contains(data[offset+i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getBytesCountForRequiredForStartSearch() {
        return START_TOKEN_SIZE;
    }

    @Override
    public PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length) {
        PacketRecevingResult packetReceivingResult = new PacketRecevingResult();
        packetReceivingResult.setResultState(PacketRecevingResultStates.INCOMPLETE);
        if (length >= HEADER_SIZE) {
            int packetSize = ByteUtils.bufToU16(data, offset + START_TOKEN_SIZE);
            packetReceivingResult.setSize(packetSize);
            if (length >= packetSize) {
                if (data[offset] == PacketTypes.VideoFrame.getNumberAsByte()) {
                    packetReceivingResult.setResultState(PacketRecevingResultStates.COMPLETE);
                } else {
                    if (AbstractPacket.checkCRC(data, offset)) {
                        packetReceivingResult.setResultState(PacketRecevingResultStates.COMPLETE);
                    } else {
                        packetReceivingResult.setResultState(PacketRecevingResultStates.TRASH);
                    }
                }
            }
        }
        return packetReceivingResult;
    }

    @Override
    public int getApproximatePacketSize(byte[] data, int offset, int length) {
        if (length >= HEADER_SIZE) {
            return ByteUtils.bufToU16(data, offset + START_TOKEN_SIZE);
        }
        return PacketTypes.minimumSize;
    }

    @Override
    public void resetReceivingState() {

    }
}
