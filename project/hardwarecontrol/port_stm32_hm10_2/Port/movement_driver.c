
#include "common.h"
#include "movement.h"
#include "config.h"
#include "stm32f1xx_hal.h"
#include "calibration.h"

#define DAC_MAX 255
#define PWR_MAX 255
DAC_HandleTypeDef *dacPtr;

HorizontalDirection lastHD = HD_Idle;
VerticalDirection lastVD = VD_Idle;

void MovementDriver_Init(DAC_HandleTypeDef *hdac) {
    dacPtr = hdac;
    HAL_DAC_SetValue(dacPtr, DAC_CHANNEL_1, DAC_ALIGN_8B_R, 0);
    HAL_DAC_SetValue(dacPtr, DAC_CHANNEL_2, DAC_ALIGN_8B_R, 0);
    HAL_DAC_Start(dacPtr, DAC_CHANNEL_1);
    HAL_DAC_Start(dacPtr, DAC_CHANNEL_2);
}


int convertPowerToDacValue(int channel, int value) {
    int result=value;
    if (channel == DAC_CHANNEL_1) {
        result = DAC1_MINIMUM_VALUE + (((DAC_MAX - DAC1_MINIMUM_VALUE - 1) * value) / PWR_MAX);
    } else if (channel == DAC_CHANNEL_2) {
        result = DAC2_MINIMUM_VALUE + (((DAC_MAX - DAC2_MINIMUM_VALUE - 1) * value) / PWR_MAX);
    }
    return result;
}


/**
 * assume 1 - Left-Right
 * 2 - Up-Down
 * @param packet
 */
void movementMove(MovementPacket_t *packet) {

    if (packet->hd == HD_Right && lastHD != HD_Right) {
        HAL_GPIO_WritePin(MV_PORT, MV_LR, GPIO_PIN_SET);
        lastHD = HD_Right;
    } else if (packet->hd == HD_Left && lastHD != HD_Left) {
        HAL_GPIO_WritePin(MV_PORT, MV_LR, GPIO_PIN_RESET);
        lastHD = HD_Left;
    }
    HAL_DAC_SetValue(dacPtr, DAC_CHANNEL_1, DAC_ALIGN_8B_R,
                     packet->hd == HD_Idle ? 0 : convertPowerToDacValue(DAC_CHANNEL_1, packet->hdPower));
    HAL_DAC_Start(dacPtr, DAC_CHANNEL_1);



    if (packet->vd == VD_Down && lastVD != VD_Down) {
        HAL_GPIO_WritePin(MV_PORT, MV_UD, GPIO_PIN_SET);
        lastVD = VD_Down;
    } else if (packet->vd == VD_Up && lastVD != VD_Up) {
        HAL_GPIO_WritePin(MV_PORT, MV_UD, GPIO_PIN_RESET);
        lastVD = VD_Up;
    }
    HAL_DAC_SetValue(dacPtr, DAC_CHANNEL_2, DAC_ALIGN_8B_R,
                     packet->vd == VD_Idle ? 0 : convertPowerToDacValue(DAC_CHANNEL_2, packet->vdPower));
    HAL_DAC_Start(dacPtr, DAC_CHANNEL_2);
}

