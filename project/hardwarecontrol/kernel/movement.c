#include "movement.h"

#define STOPPING_STEPS 3
#define STOPPING_STEP_TIME 100
#define TIMEOUT_TIME 150

MovementPacket_t lastPacket;
MovementInstance_t movementInstance;

void handleMovementPacket2(MovementPacket_t *movementPacket);

bool isActualPacket(MovementPacket_t *movementPacket);

void updateLastPacket(MovementPacket_t *movementPacket);

void startStopping();
void cancelStopping();

void cancelTimeoutTimer();
void refreshTimeoutTimer(u8 timeoutTime);


MovementPacket_t deserializeMovementPacket(u8 *body) {
    MovementPacket_t movementPacketValue;
    copy(body, &movementPacketValue, VERSION_OFFSET);
    movementPacketValue.version = getU32(body + VERSION_OFFSET);
    return movementPacketValue;
}

void handleMovementPacket(u8 *body) {
    MovementPacket_t movementPacketValue = deserializeMovementPacket(body);
    handleMovementPacket2(&movementPacketValue);
}

void handleMovementPacket2(MovementPacket_t *movementPacket) {

    //if (isActualPacket(movementPacket)) {
        bool currentIdle = movementInstance.movementState == M_Idle;
        bool actionMove = movementPacket->hd != HD_Idle || movementPacket->vd != VD_Idle;

        if (currentIdle && actionMove) {
            movementMove(movementPacket);
            refreshTimeoutTimer(movementPacket->movementTime);
            movementInstance.movementState = M_Moving;
            updateLastPacket(movementPacket);
        } else if (!currentIdle) {
            bool theSameDirectionAction = movementPacket->hd == lastPacket.hd &&
                                          movementPacket->vd == lastPacket.vd;
            if (movementInstance.movementState == M_Moving) {
                if (theSameDirectionAction) {
                    movementMove(movementPacket);
                    refreshTimeoutTimer(movementPacket->movementTime);
                    updateLastPacket(movementPacket);
                } else {
                    startStopping();
                    cancelTimeoutTimer();
                }
            } else { //isStopping
                if (theSameDirectionAction) {
                    cancelStopping();
                    movementMove(movementPacket);
                    refreshTimeoutTimer(movementPacket->movementTime);
                    updateLastPacket(movementPacket);
                } // else continue stopping
            }
        }

    //}
}


bool isActualPacket(MovementPacket_t *movementPacket) {
    return movementPacket->version > lastPacket.version;
}

void updateLastPacket(MovementPacket_t *movementPacket) {
    copy(movementPacket, &lastPacket, sizeof(MovementPacket_t));
}

bool stoppingTimerInvoke(void *param) {
    if (movementInstance.stoppingStep >= STOPPING_STEPS) {
        movementInstance.stoppingTimerUsing = false;
        lastPacket.hd = HD_Idle;
        lastPacket.hdPower = 0;
        lastPacket.vd = VD_Idle;
        lastPacket.vdPower =0;
        movementMove(&lastPacket);
        movementInstance.movementState = M_Idle;
        return true;
    } else {
        lastPacket.hdPower /= 2;
        lastPacket.vdPower /= 2;
        movementInstance.stoppingStep++;
        movementMove(&lastPacket);
        return false;
    }
}

void startStopping() {
    movementInstance.movementState = M_Stopping;
    if (!movementInstance.stoppingTimerUsing) {
        movementInstance.stoppingTimerUsing = true;
        movementInstance.stoppingTimer = addTimer(STOPPING_STEP_TIME, true, &stoppingTimerInvoke, NULL);
        movementInstance.stoppingStep = 0;
        stoppingTimerInvoke(NULL);
    }
}

void cancelStopping() {
    movementInstance.movementState = M_Moving;
    if (movementInstance.stoppingTimerUsing) {
        removeTimer(movementInstance.stoppingTimer);
        movementInstance.stoppingTimerUsing = false;
    }
}

bool timeoutTimerInvoke(void *param) {
    movementInstance.timeoutTimerUsing = false;
    if (movementInstance.movementState != M_Idle) {
        startStopping();
    }
    return true;
}

void cancelTimeoutTimer(){
    if (movementInstance.timeoutTimerUsing) {
        movementInstance.timeoutTimerUsing = false;
        removeTimer(movementInstance.timeoutTimer);
    }
}
void refreshTimeoutTimer(u8 timeoutTime) {
    if (!movementInstance.timeoutTimerUsing) {
        movementInstance.timeoutTimerUsing = true;
        movementInstance.timeoutTimer = addTimer(timeoutTime, false, &timeoutTimerInvoke, NULL);
    } else {
        restartTimer(movementInstance.timeoutTimer);
    }
}