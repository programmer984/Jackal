#include "common.h"

#ifndef __INTERNAL_PROTOCOL_RECEIVER_H
#define __INTERNAL_PROTOCOL_RECEIVER_H

extern void onPacket(u8 *data, int size);
extern bool sendData(u8 *data, int size);
#endif
