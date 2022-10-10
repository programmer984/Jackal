#include "common.h"
#include "stm32f1xx_hal.h"
#include "dma_rolling_buf.h"

#ifndef HARDWARECONTROL2_HM10_LINK_ESTABLISH_H
#define HARDWARECONTROL2_HM10_LINK_ESTABLISH_H


extern void hmLinkInit(UART_HandleTypeDef *uartPtr, DMA_HandleTypeDef *uartDmaRxPtr,
                       DMA_HandleTypeDef *uartDmaTxPtr, volatile u8 *outBufPtr, DmaRollingBufInstance_t *dmaInstance);
extern void hmLinkInvoke();
extern void onHmLinkIncomingData(u8 *incomingPacket, u8 packetSize);
extern bool isHmLinkInitialized();
extern void reStartHmLinkInitialization();
extern int getCurrentSpeed();
extern bool isUartEnabled();
extern void hmLinkSent();

#endif //HARDWARECONTROL2_HM10_LINK_ESTABLISH_H
