package com.example.androidcontrol.video;

import org.example.ByteUtils;
import org.example.packetsReceiver.PacketRecevingResult;
import org.example.packetsReceiver.PacketRecevingResultStates;
import org.example.packetsReceiver.ProtocolHandler;

/**
 * wrap YUV image into packet
 * start[3], body[yuvSize]
 */
class CodecNativeProtocolHandler implements ProtocolHandler {
    public final static int incomingHeaderLength = 3;
    public final static byte[] START_TOKEN = new byte[]{0x45, 0x45, 0x47};
    public final static int BODY_LENGTH = 4;
    ///////////// incoming packet constants  ///////////
    public final static int BODY_OFFSET = START_TOKEN.length;
    public final static int TL_SIZE = START_TOKEN.length;
    ///////////////////////////////////////////////////

    private final int imagePacketSize;
    public final PacketRecevingResult result = new PacketRecevingResult();

    public CodecNativeProtocolHandler(int yuvSize) {
        this.imagePacketSize = START_TOKEN.length +yuvSize;
    }

    @Override
    public int findRelativeStartPosition(byte[] data, int offset, int length) {
        return ByteUtils.searchSequense(data, offset, length, START_TOKEN, 0, START_TOKEN.length);
    }

    @Override
    public int getBytesCountForRequiredForStartSearch() {
        return START_TOKEN.length;
    }

    @Override
    public PacketRecevingResult checkPacketIsComplete(byte[] data, int offset, int length) {
        result.setResultState(PacketRecevingResultStates.INCOMPLETE);
        if (length >= imagePacketSize) {
            result.setResultState(PacketRecevingResultStates.COMPLETE);
            result.setSize(imagePacketSize);
        }
        return result;
    }

    @Override
    public int getApproximatePacketSize(byte[] data, int offset, int length) {
        return imagePacketSize;
    }

    @Override
    public void resetReceivingState() {

    }
}
