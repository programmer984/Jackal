package org.example.softTimer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimersManager {
    private volatile long timeStampCounter = System.currentTimeMillis();
    private List<SoftTimer> timers = new CopyOnWriteArrayList<>();
    private static final Logger logger
            = LoggerFactory.getLogger(TimersManager.class);

    public TimersManager() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    hardwareTimerInvoke();
                    invokePendingTimers();//could be called from another thread
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    logger.info("soft timers manager stopped");
                } catch (Exception e) {
                    logger.error("soft timers manager stopped", e);
                }
            }
        }, "SoftTimers");
        thread.setDaemon(true);
        thread.start();
    }

    public SoftTimer addTimer(int period, boolean repeatable, TimerCallback callback) {
        SoftTimer timer = new SoftTimer();
        timer.pending = false;
        timer.period = period;
        timer.nextInvoke = timeStampCounter + period;
        timer.repeatable = repeatable;
        timer.callback = callback;
        timers.add(timer);
        return timer;
    }

    public void removeTimer(Object timer) {
        timers.remove(timer);
    }

    private void hardwareTimerInvoke() {

        timeStampCounter = System.currentTimeMillis();
        List<SoftTimer> timersCopy = new ArrayList<>(timers);

        for (SoftTimer timer : timersCopy) {
            if (!timer.pending) {
                if (timeStampCounter >= timer.nextInvoke) {
                    if (timer.repeatable) {
                        timer.nextInvoke = timeStampCounter + timer.period;
                    }
                    timer.pending = true;
                }
            }
        }
    }

    private void invokePendingTimers() {
        List<SoftTimer> timersCopy = new ArrayList<>(timers);

        for (SoftTimer timer : timersCopy) {
            if (timer.pending) {
                //if it is still present
                if (timers.contains(timer)) {
                    try {
                        timer.callback.invoke();
                    } catch (Exception ex) {
                        logger.error("Timer's callback executing error", ex);
                    }
                    timer.pending = false;
                    if (!timer.repeatable) {
                        timers.remove(timer);
                    }
                }
            }
        }
    }


}
