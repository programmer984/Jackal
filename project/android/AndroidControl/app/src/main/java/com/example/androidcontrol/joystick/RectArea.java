package com.example.androidcontrol.joystick;

public class RectArea implements Area {
    private final int xL;
    private final int xR;
    private final int yT;
    private final int yB;
    private final int maxDimenstion;

    public RectArea(int absoluteX, int absoluteY, int with, int height) {
        this.xL = absoluteX;
        this.xR = absoluteX + with;
        this.yT = absoluteY;
        this.yB = absoluteY + height;
        this.maxDimenstion = (int) Math.sqrt(Math.pow(with, 2) + Math.pow(height, 2));
    }

    @Override
    public boolean containPoint(Point point) {
        return point.getX() > xL && point.getX() < xR
                && point.getY() > yT && point.getY() < yB;
    }

    @Override
    public int getMaxDimention() {
        return maxDimenstion;
    }

}
