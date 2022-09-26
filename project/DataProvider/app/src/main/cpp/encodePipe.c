//#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include "assert.h"
#include <pthread.h>
#include <codec_api.h>
#include <codec_def.h>
#include <codec_app_def.h>
#include "epTypes.h"

int epInitialize(EPInstance *instance, int width, int height,
                 int maxFrameRate, int targetBitrate) {
    int rv = WelsCreateSVCEncoder(&instance->encoder);
    assert(0 == rv);
    assert(instance->encoder != NULL);

    instance->wh = width * height;
    instance->yuvImageSize = width * height * 3 / 2;

    const ISVCEncoderVtbl *inst = (*instance->encoder);
    /*
    SEncParamBase *param = &instance->param;
    memset(param, 0, sizeof(SEncParamBase));
    param->iUsageType = CAMERA_VIDEO_REAL_TIME;
    param->fMaxFrameRate = maxFrameRate;
    param->iPicWidth = width;
    param->iPicHeight = height;
    param->iTargetBitrate = targetBitrate;
    inst->Initialize(instance->encoder, param);
    */

    SEncParamExt *param = &instance->param;
    rv = inst->GetDefaultParams(instance->encoder, param);
    assert(0 == rv);
    param->iUsageType = CAMERA_VIDEO_REAL_TIME;
    param->fMaxFrameRate = maxFrameRate;
    param->iPicWidth = width;
    param->iPicHeight = height;
    param->iTargetBitrate = targetBitrate;
    param->uiIntraPeriod = 500000;

    rv = inst->InitializeExt(instance->encoder, param);
    assert(0 == rv);

    int videoFormat = videoFormatI420;
    inst->SetOption(instance->encoder, ENCODER_OPTION_DATAFORMAT, &videoFormat);
    int idrInterval = maxFrameRate;
    inst->SetOption(instance->encoder, ENCODER_OPTION_IDR_INTERVAL, &idrInterval);

    SSourcePicture *pic = &instance->pic;
    memset(pic, 0, sizeof(SSourcePicture));
    pic->iPicWidth = width;
    pic->iPicHeight = height;
    pic->iColorFormat = videoFormat;
    pic->iStride[0] = pic->iPicWidth;
    pic->iStride[1] = pic->iPicWidth >> 1;
    pic->iStride[2] = pic->iPicWidth >> 1;

    //pic.pData[0] = (unsigned char *)&buf[0];
    //pic.pData[1] = (unsigned char *)&buf[width * height];
    //pic.pData[2] = (unsigned char *)&buf[(width * height) + (width * height >> 2)];

    return 0;
}

void epRun(EPInstance *instance) {
    SSourcePicture *pic = &instance->pic;
    const ISVCEncoderVtbl *inst = (*instance->encoder);
    ISVCEncoder *encoder = instance->encoder;
    SFrameBSInfo info;
    byte frameHeader[HeaderSize];
    frameHeader[0] = 0x45;
    byte *headerPtr = &frameHeader[0];
    int rv;
    while (1) {
        YUVImage *image = instance->getNextYUVImage();
        if (image->start != YUVImageStartMarker) {
            break;
        }
        long timestamp = 0;
        memcpy((byte *) &timestamp, &image->timestamp[0], YUVImageTimestampSize);
        pic->uiTimeStamp = timestamp;

        byte *data = image->body;
        pic->pData[0] = data;
        pic->pData[1] = data + instance->wh;
        pic->pData[2] = data + instance->wh + (instance->wh >> 2);
        //prepare input data
        memset(&info, 0, sizeof(SFrameBSInfo));
        rv = inst->EncodeFrame(encoder, pic, &info);
        assert(rv == cmResultSuccess);
        /// videoFrameTypeIDR,        < IDR frame in H.264
        /// videoFrameTypeI,          < I frame type
        /// videoFrameTypeP,          < P frame type
        if (info.eFrameType != videoFrameTypeSkip) {
            //output bitstream handling
            frameHeader[1] = info.eFrameType;

            for (int iLayer = 0; iLayer < info.iLayerNum; iLayer++) {
                SLayerBSInfo layerInfo = info.sLayerInfo[iLayer];

                int iLayerSize = 0;
                int iNalIdx = layerInfo.iNalCount - 1;
                do {
                    iLayerSize += layerInfo.pNalLengthInByte[iNalIdx];
                    --iNalIdx;
                } while (iNalIdx >= 0);

                byte *outBuf = layerInfo.pBsBuf;
                instance->onVideoFrameCreated(headerPtr, outBuf, iLayerSize);
            }
        }
    }
}
