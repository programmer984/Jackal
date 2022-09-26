package org.example.udphole.softTimer;

class SoftTimer {
    int period;
    long nextInvoke;
    boolean pending; //awaiting main loop to invoke it
    boolean repeatable; //after pending state on, increase nextInvoke time to invoke
    TimerCallback callback;
}
