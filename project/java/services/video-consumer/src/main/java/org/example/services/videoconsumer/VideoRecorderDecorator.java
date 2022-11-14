package org.example.services.videoconsumer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class VideoRecorderDecorator implements VideoStreamAcceptor, AutoCloseable {
    private final VideoStreamAcceptor decorable;
    private OutputStream fileOutputStream;

    public VideoRecorderDecorator(VideoStreamAcceptor decorable, File outputFile) throws IOException {
        this.decorable = decorable;
        outputFile.getAbsoluteFile().getParentFile().mkdirs();
        outputFile.createNewFile();
        fileOutputStream = new FileOutputStream(outputFile);
    }

    @Override
    public void configureVideoAcceptor(int width, int height) {
        decorable.configureVideoAcceptor(width, height);
    }

    @Override
    public void writeVideoHeader(byte[] buf, int offset, int length) throws Exception {
        decorable.writeVideoHeader(buf, offset, length);
        fileOutputStream.write(buf, offset, length);
    }

    @Override
    public void writeVideoFrame(int id, int partIndex, int partsCount, byte[] buf, int offset, int length) throws Exception {
        decorable.writeVideoFrame(id, partIndex, partsCount, buf, offset, length);
        fileOutputStream.write(buf, offset, length);
    }

    @Override
    public void close() throws Exception {
        if (fileOutputStream != null) {
            fileOutputStream.close();
            fileOutputStream = null;
        }
    }
}
