package com.example.dataprovider.service;

import org.example.ByteUtils;
import org.example.services.videoproducer.codec.VideoFrame;
import org.example.services.videoproducer.codec.VideoFrameConsumer;
import org.example.services.videoproducer.codec.VideoFrameTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

class VideoRecorderDecorator implements VideoFrameConsumer {
    private final DataProviderService dataProviderService;
    private final VideoFrameConsumer original;
    private final int framesToWrite = 500;
    private int framesWritten = 0;
    private final byte[] startTokenBuf = new byte[]{0x45, 0x45, 0x47};
    private final byte[] lengthBuf = new byte[4];
    private final Logger logger = LoggerFactory.getLogger(VideoRecorderDecorator.class);
    private FileOutputStream outputStream = new FileOutputStream("/sdcard/packets/preload.h264");

    VideoRecorderDecorator(DataProviderService dataProviderService, VideoFrameConsumer original) throws FileNotFoundException {
        this.dataProviderService = dataProviderService;
        this.original = original;
    }

    @Override
    public void accept(VideoFrame videoFrame) {
        original.accept(videoFrame);
        if (framesWritten < framesToWrite) {
            try {
                if (videoFrame.getVideoFrameType() == VideoFrameTypes.VideoFrameTypeI ||
                        videoFrame.getVideoFrameType() == VideoFrameTypes.VideoFrameTypeIDR) {
                    writeFrame((byte) videoFrame.getVideoFrameType().ordinal(), videoFrame.getFrameData());
                    framesWritten++;
                } else if (videoFrame.getVideoFrameType() == VideoFrameTypes.VideoFrameTypeP && framesWritten > 0) {
                    writeFrame((byte) videoFrame.getVideoFrameType().ordinal(), videoFrame.getFrameData());
                    framesWritten++;
                }
                if (framesWritten == framesToWrite) {
                    outputStream.close();
                }
            } catch (IOException e) {
                logger.error("During preload save", e);
            }
        }
    }

    private void writeFrame(byte frameType, byte[] frameData) throws IOException {
        outputStream.write(startTokenBuf);
        outputStream.write(frameType);
        ByteUtils.i32ToBuf(frameData.length, lengthBuf, 0);
        outputStream.write(lengthBuf);
        outputStream.write(frameData);
    }
}
