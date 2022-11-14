package org.example.packetsReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPacketsReceiver {
    protected final Logger logger;
    protected ProtocolHandler protocolHandler;
    protected OnePacketConsumer packetConsumer;

    AbstractPacketsReceiver() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public void onNewDataReceived(final byte[] data, int offsetFinal, int sizeFinal) {
        onNewDataReceived(data, offsetFinal, sizeFinal, null);
    }

    public abstract void onNewDataReceived(final byte[] data, int offsetFinal, int sizeFinal, Integer logId);

    protected PacketsPushingResult searchPacketsAndPush(byte[] data, final int dataOffset, final int size, Integer logId) {
        PacketsPushingResult result = new PacketsPushingResult();
        result.pushingResult = PacketsPushingResultStates.NOT_FOUND;
        //from 0 to size-1; Points to current packet start
        //it is relative offset
        int relativeOffset = 0;


        int startTokenSize = protocolHandler.getBytesCountForRequiredForStartSearch();

        if (size < startTokenSize) {
            //we can not recognize (not enough data)
            result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
        } else {
            while (relativeOffset < size) {
                int tailSize = size - relativeOffset;
                if (tailSize >= startTokenSize) {
                    int foundRelativeStartOffset = protocolHandler.findRelativeStartPosition(data, dataOffset + relativeOffset, tailSize);
                    if (foundRelativeStartOffset >= 0) {
                        relativeOffset += foundRelativeStartOffset; //points to new found packet
                        PacketRecevingResult recevingResult =
                                protocolHandler.checkPacketIsComplete(data, dataOffset +relativeOffset, tailSize);
                        //if internal structure of packet is wrong
                        if (recevingResult.resultState == PacketRecevingResultStates.TRASH) {
                            relativeOffset++;
                            logger.error("Trash for logId {}, offset {}", logId, relativeOffset);
                        } else if (recevingResult.resultState == PacketRecevingResultStates.COMPLETE) {
                            packetConsumer.accept(data, dataOffset + relativeOffset, recevingResult.size, logId);
                            relativeOffset += recevingResult.size;
                            result.packetsPushed++;
                        } else if (recevingResult.resultState == PacketRecevingResultStates.INCOMPLETE) {
                            result.pushingResult = PacketsPushingResultStates.PACKET_INCOMPLETE;
                            logger.debug("Incomplete LogId {}, Position {}/{}", logId, relativeOffset, tailSize);
                            break;
                        }
                    } else { // start not found
                        //we leave startTokenSize - 1
                        if (startTokenSize > 1) {
                            result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
                            relativeOffset = size - startTokenSize + 1;
                        } else {
                            //there are no unknown tail and start next packet
                            result.pushingResult = PacketsPushingResultStates.EVERYTHING_SENT;
                        }
                        break;
                    }
                } else { // tailSize<startTokenSize
                    result.pushingResult = PacketsPushingResultStates.TAIL_PRESENT;
                    break;
                }
            }
            if (relativeOffset == size) {
                result.pushingResult = PacketsPushingResultStates.EVERYTHING_SENT;
            }
        }

        result.offset = dataOffset + relativeOffset;
        return result;
    }

}
