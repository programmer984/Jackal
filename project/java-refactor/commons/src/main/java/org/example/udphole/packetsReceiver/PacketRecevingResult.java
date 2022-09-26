package org.example.udphole.packetsReceiver;

public class PacketRecevingResult {
    PacketRecevingResultStates resultState;
    int size;

    public PacketRecevingResultStates getResultState() {
        return resultState;
    }

    public void setResultState(PacketRecevingResultStates resultState) {
        this.resultState = resultState;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
