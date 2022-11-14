package org.example.packetsReceiver;

enum PacketsPushingResultStates {
    NOT_FOUND,      //no packets found
    EVERYTHING_SENT,
    PACKET_INCOMPLETE, //we started receiving a packet
    TAIL_PRESENT //we have some tail which we can not analyze
}
