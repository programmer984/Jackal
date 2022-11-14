package org.example;

public class PacketOut {
    private final byte[] data;
    private final PostLogger postLogger;

    public PacketOut(byte[] data) {
        this.data = data;
        this.postLogger = null;
    }
    public PacketOut(byte[] data, PostLogger postLogger) {
        this.data = data;
        this.postLogger = postLogger;
    }

    public byte[] getData() {
        return data;
    }

    public PostLogger getPostLogger() {
        return postLogger;
    }

}
