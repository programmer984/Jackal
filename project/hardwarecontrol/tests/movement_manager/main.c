#include "common.h"
#include "movement.h"
#include "soft_timer.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define MOVEMENT_MS 130

void beforeTest() {

}

void assertEquals(u8 a, u8 b, int exitCode) {
    if (a != b) {
        exit(exitCode);
    }
}

void log(char message[]) {
    printf(message);
    printf("\n");
}

char *leftRight(HorizontalDirection hd, int power) {
    int length = 12;
    char *result = malloc(length);

    switch (hd) {
        case HD_Idle:
            snprintf(result, length, "Idle %3d", power);
            break;
        case HD_Left:
            snprintf(result, length, "Left %3d", power);
            break;
        case HD_Right:
            snprintf(result, length, "Right %3d", power);
            break;
        default:
            snprintf(result, length, "Error %3d", power);
    }
    return result;
}

char *upDown(VerticalDirection vd, int power) {
    int length = 12;
    char *result = malloc(length);

    switch (vd) {
        case VD_Idle:
            snprintf(result, length, "Idle %3d", power);
            break;
        case VD_Up:
            snprintf(result, length, "Up %3d", power);
            break;
        case VD_Down:
            snprintf(result, length, "Down %3d", power);
            break;
        default:
            snprintf(result, length, "Error %3d", power);
    }
    return result;
}

void movementMove(MovementPacket_t *packet) {
    char *lr = leftRight(packet->hd, packet->hdPower);
    char *ud = upDown(packet->vd, packet->vdPower);
    printf("%8d ms\t %s %s - %d\n", getSoftTimestamp(), lr, ud, packet->movementTime);

    free(lr);
    free(ud);
}

void sendMovePacket(MovementPacket_t *packet) {
    packet->version++;
    handleMovementPacket2(packet);
}

void timerInvoke(int times) {
    for (int i = 0; i < times; i++) {
        hardwareTimerInvoke();
        invokePendingTimers();
    }
}

int main() {

    MovementPacket_t moveLU_130ms;
    moveLU_130ms.movementTime = MOVEMENT_MS;
    moveLU_130ms.hd = HD_Left;
    moveLU_130ms.hdPower = 50;
    moveLU_130ms.vd = VD_Up;
    moveLU_130ms.vdPower = 70;
    moveLU_130ms.version = 0;

    MovementPacket_t moveRD_130ms;
    moveRD_130ms.movementTime = MOVEMENT_MS;
    moveRD_130ms.hd = HD_Right;
    moveRD_130ms.hdPower = 150;
    moveRD_130ms.vd = VD_Down;
    moveRD_130ms.vdPower = 210;
    moveRD_130ms.version = 0;


    //one packet in a one time
    beforeTest();
    log("One move command");
    sendMovePacket(&moveLU_130ms);
    timerInvoke(MOVEMENT_MS * 10);

    beforeTest();
    log("Move then delay then move again to the same direction");
    sendMovePacket(&moveLU_130ms);
    timerInvoke(MOVEMENT_MS * 2.5);
    sendMovePacket(&moveLU_130ms);
    timerInvoke(MOVEMENT_MS * 10);


    beforeTest();
    log("Move then delay then move again to the opposite direction");
    sendMovePacket(&moveLU_130ms);
    timerInvoke(MOVEMENT_MS * 2.5);
    sendMovePacket(&moveRD_130ms);
    //stopping (ignoring movement) and idle
    timerInvoke(MOVEMENT_MS * 10);

    beforeTest();
    log("Move then delay then move again to the opposite direction");
    sendMovePacket(&moveLU_130ms);
    timerInvoke(MOVEMENT_MS * 2.1);
    sendMovePacket(&moveLU_130ms);
    for (int i = 0; i < 6; i++) {
        sendMovePacket(&moveRD_130ms);
        timerInvoke(MOVEMENT_MS);
    }
    timerInvoke(MOVEMENT_MS * 10);

    log("tests passed!");
    return 0;
}
