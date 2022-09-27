package org.example.serviceComponents.packets;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class PacketTypes {

    public final static int ImagePart = 0x10;
    public final static int VideoFrame = 0x11;
    public final static int VideoHeader = 0x12;
    public final static int VideoLacksRequest = 0x13;
    public final static int KeepAlive = 0x44;

    // 0x70 - 0x7F - hardware control packets (should be retranslated to Hardware Control)
    public final static int HWKeepAlive = 0x70;
    public final static int HWDoMove = 0x71;
    //packet which we should pass through (from UdpClient to hw OR from hw to UdpClient)
    public static boolean isHwThroughPacket(int packetType){
        return (packetType>0x70 && packetType<0x7F);
    }
}
