#include "packets_receiver.h"
#include "soft_timer.h"
#include "common.h"
#include <stdbool.h>
#include "openh264/codec_api.h"

//when we have many packets in one stream (byte buffer)
typedef enum {
    EVERYTHING_SENT,
    PACKET_INCOMPLETE, //we started receiving a packet
    TAIL_PRESENT //we have some tail which we can not analyze
} PacketsPushingResult;

typedef struct {
    PacketsPushingResult pushingResult: 8;
    u8 packetsPushed: 8;
    u16 offset: 16;
} PacketsPushingResult_t;

extern bool packetReceivingTimeout(void *prInstance_v);

extern void createAndStartTimer(PacketReceiverInstance_t *prInstance);

extern void cancelTimer(PacketReceiverInstance_t *prInstance);

extern PacketsPushingResult_t
searchPacketsAndPush(PacketReceiverInstance_t *prInstance, u8 *data, u16 size, bool skipFirstSearch);

//completness of some packets we detect using timout after last byte
void checkPacketComplete(PacketReceiverInstance_t *prInstance) {
    RxProps_t *rxProps = prInstance->rxProp;
    if (rxProps->rxIndex > 0) {
        onNewDataReceived(prInstance, rxProps->rxBuf + rxProps->rxIndex, 0);
    }
}


void onNewDataReceived(PacketReceiverInstance_t *prInstance, u8 *data, int size) {
    RxProps_t *rxProps = prInstance->rxProp;
    dataIndicatorOn();

    bool skipFirstSearch = rxProps->startStored;
    while (true) {
        int leftSize = rxProps->rxBufSize - rxProps->rxIndex;
        int deltaSize = (size <= leftSize) ? size : leftSize;

        //add to dmaRxBuf incoming bytes
        copy((void *) data, rxProps->rxBuf + rxProps->rxIndex, deltaSize);
        rxProps->rxIndex += deltaSize;
        data += deltaSize;
        size -= deltaSize;

        PacketsPushingResult_t result = searchPacketsAndPush(prInstance, rxProps->rxBuf,
                                                             rxProps->rxIndex, skipFirstSearch);
        if (result.packetsPushed > 0 && rxProps->timerUsing) {
            dataIndicatorOff();
            cancelTimer(prInstance);
        }
        if (result.pushingResult == EVERYTHING_SENT) {
            rxProps->rxIndex = 0;
            rxProps->startStored = false;
            //last loop
            if (size == 0) {
                break;
            }
        } else if (result.pushingResult == PACKET_INCOMPLETE || result.pushingResult == TAIL_PRESENT) {
            int tail = rxProps->rxIndex - result.offset;
            //shift to start
            copy(rxProps->rxBuf + result.offset, rxProps->rxBuf, tail);
            rxProps->rxIndex = tail;
            skipFirstSearch = result.pushingResult == PACKET_INCOMPLETE;
            //last loop
            if (size == 0) {
                if (result.pushingResult == PACKET_INCOMPLETE) {
                    createAndStartTimer(prInstance);
                    rxProps->startStored = true;
                }
                break;
            }
        }
    }

}

/*
 * for example we have data[100] which contains 7 packets with size 14
 * so 7*14 = 98
 * left 2 bytes could be enough to analyze a start of the next packet, may be not
 * depending on tail we return different result
 */
PacketsPushingResult_t
searchPacketsAndPush(PacketReceiverInstance_t *prInstance, u8 *data, u16 size, bool skipFirstSearch) {
    ProtocolHandlers_t *protocolHandlers = prInstance->protocolHandlers;
    int offset = 0;
    int startTokenSize = protocolHandlers->getBytesCountForRequiredForStartSearch();
    PacketsPushingResult_t result;
    memset(&result, 0, sizeof(PacketsPushingResult_t));
    if (size < startTokenSize) {
        result.pushingResult = TAIL_PRESENT;
    } else {
        while (offset < size) {
            int tailSize = size - offset;
            if (tailSize >= startTokenSize) {
                int foundStartOffset = (skipFirstSearch && offset == 0) ? 0 :
                                       protocolHandlers->findStartPosition(data + offset, tailSize);
                if (foundStartOffset >= 0) {
                    offset += foundStartOffset; //points to new found packet
                    tailSize -= foundStartOffset;
                    PacketRecevingResult_t recevingResult =
                            protocolHandlers->checkPacketIsComplete(data + offset, tailSize);
                    //if internal structure of packet is wrong
                    if (recevingResult.resultState == TRASH) {
                        offset = offset + foundStartOffset + 1;
                    } else if (recevingResult.resultState == COMPLETE) {
                        prInstance->packetConsumer(data + offset, recevingResult.size);
                        offset += recevingResult.size;
                        result.packetsPushed++;
                    } else if (recevingResult.resultState == INCOMPLETE) {
                        result.pushingResult = PACKET_INCOMPLETE;
                        result.offset = offset;
                        break;
                    }
                } else { // start not found
                    //we leave startTokenSize - 1
                    if (startTokenSize > 1) {
                        result.pushingResult = TAIL_PRESENT;
                        result.offset = offset + tailSize - startTokenSize + 1;
                    } else {
                        //there are no unknown tail and start next packet
                        result.pushingResult = EVERYTHING_SENT;
                    }
                    break;
                }
            } else { // tailSize<startTokenSize
                result.pushingResult = TAIL_PRESENT;
                result.offset = offset;
                break;
            }
        }
        if (offset == size) {
            result.pushingResult = EVERYTHING_SENT;
        }
    }
    return result;
}

bool packetReceivingTimeout(void *prInstance_v) {
    PacketReceiverInstance_t *prInstance = (PacketReceiverInstance_t *) prInstance_v;
    ProtocolHandlers_t *protocolHandlers = prInstance->protocolHandlers;
    RxProps_t *rxProps = prInstance->rxProp;
    rxProps->rxIndex = 0;
    rxProps->timerUsing = false;
    rxProps->startStored = false;
    protocolHandlers->resetReceivingState();
    return true;
}

void createAndStartTimer(PacketReceiverInstance_t *prInstance) {
    ProtocolHandlers_t *protocolHandlers = prInstance->protocolHandlers;
    RxProps_t *rxProps = prInstance->rxProp;
    if (!rxProps->timerUsing) {
        int approxSize = protocolHandlers->getApproximatePacketSize(rxProps->rxBuf, rxProps->rxIndex);
        int currentSpeed = prInstance->communicationDriver->getCurrentSpeed();
        int timeoutMs = approxSize / currentSpeed;
        timeoutMs += timeoutMs / 10; //add 10% to await time
        timeoutMs += 2;   //extra milliseconds
        rxProps->receivingTimeoutTimer = addTimer(timeoutMs, false, &packetReceivingTimeout, prInstance);
        rxProps->timerUsing = true;
    }
}



/*
void packetReceivingReset(PacketReceiverInstance_t *prInstance) {
    cancelTimer(prInstance);
    prInstance->rxProp->rxIndex = 0;
}
*/

void cancelTimer(PacketReceiverInstance_t *prInstance) {
    RxProps_t *rxProps = prInstance->rxProp;
    if (rxProps->timerUsing) {
        removeTimer(rxProps->receivingTimeoutTimer);
        rxProps->timerUsing = false;
    }
}
