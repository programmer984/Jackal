package org.example;

import java.io.IOException;

@FunctionalInterface
public interface ISocket {
    void send(byte[] buf, int offset, int length) throws IOException;
}
