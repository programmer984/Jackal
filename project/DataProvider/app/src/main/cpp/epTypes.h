#ifndef EP_TYPES_H__
#define EP_TYPES_H__
#include <codec_api.h>
#include <codec_def.h>
#include <codec_app_def.h>

typedef unsigned char byte;

#define YUVImageStartMarker 0x45
#define YUVImageTimestampSize 8
#define YUVImageHeaderSize 9

#define HeaderSize 2

typedef struct {
    byte start;     // 0x45
    byte timestamp[8]; //little endian
    byte body[1];
} YUVImage;

typedef struct {
    YUVImage* (*getNextYUVImage)(); //blocking method, blocks untill image ready
    void (*onVideoFrameCreated)(byte* header, byte* data, int dataSize);
    int yuvImageSize; //width * height * 3 / 2;
    int wh; //width * height
    ISVCEncoder *encoder;
    //SEncParamBase param; //params (set during initialization)
    SEncParamExt param;
    SSourcePicture pic; //picture
    SFrameBSInfo info; //result of encoding   
} EPInstance;

#endif