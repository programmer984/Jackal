package org.example.udphole;

public interface ConnectionStateListener {
    void onConnected();
    void onConnectionLost();
}
