package org.example.services.videoproducer;

public interface ImageProducer {
    YUVImage getFreshImageOrNull();

    void freeImage(YUVImage image);
}
