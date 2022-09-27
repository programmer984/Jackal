#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include "epTypes.h"
#include "decodePipe.h"
#include "log.h"

EPInstance instance;

void *decodeRunner();

VideoFrame *readFromPipeNextFrame();

void writeYUVPart(int size, byte *data);
void flushOut();

int pipeIn;
int pipeOut;
pthread_t readerThread;
#define INCOMING_BUF_SIZE 1024*1024
byte incomingBuffer[INCOMING_BUF_SIZE];


JNIEXPORT void JNICALL Java_com_example_androidcontrol_video_Decoder_initDecoder(JNIEnv *env, jobject thiz,
                                                                    jint width, jint height,
                                                                    jint pipe_in,
                                                                    jint pipe_out) {
    LOG_INFO("Pipe in %d, pipe out %d, line %d.", pipe_in, pipe_out, __LINE__);
    pipeIn = pipe_in;
    pipeOut = pipe_out;
    epInitialize(&instance, width, height);
    instance.getNextFrame = &readFromPipeNextFrame;
    instance.writeOutYUVPart = &writeYUVPart;
    instance.flushOut = &flushOut;
    if (INCOMING_BUF_SIZE < instance.yuvImageSize) {
        LOG_ERROR("Insufficient buffer size");
        return;
    }

    pthread_create(&readerThread, NULL, &decodeRunner, NULL);
}

void *decodeRunner() {
    epRun(&instance);
}


VideoFrame *readFromPipeNextFrame() {
    //read from pipe header+videoFrame
    byte *framePtr = &incomingBuffer;
    int offset = 0;
    int packetSize = 0;
    int bodySize = 0;

    //read frame
    while (1) {
        int readCount = read(pipeIn, framePtr + offset, packetSize == 0 ? VideoFrameHeaderSize :
                                                        packetSize - offset);
        offset += readCount;
        //check header read
        if (bodySize == 0 && framePtr[0] == VideoFrameMarker) {
            if (offset >= VideoFrameHeaderSize) {
                memcpy((byte *) &bodySize, framePtr + 1, VideoFrameBodySize);
                packetSize = bodySize + VideoFrameHeaderSize;
            } else {
                continue;
            }
        }
        if (offset == packetSize) {
            break;
        }
    }
    return (VideoFrame *) framePtr;
}

void writeYUVPart(int size, byte *data) {
    write(pipeOut, data, size);
}

void flushOut() {
    //fflush(pipeOut);
}