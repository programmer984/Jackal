package org.example.softTimer;

public class SoftTimer {
    int period;
    long nextInvoke;
    boolean pending; //awaiting main loop to invoke it
    boolean repeatable; //after pending state on, increase nextInvoke time to invoke
    TimerCallback callback;
    String timerName;

    public int getPeriod() {
        return period;
    }

    /**
     * In a future TimeUtils.nowMs() + 120ms for example
     * @param nextInvoke
     */
    public void setNextInvoke(long nextInvoke) {
        this.nextInvoke = nextInvoke;
    }
}
