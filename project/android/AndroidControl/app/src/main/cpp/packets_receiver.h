#include "common.h"
#include "soft_timer.h"

#ifndef PR_H_
#define PR_H_


typedef struct {
    u8 tmp: 6;
    //we started receiving the packet (start detected)
    bool startStored: 1;
    bool timerUsing: 1;
    SoftTimer_t *receivingTimeoutTimer;
    u32 rxIndex;
    u32 rxBufSize;
    u8 *rxBuf;
} RxProps_t;

typedef enum {
    INCOMPLETE,
    COMPLETE,
    TRASH
} PacketRecevingResultStates;

typedef struct {
    PacketRecevingResultStates resultState: 8;
    int size;
} PacketRecevingResult_t;

typedef struct {
    //find potential start of the packet
    //-1 not found
    int (*findStartPosition)(u8 *data, int length);

    int (*getBytesCountForRequiredForStartSearch)();

    //return packet size if found
    PacketRecevingResult_t (*checkPacketIsComplete)(u8 *data, int length);

    //predict or calculate currently receiving packet size (in bytes)
    int (*getApproximatePacketSize)(u8 *data, int length);

    //if receiving timeout happened (for example)
    void (*resetReceivingState)();
} ProtocolHandlers_t;

typedef struct {
    //get speed [bytes/ms]
    int (*getCurrentSpeed)();
} CommunicationDriver_t;

typedef struct {
    RxProps_t *rxProp;
    ProtocolHandlers_t *protocolHandlers;

    void (*packetConsumer)(u8 *data, int length);

    CommunicationDriver_t *communicationDriver;
} PacketReceiverInstance_t;

extern void checkPacketComplete(PacketReceiverInstance_t *prInstance);

extern void onNewDataReceived(PacketReceiverInstance_t *prInstance, u8 *data, int size);

extern bool packetReceivingTimeout(void *prInstance);

extern void packetReceivingReset(PacketReceiverInstance_t *prInstance);

extern void dataIndicatorOn();
extern void dataIndicatorOff();

#endif
