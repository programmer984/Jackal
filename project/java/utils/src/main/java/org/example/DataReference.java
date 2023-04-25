package org.example;

public final class DataReference {
    private final byte[] buf;
    private final int offset;
    private final int length;

    public DataReference(byte[] buf, int offset, int length) {
        this.buf = buf;
        this.offset = offset;
        this.length = length;
    }

    public DataReference(byte[] buf) {
        this.buf = buf;
        this.offset = 0;
        this.length = buf.length;
    }

    public byte[] getBuf() {
        return buf;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    /**
     * it copying bytes, so you can release original
     * @return
     */
    public byte[] extractBuf() {
        return ByteUtils.copyBytes(buf, offset, length);
    }
}
