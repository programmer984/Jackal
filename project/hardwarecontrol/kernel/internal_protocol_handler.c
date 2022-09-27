#include "common.h"
#include <stdlib.h>
#include "internal_protocol_handler.h"
#include "internal_protocol_receiver.h"
#include "movement.h"

#define PACKET_TYPE_KEEP_ALIVE 0x70
#define PACKET_TYPE_DO_MOVE 0x71

void onPacket(u8 *data, int size) {
    switch (data[1]) {
        case PACKET_TYPE_KEEP_ALIVE:
            //echo keep alive
            sendData(data, size);
            break;
        case PACKET_TYPE_DO_MOVE:
            handleMovementPacket(data+BODY_OFFSET);
            break;
    }
}

