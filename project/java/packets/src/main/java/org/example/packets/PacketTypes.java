package org.example.packets;


import java.util.HashMap;
import java.util.Map;

public enum PacketTypes {
    VideoFrame(0x11),
    VideoHeader(0x12),
    VideoLacksRequest(0x13),
    KeepAlive(0x44),
    // 0x70 - 0x7F - hardware control packets (should be retranslated to Hardware Control)
    HWKeepAlive(0x70),
    HWDoMove(0x71),
    ;

    private final int number;
    public static final int minimumSize = org.example.packets.KeepAlive.minimumSize;
    private final static Map<Integer, PacketTypes> typesMap = new HashMap<>();

    static {
        for (PacketTypes pt : PacketTypes.values()) {
            typesMap.put(pt.getNumber(), pt);
        }
    }

    PacketTypes(int number) {
        this.number = number;
    }

    public static PacketTypes get(byte number) {
        return typesMap.get(number & 0xFF);
    }

    public int getNumber() {
        return number;
    }

    public byte getNumberAsByte() {
        return (byte) number;
    }
}
