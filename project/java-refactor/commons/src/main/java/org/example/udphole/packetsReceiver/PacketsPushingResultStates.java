package org.example.udphole.packetsReceiver;

enum PacketsPushingResultStates {
    EVERYTHING_SENT,
    PACKET_INCOMPLETE, //we started receiving a packet
    TAIL_PRESENT //we have some tail which we can not analyze
}
