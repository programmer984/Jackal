#ifndef EP_TYPES_H__
#define EP_TYPES_H__
#include <codec_api.h>
#include <codec_def.h>
#include <codec_app_def.h>

typedef unsigned char byte;

//incoming packet format 0x45, 0x45, 0x47, timestamp[8], body[yuvImageSize]
#define YUVImageStartTokenSize 3
#define YUVImageTimestampSize 8
#define YUVImageHeaderSize (YUVImageStartTokenSize + YUVImageTimestampSize)

//outgoing packet format 0x45, 0x45, 0x47, frameType[1], bodySize[4], body[bodySize]
#define VideFrameHeaderSize 8

typedef struct {
    byte start[3];     // 0x45, 0x45, 0x47
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