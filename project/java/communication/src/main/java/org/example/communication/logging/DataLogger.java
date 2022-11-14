package org.example.communication.logging;

public interface DataLogger {
    default Integer addIncomingBunch(byte[] data) {
        return addIncomingBunch(data, 0, data.length);
    }

    Integer addIncomingBunch(byte[] data, int offset, int length);

    /**
     * invoking postLogger after data was send
     *
     * @param data
     * @param postLogger
     */
    default void addOutgoingBunch(byte[] data, PostLogger postLogger) {
        addOutgoingBunch(data, 0, data.length, postLogger);
    }

    void addOutgoingBunch(byte[] data, int offset, int length, PostLogger postLogger);

    /**
     * not pending tasks
     * @return
     */
    boolean isEmpty();

    void join() throws InterruptedException;
}
