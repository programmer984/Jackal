package org.example.services.videoconsumer;

import org.example.DataReference;

public interface VideoStreamAcceptor {
    void configureVideoAcceptor(int width, int height);
    void writeVideoHeader(DataReference data) throws Exception;
    void writeVideoFrame(int id, int partIndex, int partsCount, DataReference data) throws Exception;
}
