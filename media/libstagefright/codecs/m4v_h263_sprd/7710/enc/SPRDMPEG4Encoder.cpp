/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "SPRDMPEG4Encoder"
#include <utils/Log.h>

#include "m4v_h263_enc_api.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <MetadataBufferType.h>
#include <HardwareAPI.h>

#include <ui/GraphicBufferMapper.h>
#include <gui/ISurfaceTexture.h>

#include <linux/ion.h>
#include <binder/MemoryHeapIon.h>

#include <dlfcn.h>

#include "SPRDMPEG4Encoder.h"


namespace android {

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}
void dump_yuv( uint8 * pBuffer,uint32 aInBufSize)
{
	FILE *fp = fopen("/data/encoder_out.bin","ab");
	fwrite(pBuffer,1,aInBufSize,fp);
	fclose(fp);
}
inline static void ConvertYUV420PlanarToYUV420SemiPlanar(
        uint8_t *inyuv, uint8_t* outyuv,
        int32_t width, int32_t height) {

    int32_t outYsize = width * height;
    uint32_t *outy =  (uint32_t *) outyuv;
    uint16_t *incb = (uint16_t *) (inyuv + outYsize);
    uint16_t *incr = (uint16_t *) (inyuv + outYsize + (outYsize >> 2));

    /* Y copying */
    memcpy(outy, inyuv, outYsize);

    /* U & V copying */
    uint32_t *outyuv_4 = (uint32_t *) (outyuv + outYsize);
    for (int32_t i = height >> 1; i > 0; --i) {
        for (int32_t j = width >> 2; j > 0; --j) {
            uint32_t tempU = *incb++;
            uint32_t tempV = *incr++;

            tempU = (tempU & 0xFF) | ((tempU & 0xFF00) << 8);
            tempV = (tempV & 0xFF) | ((tempV & 0xFF00) << 8);
            uint32_t temp = tempV | (tempU << 8);
            
            // Flip U and V
            *outyuv_4++ = temp;
        }
    }
}

SPRDMPEG4Encoder::SPRDMPEG4Encoder(
            const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component)
    : SprdSimpleOMXComponent(name, callbacks, appData, component),
      mVideoWidth(176),
      mVideoHeight(144),
      mVideoFrameRate(30),
      mVideoBitRate(192000),
      mVideoColorFormat(OMX_COLOR_FormatYUV420SemiPlanar),
      mIDRFrameRefreshIntervalInSec(1),
      mNumInputFrames(-1),
      mStarted(false),
      mSawInputEOS(false),
      mSignalledError(false),
      mStoreMetaData(OMX_FALSE),
      mYUVIn(NULL),
      mBuf_inter(NULL),
      mBuf_extra(NULL),
      mHandle(new tagvideoEncControls),
      mEncConfig(new MMEncConfig),
      mLibHandle(NULL),
        mMP4EncInit(NULL),
        mMP4EncSetConf(NULL),
        mMP4EncGetConf(NULL),
        mMP4EncStrmEncode(NULL),
        mMP4EncGenHeader(NULL),
        mMP4EncRelease(NULL){
    ALOGI("%s, %d, name: %s", __FUNCTION__, __LINE__, name);
    
    CHECK_EQ(openEncoder("libomx_m4vh263enc_hw_sprd.so"), true);

   if (!strcmp(name, "OMX.sprd.h263.encoder")) {
        mIsH263 = 1;
    } else {
        mIsH263 = 0;
        CHECK(!strcmp(name, "OMX.sprd.mpeg4.encoder"));
    }

    initPorts();
    ALOGI("Construct SPRDMPEG4Encoder");
}

SPRDMPEG4Encoder::~SPRDMPEG4Encoder() {
    ALOGV("Destruct SPRDMPEG4Encoder");
    releaseEncoder();
    List<BufferInfo *> &outQueue = getPortQueue(1);
    List<BufferInfo *> &inQueue = getPortQueue(0);
    CHECK(outQueue.empty());
    CHECK(inQueue.empty());

    if(mLibHandle)
    {
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }
}

OMX_ERRORTYPE SPRDMPEG4Encoder::initEncParams() {
    CHECK(mHandle != NULL);
    memset(mHandle, 0, sizeof(tagvideoEncControls));

    CHECK(mEncConfig != NULL);
    memset(mEncConfig, 0, sizeof(MMEncConfig));

    MMCodecBuffer InterMemBfr;
    MMCodecBuffer ExtaMemBfr;
    int phy_addr = 0;
    int size = 0;
    
    unsigned int size_inter = mVideoWidth/16*7*4*2+14*4+6*4*3+68   +200 +4;
    mBuf_inter = (unsigned char*)malloc(size_inter * sizeof(unsigned char));
    if (mBuf_inter == NULL) {
        ALOGE("Failed to alloc inter mem");
        return OMX_ErrorInsufficientResources;
    }
    InterMemBfr.common_buffer_ptr = mBuf_inter;
    InterMemBfr.size = size_inter;
    
    unsigned int size_extra = mVideoWidth * mVideoHeight * 3/2 * 2  + 1500*1024 + 300;
    mPmem_extra = new MemoryHeapIon("/dev/ion", size_extra, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
    if (mPmem_extra->getHeapID() < 0) {
        ALOGE("Failed to alloc extra pmem (%d)", size_extra);
        return OMX_ErrorInsufficientResources;
    }
    mPmem_extra->get_phy_addr_from_ion(&phy_addr, &size);
    mBuf_extra = (unsigned char*)mPmem_extra->base();
    unsigned char* pbuf_extra_phy = (unsigned char*)phy_addr;
    if (mBuf_extra == NULL) {
        ALOGE("Failed to alloc extra pmem");
        return OMX_ErrorInsufficientResources;
    }
    ExtaMemBfr.common_buffer_ptr = mBuf_extra;
    ExtaMemBfr.common_buffer_ptr_phy = pbuf_extra_phy;
    ExtaMemBfr.size	= size_extra;

    mEncInfo.is_h263 = mIsH263;
    mEncInfo.frame_width = mVideoWidth;
    mEncInfo.frame_height = mVideoHeight;
    mEncInfo.uv_interleaved = 1;
    mEncInfo.time_scale = 1000;

    if ((*mMP4EncInit)(&InterMemBfr, &ExtaMemBfr,&mEncInfo)) {
        ALOGE("Failed to init mp4enc");
        return OMX_ErrorUndefined;
    }
    
    if ((*mMP4EncGetConf)(mEncConfig)) {
        ALOGE("Failed to get default encoding parameters");
        return OMX_ErrorUndefined;
    }
    
    mEncConfig->h263En = mIsH263;
    mEncConfig->RateCtrlEnable = 0;//1;
    mEncConfig->targetBitRate = mVideoBitRate;
    mEncConfig->FrameRate = mVideoFrameRate;
    mEncConfig->QP_IVOP = 6;//4;
    mEncConfig->QP_PVOP = 6;//4;
    mEncConfig->vbv_buf_size = mVideoBitRate/2;
    mEncConfig->profileAndLevel = 1;
    
    if ((*mMP4EncSetConf)(mEncConfig)) {
        ALOGE("Failed to set default encoding parameters");
        return OMX_ErrorUndefined;
    }
    
    // SPRD's MPEG4 encoder requires the video dimension of multiple
    if (mVideoWidth % 16 != 0 || mVideoHeight % 16 != 0) {
        ALOGE("Video frame size %dx%d must be a multiple of 16",
            mVideoWidth, mVideoHeight);
        return OMX_ErrorBadParameter;
    }

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SPRDMPEG4Encoder::initEncoder() {
    CHECK(!mStarted);

    OMX_ERRORTYPE errType = OMX_ErrorNone;
    if (OMX_ErrorNone != (errType = initEncParams())) {
        ALOGE("Failed to initialized encoder params");
        mSignalledError = true;
        notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
        return errType;
    }

    mNumInputFrames = -1;  // 1st buffer for codec specific data
    mStarted = true;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SPRDMPEG4Encoder::releaseEncoder() {
    if (!mStarted) {
        return OMX_ErrorNone;
    }

    (*mMP4EncRelease)();

    if (mBuf_inter != NULL) {
        free(mBuf_inter);
        mBuf_inter = NULL;
    }
    if (mBuf_extra != NULL) {
        mPmem_extra.clear();
        mBuf_extra = NULL;
    }

    if (mYUVIn != NULL) {
        mYUVInPmemHeap.clear();
        mYUVIn = NULL;
    }

    delete mEncConfig;
    mEncConfig = NULL;

    delete mHandle;
    mHandle = NULL;

    mStarted = false;

    return OMX_ErrorNone;
}

void SPRDMPEG4Encoder::initPorts() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    const size_t kInputBufferSize = (mVideoWidth * mVideoHeight * 3) >> 1;

    // 256 * 1024 is a magic number for PV's encoder, not sure why
    const size_t kOutputBufferSize =
        (kInputBufferSize > 256 * 1024)
            ? kInputBufferSize: 256 * 1024;

    def.nPortIndex = 0;
    def.eDir = OMX_DirInput;
    def.nBufferCountMin = kNumBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = kInputBufferSize;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 1;

    def.format.video.cMIMEType = const_cast<char *>("video/raw");

    def.format.video.eCompressionFormat = OMX_VIDEO_CodingUnused;
    def.format.video.eColorFormat = OMX_COLOR_FormatYUV420SemiPlanar;
    def.format.video.xFramerate = (mVideoFrameRate << 16);  // Q16 format
    def.format.video.nBitrate = mVideoBitRate;
    def.format.video.nFrameWidth = mVideoWidth;
    def.format.video.nFrameHeight = mVideoHeight;
    def.format.video.nStride = mVideoWidth;
    def.format.video.nSliceHeight = mVideoHeight;

    addPort(def);

    def.nPortIndex = 1;
    def.eDir = OMX_DirOutput;
    def.nBufferCountMin = kNumBuffers;
    def.nBufferCountActual = def.nBufferCountMin;
    def.nBufferSize = kOutputBufferSize;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 2;

    def.format.video.cMIMEType =
        (mIsH263 == 0) 
            ? const_cast<char *>(MEDIA_MIMETYPE_VIDEO_MPEG4)
            : const_cast<char *>(MEDIA_MIMETYPE_VIDEO_H263);

    def.format.video.eCompressionFormat =
        (mIsH263 == 0) 
            ? OMX_VIDEO_CodingMPEG4
            : OMX_VIDEO_CodingH263;

    def.format.video.eColorFormat = OMX_COLOR_FormatUnused;
    def.format.video.xFramerate = (0 << 16);  // Q16 format
    def.format.video.nBitrate = mVideoBitRate;
    def.format.video.nFrameWidth = mVideoWidth;
    def.format.video.nFrameHeight = mVideoHeight;
    def.format.video.nStride = mVideoWidth;
    def.format.video.nSliceHeight = mVideoHeight;

    addPort(def);
}

OMX_ERRORTYPE SPRDMPEG4Encoder::internalGetParameter(
        OMX_INDEXTYPE index, OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamVideoErrorCorrection:
        {
            return OMX_ErrorNotImplemented;
        }

        case OMX_IndexParamVideoBitrate:
        {
            OMX_VIDEO_PARAM_BITRATETYPE *bitRate =
                (OMX_VIDEO_PARAM_BITRATETYPE *) params;

            if (bitRate->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            bitRate->eControlRate = OMX_Video_ControlRateVariable;
            bitRate->nTargetBitrate = mVideoBitRate;
            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoPortFormat:
        {
            OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex > 1) {
                return OMX_ErrorNoMore;
            }

            if (formatParams->nPortIndex == 0) {
                formatParams->eCompressionFormat = OMX_VIDEO_CodingUnused;
                if (formatParams->nIndex == 0) {
                    formatParams->eColorFormat = OMX_COLOR_FormatYUV420Planar;
                } else {
                    formatParams->eColorFormat = OMX_COLOR_FormatYUV420SemiPlanar;
                }
            } else {
                formatParams->eCompressionFormat =
                    (mIsH263 == 0) 
                        ? OMX_VIDEO_CodingMPEG4
                        : OMX_VIDEO_CodingH263;

                formatParams->eColorFormat = OMX_COLOR_FormatUnused;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoH263:
        {
            OMX_VIDEO_PARAM_H263TYPE *h263type =
                (OMX_VIDEO_PARAM_H263TYPE *)params;

            if (h263type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            h263type->nAllowedPictureTypes =
                (OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP);
            h263type->eProfile = OMX_VIDEO_H263ProfileBaseline;
            h263type->eLevel = OMX_VIDEO_H263Level45;
            h263type->bPLUSPTYPEAllowed = OMX_FALSE;
            h263type->bForceRoundingTypeToZero = OMX_FALSE;
            h263type->nPictureHeaderRepetition = 0;
            h263type->nGOBHeaderInterval = 0;

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoMpeg4:
        {
            OMX_VIDEO_PARAM_MPEG4TYPE *mpeg4type =
                (OMX_VIDEO_PARAM_MPEG4TYPE *)params;

            if (mpeg4type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            mpeg4type->eProfile = OMX_VIDEO_MPEG4ProfileCore;
            mpeg4type->eLevel = OMX_VIDEO_MPEG4Level2;
            mpeg4type->nAllowedPictureTypes =
                (OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP);
            mpeg4type->nBFrames = 0;
            mpeg4type->nIDCVLCThreshold = 0;
            mpeg4type->bACPred = OMX_TRUE;
            mpeg4type->nMaxPacketSize = 256;
            mpeg4type->nTimeIncRes = 1000;
            mpeg4type->nHeaderExtension = 0;
            mpeg4type->bReversibleVLC = OMX_FALSE;

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoProfileLevelQuerySupported:
        {
            OMX_VIDEO_PARAM_PROFILELEVELTYPE *profileLevel =
                (OMX_VIDEO_PARAM_PROFILELEVELTYPE *)params;

            if (profileLevel->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (profileLevel->nProfileIndex > 0) {
                return OMX_ErrorNoMore;
            }

            if (mIsH263 == 1)  {
                profileLevel->eProfile = OMX_VIDEO_H263ProfileBaseline;
                profileLevel->eLevel = OMX_VIDEO_H263Level45;
            } else {
                profileLevel->eProfile = OMX_VIDEO_MPEG4ProfileCore;
                profileLevel->eLevel = OMX_VIDEO_MPEG4Level2;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamStoreMetaDataBuffer:
        {
            StoreMetaDataInBuffersParams *pStoreMetaData = (StoreMetaDataInBuffersParams *)params;
            pStoreMetaData->bStoreMetaData = mStoreMetaData;
            return OMX_ErrorNone;
        }

        default:
            return SprdSimpleOMXComponent::internalGetParameter(index, params);
    }
}

OMX_ERRORTYPE SPRDMPEG4Encoder::internalSetParameter(
        OMX_INDEXTYPE index, const OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamVideoErrorCorrection:
        {
            return OMX_ErrorNotImplemented;
        }

        case OMX_IndexParamVideoBitrate:
        {
            OMX_VIDEO_PARAM_BITRATETYPE *bitRate =
                (OMX_VIDEO_PARAM_BITRATETYPE *) params;

            if (bitRate->nPortIndex != 1 ||
                bitRate->eControlRate != OMX_Video_ControlRateVariable) {
                return OMX_ErrorUndefined;
            }

            mVideoBitRate = bitRate->nTargetBitrate;
            return OMX_ErrorNone;
        }

        case OMX_IndexParamPortDefinition:
        {
            OMX_PARAM_PORTDEFINITIONTYPE *def =
                (OMX_PARAM_PORTDEFINITIONTYPE *)params;
            if (def->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (def->nPortIndex == 0) {
                if (def->format.video.eCompressionFormat != OMX_VIDEO_CodingUnused ||
                    (def->format.video.eColorFormat != OMX_COLOR_FormatYUV420Planar &&
                     def->format.video.eColorFormat != OMX_COLOR_FormatYUV420SemiPlanar)) {
                    return OMX_ErrorUndefined;
                }
            } else {
                if (((mIsH263 == 0)  &&
                        def->format.video.eCompressionFormat != OMX_VIDEO_CodingMPEG4) ||
                    ((mIsH263 == 1)  &&
                        def->format.video.eCompressionFormat != OMX_VIDEO_CodingH263) ||
                    (def->format.video.eColorFormat != OMX_COLOR_FormatUnused)) {
                    return OMX_ErrorUndefined;
                }
            }

            OMX_ERRORTYPE err = SprdSimpleOMXComponent::internalSetParameter(index, params);
            if (OMX_ErrorNone != err) {
                return err;
            }

            if (def->nPortIndex == 0) {
                mVideoWidth = def->format.video.nFrameWidth;
                mVideoHeight = def->format.video.nFrameHeight;
                mVideoFrameRate = def->format.video.xFramerate >> 16;
                mVideoColorFormat = def->format.video.eColorFormat;
            } else {
                mVideoBitRate = def->format.video.nBitrate;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamStandardComponentRole:
        {
            const OMX_PARAM_COMPONENTROLETYPE *roleParams =
                (const OMX_PARAM_COMPONENTROLETYPE *)params;

            if (strncmp((const char *)roleParams->cRole,
                        (mIsH263 == 1) 
                            ? "video_encoder.h263": "video_encoder.mpeg4",
                        OMX_MAX_STRINGNAME_SIZE - 1)) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoPortFormat:
        {
            const OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (const OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex > 1) {
                return OMX_ErrorNoMore;
            }

            if (formatParams->nPortIndex == 0) {
                if (formatParams->eCompressionFormat != OMX_VIDEO_CodingUnused ||
                    ((formatParams->nIndex == 0 &&
                      formatParams->eColorFormat != OMX_COLOR_FormatYUV420Planar) ||
                    (formatParams->nIndex == 1 &&
                     formatParams->eColorFormat != OMX_COLOR_FormatYUV420SemiPlanar))) {
                    return OMX_ErrorUndefined;
                }
                mVideoColorFormat = formatParams->eColorFormat;
            } else {
                if (((mIsH263 == 1)  &&
                        formatParams->eCompressionFormat != OMX_VIDEO_CodingH263) ||
                    ((mIsH263 == 0)  &&
                        formatParams->eCompressionFormat != OMX_VIDEO_CodingMPEG4) ||
                    formatParams->eColorFormat != OMX_COLOR_FormatUnused) {
                    return OMX_ErrorUndefined;
                }
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoH263:
        {
            OMX_VIDEO_PARAM_H263TYPE *h263type =
                (OMX_VIDEO_PARAM_H263TYPE *)params;

            if (h263type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (h263type->eProfile != OMX_VIDEO_H263ProfileBaseline ||
                h263type->eLevel != OMX_VIDEO_H263Level45 ||
                (h263type->nAllowedPictureTypes & OMX_VIDEO_PictureTypeB) ||
                h263type->bPLUSPTYPEAllowed != OMX_FALSE ||
                h263type->bForceRoundingTypeToZero != OMX_FALSE ||
                h263type->nPictureHeaderRepetition != 0 ||
                h263type->nGOBHeaderInterval != 0) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoMpeg4:
        {
            OMX_VIDEO_PARAM_MPEG4TYPE *mpeg4type =
                (OMX_VIDEO_PARAM_MPEG4TYPE *)params;

            if (mpeg4type->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            if (mpeg4type->eProfile != OMX_VIDEO_MPEG4ProfileCore ||
                mpeg4type->eLevel != OMX_VIDEO_MPEG4Level2 ||
                (mpeg4type->nAllowedPictureTypes & OMX_VIDEO_PictureTypeB) ||
                mpeg4type->nBFrames != 0 ||
                mpeg4type->nIDCVLCThreshold != 0 ||
                mpeg4type->bACPred != OMX_TRUE ||
                mpeg4type->nMaxPacketSize != 256 ||
                mpeg4type->nTimeIncRes != 1000 ||
                mpeg4type->nHeaderExtension != 0 ||
                mpeg4type->bReversibleVLC != OMX_FALSE) {
                return OMX_ErrorUndefined;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamStoreMetaDataBuffer:
        {
            StoreMetaDataInBuffersParams *pStoreMetaData = (StoreMetaDataInBuffersParams *)params;
            mStoreMetaData = pStoreMetaData->bStoreMetaData;
            return OMX_ErrorNone;
        }

        default:
            return SprdSimpleOMXComponent::internalSetParameter(index, params);
    }
}


OMX_ERRORTYPE SPRDMPEG4Encoder::getExtensionIndex(
            const char *name, OMX_INDEXTYPE *index)
{
    ALOGI("getExtensionIndex, %s",name);
    if(strcmp(name, "OMX.google.android.index.storeMetaDataInBuffers") == 0) {
            *index = (OMX_INDEXTYPE) OMX_IndexParamStoreMetaDataBuffer;
            return OMX_ErrorNone;
    }

    return SprdSimpleOMXComponent::getExtensionIndex(name, index);
}

void SPRDMPEG4Encoder::onQueueFilled(OMX_U32 portIndex) {
    if (mSignalledError || mSawInputEOS) {
        return;
    }
    if (!mStarted) {
        if (OMX_ErrorNone != initEncoder()) {
            return;
        }
    }
    List<BufferInfo *> &inQueue = getPortQueue(0);
    List<BufferInfo *> &outQueue = getPortQueue(1);

    static int bs_remain_len = mVideoBitRate/2;
    while (!mSawInputEOS && !inQueue.empty() && !outQueue.empty()) {
        BufferInfo *inInfo = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *inHeader = inInfo->mHeader;
        BufferInfo *outInfo = *outQueue.begin();
        OMX_BUFFERHEADERTYPE *outHeader = outInfo->mHeader;

        outHeader->nTimeStamp = 0;
        outHeader->nFlags = 0;
        outHeader->nOffset = 0;
        outHeader->nFilledLen = 0;
        outHeader->nOffset = 0;

        uint8_t *outPtr = (uint8_t *) outHeader->pBuffer;
        int32_t dataLength = outHeader->nAllocLen;

        if (mNumInputFrames < 0) {
            MMEncOut encOut;
            if ((*mMP4EncGenHeader)(&encOut)) {
                ALOGE("Failed to get VOL header");
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
                return;
            }

            dataLength = encOut.strmSize;
            memcpy(outPtr, encOut.pOutBuf, dataLength);

            ALOGV("Output VOL header: %d bytes", dataLength);
            ++mNumInputFrames;
            outHeader->nFlags |= OMX_BUFFERFLAG_CODECCONFIG;
            outHeader->nFilledLen = dataLength;
            outQueue.erase(outQueue.begin());
            outInfo->mOwnedByUs = false;
            notifyFillBufferDone(outHeader);
            return;
        }

        // Save the input buffer info so that it can be
        // passed to an output buffer
        InputBufferInfo info;
        info.mTimeUs = inHeader->nTimeStamp;
        info.mFlags = inHeader->nFlags;
        mInputBufferInfoVec.push(info);

        if (inHeader->nFlags & OMX_BUFFERFLAG_EOS) {
            mSawInputEOS = true;
        }
        ALOGI("inHeader:0x%x, pbuffer:0x%x, nFilledLen:%d, time:%lld,mStoreMetaData:%d,mVideoColorFormat:%d",
            inHeader,inHeader->pBuffer, inHeader->nFilledLen,inHeader->nTimeStamp,mStoreMetaData,mVideoColorFormat);
        ALOGI("outHeader:0x%x, pBuffer:0x%x, size:%d",outHeader,outHeader->pBuffer,outHeader->nAllocLen);
        if (inHeader->nFilledLen > 0) {
            const void *inData = inHeader->pBuffer + inHeader->nOffset;
            uint8_t *inputData = (uint8_t *) inData;

            MMEncIn vid_in;
            MMEncOut vid_out;
            memset(&vid_in, 0, sizeof(vid_in));
            memset(&vid_out, 0, sizeof(vid_out));
            unsigned char* py = NULL;
            unsigned char* py_phy = NULL;


            if (mStoreMetaData) {
                unsigned int type = *(unsigned int *) inputData;
                if (type == kMetadataBufferTypeCameraSource) {
                    py = (uint8*)(*((int *) inputData + 2));
                    py_phy = (uint8*)(*((int *) inputData + 1));
                } else if (type == kMetadataBufferTypeGrallocSource) {
                    if (mYUVIn == NULL) {
                        mYUVInPmemHeap = new MemoryHeapIon("/dev/ion", mVideoWidth * mVideoHeight *3/2, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
                        if (mYUVInPmemHeap->getHeapID() >= 0) {
                                    int ret,phy_addr, buffer_size;
                                    ret = mYUVInPmemHeap->get_phy_addr_from_ion(&phy_addr, &buffer_size);
                                    if(ret) {
                                        ALOGE("Failed to get_phy_addr_from_ion %d", ret);
                                        return;
                                    }
                                    mYUVIn =(uint8 *) mYUVInPmemHeap->base();
                                    py = mYUVIn;
                                    py_phy = (uint8*)phy_addr;
                        } else {
                            ALOGE("Failed to alloc yuv pmem");
                            return;
                        }
                    }
 		
                    GraphicBufferMapper &mapper = GraphicBufferMapper::get();
                    buffer_handle_t buf = *((buffer_handle_t *)(inputData + 4));
                    Rect bounds(mVideoWidth, mVideoHeight);

                    void* vaddr;
                    if (mapper.lock(buf, GRALLOC_USAGE_SW_READ_OFTEN|GRALLOC_USAGE_SW_WRITE_NEVER, bounds, &vaddr)) {
                        return;
                    }
                    if (mVideoColorFormat != OMX_COLOR_FormatYUV420SemiPlanar) {
                        ConvertYUV420PlanarToYUV420SemiPlanar((uint8_t*)vaddr, py, ((mVideoWidth+15)&(~15)), ((mVideoHeight+15)&(~15)));
                    }else{
                        memcpy(py, vaddr, ((mVideoWidth+15)&(~15))*((mVideoHeight+15)&(~15))*3/2);
                    }

                    if (mapper.unlock(buf)) {
                        return;
                    }
                } else {
                    ALOGE("Error MetadataBufferType %d", type);
                    return;
                }
            } else {
                if (mYUVIn == NULL) {
                    mYUVInPmemHeap = new MemoryHeapIon("/dev/ion", ((mVideoWidth+15)&(~15))*((mVideoHeight+15)&(~15))*3/2, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
                    if (mYUVInPmemHeap->getHeapID() < 0) {
                        ALOGE("Failed to alloc yuv pmem");
                        return;
                    }
                }

                int ret,phy_addr, buffer_size;
                ret = mYUVInPmemHeap->get_phy_addr_from_ion(&phy_addr, &buffer_size);
                if(ret) {
                    ALOGE("Failed to get_phy_addr_from_ion %d", ret);
                    return;
                }
                mYUVIn =(uint8 *) mYUVInPmemHeap->base();
                py = mYUVIn;
                py_phy = (uint8*)phy_addr;
                if (mVideoColorFormat != OMX_COLOR_FormatYUV420SemiPlanar) {
                    ConvertYUV420PlanarToYUV420SemiPlanar(inputData, py, ((mVideoWidth+15)&(~15)), ((mVideoHeight+15)&(~15)));
                }else{
                    memcpy(py, inputData, ((mVideoWidth+15)&(~15))*((mVideoHeight+15)&(~15))*3/2);
                }
                //dump_yuv(inputData, mVideoWidth*mVideoHeight*3/2);
            }

            
            vid_in.time_stamp = (inHeader->nTimeStamp + 500) / 1000;  // in ms;
            vid_in.bs_remain_len = bs_remain_len;
            vid_in.channel_quality = 1;
            vid_in.vopType = (mNumInputFrames % mVideoFrameRate) ? 1 : 0;
            vid_in.p_src_y = py;
            vid_in.p_src_u = py + mVideoWidth * mVideoHeight;
            vid_in.p_src_v = 0;
            vid_in.p_src_y_phy = py_phy;
            vid_in.p_src_u_phy = py_phy + mVideoWidth * mVideoHeight;
            vid_in.p_src_v_phy = 0;

            int64_t start_encode = systemTime();
            int ret = (*mMP4EncStrmEncode)(&vid_in, &vid_out);
            int64_t end_encode = systemTime();
            ALOGI("MP4EncStrmEncode[%lld] %dms, in {0x%p-0x%p, %dx%d}, out {0x%p-%d},outPtr{0x%x}", mNumInputFrames, (unsigned int)((end_encode-start_encode) / 1000000L), py, py_phy, mVideoWidth, mVideoHeight, vid_out.pOutBuf, vid_out.strmSize,outPtr);
            if ((vid_out.strmSize < 0) || (ret != 0)) {
                ALOGE("Failed to encode frame %lld, ret=%d", mNumInputFrames,ret);
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
            }

            if(vid_out.strmSize > 0) {
                dataLength = vid_out.strmSize;
                memcpy(outPtr, vid_out.pOutBuf, dataLength);
                //dump_yuv(vid_out.pOutBuf, vid_out.strmSize);
                if (vid_in.vopType == 0) {
                    outHeader->nFlags |= OMX_BUFFERFLAG_SYNCFRAME;
                }
            } else {
                dataLength = 0;
            }

            bs_remain_len += (vid_out.strmSize << 3);
            bs_remain_len -= mVideoBitRate / mVideoFrameRate;
            if (bs_remain_len > (int)mVideoBitRate) {
                bs_remain_len = mVideoBitRate;
            } else if (bs_remain_len < 0) {
                bs_remain_len = 0;
             }

            ++mNumInputFrames;
        } else {
            dataLength = 0;
        }

        inQueue.erase(inQueue.begin());
        inInfo->mOwnedByUs = false;
        notifyEmptyBufferDone(inHeader);

        //if(dataLength > 0) {
        outQueue.erase(outQueue.begin());
        CHECK(!mInputBufferInfoVec.empty());
        InputBufferInfo *inputBufInfo = mInputBufferInfoVec.begin();
        outHeader->nTimeStamp = inputBufInfo->mTimeUs;
        outHeader->nFlags |= (inputBufInfo->mFlags | OMX_BUFFERFLAG_ENDOFFRAME);
        outHeader->nFilledLen = dataLength;
        mInputBufferInfoVec.erase(mInputBufferInfoVec.begin());        
        outInfo->mOwnedByUs = false;
        notifyFillBufferDone(outHeader);
        //}
    }
}

bool SPRDMPEG4Encoder::openEncoder(const char* libName)
{
    if(mLibHandle){
        dlclose(mLibHandle);
    }
    
    ALOGI("openEncoder, lib: %s",libName);

    mLibHandle = dlopen(libName, RTLD_NOW);
    if(mLibHandle == NULL){
        ALOGE("openEncoder, can't open lib: %s",libName);
        return false;
    }

    mMP4EncInit = (FT_MP4EncInit)dlsym(mLibHandle, "MP4EncInit");
    if(mMP4EncInit == NULL){
        ALOGE("Can't find MP4EncInit in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4EncSetConf = (FT_MP4EncSetConf)dlsym(mLibHandle, "MP4EncSetConf");
    if(mMP4EncSetConf == NULL){
        ALOGE("Can't find MP4EncSetConf in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4EncGetConf = (FT_MP4EncGetConf)dlsym(mLibHandle, "MP4EncGetConf");
    if(mMP4EncGetConf == NULL){
        ALOGE("Can't find MP4EncGetConf in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4EncStrmEncode = (FT_MP4EncStrmEncode)dlsym(mLibHandle, "MP4EncStrmEncode");
    if(mMP4EncStrmEncode == NULL){
        ALOGE("Can't find MP4EncStrmEncode in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4EncGenHeader = (FT_MP4EncGenHeader)dlsym(mLibHandle, "MP4EncGenHeader");
    if(mMP4EncGenHeader == NULL){
        ALOGE("Can't find MP4EncGenHeader in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4EncRelease = (FT_MP4EncRelease)dlsym(mLibHandle, "MP4EncRelease");
    if(mMP4EncRelease == NULL){
        ALOGE("Can't find MP4EncRelease in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }

    return true;
}

}  // namespace android

android::SprdOMXComponent *createSprdOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    return new android::SPRDMPEG4Encoder(name, callbacks, appData, component);
}
