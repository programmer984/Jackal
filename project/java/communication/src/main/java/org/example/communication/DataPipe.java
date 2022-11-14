package org.example.communication;

import org.example.communication.logging.PostLogger;

/**
 * transport layer
 */
public interface DataPipe {
    DataPipeStates getCurrentState();

    /**
     * method for sending data
     * @param data
     * @param offset
     * @param length
     * @param postLogger after data send, postLogger will be invoked
     */
    void sendData(byte[] data, int offset, int length, PostLogger postLogger);

    /**
     * method to setup receiver (please call during initialization, due prevent thread synchronization issue)
     * @param incomingDataConsumer
     */
    void setIncomingDataConsumer(PipeDataConsumer incomingDataConsumer);

    /**
     * must be called after setIncomingDataConsumer method
     */
    void startConnectAsync();
    void stop();
}
