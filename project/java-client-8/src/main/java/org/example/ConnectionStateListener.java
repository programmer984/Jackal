package org.example;

public interface ConnectionStateListener {
    void onConnected();
    void onConnectionLost();
}
