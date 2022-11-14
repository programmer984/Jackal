package org.example.sync;

public enum PacketTypes {
    UNKNOWN(0),
    DIRECT(1),
    REPLY(2),
    COUNTDOWN(3);

    private final int binarySerialization;

    PacketTypes(int binarySerialization) {
        this.binarySerialization = binarySerialization;
    }

    public static PacketTypes forValue(int value) {
        if (value > 0 && value < PacketTypes.values().length) {
            return PacketTypes.values()[value];
        }
        return UNKNOWN;
    }

    public int value(){
        return binarySerialization;
    }
}
