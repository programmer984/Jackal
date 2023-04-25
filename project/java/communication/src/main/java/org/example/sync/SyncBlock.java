package org.example.sync;

class SyncBlock {
    private final int id;
    private final byte[] data;

    public SyncBlock(int id, byte[] data) {
        this.id = id;
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }
}
