#include "common.h"
#include "packets_receiver.h"
#include "search.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>


//packet format start[4], type[1], length[2], crc[1], data[n]
//
u8 packetStart[] = {0x10, 0x11, 0x12, 0x13};
#define START_TOKEN_SIZE 4
#define LENGTH_OFFSET 5
#define BODY_OFFSET 7
#define CRC_LENGTH 1
#define MINIMUM_PACKET_SIZE 8
#define MAXIMUM_PACKET_SIZE 15
#define RX_BUF_SIZE 15

extern u8 calculateCRC(u8 *data, int length);

u8 packetsReceived;
u8 rxBuf[RX_BUF_SIZE];

int findStartPositionIP(u8 *data, int length) {
    int foundPosition = searchSequense(data, length, &packetStart[0],
                                       START_TOKEN_SIZE);
    return foundPosition;
}

int getBytesCountForRequiredForStartSearchIP() {
    return START_TOKEN_SIZE;
}

void packetConsumer(u8* data, int size){
    packetsReceived++;
}

PacketRecevingResult_t checkPacketIsCompleteIP(u8 *data, int length) {
    PacketRecevingResult_t result;
    u16 foundPacketSize = -1;

    u16 packetSize = getU16(data + LENGTH_OFFSET);
    if (length < BODY_OFFSET) { //we can not calculate packetSize at this moment
        result.resultState = INCOMPLETE;
    } else if (packetSize > MAXIMUM_PACKET_SIZE || packetSize < MINIMUM_PACKET_SIZE) {
        result.resultState = TRASH;
    } else if (length >= packetSize) {
        foundPacketSize = packetSize;

        u8 calculatedCRC = 1;
        u8 packetCRC = data[7];

        if (calculatedCRC == packetCRC) {
            result.resultState = COMPLETE;
        }else{
            result.resultState = TRASH;
        }

        result.size = foundPacketSize;
    } else {
        result.resultState = INCOMPLETE;
    }
    return result;
}

int getApproximatePacketSizeIP(u8 *data, int length) {
    if (length >= BODY_OFFSET) {
        u16 packetSize = getU16(data + LENGTH_OFFSET);
        return packetSize;
    }
    return MAXIMUM_PACKET_SIZE;
}

void resetReceivingStateIP() {

}

int getCurrentSpeed() {
    return 1; //1 byte/ms - 1KB/s
}

SoftTimer_t*  addTimer(u16 period, bool repeatable, void (*funcPtr)(void *), void *state) {
    return NULL;
}

void removeTimer(SoftTimer_t* timer) {

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

CommunicationDriver_t *createDriverInstance() {
    CommunicationDriver_t *driver = malloc(sizeof(CommunicationDriver_t));
    driver->getCurrentSpeed = &getCurrentSpeed;
    return driver;
}

RxProps_t *createRxProps() {
    RxProps_t *rxProps = malloc(sizeof(RxProps_t));
    memset(rxProps, 0, sizeof(RxProps_t));
    rxProps->rxBufSize = RX_BUF_SIZE;
    rxProps->rxBuf = &rxBuf[0];
    return rxProps;
}

void beforeTest() {
    packetsReceived = 0;
}

void assertEquals(u8 a, u8 b, int exitCode) {
    if (a != b) {
        exit(exitCode);
    }
}
//ONE packet size MUST be less or equal to RX_BUF_SIZE
//RX_BUF_SIZE = 15
//ONE_PACKET_SIZE  = 10 (in this test)
u8 PACKET_1[] = {0x10, 0x11, 0x12, 0x13, 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00};
//one packet with incorrect crc
u8 PACKET_1_INCORRECT[] = {0x10, 0x11, 0x12, 0x13, 0xF5, 0x0A, 0x00, 0x02, 0x00, 0x00};
u8 PACKET_2[] = {0x10, 0x11, 0x12, 0x13, 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00,
                 0x10, 0x11, 0x12, 0x13, 0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00,};
u8 PACKET_000_1[] = {0, 0, 0, 0, 0, 0, 0, 0x10, 0x11, 0x12, 0x13,
                     0xF5, 0x0A, 0x00, 0x01, 0x00, 0x00};

void log(char message[]) {
    printf(message);
    printf("\n");
}

int main() {
    PacketReceiverInstance_t *prInstance = malloc(sizeof(PacketReceiverInstance_t));
    prInstance->protocolHandlers = createProtocolHandlers();
    prInstance->communicationDriver = createDriverInstance();
    prInstance->rxProp = createRxProps();
    prInstance->packetConsumer = &packetConsumer;

    int onePacketSize = sizeof(PACKET_1);
    int twoPacketsSize = onePacketSize * 2;
    int oneAndHalfSize = onePacketSize * 1.6;
    int halfSize = (onePacketSize * 2) - oneAndHalfSize;
    int headerSize = 4;

    //one packet in a one time
    beforeTest();
    log("One correct packet");
    onNewDataReceived(prInstance, &PACKET_1[0], onePacketSize);
    assertEquals(packetsReceived, 1, 1);

    beforeTest();
    log("Half (start, no size) and half");
    onNewDataReceived(prInstance, &PACKET_1[0], 5);
    onNewDataReceived(prInstance, &PACKET_1[5], onePacketSize - 5);
    assertEquals(packetsReceived, 1, 2);

    beforeTest();
    log("Half (start and size) and half");
    onNewDataReceived(prInstance, &PACKET_1[0], 7);
    onNewDataReceived(prInstance, &PACKET_1[7], onePacketSize - 7);
    assertEquals(packetsReceived, 1, 3);

    beforeTest();
    log("Half (no start) and half");
    onNewDataReceived(prInstance, &PACKET_1[0], 2);
    onNewDataReceived(prInstance, &PACKET_1[2], onePacketSize - 2);
    assertEquals(packetsReceived, 1, 4);

    beforeTest();
    log("Byte by byte");
    for (int i = 0; i < onePacketSize; i++) {
        onNewDataReceived(prInstance, &PACKET_1[i], 1);
    }
    assertEquals(packetsReceived, 1, 5);

    beforeTest();
    log("One incorrect packet");
    onNewDataReceived(prInstance, &PACKET_1_INCORRECT[0], onePacketSize);
    assertEquals(packetsReceived, 0, 6);

    beforeTest();
    log("Incoming buffer more than rxBufSize");
    onNewDataReceived(prInstance, &PACKET_2[0], twoPacketsSize);
    assertEquals(packetsReceived, 2, 7);

    beforeTest();
    log("1.5 packets, then 0.5");
    onNewDataReceived(prInstance, &PACKET_2[0], oneAndHalfSize);
    onNewDataReceived(prInstance, &PACKET_2[oneAndHalfSize], halfSize);
    assertEquals(packetsReceived, 2, 8);

    beforeTest();
    log("1.5 packets, 1 packet, 1.5 packets");
    onNewDataReceived(prInstance, &PACKET_2[0], oneAndHalfSize);
    onNewDataReceived(prInstance, &PACKET_1[0], onePacketSize);
    onNewDataReceived(prInstance, &PACKET_2[0], oneAndHalfSize);
    assertEquals(packetsReceived, 3, 9);

    beforeTest();
    log("4 packets in two times");
    onNewDataReceived(prInstance, &PACKET_2[0], twoPacketsSize);
    onNewDataReceived(prInstance, &PACKET_2[0], twoPacketsSize);
    assertEquals(packetsReceived, 4, 10);

    beforeTest();
    log("send header 10 times, then a packet");
    for (int i = 0; i < 10; i++) {
        onNewDataReceived(prInstance, &PACKET_1[0], headerSize);
    }
    onNewDataReceived(prInstance, &PACKET_1[0], onePacketSize);
    assertEquals(packetsReceived, 1, 11);

    //playing with timout
    beforeTest();
    log("1.5 packets, then timeout, then 0.5");
    onNewDataReceived(prInstance, &PACKET_2[0], oneAndHalfSize);
    packetReceivingTimeout(prInstance);
    onNewDataReceived(prInstance, &PACKET_2[oneAndHalfSize], halfSize);
    assertEquals(packetsReceived, 1, 12);

    beforeTest();
    log("header, timeout, next packet");
    onNewDataReceived(prInstance, &PACKET_1[0], headerSize);
    packetReceivingTimeout(prInstance);
    onNewDataReceived(prInstance, &PACKET_1[headerSize], onePacketSize-headerSize);
    assertEquals(packetsReceived, 0, 13);

    beforeTest();
    log("zeroes, then packet");
    onNewDataReceived(prInstance, &PACKET_000_1[0], sizeof(PACKET_000_1));
    assertEquals(packetsReceived, 1, 14);

    beforeTest();
    log("Half (start, no size), timer invoke and half");
    onNewDataReceived(prInstance, &PACKET_1[0], 5);
    checkPacketComplete(prInstance);
    onNewDataReceived(prInstance, &PACKET_1[5], onePacketSize - 5);
    assertEquals(packetsReceived, 1, 15);

    log("tests passed!");
    return 0;
}
