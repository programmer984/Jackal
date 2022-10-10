#include "packets_receiver.h"

#ifndef __INTERNAL_PROTOCOL_H
#define __INTERNAL_PROTOCOL_H

#define IP_MINIMUM_PACKET_SIZE 4
#define IP_MAXIMUM_PACKET_SIZE 19
#define IP_START_TOKEN 0x69
#define START_TOKEN_SIZE 1
#define TYPE_OFFSET 1
#define LENGTH_OFFSET 2
#define BODY_OFFSET 4
#define TLC_LENGTH 4
#define CRC_LENGTH 1

extern ProtocolHandlers_t* createProtocolHandlers();

#endif
