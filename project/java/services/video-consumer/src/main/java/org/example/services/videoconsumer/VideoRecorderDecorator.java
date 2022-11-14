package org.example.services.videoconsumer;

import org.example.DataReference;

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
    public void writeVideoHeader(DataReference dataReference) throws Exception {
        decorable.writeVideoHeader(dataReference);
        fileOutputStream.write(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength());
    }

    @Override
    public void writeVideoFrame(int id, int partIndex, int partsCount, DataReference dataReference) throws Exception {
        decorable.writeVideoFrame(id, partIndex, partsCount, dataReference);
        fileOutputStream.write(dataReference.getBuf(), dataReference.getOffset(), dataReference.getLength());
    }

    @Override
    public void close() throws Exception {
        if (fileOutputStream != null) {
            fileOutputStream.close();
            fileOutputStream = null;
        }
    }
}
