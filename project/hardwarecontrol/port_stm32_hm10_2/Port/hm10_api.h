#include "common.h"

#ifndef HARDWARECONTROL2_HM10_API_H
#define HARDWARECONTROL2_HM10_API_H

extern void hm10ApiInvoke();
extern void onHmIncomingData(u8 *incomingPacket, u8 packetSize);
extern bool isHmInitialized();
extern void startHmInitialization();
extern void hmSent();
#endif //HARDWARECONTROL2_HM10_API_H
