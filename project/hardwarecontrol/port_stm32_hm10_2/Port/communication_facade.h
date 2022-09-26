#include "common.h"
#include "stm32f1xx_hal.h"

#ifndef HARDWARECONTROL2_HM10_DRIVER_H
#define HARDWARECONTROL2_HM10_DRIVER_H

typedef enum {
    SPEED_UNKNOWN,
    MODULE_CONFIGURATION,
    STREAM_MODE
} ModuleStates;





extern void communicationInit(UART_HandleTypeDef* uartPtr, DMA_HandleTypeDef* uartDmaRxPtr, DMA_HandleTypeDef* uartDmaTxPtr);
extern void communicationInvoke();
extern bool sendData(u8 *text, int size);
extern bool outputStreamAvailable();

#endif //HARDWARECONTROL2_HM10_DRIVER_H
