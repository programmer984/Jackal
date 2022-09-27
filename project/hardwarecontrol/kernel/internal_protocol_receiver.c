#include "search.h"
#include "packets_receiver.h"
#include "internal_protocol_receiver.h"
#include "internal_protocol_handler.h"
#include <stdlib.h>

//start(1), type(1), length(2), body(length), crc(1)
//length - from type to crc
//crc - from type to last body
u8 packetStart[] = {IP_START_TOKEN};


extern u8 calculateCRC(u8 *data, int length);

int findStartPositionIP(u8 *data, int length) {
    int foundPosition = searchSequense(data, length, &packetStart[0],
                                       START_TOKEN_SIZE);
    return foundPosition;
}

int getBytesCountForRequiredForStartSearchIP() {
    return START_TOKEN_SIZE;
}

PacketRecevingResult_t checkPacketIsCompleteIP(volatile u8 *data, int length) {
    PacketRecevingResult_t result;

    u16 packetSize = getU16(data + LENGTH_OFFSET);
    if (length < BODY_OFFSET) {
        result.resultState = INCOMPLETE;
    } else if (packetSize > IP_MAXIMUM_PACKET_SIZE || packetSize < IP_MINIMUM_PACKET_SIZE) {
        result.resultState = TRASH;
    } else if (length >= packetSize + START_TOKEN_SIZE) {
        u8 calculatedCRC = calculateCRC(data + START_TOKEN_SIZE,
                                        packetSize - CRC_LENGTH);
        u8 packetCRC = data[START_TOKEN_SIZE + packetSize - CRC_LENGTH];

        if (calculatedCRC == packetCRC) {
            result.resultState = COMPLETE;
        }else{
            result.resultState = TRASH;
        }
        result.size = START_TOKEN_SIZE + packetSize;
    } else {
        result.resultState = INCOMPLETE;
    }
    return result;
}

u8 calculateCRC(u8 *data, int length) {
    u8 crc = 0;
    for (int i = 0; i < length; i++) {
        crc += data[i];
    }
    return crc;
}

int getApproximatePacketSizeIP(u8 *data, int length) {
    if (length >= BODY_OFFSET) {
        u16 packetSize = getU16(data + LENGTH_OFFSET);
        return packetSize;
    }
    return IP_MAXIMUM_PACKET_SIZE;
}

void resetReceivingStateIP() {

}

ProtocolHandlers_t *createProtocolHandlers() {
    ProtocolHandlers_t *ip = malloc(sizeof(ProtocolHandlers_t));
    ip->findStartPosition = &findStartPositionIP;
    ip->getBytesCountForRequiredForStartSearch = &getBytesCountForRequiredForStartSearchIP;
    ip->checkPacketIsComplete = &checkPacketIsCompleteIP;
    ip->resetReceivingState = &resetReceivingStateIP;
    ip->getApproximatePacketSize = &getApproximatePacketSizeIP;
    return ip;
}

