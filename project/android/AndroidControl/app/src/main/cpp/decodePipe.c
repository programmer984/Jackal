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
#include "video_frame_receiver.h"
#include "log.h"
#include "search.h"
#define VideFrameHeaderSize 3

int epInitialize(EPInstance *instance, int width, int height) {
    int rv = WelsCreateDecoder(&instance->pSvcDecoder);
    assert(0 == rv);
    assert(instance->pSvcDecoder != NULL);

    SDecodingParam *param = &instance->sDecParam;
    memset(param, 0, sizeof(SDecodingParam));
    instance->yuvImageSize = (width * height) * 3 / 2;
    instance->width = width;
    instance->height = height;
    instance->wh = width * height;
    instance->wh4 = instance->wh / 4;
    instance->wh2 = instance->wh / 2;
    instance->outBuf = malloc(instance->yuvImageSize * 4);
    instance->spsSet = false;

    const ISVCDecoderVtbl *inst = (*instance->pSvcDecoder);

    rv = inst->Initialize(instance->pSvcDecoder, param);
    assert(0 == rv);
    return rv;
}


void putNewFrame(EPInstance *instance, VideoFrame *image) {
    const ISVCDecoderVtbl *inst = (*instance->pSvcDecoder);
    SBufferInfo *pDstInfo = &instance->sDstBufInfo;

    //00 00 00 01 67
    if (!instance->spsSet && ((image->body[4] & 0x0F) == 7)) {
        //detect size of sps buf
        //if size less 30 - highly likely SPS
        if (image->size < 30) {
            int rv = inst->DecodeFrameNoDelay(instance->pSvcDecoder, image->body, image->size,
                                              (unsigned char **) instance->outBuf, pDstInfo);
            if (rv == cmResultSuccess) {
                instance->spsSet = true;
            }
        } else {
            byte packetStart[] = {0, 0, 0, 1};
            int nextBlockPosition = searchSequense(image->body + 10, image->size, &packetStart[0],
                                                   4);
            if (nextBlockPosition > 0 && nextBlockPosition < 50) {
                int rv = inst->DecodeFrameNoDelay(instance->pSvcDecoder, image->body,
                                                  nextBlockPosition + 10,
                                                  (unsigned char **) instance->outBuf, pDstInfo);
                if (rv == cmResultSuccess) {
                    instance->spsSet = true;
                }
            }
        }
    }


    inst->DecodeFrameNoDelay(instance->pSvcDecoder, image->body, image->size,
                             (unsigned char **) instance->outBuf, pDstInfo);


    if (pDstInfo->iBufferStatus == 1) {
        byte frameHeader[VideFrameHeaderSize];
        frameHeader[0] = 0x45;
        frameHeader[1] = 0x45;
        frameHeader[2] = 0x47;
        instance->writeOutYUVPart((byte *) &frameHeader, VideFrameHeaderSize);

        //output bitstream handling
        int widthStride = pDstInfo->UsrData.sSystemBuffer.iStride[0];
        for (int rowIndex = 0; rowIndex < instance->height; rowIndex++) {
            int srcRowOffset = rowIndex * widthStride;
            instance->writeOutYUVPart(pDstInfo->pDst[0] + srcRowOffset, instance->width);
        }


        //instance->writeOutYUVPart(instance->wh4, pDstInfo->pDst[1]);
        //instance->writeOutYUVPart(instance->wh4, pDstInfo->pDst[2]);
        //convert planar plane UUUUVVVV to semiplanar VUVUVU
        widthStride = pDstInfo->UsrData.sSystemBuffer.iStride[1];
        byte *u = pDstInfo->pDst[1];
        byte *v = pDstInfo->pDst[2];
        int vPlusU = instance->width;
        int vOrU = vPlusU/2;
        byte vuRow[vPlusU];
        for (int rowIndex = 0; rowIndex < instance->height/2; rowIndex++) {
            int srcRowOffset = rowIndex * widthStride;
            int outOffset=0;
            for (int i=0; i < vOrU; i++){
                vuRow[outOffset++]= *(v + srcRowOffset + i);
                vuRow[outOffset++]= *(u + srcRowOffset + i);
            }
            instance->writeOutYUVPart(&vuRow, vPlusU);
        }
        instance->flushOut();
    }
}

