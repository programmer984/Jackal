#include "common.h"
#ifndef HARDWARECONTROL2_DMA_ROLLING_BUF_H
#define HARDWARECONTROL2_DMA_ROLLING_BUF_H

/*
 * remember index1 and track difference index2
 * for example index1=0 index2=4 -> we have 5 bytes
 * then setup index1=5 and track again
 *
 * if we have circled dma buffer with size 20 bytes
 * index1=17 and index2=3 then we have two buffers
 * [17, 18, 19] and [0,1,2]
 *
 */

typedef struct {
    u8 (*currentOffsetPtr)();
    volatile u8* buffer;
    u8 bufferSize;
    u8 index1;
} DmaRollingBufInstance_t;


typedef struct {
    volatile u8* ptr;
    u8 size: 8;
} RollingBuf_t;

extern RollingBuf_t getNewDmaData(DmaRollingBufInstance_t* instance);
extern void dmaRollingReset(DmaRollingBufInstance_t* instance);

#endif //HARDWARECONTROL2_DMA_ROLLING_BUF_H
