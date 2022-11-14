#include "search.h"
#include "packets_receiver.h"
#include "video_frame_receiver.h"
#include <unistd.h>
#include <string.h>
#include <stdio.h>

typedef unsigned char byte;

//start(4), length(4), body(length)
//length - from type to crc
//crc - from type to last body
byte packetStart[] = {0x35, 0x11, 0x89, 0x14};


extern byte calculateCRC(byte *data, int length);

int findStartPositionIP(byte *data, int length) {
    int foundPosition = searchSequense(data, length, &packetStart[0],
                                       START_TOKEN_SIZE);
    return foundPosition;
}

int getBytesCountForRequiredForStartSearchIP() {
    return START_TOKEN_SIZE;
}

PacketRecevingResult_t checkPacketIsCompleteIP(byte *data, int length) {
    PacketRecevingResult_t result;

    int packetSize = getU32(data + LENGTH_OFFSET);
    if (length < BODY_OFFSET) {
        result.resultState = INCOMPLETE;
    } else if (packetSize > IP_MAXIMUM_PACKET_SIZE || packetSize < IP_MINIMUM_PACKET_SIZE) {
        result.resultState = TRASH;
    } else if (length >= packetSize) {
        result.size = packetSize;
        result.resultState = COMPLETE;
    } else {
        result.resultState = INCOMPLETE;
    }
    return result;
}


int getApproximatePacketSizeIP(byte *data, int length) {

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

