#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include "epTypes.h"
#include "decodePipe.h"
#include "log.h"
#include "packets_receiver.h"
#include "video_frame_receiver.h"
#include "search.h"

#define STOP_PACKET_SIZE 12


EPInstance instance;
VideoFrame videoFrame;

void *decodeRunner();

void writeYUVPart(byte *data, int size);

void flushOut();

int pipeIn;
int pipeOut;
int width;
int height;
pthread_t readerThread;
volatile bool shouldWork;



JNIEXPORT void JNICALL
Java_com_example_androidcontrol_video_Decoder_initDecoder(JNIEnv *env, jobject thiz,
                                                          jint width_, jint height_,
                                                          jint pipe_in,
                                                          jint pipe_out) {
    LOG_INFO("Pipe in %d, pipe out %d, line %d.", pipe_in, pipe_out, __LINE__);
    pipeIn = pipe_in;
    pipeOut = pipe_out;
    width=width_;
    height=height_;
    pthread_create(&readerThread, NULL, &decodeRunner, NULL);
}

JNIEXPORT void JNICALL
Java_com_example_androidcontrol_video_Decoder_destroyDecoder(JNIEnv *env, jobject thiz){
    shouldWork = false;
}

int getCurrentSpeed() {
    return 1; //1 byte/ms - 1KB/s
}


CommunicationDriver_t *createDriverInstance() {
    CommunicationDriver_t *driver = malloc(sizeof(CommunicationDriver_t));
    driver->getCurrentSpeed = &getCurrentSpeed;
    return driver;
}

RxProps_t *createRxProps() {
    RxProps_t *rxProps = malloc(sizeof(RxProps_t));
    memset(rxProps, 0, sizeof(RxProps_t));
    rxProps->rxBufSize = width*height*2;
    rxProps->rxBuf = malloc(rxProps->rxBufSize);
    return rxProps;
}

void packetConsumer(byte *data, int size) {
    videoFrame.size = size - TLC_LENGTH;
    videoFrame.body = data + BODY_OFFSET;
    putNewFrame(&instance, &videoFrame);
}

void *decodeRunner() {
    //read from pipe header+videoFrame
    LOG_DEBUG("epInitialize, line %d.", __LINE__);
    epInitialize(&instance, width, height);
    instance.writeOutYUVPart = &writeYUVPart;
    instance.flushOut = &flushOut;

    LOG_DEBUG("PacketReceiverInstance malloc, line %d.", __LINE__);
    PacketReceiverInstance_t *prInstance = malloc(sizeof(PacketReceiverInstance_t));
    prInstance->protocolHandlers = createProtocolHandlers();
    prInstance->communicationDriver = createDriverInstance();
    prInstance->rxProp = createRxProps();
    prInstance->packetConsumer = &packetConsumer;


    int tmpBufSize = 20000;
    byte tmpBuffer[tmpBufSize];
    byte *tmpBufPtr = &tmpBuffer;
    shouldWork = true;

    //read frame
    while (shouldWork) {
        int readCount = read(pipeIn, tmpBufPtr, tmpBufSize);
        onNewDataReceived(prInstance, tmpBufPtr, readCount);
    }

    LOG_DEBUG("WelsDestroyDecoder, line %d.", __LINE__);
    WelsDestroyDecoder(instance.pSvcDecoder);

    free(instance.outBuf);
    free(prInstance->rxProp->rxBuf);
    free(prInstance->rxProp);
    free(prInstance->communicationDriver);
    free(prInstance->protocolHandlers);
    free(prInstance);
    //close(pipeIn);
    //close(pipeOut);
}

void writeAll(byte *data, int dataSize) {
    while (dataSize > 0) {
        ssize_t sentSize = write(pipeOut, data, dataSize);
        dataSize -= sentSize;
        data += sentSize;
    }
}

void dataIndicatorOn(){

}
void dataIndicatorOff(){

}

void writeYUVPart(byte *data, int size) {
    writeAll(data, size);
}

void flushOut() {
    //fflush(pipeOut);
}