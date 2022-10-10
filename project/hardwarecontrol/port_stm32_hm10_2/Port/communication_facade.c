#include "communication_facade.h"
#include "packets_receiver.h"
#include "internal_protocol_receiver.h"
#include "hm10_api.h"
#include "hm10_pr.h"
#include "common.h"
#include "packets_receiver.h"
#include "string.h"
#include "stdlib.h"
#include "dma_rolling_buf.h"
#include "hm10_link_establish.h"
#include "search.h"
#include "internal_protocol_handler.h"
#include "config.h"

#define INCOMING_BUF_SIZE 32
#define OUTGOING_BUF_SIZE 20


u8 incomingPacketBuf[INCOMING_BUF_SIZE];
u8 outgoingPacketBuf[OUTGOING_BUF_SIZE];

PacketReceiverInstance_t hmLinkInstance;
PacketReceiverInstance_t hmInstance;
PacketReceiverInstance_t prInstance;

DmaRollingBufInstance_t dmaInstance;

volatile bool reInitRequired;
ModuleStates hmTopState;

extern CommunicationDriver_t *createDriverInstance();

extern RxProps_t *createRxProps();

void internalProtocolProxy(u8 *data, int size) {
    onPacket(data, size);
}

void communicationInit(UART_HandleTypeDef *uartPtr, DMA_HandleTypeDef *uartDmaRxPtr,
                       DMA_HandleTypeDef *uartDmaTxPtr) {

    //common driver and rx/tx buffer for internalProtocol and hm10 at commands
    //since they works exclusively
    CommunicationDriver_t *driver = createDriverInstance();
    RxProps_t *rxProps = createRxProps();

    ProtocolHandlers_t *atCommandHandlers = createATHandlers(driver);


    hmLinkInstance.protocolHandlers = atCommandHandlers;
    hmLinkInstance.communicationDriver = driver;
    hmLinkInstance.rxProp = rxProps;
    hmLinkInstance.packetConsumer = &onHmLinkIncomingData;

    hmInstance.protocolHandlers = atCommandHandlers;
    hmInstance.communicationDriver = driver;
    hmInstance.rxProp = rxProps;
    hmInstance.packetConsumer = &onHmIncomingData;

    prInstance.protocolHandlers = createProtocolHandlers();
    prInstance.communicationDriver = driver;
    prInstance.rxProp = rxProps;
    prInstance.packetConsumer = &internalProtocolProxy;

    hmLinkInit(uartPtr, uartDmaRxPtr, uartDmaTxPtr, &outgoingPacketBuf[0], &dmaInstance);

    reInitRequired = false;
    hmTopState = SPEED_UNKNOWN;
}


void communicationInvoke() {

    if (hmTopState == SPEED_UNKNOWN) {
        if (isUartEnabled()) {
            RollingBuf_t rollingBuf = getNewDmaData(&dmaInstance);
            if (rollingBuf.size > 0) {
                onNewDataReceived(&hmLinkInstance, (u8 *) rollingBuf.ptr, rollingBuf.size);
            }else{
                checkPacketComplete(&hmLinkInstance);
            }
        }

        if (!isHmLinkInitialized()) {
            hmLinkInvoke();
        } else {
            hmTopState = MODULE_CONFIGURATION;
        }
    }

    if (hmTopState == MODULE_CONFIGURATION) {
        RollingBuf_t rollingBuf = getNewDmaData(&dmaInstance);
        if (rollingBuf.size > 0) {
            onNewDataReceived(&hmInstance, (u8 *) rollingBuf.ptr, rollingBuf.size);
        }else{
            checkPacketComplete(&hmInstance);
        }
        if (!isHmInitialized()) {
            hm10ApiInvoke();
        } else {
            hmTopState = STREAM_MODE;
        }
    }

    if (hmTopState == STREAM_MODE) {
        RollingBuf_t rollingBuf = getNewDmaData(&dmaInstance);
        if (rollingBuf.size > 0) {
            onNewDataReceived(&prInstance, (u8 *) rollingBuf.ptr, rollingBuf.size);
        }
        //watchdog check
        if (reInitRequired) {
            reInitRequired = false;
            reStartHmLinkInitialization();
            hmTopState = SPEED_UNKNOWN;
        }
    }
}
void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart) {

}
void HAL_UART_ErrorCallback(UART_HandleTypeDef *huart)
{
    Error_Handler();
}
void HAL_UART_TxCpltCallback(UART_HandleTypeDef *huart) {
    if (hmTopState == SPEED_UNKNOWN) {
        hmLinkSent();
    } else if (hmTopState == STREAM_MODE) {

    }
}

bool outputStreamAvailable() {
    return hmTopState == STREAM_MODE;
}

CommunicationDriver_t *createDriverInstance() {
    CommunicationDriver_t *driver = malloc(sizeof(CommunicationDriver_t));
    driver->getCurrentSpeed = &getCurrentSpeed;
    return driver;
}

RxProps_t *createRxProps() {
    RxProps_t *rxProps = malloc(sizeof(RxProps_t));
    memset(rxProps, 0, sizeof(RxProps_t));
    rxProps->rxBufSize = INCOMING_BUF_SIZE;
    rxProps->rxBuf = &incomingPacketBuf[0];
    return rxProps;
}

void dataIndicatorOn(){
    HAL_GPIO_WritePin(DATA_LED_PORT, DATA_LED_PIN,  GPIO_PIN_SET);
}

void dataIndicatorOff(){
    HAL_GPIO_WritePin(DATA_LED_PORT, DATA_LED_PIN,  GPIO_PIN_RESET);
}