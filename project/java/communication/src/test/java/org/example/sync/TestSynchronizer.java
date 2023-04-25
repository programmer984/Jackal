package org.example.sync;

import org.example.DataReference;
import org.example.softTimer.TimersManager;

import java.util.function.Consumer;

class TestSynchronizer extends Synchronizer{
    Consumer<DataReference> sendingDataAcceptor;

    byte[] outBuf=new byte[300];
    int index;
    public TestSynchronizer(ResultAcceptor resultAcceptor, TimersManager timersManager, String threadName) {
        super(resultAcceptor, timersManager, threadName);
    }

    @Override
    protected void sendPacket(BasePacket packet) {
        packet.toArray(outBuf, index);
        sendingDataAcceptor.accept(new DataReference(outBuf, index, packet.calculateSize()));
        index++;
    }

    public void setSendingDataAcceptor(Consumer<DataReference> sendingDataAcceptor){
        this.sendingDataAcceptor = sendingDataAcceptor;
    }

}
