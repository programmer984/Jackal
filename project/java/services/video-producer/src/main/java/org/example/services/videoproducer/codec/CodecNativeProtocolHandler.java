package org.example.services.videoproducer.codec;

import org.example.ByteUtils;
import org.example.packetsReceiver.PacketRecevingResult;
import org.example.packetsReceiver.PacketRecevingResultStates;
import org.example.packetsReceiver.ProtocolHandler;

/**
 * During with native codec interacting we ought to receive video frames
 * start[3], frame_type[1], body_length[4], body[body_length]
 */
class CodecNativeProtocolHandler implements ProtocolHandler {

    public final static byte[] START_TOKEN = new byte[]{0x45, 0x45, 0x47};

    ///////////// incoming packet constants  ///////////
    public final static int FRAME_TYPE_OFFSET = START_TOKEN.length;
    public final static int BODY_LENGTH_OFFSET = FRAME_TYPE_OFFSET + 1;
    public final static int BODY_OFFSET = BODY_LENGTH_OFFSET + 4;
    public final static int TL_SIZE = BODY_OFFSET;
    ///////////////////////////////////////////////////
    //TODO remove magic number, calculate using some statistic
    private final int approximateSize = 2000;
    private final int videFrameMaxSize;
    public final PacketRecevingResult result = new PacketRecevingResult();

    public CodecNativeProtocolHandler(int videFrameMaxSize) {
        this.videFrameMaxSize = videFrameMaxSize;
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
        if (length >= TL_SIZE) {
            int bodySize = ByteUtils.bufToI32(data, offset + BODY_LENGTH_OFFSET);
            if (bodySize < 0 || bodySize + TL_SIZE > videFrameMaxSize) {
                result.setResultState(PacketRecevingResultStates.TRASH);
            } else if (length >= bodySize + TL_SIZE) {
                result.setResultState(PacketRecevingResultStates.COMPLETE);
                result.setSize(bodySize + TL_SIZE);
            }
        }
        return result;
    }

    @Override
    public int getApproximatePacketSize(byte[] data, int offset, int length) {
        if (length >= TL_SIZE) {
            int size = ByteUtils.bufToI32(data, offset + BODY_LENGTH_OFFSET);
            if (size < approximateSize) {
                return size;
            }
        }
        return approximateSize;
    }

    @Override
    public void resetReceivingState() {

    }
}
