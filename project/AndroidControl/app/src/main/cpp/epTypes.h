#ifndef EP_TYPES_H__
#define EP_TYPES_H__
#include <codec_api.h>
#include <codec_def.h>
#include <codec_app_def.h>

typedef unsigned char byte;

#define VideoFrameMarker 0x45
#define VideoFrameBodySize 2
#define VideoFrameHeaderSize 3

typedef struct {
    byte start;     // 0x45
    byte bodySize[2]; //little endian
    byte body[1];
} VideoFrame;

typedef struct {
    VideoFrame* (*getNextFrame)(); //blocking method
    void (*writeOutYUVPart)(int dataSize, byte* data);
    void (*flushOut)();
    int yuvImageSize; //width * height * 3 / 2;
    int wh;
    int wh4;
    int wh2;
    ISVCDecoder *pSvcDecoder;
    SDecodingParam sDecParam;
    SBufferInfo sDstBufInfo;
} EPInstance;

#endif