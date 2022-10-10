#include "common.h"
#include "soft_timer.h"
#include <stdlib.h>

#ifndef HARDWARECONTROL2_MOVEMENT_H
#define HARDWARECONTROL2_MOVEMENT_H

#define VERSION_OFFSET 5

/**
 * 1 - Horizontal direction
 * 1 - Horizontal power [%] (could be zero - then do not move)
 * 1 - Vertical direction
 * 1 - vertical power (could be zero - then do not move)
 * 1 - Time to move [ms] (for both axes)
 * 4 - version
 */
typedef enum {
    HD_Idle,
    HD_Left,
    HD_Right
} HorizontalDirection;

typedef enum {
    VD_Idle,
    VD_Up,
    VD_Down
} VerticalDirection;

typedef struct {
    HorizontalDirection hd:8;
    u8 hdPower:8;
    VerticalDirection vd:8;
    u8 vdPower:8;
    u8 movementTime:8;
    u32 version:32;     //VERSION_OFFSET
} MovementPacket_t;

typedef enum {
    M_Idle,
    M_Moving,
    M_Stopping
} MovementState;


typedef struct {
    u8 stoppingStep:6;
    bool timeoutTimerUsing: 1;
    bool stoppingTimerUsing: 1;
    //restart after each command - used to start stopping on command absent
    SoftTimer_t* timeoutTimer;
    //3-4 ticks for soft stopping (repeatable)
    SoftTimer_t* stoppingTimer;
    MovementState movementState;
} MovementInstance_t;



extern void handleMovementPacket(u8 *body);
extern void handleMovementPacket2(MovementPacket_t* movementPacket);
extern void movementMove(MovementPacket_t *packet);

#endif //HARDWARECONTROL2_MOVEMENT_H
