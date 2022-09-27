#include "hm10_link_establish.h"
#include "config.h"
#include "soft_timer.h"
#include "communication_facade.h"
#include "common.h"
#include "search.h"
#include "stm32f1xx_hal.h"
#include "config.h"
#include "dma_rolling_buf.h"
#include "string.h"

const u8 textAT2[] = {"AT"};
const u8 textBAUD[] = {"AT+BAUD4"};
const u8 textOK2[] = {"OK"};

typedef enum {
    SdDeInit, //configure ports pull down, power off
    SdDeInitPause, //pause 1-2 seconds
    SdInit, //configure ports Rx/Tx, set speed
    SdInitPause, //half second
    SdSendAt,
    SdSendAtAwaitOK,
    SdSetSpeed,
    SdSetSpeedAwaitOK
} SpeedDetectionStates;

typedef struct {
    bool timerWorks: 1; //have to make delay between peckets
    bool highSpeed: 1;//9600 or 115200
    bool initialized: 1;
    bool sending: 1;
    SpeedDetectionStates state: 4;
    SoftTimer_t* timerNumber;
    u8 switchSpeedAttemps;
} HMLinkModule_t;

#define DEINIT_PAUSE 2000
#define AFTER_ON_PAUSE 5000
#define AWAIT_OK_TIME 300
#define RX_DMA_BUF_SIZE 10
#define HIGH_SPEED_BPS 115200
#define LOW_SPEED_BPS 9600
#define MAX_SWITCH_SPEED_ATTEMPS 4

volatile HMLinkModule_t hmLinkState;
volatile u8 dmaRxBuf[RX_DMA_BUF_SIZE];
volatile u8 *outBuf;
UART_HandleTypeDef *uart;
DMA_HandleTypeDef *dma_usart_tx;
DMA_HandleTypeDef *dma_usart_rx;


extern void configModuleLink();

extern void startTimerLink(int period);

extern void cancelTimerLink();

extern bool configSendCommandLink(u8 *command, int size);

extern int getBps();

extern u8 getDmaOffset();
extern void receivingComplete(DMA_HandleTypeDef *hdma);
extern void sendingComplete(DMA_HandleTypeDef *hdma);


void hmLinkInit(UART_HandleTypeDef *uartPtr, DMA_HandleTypeDef *uartDmaRxPtr,
                DMA_HandleTypeDef *uartDmaTxPtr, volatile u8 *outBufPtr, DmaRollingBufInstance_t *dmaInstance) {
    uart = uartPtr;
    dma_usart_tx = uartDmaTxPtr;
    dma_usart_rx = uartDmaRxPtr;
    outBuf = outBufPtr;

    //it remembers that was configured for high speed last time
    hmLinkState.highSpeed = true;
    hmLinkState.state = SdInit;

    dmaInstance->bufferSize = RX_DMA_BUF_SIZE;
    dmaInstance->buffer = &dmaRxBuf[0];
    dmaInstance->index1 = 0;
    dmaInstance->currentOffsetPtr = &getDmaOffset;
}


void hmLinkInvoke() {
    if (!hmLinkState.timerWorks) {
        configModuleLink();
    }
}

void configModuleLink() {

    if (hmLinkState.state == SdDeInit) {
        hmLinkState.switchSpeedAttemps = 0;
        hmLinkState.initialized = false;

        HAL_UART_DeInit(uart);
        HAL_GPIO_WritePin(BT_PORT, BT_ENABLE, GPIO_PIN_SET);

        GPIO_InitTypeDef GPIO_InitStruct = {0};
        GPIO_InitStruct.Pin = BT_TX | BT_RX;
        GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
        GPIO_InitStruct.Pull = GPIO_NOPULL;
        GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
        HAL_GPIO_Init(BT_PORT, &GPIO_InitStruct);
        HAL_GPIO_WritePin(BT_PORT, BT_TX | BT_RX, GPIO_PIN_RESET);

        startTimerLink(DEINIT_PAUSE);
        hmLinkState.state = SdDeInitPause;
    }

    if (hmLinkState.state == SdInit) {
        HAL_GPIO_WritePin(BT_PORT, BT_ENABLE, GPIO_PIN_RESET);
        uart->Init.BaudRate = getBps();
        uart->Init.StopBits = UART_STOPBITS_1;
        HAL_UART_Init(uart);
        HAL_UART_Receive_DMA(uart, &dmaRxBuf, RX_DMA_BUF_SIZE);

        startTimerLink(AFTER_ON_PAUSE);
        hmLinkState.state = SdInitPause;
    }

    if (hmLinkState.state == SdSendAt) {
        if (configSendCommandLink(&textAT2[0], strlen(textAT2))) {
            startTimerLink(AWAIT_OK_TIME);
            hmLinkState.state = SdSendAtAwaitOK;
        }
    }

    if (hmLinkState.state == SdSetSpeed) {
        if (configSendCommandLink(&textBAUD[0], strlen(textBAUD))) {
            startTimerLink(AWAIT_OK_TIME);
            hmLinkState.state = SdSetSpeedAwaitOK;
        }
    }
}


void onHmLinkIncomingData(u8 *incomingPacket, u8 packetSize) {
    if (hmLinkState.state == SdSendAtAwaitOK) {
        if (searchSequense(incomingPacket, packetSize, &textOK2, strlen(textOK2))==0) {
            cancelTimerLink();
            if (!hmLinkState.highSpeed) {
                hmLinkState.state = SdSetSpeed;
                startTimerLink(AWAIT_OK_TIME);
            } else {
                hmLinkState.initialized = true;
            }
        }
    }
    if (hmLinkState.state == SdSetSpeedAwaitOK) {
        if (searchSequense(incomingPacket, packetSize, &textOK2, strlen(textOK2))==0) {
            cancelTimerLink();
            hmLinkState.highSpeed = true;
            hmLinkState.state = SdInit;
            startTimerLink(AFTER_ON_PAUSE);
        }
    }
}

bool onTimerLink(void *param) {
    hmLinkState.timerWorks = false;
    if (hmLinkState.state == SdDeInitPause) {
        hmLinkState.state = SdInit;
    }
    if (hmLinkState.state == SdInitPause) {
        hmLinkState.state = SdSendAt;
    }
    if (hmLinkState.state == SdSendAtAwaitOK) {
        //switch speed and init
        hmLinkState.highSpeed = !hmLinkState.highSpeed;
        hmLinkState.switchSpeedAttemps++;
        if (hmLinkState.switchSpeedAttemps < MAX_SWITCH_SPEED_ATTEMPS) {
            hmLinkState.state = SdInit;
        } else {
            hmLinkState.state = SdDeInit;
        }
    }
    return true;
}

void startTimerLink(int period) {
    if (!hmLinkState.timerWorks) {
        hmLinkState.timerWorks = true;
        hmLinkState.timerNumber = addTimer(period, false, &onTimerLink, NULL);
    }
}

void cancelTimerLink() {
    if (hmLinkState.timerWorks) {
        hmLinkState.timerWorks = false;
        removeTimer(hmLinkState.timerNumber);
    }
}

bool configSendCommandLink(u8 *command, int size) {
    if (!sendData(command, size)) {
        hmLinkState.state = SdDeInit;
        cancelTimerLink();
        return false;
    }
    return true;
}

bool sendData(u8 *text, int size) {
    copy(text, outBuf, size);
    if (HAL_UART_Transmit_DMA(uart, (uint8_t *) outBuf, size)
        == HAL_OK) {
        hmLinkState.sending = true;
        return true;
    }
    return false;
}
void hmLinkSent() {
    hmLinkState.sending = false;
}

int getBps() {
    return hmLinkState.highSpeed ? HIGH_SPEED_BPS : LOW_SPEED_BPS;
}

u8 getDmaOffset() {
    //CNDTR indicating the
    //remaining bytes to be transmitted.
    u8 leftBytes = dma_usart_rx->Instance->CNDTR;
    return RX_DMA_BUF_SIZE - leftBytes;
}

int getCurrentSpeed() {
    int bytesInMs = (getBps() / 10) / 1000; //1 byte/ms - 1KB/s
    if (bytesInMs < 1) {
        return 1;
    }
    return bytesInMs;
}

bool isUartEnabled(){
    return hmLinkState.state > SdDeInitPause;
}

bool isHmLinkInitialized() {
    return hmLinkState.initialized;
}


void reStartHmLinkInitialization() {
    hmLinkState.state = SdDeInit;
    hmLinkState.initialized = false;
}
