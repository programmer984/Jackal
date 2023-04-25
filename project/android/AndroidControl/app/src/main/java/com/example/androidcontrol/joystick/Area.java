package com.example.androidcontrol.joystick;

public interface Area {
    boolean containPoint(Point point);

    /**
     * if circle - diameter
     * if rectangle - diagonal
     * @return
     */
    int getMaxDimention();
}
