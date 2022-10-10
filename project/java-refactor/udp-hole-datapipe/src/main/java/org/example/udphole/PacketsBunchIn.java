package org.example.udphole;

public class PacketsBunchIn {
    private int logId;
    private byte[] bunch;

    public PacketsBunchIn(int logId, byte[] bunch) {
        this.logId = logId;
        this.bunch = bunch;
    }

    public int getLogId() {
        return logId;
    }

    public byte[] getBunch() {
        return bunch;
    }

    public int getLength(){
        return bunch.length;
    }
}
