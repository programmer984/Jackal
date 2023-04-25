package com.example.androidcontrol.joystick.value;

public class JoystickValue {
    private final static int verticalEnumOffset = 14 + 16;
    private final static int verticalValueOffset = 16;
    private final static int horizontalEnumOffset = 14;
    private final static int horizontalValueOffset = 0;
    private final static int INTERNAL_MAX_VALUE = 0x3FF;
    private final int maxValue;

    /*
    value - maxValue
    internalValue - INTERNAL_MAX_VALUE
     */


    private int horizontalInternalValue;
    private HorizontalDirection horizontalDirection;
    private int verticalInternalValue;
    private VerticalDirection verticalDirection;

    public JoystickValue(int maxValue) {
        this.maxValue = maxValue;
    }

    public void setHorizontalValue(int value, HorizontalDirection direction) {
        horizontalInternalValue = Math.abs(value) * INTERNAL_MAX_VALUE / maxValue;
        if (horizontalInternalValue > INTERNAL_MAX_VALUE) {
            horizontalInternalValue = INTERNAL_MAX_VALUE;
        }
        horizontalDirection = direction;
    }

    public void setVerticalValue(int value, VerticalDirection direction) {
        verticalInternalValue = Math.abs(value) * INTERNAL_MAX_VALUE / maxValue;
        if (verticalInternalValue > INTERNAL_MAX_VALUE) {
            verticalInternalValue = INTERNAL_MAX_VALUE;
        }
        verticalDirection = direction;
    }

    public void setVerticalDirection(VerticalDirection direction) {
        verticalDirection = direction;
    }

    public void setHorizontalDirection(HorizontalDirection direction) {
        horizontalDirection = direction;
    }

    //[2] vertical enum, [14] vertical value, [2] horizontal enum, [14] horizontal value
    public int toInt() {
        return verticalDirection.ordinal() << verticalEnumOffset |
                (verticalInternalValue << verticalValueOffset) |
                horizontalDirection.ordinal() << horizontalEnumOffset |
                (horizontalInternalValue << horizontalValueOffset)
                ;
    }

    public static HorizontalDirection getHorizontalDirection(int serializedValue) {
        return HorizontalDirection.values()[(serializedValue >> horizontalEnumOffset) & 0x03];
    }

    public static int getHorizontalValue(int maxValue, int serializedObject) {
        int internalValue = (serializedObject >> horizontalValueOffset) & INTERNAL_MAX_VALUE;
        return internalValue*maxValue/INTERNAL_MAX_VALUE;
    }

    public static VerticalDirection getVerticalDirection(int serializedValue) {
        return VerticalDirection.values()[(serializedValue >> verticalEnumOffset) & 0x03];
    }

    public static int getVerticalValue(int maxValue, int serializedObject) {
        int internalValue = (serializedObject >> verticalValueOffset) & INTERNAL_MAX_VALUE;
        return internalValue*maxValue/INTERNAL_MAX_VALUE;
    }


}
