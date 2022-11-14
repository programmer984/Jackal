#include "packets_receiver.h"

#ifndef __VF_PROTOCOL_H
#define __VF_PROTOCOL_H

#define IP_MINIMUM_PACKET_SIZE 12
#define IP_MAXIMUM_PACKET_SIZE 200000
#define START_TOKEN_SIZE 4
#define LENGTH_OFFSET 4
#define BODY_OFFSET 8
#define TLC_LENGTH 8

extern ProtocolHandlers_t* createProtocolHandlers();

#endif
