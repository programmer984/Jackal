package org.example.softTimer;
@FunctionalInterface
public interface TimerCallback {
    void invoke() throws Exception;
}
