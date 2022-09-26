#include <stdio.h>
#include "common.h"
#include "dma_rolling_buf.h"
#include "stdlib.h"


#define RX_DMA_BUF_SIZE 20
DmaRollingBufInstance_t instance;
volatile u8 dmaRxBuf[RX_DMA_BUF_SIZE];
u8 index2;

u8 getIndex2() {
    return index2;
}

void assertEquals(u8 a, u8 b, int exitCode) {
    if (a != b) {
        exit(exitCode);
    }
}
void writeLog(char message[]) {
    printf(message);
    printf("\n");
}

int main() {
    printf("Hello, World!\n");
    for (int i = 0; i < RX_DMA_BUF_SIZE; i++) {
        dmaRxBuf[i] = i;
    }
    instance.currentOffsetPtr  = &getIndex2;
    instance.buffer = &dmaRxBuf[0];
    instance.bufferSize = RX_DMA_BUF_SIZE;
    instance.index1=0;
    DmaRollingBufInstance_t* inst = &instance;

    writeLog("receive 10 bytes");
    index2 = 10;
    RollingBuf_t result = getNewDmaData(inst);
    assertEquals(10, result.size, 1);
    assertEquals(1, result.ptr[1], 2);

    writeLog("receive next 10 bytes");
    index2 = RX_DMA_BUF_SIZE;
    result = getNewDmaData(inst);
    assertEquals(10, result.size, 1);
    assertEquals(10, result.ptr[0], 2);

    writeLog("receive tail 5 bytes");
    dmaRollingReset(inst);
    index2 = 15;
    //receive 15 bytes
    result = getNewDmaData(inst);
    assertEquals(15, result.size, 3);
    index2 = 10;
    //receive tail -5 bytes
    result = getNewDmaData(inst);
    assertEquals(5, result.size, 4);
    assertEquals(15, result.ptr[0], 5);

    writeLog("receive tail 0 bytes");
    dmaRollingReset(inst);
    index2 = 20;
    //receive 20 bytes
    result = getNewDmaData(inst);
    assertEquals(20, result.size, 6);
    index2 = 10;
    //receive next 10 bytes
    result = getNewDmaData(inst);
    assertEquals(10, result.size, 7);
    assertEquals(0, result.ptr[0], 8);
    index2 = 12;
    //receive next 2 bytes
    result = getNewDmaData(inst);
    assertEquals(2, result.size, 9);
    assertEquals(11, result.ptr[1], 10);
    return 0;
}
