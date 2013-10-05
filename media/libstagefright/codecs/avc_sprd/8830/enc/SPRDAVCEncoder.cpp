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

//#define LOG_NDEBUG 0
#define LOG_TAG "SPRDAVCEncoder"
#include <utils/Log.h>

#include "avc_enc_api.h"

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

#include "SPRDAVCEncoder.h"

namespace android {

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

typedef struct LevelConversion {
    OMX_U32 omxLevel;
    AVCLevel avcLevel;
} LevelConcersion;

static LevelConversion ConversionTable[] = {
    { OMX_VIDEO_AVCLevel1,  AVC_LEVEL1_B },
    { OMX_VIDEO_AVCLevel1b, AVC_LEVEL1   },
    { OMX_VIDEO_AVCLevel11, AVC_LEVEL1_1 },
    { OMX_VIDEO_AVCLevel12, AVC_LEVEL1_2 },
    { OMX_VIDEO_AVCLevel13, AVC_LEVEL1_3 },
    { OMX_VIDEO_AVCLevel2,  AVC_LEVEL2 },
#if 1
    // encoding speed is very poor if video
    // resolution is higher than CIF
    { OMX_VIDEO_AVCLevel21, AVC_LEVEL2_1 },
    { OMX_VIDEO_AVCLevel22, AVC_LEVEL2_2 },
    { OMX_VIDEO_AVCLevel3,  AVC_LEVEL3   },
    { OMX_VIDEO_AVCLevel31, AVC_LEVEL3_1 },
    { OMX_VIDEO_AVCLevel32, AVC_LEVEL3_2 },
    { OMX_VIDEO_AVCLevel4,  AVC_LEVEL4   },
    { OMX_VIDEO_AVCLevel41, AVC_LEVEL4_1 },
    { OMX_VIDEO_AVCLevel42, AVC_LEVEL4_2 },
    { OMX_VIDEO_AVCLevel5,  AVC_LEVEL5   },
    { OMX_VIDEO_AVCLevel51, AVC_LEVEL5_1 },
#endif
};

static status_t ConvertOmxAvcLevelToAvcSpecLevel(
        OMX_U32 omxLevel, AVCLevel *avcLevel) {
    for (size_t i = 0, n = sizeof(ConversionTable)/sizeof(ConversionTable[0]);
        i < n; ++i) {
        if (omxLevel == ConversionTable[i].omxLevel) {
            *avcLevel = ConversionTable[i].avcLevel;
            return OK;
        }
    }

    ALOGE("ConvertOmxAvcLevelToAvcSpecLevel: %d level not supported",
            (int32_t)omxLevel);

    return BAD_VALUE;
}

static status_t ConvertAvcSpecLevelToOmxAvcLevel(
    AVCLevel avcLevel, OMX_U32 *omxLevel) {
    for (size_t i = 0, n = sizeof(ConversionTable)/sizeof(ConversionTable[0]);
        i < n; ++i) {
        if (avcLevel == ConversionTable[i].avcLevel) {
            *omxLevel = ConversionTable[i].omxLevel;
            return OK;
        }
    }

    ALOGE("ConvertAvcSpecLevelToOmxAvcLevel: %d level not supported",
            (int32_t) avcLevel);

    return BAD_VALUE;
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

SPRDAVCEncoder::SPRDAVCEncoder(
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
      mAVCEncProfile(AVC_BASELINE),
      mAVCEncLevel(AVC_LEVEL2),
      mNumInputFrames(-1),
      mPrevTimestampUs(-1),
      mStarted(false),
      mSawInputEOS(false),
      mSignalledError(false),
      mStoreMetaData(OMX_FALSE),
      mYUVIn(NULL),
      mBuf_inter(NULL),
      mBuf_extra(NULL),
      mHandle(new tagAVCHandle),
      mEncConfig(new MMEncConfig),
      mEncParams(new tagAVCEncParam),
      mInputFrameData(NULL),
      mSliceGroup(NULL),
        mLibHandle(NULL),
        mH264EncInit(NULL),
        mH264EncSetConf(NULL),
        mH264EncGetConf(NULL),
        mH264EncStrmEncode(NULL),
        mH264EncGenHeader(NULL),
        mH264EncRelease(NULL){
      
    CHECK_EQ(openEncoder("libomx_avcenc_hw_sprd.so"), true);

    ALOGI("%s, %d, name: %s", __FUNCTION__, __LINE__, name);
    initPorts();
    ALOGI("Construct SPRDAVCEncoder");
}

SPRDAVCEncoder::~SPRDAVCEncoder() {
    ALOGV("Destruct SPRDAVCEncoder");
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

OMX_ERRORTYPE SPRDAVCEncoder::initEncParams() {
    CHECK(mHandle != NULL);
    memset(mHandle, 0, sizeof(tagAVCHandle));
    CHECK(mEncConfig != NULL);
    memset(mEncConfig, 0, sizeof(MMEncConfig));

    mHandle->AVCObject = NULL;
    mHandle->userData = this;
//    mHandle->CBAVC_DPBAlloc = DpbAllocWrapper;
//    mHandle->CBAVC_FrameBind = BindFrameWrapper;
//    mHandle->CBAVC_FrameUnbind = UnbindFrameWrapper;
//    mHandle->CBAVC_Malloc = MallocWrapper;
//    mHandle->CBAVC_Free = FreeWrapper;

    CHECK(mEncParams != NULL);
    memset(mEncParams, 0, sizeof(mEncParams));
    mEncParams->rate_control = AVC_ON;
    mEncParams->initQP = 0;
    mEncParams->init_CBP_removal_delay = 1600;

    mEncParams->intramb_refresh = 0;
    mEncParams->auto_scd = AVC_ON;
    mEncParams->out_of_band_param_set = AVC_ON;
    mEncParams->poc_type = 2;
    mEncParams->log2_max_poc_lsb_minus_4 = 12;
    mEncParams->delta_poc_zero_flag = 0;
    mEncParams->offset_poc_non_ref = 0;
    mEncParams->offset_top_bottom = 0;
    mEncParams->num_ref_in_cycle = 0;
    mEncParams->offset_poc_ref = NULL;

    mEncParams->num_ref_frame = 1;
    mEncParams->num_slice_group = 1;
    mEncParams->fmo_type = 0;

    mEncParams->db_filter = AVC_ON;
    mEncParams->disable_db_idc = 0;

    mEncParams->alpha_offset = 0;
    mEncParams->beta_offset = 0;
    mEncParams->constrained_intra_pred = AVC_OFF;

    mEncParams->data_par = AVC_OFF;
    mEncParams->fullsearch = AVC_OFF;
    mEncParams->search_range = 16;
    mEncParams->sub_pel = AVC_OFF;
    mEncParams->submb_pred = AVC_OFF;
    mEncParams->rdopt_mode = AVC_OFF;
    mEncParams->bidir_pred = AVC_OFF;

    mEncParams->use_overrun_buffer = AVC_OFF;

    MMCodecBuffer InterMemBfr;
    MMCodecBuffer ExtraMemBfr;
    MMCodecBuffer StreamMemBfr;
    int phy_addr = 0;
    int size = 0;

    ALOGI("%s, %d", __FUNCTION__, __LINE__);

    unsigned int size_inter = H264ENC_INTERNAL_BUFFER_SIZE;
    mPmem_inter = new MemoryHeapIon("/dev/ion", size_inter, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
	if (mPmem_inter->getHeapID() < 0) {
        ALOGE("Failed to alloc inter pmem (%d)", size_inter);
        return OMX_ErrorInsufficientResources;
    }
    mPmem_inter->get_phy_addr_from_ion(&phy_addr, &size);
    mBuf_inter = (unsigned char*)mPmem_inter->base();
    unsigned char*  pBuf_inter_phy = (unsigned char*)phy_addr;		
    if (mBuf_inter == NULL) {
        ALOGE("Failed to alloc inter mem");
        return OMX_ErrorInsufficientResources;
    }
    
    unsigned int size_extra = mVideoWidth * mVideoHeight * 3/2 * 2;
    mPmem_extra = new MemoryHeapIon("/dev/ion", size_extra, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
    if (mPmem_extra->getHeapID() < 0) {
        ALOGE("Failed to alloc extra pmem (%d)", size_extra);
        return OMX_ErrorInsufficientResources;
    }
    mPmem_extra->get_phy_addr_from_ion(&phy_addr, &size);
    mBuf_extra = (unsigned char*)mPmem_extra->base();
    unsigned char* pBuf_extra_phy = (unsigned char*)phy_addr;
    if (mBuf_extra == NULL) {
        ALOGE("Failed to alloc extra pmem");
        return OMX_ErrorInsufficientResources;
    }
    unsigned int size_stream = ONEFRAME_BITSTREAM_BFR_SIZE;
	mPmem_stream = new MemoryHeapIon("/dev/ion", size_stream, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
	if (mPmem_stream->getHeapID() < 0) {
        ALOGE("Failed to alloc stream pmem (%d)", size_stream);
        return OMX_ErrorInsufficientResources;
    }
    mPmem_stream->get_phy_addr_from_ion(&phy_addr, &size);
    mBuf_stream = (unsigned char*)mPmem_stream->base();
    unsigned char* pBuf_stream_phy = (unsigned char*)phy_addr;
    if (mBuf_stream == NULL)
    {
	return OMX_ErrorInsufficientResources;
    }

    InterMemBfr.common_buffer_ptr = mBuf_inter;
    InterMemBfr.common_buffer_ptr_phy = pBuf_inter_phy;
    InterMemBfr.size = size_inter;

    ExtraMemBfr.common_buffer_ptr = mBuf_extra;
    ExtraMemBfr.common_buffer_ptr_phy = pBuf_extra_phy;
    ExtraMemBfr.size	= size_extra;
	
    StreamMemBfr.common_buffer_ptr = mBuf_stream;
    StreamMemBfr.common_buffer_ptr_phy = pBuf_stream_phy;
    StreamMemBfr.size	= size_stream;

    mEncInfo.is_h263 = 0;
    mEncInfo.frame_width = mVideoWidth;
    mEncInfo.frame_height = mVideoHeight;
    mEncInfo.uv_interleaved = 1;
    mEncInfo.time_scale = 1000;

    if ((*mH264EncInit)(&InterMemBfr, &ExtraMemBfr,&StreamMemBfr, &mEncInfo)) {
        ALOGE("Failed to init mp4enc");
        return OMX_ErrorUndefined;
    }
    
    if ((*mH264EncGetConf)(mEncConfig)) {
        ALOGE("Failed to get default encoding parameters");
        return OMX_ErrorUndefined;
    }
    
    mEncConfig->h263En = 0;
    mEncConfig->RateCtrlEnable = 1;
    mEncConfig->targetBitRate = mVideoBitRate;
    mEncConfig->FrameRate = mVideoFrameRate;
    mEncConfig->QP_IVOP = 28;
    mEncConfig->QP_PVOP = 28;
    mEncConfig->vbv_buf_size = mVideoBitRate/2;
    mEncConfig->profileAndLevel = 1;
    
    if ((*mH264EncSetConf)(mEncConfig)) {
        ALOGE("Failed to set default encoding parameters");
        return OMX_ErrorUndefined;
    }

    if (mVideoColorFormat == OMX_COLOR_FormatYUV420Planar) {
        // Color conversion is needed.
        CHECK(mInputFrameData == NULL);
        mInputFrameData =
            (uint8_t *) malloc((mVideoWidth * mVideoHeight * 3 ) >> 1);
        CHECK(mInputFrameData != NULL);
    }

    // SPRD's AVC encoder requires the video dimension of multiple
    if (mVideoWidth % 16 != 0 || mVideoHeight % 16 != 0) {
        ALOGE("Video frame size %dx%d must be a multiple of 16",
            mVideoWidth, mVideoHeight);
        return OMX_ErrorBadParameter;
    }

    mEncParams->width = mVideoWidth;
    mEncParams->height = mVideoHeight;
    mEncParams->bitrate = mVideoBitRate;
    mEncParams->frame_rate = 1000 * mVideoFrameRate;  // In frames/ms!
    mEncParams->CPB_size = (uint32_t) (mVideoBitRate >> 1);

#if 0
    int32_t nMacroBlocks = ((((mVideoWidth + 15) >> 4) << 4) *
            (((mVideoHeight + 15) >> 4) << 4)) >> 8;
    CHECK(mSliceGroup == NULL);
    mSliceGroup = (uint32_t *) malloc(sizeof(uint32_t) * nMacroBlocks);
    CHECK(mSliceGroup != NULL);
    for (int ii = 0, idx = 0; ii < nMacroBlocks; ++ii) {
        mSliceGroup[ii] = idx++;
        if (idx >= mEncParams->num_slice_group) {
            idx = 0;
        }
    }
    mEncParams->slice_group = mSliceGroup;
#endif

    // Set IDR frame refresh interval
    if (mIDRFrameRefreshIntervalInSec < 0) {
        mEncParams->idr_period = -1;
    } else if (mIDRFrameRefreshIntervalInSec == 0) {
        mEncParams->idr_period = 1;  // All I frames
    } else {
        mEncParams->idr_period =
            (mIDRFrameRefreshIntervalInSec * mVideoFrameRate);
    }

    // Set profile and level
    mEncParams->profile = mAVCEncProfile;
    mEncParams->level = mAVCEncLevel;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SPRDAVCEncoder::initEncoder() {
    CHECK(!mStarted);

    OMX_ERRORTYPE errType = OMX_ErrorNone;
    if (OMX_ErrorNone != (errType = initEncParams())) {
        ALOGE("Failed to initialized encoder params");
        mSignalledError = true;
        notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
        return errType;
    }

#if 0
    AVCEnc_Status err;
    err = PVAVCEncInitialize(mHandle, mEncParams, NULL, NULL);
    if (err != AVCENC_SUCCESS) {
        ALOGE("Failed to initialize the encoder: %d", err);
        mSignalledError = true;
        notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
        return OMX_ErrorUndefined;
    }
#endif


    mNumInputFrames = -2;  // 1st two buffers contain SPS and PPS
    mSpsPpsHeaderReceived = false;
    mReadyForNextFrame = true;
    mIsIDRFrame = false;
    mStarted = true;

    return OMX_ErrorNone;
}

OMX_ERRORTYPE SPRDAVCEncoder::releaseEncoder() {
    if (!mStarted) {
        return OMX_ErrorNone;
    }

    (*mH264EncRelease)();

    if (mBuf_inter != NULL) {
        mPmem_inter.clear();
        mBuf_inter = NULL;
    }
    if (mBuf_extra != NULL) {
        mPmem_extra.clear();
        mBuf_extra = NULL;
    }
	if (mBuf_stream != NULL) {
        mPmem_stream.clear();
        mBuf_stream = NULL;
    }
    if (mYUVIn != NULL) {
        mYUVInPmemHeap.clear();
        mYUVIn = NULL;
    }
	
//   PVAVCCleanUpEncoder(mHandle);
//    releaseOutputBuffers();

    delete mInputFrameData;
    mInputFrameData = NULL;

//    delete mSliceGroup;
//    mSliceGroup = NULL;

    delete mEncParams;
    mEncParams = NULL;

    delete mEncConfig;
    mEncConfig = NULL;

    delete mHandle;
    mHandle = NULL;

    mStarted = false;

    return OMX_ErrorNone;
}

void SPRDAVCEncoder::initPorts() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    const size_t kInputBufferSize = (mVideoWidth * mVideoHeight * 3) >> 1;

    // 31584 is PV's magic number.  Not sure why.
    const size_t kOutputBufferSize =
            (kInputBufferSize > 31584) ? kInputBufferSize: 31584;

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

    def.format.video.cMIMEType = const_cast<char *>("video/avc");
    def.format.video.eCompressionFormat = OMX_VIDEO_CodingAVC;
    def.format.video.eColorFormat = OMX_COLOR_FormatUnused;
    def.format.video.xFramerate = (0 << 16);  // Q16 format
    def.format.video.nBitrate = mVideoBitRate;
    def.format.video.nFrameWidth = mVideoWidth;
    def.format.video.nFrameHeight = mVideoHeight;
    def.format.video.nStride = mVideoWidth;
    def.format.video.nSliceHeight = mVideoHeight;

    addPort(def);
}

OMX_ERRORTYPE SPRDAVCEncoder::internalGetParameter(
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
                formatParams->eCompressionFormat = OMX_VIDEO_CodingAVC;
                formatParams->eColorFormat = OMX_COLOR_FormatUnused;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoAvc:
        {
            OMX_VIDEO_PARAM_AVCTYPE *avcParams =
                (OMX_VIDEO_PARAM_AVCTYPE *)params;

            if (avcParams->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            avcParams->eProfile = OMX_VIDEO_AVCProfileBaseline;
            OMX_U32 omxLevel = AVC_LEVEL2;
            if (OMX_ErrorNone !=
                ConvertAvcSpecLevelToOmxAvcLevel(mAVCEncLevel, &omxLevel)) {
                return OMX_ErrorUndefined;
            }

            avcParams->eLevel = (OMX_VIDEO_AVCLEVELTYPE) omxLevel;
            avcParams->nRefFrames = 1;
            avcParams->nBFrames = 0;
            avcParams->bUseHadamard = OMX_TRUE;
            avcParams->nAllowedPictureTypes =
                    (OMX_VIDEO_PictureTypeI | OMX_VIDEO_PictureTypeP);
            avcParams->nRefIdx10ActiveMinus1 = 0;
            avcParams->nRefIdx11ActiveMinus1 = 0;
            avcParams->bWeightedPPrediction = OMX_FALSE;
            avcParams->bEntropyCodingCABAC = OMX_FALSE;
            avcParams->bconstIpred = OMX_FALSE;
            avcParams->bDirect8x8Inference = OMX_FALSE;
            avcParams->bDirectSpatialTemporal = OMX_FALSE;
            avcParams->nCabacInitIdc = 0;
            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoProfileLevelQuerySupported:
        {
            OMX_VIDEO_PARAM_PROFILELEVELTYPE *profileLevel =
                (OMX_VIDEO_PARAM_PROFILELEVELTYPE *)params;

            if (profileLevel->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            const size_t size =
                    sizeof(ConversionTable) / sizeof(ConversionTable[0]);

            if (profileLevel->nProfileIndex >= size) {
                return OMX_ErrorNoMore;
            }

            profileLevel->eProfile = OMX_VIDEO_AVCProfileBaseline;
            profileLevel->eLevel = ConversionTable[profileLevel->nProfileIndex].omxLevel;

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

OMX_ERRORTYPE SPRDAVCEncoder::internalSetParameter(
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
                if (def->format.video.eCompressionFormat != OMX_VIDEO_CodingAVC ||
                    (def->format.video.eColorFormat != OMX_COLOR_FormatUnused)) {
                    return OMX_ErrorUndefined;
                }
            }

            if(def->nPortIndex == 1) {
                int32_t bufferSize = def->format.video.nFrameWidth*def->format.video.nFrameHeight*3/16;
                if(bufferSize > def->nBufferSize) {
                    def->nBufferSize = bufferSize;
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
                        "video_encoder.avc",
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
                if (formatParams->eCompressionFormat != OMX_VIDEO_CodingAVC ||
                    formatParams->eColorFormat != OMX_COLOR_FormatUnused) {
                    return OMX_ErrorUndefined;
                }
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoAvc:
        {
            OMX_VIDEO_PARAM_AVCTYPE *avcType =
                (OMX_VIDEO_PARAM_AVCTYPE *)params;

            if (avcType->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            // PV's AVC encoder only supports baseline profile
            if (avcType->eProfile != OMX_VIDEO_AVCProfileBaseline ||
                avcType->nRefFrames != 1 ||
                avcType->nBFrames != 0 ||
                avcType->bUseHadamard != OMX_TRUE ||
                (avcType->nAllowedPictureTypes & OMX_VIDEO_PictureTypeB) != 0 ||
                avcType->nRefIdx10ActiveMinus1 != 0 ||
                avcType->nRefIdx11ActiveMinus1 != 0 ||
                avcType->bWeightedPPrediction != OMX_FALSE ||
                avcType->bEntropyCodingCABAC != OMX_FALSE ||
                avcType->bconstIpred != OMX_FALSE ||
                avcType->bDirect8x8Inference != OMX_FALSE ||
                avcType->bDirectSpatialTemporal != OMX_FALSE ||
                avcType->nCabacInitIdc != 0) {
                return OMX_ErrorUndefined;
            }

            if (OK != ConvertOmxAvcLevelToAvcSpecLevel(avcType->eLevel, &mAVCEncLevel)) {
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


OMX_ERRORTYPE SPRDAVCEncoder::getExtensionIndex(
            const char *name, OMX_INDEXTYPE *index)
{
    if(strcmp(name, "OMX.google.android.index.storeMetaDataInBuffers") == 0) {
            *index = (OMX_INDEXTYPE) OMX_IndexParamStoreMetaDataBuffer;
            return OMX_ErrorNone;
    }

    return SprdSimpleOMXComponent::getExtensionIndex(name, index);
}

void dump_bs( uint8* pBuffer,int32 aInBufSize)
{
	FILE *fp = fopen("/data/video.264","ab");
	fwrite(pBuffer,1,aInBufSize,fp);
	fclose(fp);
}

void SPRDAVCEncoder::onQueueFilled(OMX_U32 portIndex) {
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
        uint32_t dataLength = outHeader->nAllocLen;

        // Save the input buffer info so that it can be
        // passed to an output buffer
        InputBufferInfo info;
        info.mTimeUs = inHeader->nTimeStamp;
        info.mFlags = inHeader->nFlags;
        mInputBufferInfoVec.push(info);

        if (inHeader->nFlags & OMX_BUFFERFLAG_EOS) {
            mSawInputEOS = true;
        }

        if (inHeader->nFilledLen > 0) {
            const void *inData = inHeader->pBuffer + inHeader->nOffset;
            uint8_t *inputData = (uint8_t *) inData;
            if (mVideoColorFormat != OMX_COLOR_FormatYUV420SemiPlanar) {
                ConvertYUV420PlanarToYUV420SemiPlanar(inputData, mInputFrameData, mVideoWidth, mVideoHeight);
                inputData = mInputFrameData;
            }
            CHECK(inputData != NULL);

            // Combine SPS and PPS and place them in the very first output buffer
            // SPS and PPS are separated by start code 0x00000001
            // Assume that we have exactly one SPS and exactly one PPS.
            if (!mSpsPpsHeaderReceived && mNumInputFrames <= 0) {
                MMEncOut sps_header, pps_header;
                int ret;
                
                memset(&sps_header, 0, sizeof(MMEncOut));
                memset(&pps_header, 0, sizeof(MMEncOut));

                 ++mNumInputFrames;
                 ret = (*mH264EncGenHeader)(&sps_header, 1);
                 outHeader->nFilledLen = sps_header.strmSize;
                 ALOGE("%s, %d, sps_header.strmSize: %d", __FUNCTION__, __LINE__, sps_header.strmSize);

                 memcpy(outPtr, sps_header.pOutBuf, sps_header.strmSize);
                 outPtr+= sps_header.strmSize;

                 ++mNumInputFrames;
                 ret = (*mH264EncGenHeader)(&pps_header, 0);
                 ALOGE("%s, %d, pps_header.strmSize: %d", __FUNCTION__, __LINE__, pps_header.strmSize);

                 outHeader->nFilledLen += pps_header.strmSize;
                 memcpy(outPtr, pps_header.pOutBuf, pps_header.strmSize);

                 mSpsPpsHeaderReceived = true;
                    CHECK_EQ(0, mNumInputFrames);  // 1st video frame is 0
                    outHeader->nFlags = OMX_BUFFERFLAG_CODECCONFIG;
                    outQueue.erase(outQueue.begin());
                    outInfo->mOwnedByUs = false;
                    notifyFillBufferDone(outHeader);
                    return;
            }

    ALOGE("%s, %d", __FUNCTION__, __LINE__);

            MMEncIn vid_in;
            MMEncOut vid_out;
            memset(&vid_in, 0, sizeof(MMEncIn));
            memset(&vid_out, 0, sizeof(MMEncOut));
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

                    memcpy(py, vaddr, mVideoWidth*mVideoHeight*3/2);

                    if (mapper.unlock(buf)) {
                        return;
                    }
                } else {
                    ALOGE("Error MetadataBufferType %d", type);
                    return;
                }
            } else {
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

                memcpy(py, inputData, mVideoWidth*mVideoHeight*3/2);
            }

            
            vid_in.time_stamp = (inHeader->nTimeStamp + 500) / 1000;  // in ms;
//            vid_in.bs_remain_len = bs_remain_len;
            vid_in.channel_quality = 1;
            vid_in.vopType = (mNumInputFrames % mVideoFrameRate) ? 1 : 0;
            vid_in.p_src_y = py;
            vid_in.p_src_u = py + mVideoWidth * mVideoHeight;
            vid_in.p_src_v = 0;
            vid_in.p_src_y_phy = py_phy;
            vid_in.p_src_u_phy = py_phy + mVideoWidth * mVideoHeight;
            vid_in.p_src_v_phy = 0;

            int64_t start_encode = systemTime();
            int ret = (*mH264EncStrmEncode)(&vid_in, &vid_out);
            int64_t end_encode = systemTime();
            ALOGI("H264EncStrmEncode[%lld] %dms, in {0x%p-0x%p, %dx%d}, out {0x%p-%d}", mNumInputFrames, (unsigned int)((end_encode-start_encode) / 1000000L), py, py_phy, mVideoWidth, mVideoHeight, vid_out.pOutBuf, vid_out.strmSize);
            if ((vid_out.strmSize <= 0) || (ret != 0)) {
                ALOGE("Failed to encode frame %lld", mNumInputFrames);
                mSignalledError = true;
                notify(OMX_EventError, OMX_ErrorUndefined, 0, 0);
            }

ALOGE("%s, %d, out_stream_ptr: %0x", __FUNCTION__, __LINE__, outPtr);
 //       dump_bs (vid_out.pOutBuf, vid_out.strmSize);

            dataLength = vid_out.strmSize;
            memcpy(outPtr, vid_out.pOutBuf, dataLength);

            if (vid_in.vopType == 0) {
                outHeader->nFlags |= OMX_BUFFERFLAG_SYNCFRAME;
            }
#if 0
            bs_remain_len += (vid_out.strmSize << 3);
            bs_remain_len -= mVideoBitRate / mVideoFrameRate;
            if (bs_remain_len > (int)mVideoBitRate) {
                bs_remain_len = mVideoBitRate;
            } else if (bs_remain_len < 0) {
                bs_remain_len = 0;
             }
#endif
            ++mNumInputFrames;
        } else {
            dataLength = 0;
        }

        inQueue.erase(inQueue.begin());
        inInfo->mOwnedByUs = false;
        notifyEmptyBufferDone(inHeader);

        outQueue.erase(outQueue.begin());
        CHECK(!mInputBufferInfoVec.empty());
        InputBufferInfo *inputBufInfo = mInputBufferInfoVec.begin();
        mInputBufferInfoVec.erase(mInputBufferInfoVec.begin());
        outHeader->nTimeStamp = inputBufInfo->mTimeUs;
        outHeader->nFlags |= (inputBufInfo->mFlags | OMX_BUFFERFLAG_ENDOFFRAME);
        outHeader->nFilledLen = dataLength;
        outInfo->mOwnedByUs = false;
        notifyFillBufferDone(outHeader);
    }
}

bool SPRDAVCEncoder::openEncoder(const char* libName)
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

    mH264EncInit = (FT_H264EncInit)dlsym(mLibHandle, "H264EncInit");
    if(mH264EncInit == NULL){
        ALOGE("Can't find H264EncInit in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mH264EncSetConf = (FT_H264EncSetConf)dlsym(mLibHandle, "H264EncSetConf");
    if(mH264EncSetConf == NULL){
        ALOGE("Can't find H264EncSetConf in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mH264EncGetConf = (FT_H264EncGetConf)dlsym(mLibHandle, "H264EncGetConf");
    if(mH264EncGetConf == NULL){
        ALOGE("Can't find H264EncGetConf in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mH264EncStrmEncode = (FT_H264EncStrmEncode)dlsym(mLibHandle, "H264EncStrmEncode");
    if(mH264EncStrmEncode == NULL){
        ALOGE("Can't find H264EncStrmEncode in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mH264EncGenHeader = (FT_H264EncGenHeader)dlsym(mLibHandle, "H264EncGenHeader");
    if(mH264EncGenHeader == NULL){
        ALOGE("Can't find H264EncGenHeader in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mH264EncRelease = (FT_H264EncRelease)dlsym(mLibHandle, "H264EncRelease");
    if(mH264EncRelease == NULL){
        ALOGE("Can't find H264EncRelease in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }

    return true;
}

}  // namespace android

android::SprdOMXComponent *createSprdOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    return new android::SPRDAVCEncoder(name, callbacks, appData, component);
}
