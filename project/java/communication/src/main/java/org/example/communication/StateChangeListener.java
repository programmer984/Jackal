package org.example.communication;

public interface StateChangeListener {
    void onConnected();
    void onConnectFailed();
    void onDisconnected();
}
