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

int epInitialize(EPInstance *instance, int width, int height) {
    int rv = WelsCreateDecoder(&instance->pSvcDecoder);
    assert(0 == rv);
    assert(instance->pSvcDecoder != NULL);

    SDecodingParam *param = &instance->sDecParam;
    memset(param, 0, sizeof(SDecodingParam));
    instance->yuvImageSize = width * height * 3 / 2;
    instance->wh = width * height;
    instance->wh4 = instance->wh / 4;
    instance->wh2 = instance->wh / 2;

    const ISVCDecoderVtbl *inst = (*instance->pSvcDecoder);

    rv = inst->Initialize(instance->pSvcDecoder, param);
    assert(0 == rv);
    return rv;
}

_Noreturn void epRun(EPInstance *instance) {

    const ISVCDecoderVtbl *inst = (*instance->pSvcDecoder);
    ISVCDecoder *pSvcDecoder = instance->pSvcDecoder;
    SBufferInfo *pDstInfo = &instance->sDstBufInfo;

    int rv;
    int bodySize = 0;
    byte *outBuf = malloc(instance->yuvImageSize * 2);
    byte *uvBuf = malloc(instance->wh2);
    while (1) {
        VideoFrame *image = instance->getNextFrame();
        if (image->start != VideoFrameMarker) {
            continue;
        }
        byte *data = image->body;
        memcpy((byte *) &bodySize, &image->bodySize, VideoFrameBodySize);

        rv = inst->DecodeFrameNoDelay(pSvcDecoder, data, bodySize, outBuf, pDstInfo);

        if (rv == cmResultSuccess && pDstInfo->iBufferStatus == 1) {
            //output bitstream handling
            instance->writeOutYUVPart(instance->wh, pDstInfo->pDst[0]);

            //instance->writeOutYUVPart(instance->wh4, pDstInfo->pDst[1]);
            //instance->writeOutYUVPart(instance->wh4, pDstInfo->pDst[2]);
            //convert planar plane UUUUVVVV to semiplanar VUVUVU
            int offset2 = 0;
            byte *u = pDstInfo->pDst[1];
            byte *v = pDstInfo->pDst[2];
            for (int offset = 0; offset < instance->wh4; offset++) {
                uvBuf[offset2++] = v[offset];
                uvBuf[offset2++] = u[offset];
            }
            instance->writeOutYUVPart(instance->wh2, uvBuf);
            instance->flushOut();
        }
    }
}

/*
int main()
{
  printf("starting");

  int width = 640;
  int height = 480;
  int total_num = 200;
  int bitRate = 1500000;
  int maxFrameRate = 20;
  int videoFormat = videoFormatI420;
  int frameSize = width * height * 3 / 2;
  unsigned char buf[frameSize];

  ISVCEncoder *encoder_ = NULL;
  int rv = WelsCreateSVCEncoder(&encoder_);
  assert(0 == rv);
  assert(encoder_ != NULL);

  FILE *fptr = fopen("./file.h264", "wb");

  SEncParamBase param;
  memset(&param, 0, sizeof(SEncParamBase));
  param.iUsageType = CAMERA_VIDEO_REAL_TIME;
  param.fMaxFrameRate = maxFrameRate;
  param.iPicWidth = width;
  param.iPicHeight = height;
  param.iTargetBitrate = bitRate;

  const ISVCEncoderVtbl *inst = (*encoder_);
  inst->Initialize(encoder_, &param);

  inst->SetOption(encoder_, ENCODER_OPTION_DATAFORMAT, &videoFormat);

  SSourcePicture pic;
  memset(&pic, 0, sizeof(SSourcePicture));
  pic.iPicWidth = width;
  pic.iPicHeight = height;
  pic.iColorFormat = videoFormat;
  pic.iStride[0] = pic.iPicWidth;
  pic.iStride[1] = pic.iStride[2] = pic.iPicWidth >> 1;
  pic.pData[0] = (unsigned char *)&buf[0];
  pic.pData[1] = (unsigned char *)&buf[width * height];
  pic.pData[2] = (unsigned char *)&buf[(width * height) + (width * height >> 2)];

  SFrameBSInfo info;
  memset(&info, 0, sizeof(SFrameBSInfo));
  int msStep = 1000 / maxFrameRate;
  for (int num = 0; num < total_num; num++)
  {
    for (int i = 0; i < frameSize; i++)
    {
      buf[i] = (char)num;
    }
    pic.uiTimeStamp = num * msStep;
    //prepare input data
    rv = inst->EncodeFrame(encoder_, &pic, &info);
    assert(rv == cmResultSuccess);
    if (info.eFrameType != videoFrameTypeSkip)
    {
      //output bitstream handling

      printf("num %d, layers num %d\n", num, info.iLayerNum);
      for (int iLayer = 0; iLayer < info.iLayerNum; iLayer++)
      {
        SLayerBSInfo layerInfo = info.sLayerInfo[iLayer];

        int iLayerSize = 0;
        int iNalIdx = layerInfo.iNalCount - 1;
        do
        {
          iLayerSize += layerInfo.pNalLengthInByte[iNalIdx];
          --iNalIdx;
        } while (iNalIdx >= 0);

        unsigned char *outBuf = layerInfo.pBsBuf;
        fwrite((char *)outBuf, iLayerSize, 1, fptr);
      }
    }
  }
  fclose(fptr);
}
*/