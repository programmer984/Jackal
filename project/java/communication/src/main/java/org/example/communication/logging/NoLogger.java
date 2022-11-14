package org.example.communication.logging;

public class NoLogger implements DataLogger {
    @Override
    public Integer addIncomingBunch(byte[] data, int offset, int length) {
        return null;
    }

    @Override
    public void addOutgoingBunch(byte[] data, int offset, int length, PostLogger postLogger) {
        if (postLogger != null) {
            postLogger.logAttempHappen(null);
        }
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public void join() throws InterruptedException {

    }
}
