package org.example.communication;

@FunctionalInterface
public interface PipeDataConsumer {
    void onDataReceived(byte[] data, int offset, int size, Integer logId);

    default void onDataReceived(byte[] data, int offset, int size){
        onDataReceived(data, offset, size, null);
    }
}
