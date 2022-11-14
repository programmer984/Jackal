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
#include "log.h"
#include "common.h"
volatile bool shouldWork;


//produces start[3], frame_type[1], body_length[4], body[body_length]
//start 0x45 0x45 0x47
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
    param->uiIntraPeriod = 2000;


    rv = inst->InitializeExt(instance->encoder, param);
    assert(0 == rv);

    int videoFormat = videoFormatI420;
    inst->SetOption(instance->encoder, ENCODER_OPTION_DATAFORMAT, &videoFormat);
    int idrInterval = maxFrameRate*2; //every 2 seconds
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

void stopThread(){
    shouldWork = false;
}

void epRun(EPInstance *instance) {
    SSourcePicture *pic = &instance->pic;
    const ISVCEncoderVtbl *inst = (*instance->encoder);
    ISVCEncoder *encoder = instance->encoder;
    SFrameBSInfo info;
    byte frameHeader[VideFrameHeaderSize];
    frameHeader[0] = 0x45;
    frameHeader[1] = 0x45;
    frameHeader[2] = 0x47;
    byte *headerPtr = &frameHeader[0];
    shouldWork = true;
    int rv;
    while (shouldWork) {
        YUVImage *image = instance->getNextYUVImage();
        if (image->start[0] != frameHeader[0]
            || image->start[1] != frameHeader[1]
               || image->start[2] != frameHeader[2]) {
            break;
        }

        pic->uiTimeStamp = getS64(&image->timestamp[0]);
        //LOG_DEBUG("Picture timestamp %lld", pic->uiTimeStamp);

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
        //LOG_DEBUG("Frame type %u", info.eFrameType);


        if (info.eFrameType == videoFrameTypeP ||
        info.eFrameType == videoFrameTypeI || info.eFrameType == videoFrameTypeIDR) {
            //output bitstream handling
            frameHeader[3] = info.eFrameType;
            int totalSize=0;
            for (int iLayer = 0; iLayer < info.iLayerNum; iLayer++) {
                SLayerBSInfo layerInfo = info.sLayerInfo[iLayer];
                int iNalIdx = layerInfo.iNalCount - 1;
                do {
                    totalSize += layerInfo.pNalLengthInByte[iNalIdx];
                    --iNalIdx;
                } while (iNalIdx >= 0);
            }
            frameHeader[4] = (byte)totalSize;
            frameHeader[5] = (byte)(totalSize>>8);
            frameHeader[6] = (byte)(totalSize>>16);
            frameHeader[7] = (byte)(totalSize>>24);

            instance->writeOut(headerPtr, VideFrameHeaderSize);

            for (int iLayer = 0; iLayer < info.iLayerNum; iLayer++) {
                SLayerBSInfo layerInfo = info.sLayerInfo[iLayer];

                int iLayerSize = 0;
                int iNalIdx = layerInfo.iNalCount - 1;
                do {
                    iLayerSize += layerInfo.pNalLengthInByte[iNalIdx];
                    --iNalIdx;
                } while (iNalIdx >= 0);

                instance->writeOut(layerInfo.pBsBuf, iLayerSize);
            }
        }
    }
    WelsDestroySVCEncoder(inst);
}
