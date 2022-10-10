//
// Created by user on 29.05.22.
//

#ifndef HARDWARECONTROL2_CONFIG_H
#define HARDWARECONTROL2_CONFIG_H

#define BT_ENABLE GPIO_PIN_1
#define BT_TX GPIO_PIN_2
#define BT_RX GPIO_PIN_3
#define BT_PORT GPIOA

#define MV_LR GPIO_PIN_14
#define MV_UD GPIO_PIN_15
#define MV_PORT GPIOB

#define DATA_LED_PIN GPIO_PIN_12
#define DATA_LED_PORT GPIOB


#define HorizontalPwr_MinValue 0
#define HorizontalPwr_MaxValue 255
#define VerticalPwr_MinValue 0
#define VerticalPwr_MaxValue 255

#endif //HARDWARECONTROL2_CONFIG_H
