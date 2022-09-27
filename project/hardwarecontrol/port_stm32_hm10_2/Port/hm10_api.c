#include "common.h"
#include "search.h"
#include "communication_facade.h"
#include "soft_timer.h"
#include "stdlib.h"
#include "string.h"

const u8 textAT[] = {"AT"};
const u8 textOK[] = {"OK"};
const u8 textATNameGET[] = {"AT+NAME?"};
const u8 textNameGETOK[] = {"OK+NAME:HWControl"};
const u8 textATNameSET[] = {"AT+NAMEHWControl"};
const u8 textNameSETOK[] = {"OK+Set:HWControl"};

typedef enum {
    AT = 0,
    ATAnswerWait,
    ATNameGet,
    ATNameGetAnswerWait,
    ATNameSet,
    ATNameSetAnswerWait,
    ATWaitStream
} ATStates;

typedef struct {
    bool delayingBetweenCommands: 1; //have to make delay between peckets
    ATStates ATState: 7;
    SoftTimer_t* delayingBetweenCommandsTimer;
} HM10Module_t;


#define BETWEEN_COMMANDS_PERIOD 300
volatile HM10Module_t hmState;

extern void configSendCommand(u8 *command, int size);

extern void configModuleAT();

extern bool incomingPacketContains(u8 *incomingPacket, u8 packetSize, u8 *text, int size);
extern void cancelBetweenCommandsTimer();
extern void startBetweenCommandPause();

void hm10ApiInvoke() {
    if (!hmState.delayingBetweenCommands) {
        configModuleAT();
    }
}


void configModuleAT() {

    if (hmState.ATState == AT) {
        configSendCommand(&textAT[0], strlen(textAT));
        hmState.ATState = ATAnswerWait;
    }

    if (hmState.ATState == ATNameGet) {
        configSendCommand(&textATNameGET[0], strlen(textATNameGET));
        hmState.ATState = ATNameGetAnswerWait;
    }

    if (hmState.ATState == ATNameSet) {
        configSendCommand(&textATNameSET[0], strlen(textATNameSET));
        hmState.ATState = ATNameSetAnswerWait;
    }

}

void onHmIncomingData(u8 *incomingPacket, int packetSize) {
    cancelBetweenCommandsTimer();

    if (hmState.ATState == ATAnswerWait) { //Tx Idle means everything sent
        if (incomingPacketContains(incomingPacket, packetSize, &textOK[0], strlen(textOK))){
            hmState.ATState = ATNameGet;
        }
    }
    if (hmState.ATState == ATNameGetAnswerWait) {
        //size of OK+NAME:DSD TECH 	16
        if (incomingPacketContains(incomingPacket, packetSize, &textNameGETOK[0], strlen(textNameGETOK))) {
            hmState.ATState = ATWaitStream;
        } else {
            hmState.ATState = ATNameSet;
            startBetweenCommandPause();
        }
    }
    if (hmState.ATState == ATNameSetAnswerWait) {
        if (incomingPacketContains(incomingPacket, packetSize, &textNameSETOK[0], strlen(textNameSETOK))) {
            hmState.ATState = ATWaitStream;
        } else {
            hmState.ATState = ATNameSet;
            startBetweenCommandPause();
        }
    }
}


bool incomingPacketContains(u8 *incomingPacket, u8 packetSize, u8 *text, int size) {
    if (searchSequense(incomingPacket, packetSize, text, size) == 0) {
        return true;
    }
    return false;
}

void onTimer(void *param) {
    hmState.delayingBetweenCommands = false;
    if (hmState.ATState == ATAnswerWait
        || hmState.ATState == ATNameGetAnswerWait
        || hmState.ATState == ATNameSetAnswerWait)
    {
        hmState.ATState = AT;
    }
}

void startBetweenCommandPause() {
    if (!hmState.delayingBetweenCommands) {
        hmState.delayingBetweenCommands = true;
        hmState.delayingBetweenCommandsTimer = addTimer(BETWEEN_COMMANDS_PERIOD, false, &onTimer, NULL);
    }
}

void cancelBetweenCommandsTimer(){
    if (hmState.delayingBetweenCommands) {
        hmState.delayingBetweenCommands = false;
        removeTimer(hmState.delayingBetweenCommandsTimer);
    }
}


void configSendCommand(u8 *command, int size) {
    if (sendData(command, size)) {
        startBetweenCommandPause();
    }else{
        hmState.ATState = AT;
        cancelBetweenCommandsTimer();
    }
}

void startHmInitialization() {
    hmState.ATState = AT;
}

bool isHmInitialized() {
    return hmState.ATState == ATWaitStream;
}

