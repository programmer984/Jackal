#include "common.h"
#include "dma_rolling_buf.h"

RollingBuf_t getNewDmaData(DmaRollingBufInstance_t *instance) {
    RollingBuf_t result;
    result.size = 0;
    u8 index2 = instance->currentOffsetPtr();
    if (instance->index1 != index2) {
        if (index2 > instance->index1) {
            result.ptr = instance->buffer + instance->index1;
            result.size = index2 - instance->index1;
            instance->index1 = index2;
        } else {
            u8 tail = instance->bufferSize - instance->index1;
            if (tail > 0) {
                result.ptr = instance->buffer + instance->index1;
                result.size = tail;
                instance->index1 = 0;
            } else {
                result.ptr = instance->buffer;
                result.size = index2;
                instance->index1 = index2;
            }
        }
    }
    return result;
}

void dmaRollingReset(DmaRollingBufInstance_t *instance) {
    instance->index1 = 0;
}