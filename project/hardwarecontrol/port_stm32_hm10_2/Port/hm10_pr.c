#include "common.h"
#include "packets_receiver.h"
#include "search.h"
#include "hm10_api.h"
#include <stdlib.h>
#include "soft_timer.h"

#define START_TOKEN_SIZE 2
#define AT_MAX_INCOMING_BUF_SIZE 20
CommunicationDriver_t *driver;
volatile u32 startTimestamp;
bool startTimestampSet = false;

int findStartPositionDSD(u8 *data, int length) {
    static const u8 at[] = {"OK"};
    int result = searchSequense(data, length, &at[0],
                                START_TOKEN_SIZE);
    if (result >= 0) {
        startTimestamp = getSoftTimestamp();
        startTimestampSet = true;
    }
    return result;
}

int getBytesCountForRequiredForStartSearchDSD() {
    return START_TOKEN_SIZE;
}

PacketRecevingResult_t checkPacketIsCompleteDSD(u8 *data, int length) {
    PacketRecevingResult_t result;

    int bytesPerMs = driver->getCurrentSpeed();
    //time which we spent for receiving this packet
    u32 spentMS = getSoftTimestamp() - startTimestamp;
    int bytesWeCouldReceive = bytesPerMs * spentMS;
    int delta =  bytesWeCouldReceive - length;

    if (delta > 10 && startTimestampSet) {
        result.resultState = COMPLETE;
        result.size = length;
        startTimestampSet = false;
    } else if (length >= AT_MAX_INCOMING_BUF_SIZE) {
        result.resultState = TRASH;
    } else {
        result.resultState = INCOMPLETE;
    }
    return result;
}


int getApproximatePacketSizeDSD(u8 *data, int length) {
    return AT_MAX_INCOMING_BUF_SIZE;
}

void resetReceivingStateDSD() {
    startTimestampSet = false;
}

ProtocolHandlers_t *createATHandlers(CommunicationDriver_t *driver_) {
    driver = driver_;
    ProtocolHandlers_t *ip = malloc(sizeof(ProtocolHandlers_t));
    ip->findStartPosition = &findStartPositionDSD;
    ip->getBytesCountForRequiredForStartSearch = &getBytesCountForRequiredForStartSearchDSD;
    ip->checkPacketIsComplete = &checkPacketIsCompleteDSD;
    ip->resetReceivingState = &resetReceivingStateDSD;
    ip->getApproximatePacketSize = &getApproximatePacketSizeDSD;
    return ip;
}
