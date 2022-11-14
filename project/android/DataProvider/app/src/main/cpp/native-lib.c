#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include "epTypes.h"
#include "encodePipe.h"
#include "log.h"

EPInstance instance;

void *encoderRunner();

YUVImage *readFromPipeNextImage();

void writeResultVideoFrame(byte *header, byte *data, int dataSize);

int pipeIn;
int pipeOut;
pthread_t readerThread;
#define INCOMING_BUF_SIZE 1024*1024
byte incomingBuffer[INCOMING_BUF_SIZE];
byte frameSize[4];
volatile int shouldWork;

JNIEXPORT void JNICALL
Java_com_example_dataprovider_service_H264Codec_init(JNIEnv *env, jobject thiz,
                                                     jint width, jint height,
                                                     jint max_frame_rate,
                                                     jint target_bitrate,
                                                     jint pipe_in,
                                                     jint pipe_out) {
    LOG_INFO("Pipe in %d, pipe out %d, line %d.", pipe_in, pipe_out, __LINE__);
    pipeIn = pipe_in;
    pipeOut = pipe_out;
    epInitialize(&instance, width, height, max_frame_rate, target_bitrate);
    instance.getNextYUVImage = &readFromPipeNextImage;
    instance.onVideoFrameCreated = &writeResultVideoFrame;
    if (INCOMING_BUF_SIZE < instance.yuvImageSize) {
        LOG_ERROR("Insufficient buffer size");
        return;
    }
    shouldWork = 1;
    pthread_create(&readerThread, NULL, &encoderRunner, NULL);
}

JNIEXPORT void JNICALL
Java_com_example_dataprovider_service_H264Codec_disposeLowLevel(JNIEnv *env, jobject thiz) {
    shouldWork = 0;
    pthread_kill(readerThread, 0);
    WelsDestroySVCEncoder(&instance.encoder);
    LOG_INFO("Read thread killed");
}

void *encoderRunner() {
    epRun(&instance);
}


YUVImage *readFromPipeNextImage() {
    //read from pipe header+yuvImage
    byte *imgPtr = &incomingBuffer;
    int offset = 0;
    int packetSize = instance.yuvImageSize + YUVImageHeaderSize;
    while (shouldWork) {
        int readCount = read(pipeIn, imgPtr + offset, packetSize - offset);
        offset += readCount;
        if (offset == packetSize) {
            break;
        }
    }
    return (YUVImage *) imgPtr;
}

void writeAll(byte *data, int dataSize) {
    while (dataSize > 0) {
        ssize_t sentSize = write(pipeOut, data, dataSize);
        dataSize -= sentSize;
        data += sentSize;
    }
}

void writeResultVideoFrame(byte *header, byte *data, int dataSize) {
    // 0x45, frameType, dataSize(4), data
    //write(pipeOut, header, VideFrameHeaderSize);
    //write(pipeOut, data, dataSize);
    writeAll(header, VideFrameHeaderSize);
    writeAll(data, dataSize);
}

