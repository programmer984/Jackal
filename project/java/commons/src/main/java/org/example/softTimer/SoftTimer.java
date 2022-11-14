package org.example.softTimer;

public class SoftTimer {
    int period;
    long nextInvoke;
    boolean pending; //awaiting main loop to invoke it
    boolean repeatable; //after pending state on, increase nextInvoke time to invoke
    TimerCallback callback;

    public int getPeriod() {
        return period;
    }
}
