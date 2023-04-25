package com.example.androidcontrol.joystick.user_listen;

@FunctionalInterface
public interface CurrentValueListener {
    void onValueChanged(int value);
}
