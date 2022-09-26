#ifndef EP_CLIENT_H__
#define EP_CLIENT_H__
#include "epTypes.h"

//returns 0 on success
int epInitialize(EPInstance *instance, int width, int height, 
    int maxFrameRate, int targetBitrate);

void epRun(EPInstance *instance);

#endif