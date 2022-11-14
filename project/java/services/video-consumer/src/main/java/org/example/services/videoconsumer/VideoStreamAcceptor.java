package org.example.services.videoconsumer;

public interface VideoStreamAcceptor {
    void configureVideoAcceptor(int width, int height);
    void writeVideoHeader(byte[] buf, int offset, int length) throws Exception;
    void writeVideoFrame(int id, int partIndex, int partsCount, byte[] buf, int offset, int length) throws Exception;
}
