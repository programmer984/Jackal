#ifndef EP_TYPES_H__
#define EP_TYPES_H__
#include <codec_api.h>
#include <codec_def.h>
#include <codec_app_def.h>

typedef unsigned char byte;

typedef struct {
    int size;
    byte* body;
} VideoFrame;

typedef struct {
    void (*writeOutYUVPart)(byte* data, int dataSize);
    void (*flushOut)();
    int yuvImageSize; //width * height * 3 / 2;
    ISVCDecoder *pSvcDecoder;
    SDecodingParam sDecParam;
    SBufferInfo sDstBufInfo;
    int width;
    int height;
    int wh;
    int wh4;
    int wh2;
    bool spsSet;
    byte* outBuf;
} EPInstance;

#endif