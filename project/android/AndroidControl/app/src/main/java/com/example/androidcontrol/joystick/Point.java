package com.example.androidcontrol.joystick;

public class Point {
    private int x;
    private int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Point(Number x, Number y) {
        this.x = x.intValue();
        this.y = y.intValue();
    }


    public int getX() {
        return x;
    }
    public void setX(int x) {
        this.x = x;
    }
    public void setX(Number x) {
        this.x = x.intValue();
    }


    public int getY() {
        return y;
    }
    public void setY(int y) {
        this.y = y;
    }
    public void setY(Number y) {
        this.y = y.intValue();
    }
}
