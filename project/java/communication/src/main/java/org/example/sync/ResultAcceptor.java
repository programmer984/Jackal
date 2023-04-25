package org.example.sync;


public interface ResultAcceptor {
    void synchronised(byte[] thisBlock, byte[] thatBlock);
    void notSynchronized();
}
