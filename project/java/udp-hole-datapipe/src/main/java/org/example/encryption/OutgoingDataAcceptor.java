package org.example.encryption;

import java.io.IOException;

@FunctionalInterface
public interface OutgoingDataAcceptor {
    void sendEncryptedData(byte[] data, int offset, int length) throws IOException;
}
