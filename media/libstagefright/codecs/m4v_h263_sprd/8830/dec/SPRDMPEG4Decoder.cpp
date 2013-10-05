/*
 * Copyright (C) 2011 The Android Open Source Project
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
#define LOG_TAG "SPRDMPEG4Decoder"
#include <utils/Log.h>

#include "SPRDMPEG4Decoder.h"

#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/IOMX.h>
#include <media/hardware/HardwareAPI.h>
#include <ui/GraphicBufferMapper.h>

#include "gralloc_priv.h"
#include "m4v_h263_dec_api.h"
#include <dlfcn.h>
#include "ion_sprd.h"

namespace android {

typedef enum
{
	H263_MODE = 0,MPEG4_MODE,
	FLV_MODE,
	UNKNOWN_MODE	
}MP4DecodingMode;


#define SW_EXTENTION_SIZE 16

static const CodecProfileLevel kM4VProfileLevels[] = {
    { OMX_VIDEO_MPEG4ProfileSimple, OMX_VIDEO_MPEG4Level0 },
    { OMX_VIDEO_MPEG4ProfileSimple, OMX_VIDEO_MPEG4Level0b },
    { OMX_VIDEO_MPEG4ProfileSimple, OMX_VIDEO_MPEG4Level1 },
    { OMX_VIDEO_MPEG4ProfileSimple, OMX_VIDEO_MPEG4Level2 },
    { OMX_VIDEO_MPEG4ProfileSimple, OMX_VIDEO_MPEG4Level3 },
    
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level0 },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level0b },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level1 },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level2 },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level3 },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level4 },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level4a },
    { OMX_VIDEO_MPEG4ProfileAdvancedSimple, OMX_VIDEO_MPEG4Level5 },
};

static const CodecProfileLevel kH263ProfileLevels[] = {
    { OMX_VIDEO_H263ProfileBaseline, OMX_VIDEO_H263Level10 },
    { OMX_VIDEO_H263ProfileBaseline, OMX_VIDEO_H263Level20 },
    { OMX_VIDEO_H263ProfileBaseline, OMX_VIDEO_H263Level30 },
    { OMX_VIDEO_H263ProfileBaseline, OMX_VIDEO_H263Level45 },
    { OMX_VIDEO_H263ProfileISWV2,    OMX_VIDEO_H263Level10 },
    { OMX_VIDEO_H263ProfileISWV2,    OMX_VIDEO_H263Level20 },
    { OMX_VIDEO_H263ProfileISWV2,    OMX_VIDEO_H263Level30 },
    { OMX_VIDEO_H263ProfileISWV2,    OMX_VIDEO_H263Level45 },
};

template<class T>
static void InitOMXParams(T *params) {
    params->nSize = sizeof(T);
    params->nVersion.s.nVersionMajor = 1;
    params->nVersion.s.nVersionMinor = 0;
    params->nVersion.s.nRevision = 0;
    params->nVersion.s.nStep = 0;
}

void dump_bs( uint8 * pBuffer,uint32 aInBufSize)
{
	FILE *fp = fopen("/data/video_es.m4v","ab");
	fwrite(pBuffer,1,aInBufSize,fp);
	fclose(fp);
}

void dump_yuv( uint8 * pBuffer,uint32 aInBufSize)
{
	FILE *fp = fopen("/data/video_omx.yuv","ab");
	fwrite(pBuffer,1,aInBufSize,fp);
	fclose(fp);
}

SPRDMPEG4Decoder::SPRDMPEG4Decoder(
        const char *name,
        const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData,
        OMX_COMPONENTTYPE **component)
    : SprdSimpleOMXComponent(name, callbacks, appData, component),
      mMode(MODE_MPEG4),
      mHandle(new tagMP4Handle),
      mInputBufferCount(0),
      mWidth(352),
      mHeight(288),
      mCropLeft(0),
      mCropTop(0),
      mCropRight(mWidth - 1),
      mCropBottom(mHeight - 1),
      mSignalledError(false),
      mInitialized(false),
      mFramesConfigured(false),
      mNumSamplesOutput(0),
      mOutputPortSettingsChange(NONE),
      mCodecExtraBufferMalloced(false),
      iDecExtVAddr(NULL),
      iCMDbufferVAddr(NULL),
      mLibHandle(NULL),
      mDecoderSwFlag(false),
      mChangeToSwDec(false),
      mMP4DecSetCurRecPic(NULL),
//      mMP4DecMemCacheInit(NULL),
      mMP4DecInit(NULL),
      mMP4DecVolHeader(NULL),
      mMP4DecMemInit(NULL),
      mMP4DecDecode(NULL),
      mMP4DecRelease(NULL),
      mMp4GetVideoDimensions(NULL),
      mMp4GetBufferDimensions(NULL){
    if (!strcmp(name, "OMX.sprd.h263.decoder")) {
        mMode = MODE_H263;
    } else {
        CHECK(!strcmp(name, "OMX.sprd.mpeg4.decoder"));
    }

    iUseAndroidNativeBuffer[OMX_DirInput] = OMX_FALSE;
    iUseAndroidNativeBuffer[OMX_DirOutput] = OMX_FALSE;

    CHECK_EQ(openDecoder("libomx_m4vh263dec_hw_sprd.so"), true);
    
    initPorts();
    CHECK_EQ(initDecoder(), (status_t)OK);
}

SPRDMPEG4Decoder::~SPRDMPEG4Decoder() {
    if (mInitialized) {
        (*mMP4DecRelease)(mHandle);
    }

    delete mHandle;
    mHandle = NULL;

    if (pbuf_inter != NULL)
    {
        pmem_inter.clear();
        pbuf_inter = NULL;
    }

    if (pmem_stream != NULL)
    {
        pmem_stream.clear();
        pbuf_stream = NULL;
    }

     if (mCodecExtraBufferMalloced)
    {
        free(mCodecExtraBuffer);
        mCodecExtraBuffer = NULL;
    }

    if (!mDecoderSwFlag)
    {
        iDecExtPmemHeap.clear();
        if(iDecExtVAddr)
        {
            iDecExtVAddr = NULL;
        }
                
        iCMDbufferPmemHeap.clear();
        if(iCMDbufferVAddr)
        {
            iCMDbufferVAddr = NULL;
        }
    }

    if(mLibHandle)
    {
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }
}

void SPRDMPEG4Decoder::initPorts() {
    OMX_PARAM_PORTDEFINITIONTYPE def;
    InitOMXParams(&def);

    def.nPortIndex = 0;
    def.eDir = OMX_DirInput;
    def.nBufferCountMin = 1;
    def.nBufferCountActual = kNumInputBuffers;
    def.nBufferSize = 8192;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 1;

    def.format.video.cMIMEType =
        (mMode == MODE_MPEG4)
            ? const_cast<char *>(MEDIA_MIMETYPE_VIDEO_MPEG4)
            : const_cast<char *>(MEDIA_MIMETYPE_VIDEO_H263);

    def.format.video.pNativeRender = NULL;
    def.format.video.nFrameWidth = mWidth;
    def.format.video.nFrameHeight = mHeight;
    def.format.video.nStride = def.format.video.nFrameWidth;
    def.format.video.nSliceHeight = def.format.video.nFrameHeight;
    def.format.video.nBitrate = 0;
    def.format.video.xFramerate = 0;
    def.format.video.bFlagErrorConcealment = OMX_FALSE;

    def.format.video.eCompressionFormat =
        mMode == MODE_MPEG4 ? OMX_VIDEO_CodingMPEG4 : OMX_VIDEO_CodingH263;

    def.format.video.eColorFormat = OMX_COLOR_FormatUnused;
    def.format.video.pNativeWindow = NULL;

    addPort(def);

    def.nPortIndex = 1;
    def.eDir = OMX_DirOutput;
    def.nBufferCountMin = 2;
    def.nBufferCountActual = kNumOutputBuffers;
    def.bEnabled = OMX_TRUE;
    def.bPopulated = OMX_FALSE;
    def.eDomain = OMX_PortDomainVideo;
    def.bBuffersContiguous = OMX_FALSE;
    def.nBufferAlignment = 2;

    def.format.video.cMIMEType = const_cast<char *>(MEDIA_MIMETYPE_VIDEO_RAW);
    def.format.video.pNativeRender = NULL;
    def.format.video.nFrameWidth = mWidth;
    def.format.video.nFrameHeight = mHeight;
    def.format.video.nStride = def.format.video.nFrameWidth;
    def.format.video.nSliceHeight = def.format.video.nFrameHeight;
    def.format.video.nBitrate = 0;
    def.format.video.xFramerate = 0;
    def.format.video.bFlagErrorConcealment = OMX_FALSE;
    def.format.video.eCompressionFormat = OMX_VIDEO_CodingUnused;
    def.format.video.eColorFormat = OMX_COLOR_FormatYUV420Planar;
    def.format.video.pNativeWindow = NULL;

    def.nBufferSize =
        (def.format.video.nFrameWidth * def.format.video.nFrameHeight * 3) / 2;

    addPort(def);
}

status_t SPRDMPEG4Decoder::initDecoder() {
    memset(mHandle, 0, sizeof(tagMP4Handle));

    mHandle->userdata = (void *)this;
    mHandle->VSP_extMemCb = extMemoryAllocWrapper;
    mHandle->VSP_bindCb = BindFrameWrapper;
    mHandle->VSP_unbindCb = UnbindFrameWrapper;

    int phy_addr = 0;
    int size = 0, size_stream, size_inter;

    size_stream = ONEFRAME_BITSTREAM_BFR_SIZE;
    pmem_stream = new MemoryHeapIon(SPRD_ION_DEV, size_stream, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
    if (pmem_stream->getHeapID() < 0)
    {
        ALOGE("Failed to alloc bitstream pmem buffer\n");
    }
    pmem_stream->get_phy_addr_from_ion(&phy_addr, &size);
    pbuf_stream = (unsigned char*)pmem_stream->base();
    pbuf_stream_phy = (unsigned char*)phy_addr;
    if (pbuf_stream == NULL)
    {
        ALOGE("Failed to alloc bitstream pmem buffer\n");
    }

     ALOGE("%s, %d", __FUNCTION__, __LINE__);

    size_inter = MP4DEC_INTERNAL_BUFFER_SIZE;
    pmem_inter = new MemoryHeapIon(SPRD_ION_DEV, size_inter, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);
    pmem_inter->get_phy_addr_from_ion(&phy_addr, &size);
    pbuf_inter = (unsigned char*)pmem_inter->base();
    pbuf_inter_phy = (unsigned char*)phy_addr;

    MMCodecBuffer InterMemBfr;
        	
    InterMemBfr.common_buffer_ptr = pbuf_inter;
    InterMemBfr.common_buffer_ptr_phy = pbuf_inter_phy;
    InterMemBfr.size = size_inter;

    MMDecRet success = (*mMP4DecInit)( mHandle, &InterMemBfr);

//    Mp4DecRegMemAllocCB (mHandle, (void *)this, extMemoryAllocWrapper);
    
    return OK;
}

OMX_ERRORTYPE SPRDMPEG4Decoder::internalGetParameter(
        OMX_INDEXTYPE index, OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamVideoPortFormat:
        {
            OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex != 0) {
                return OMX_ErrorNoMore;
            }

            if (formatParams->nPortIndex == 0) {
                formatParams->eCompressionFormat =
                    (mMode == MODE_MPEG4)
                        ? OMX_VIDEO_CodingMPEG4 : OMX_VIDEO_CodingH263;

                formatParams->eColorFormat = OMX_COLOR_FormatUnused;
                formatParams->xFramerate = 0;
            } else {
                CHECK_EQ(formatParams->nPortIndex, 1u);

                PortInfo *pOutPort = editPortInfo(OMX_DirOutput);
                ALOGI("internalGetParameter, OMX_IndexParamVideoPortFormat, eColorFormat: 0x%x",pOutPort->mDef.format.video.eColorFormat);
                formatParams->eCompressionFormat = OMX_VIDEO_CodingUnused;
                formatParams->eColorFormat = pOutPort->mDef.format.video.eColorFormat;//OMX_COLOR_FormatYUV420Planar;
                formatParams->xFramerate = 0;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoProfileLevelQuerySupported:
        {
            OMX_VIDEO_PARAM_PROFILELEVELTYPE *profileLevel =
                    (OMX_VIDEO_PARAM_PROFILELEVELTYPE *) params;

            if (profileLevel->nPortIndex != 0) {  // Input port only
                ALOGE("Invalid port index: %ld", profileLevel->nPortIndex);
                return OMX_ErrorUnsupportedIndex;
            }

            size_t index = profileLevel->nProfileIndex;
            if (mMode == MODE_H263) {
                size_t nProfileLevels =
                    sizeof(kH263ProfileLevels) / sizeof(kH263ProfileLevels[0]);
                if (index >= nProfileLevels) {
                    return OMX_ErrorNoMore;
                }

                profileLevel->eProfile = kH263ProfileLevels[index].mProfile;
                profileLevel->eLevel = kH263ProfileLevels[index].mLevel;
            } else {
                size_t nProfileLevels =
                    sizeof(kM4VProfileLevels) / sizeof(kM4VProfileLevels[0]);
                if (index >= nProfileLevels) {
                    return OMX_ErrorNoMore;
                }

                profileLevel->eProfile = kM4VProfileLevels[index].mProfile;
                profileLevel->eLevel = kM4VProfileLevels[index].mLevel;
            }
            return OMX_ErrorNone;
        }

        case OMX_IndexParamEnableAndroidBuffers:
        {
            EnableAndroidNativeBuffersParams *peanbp = (EnableAndroidNativeBuffersParams *)params;			
            peanbp->enable = iUseAndroidNativeBuffer[OMX_DirOutput];
            ALOGI("internalGetParameter, OMX_IndexParamEnableAndroidBuffers %d",peanbp->enable);
            return OMX_ErrorNone;
        }

        case OMX_IndexParamGetAndroidNativeBuffer:
        {
            GetAndroidNativeBufferUsageParams *pganbp;

            pganbp = (GetAndroidNativeBufferUsageParams *)params;
            pganbp->nUsage = GRALLOC_USAGE_PRIVATE_0|GRALLOC_USAGE_SW_READ_OFTEN|GRALLOC_USAGE_SW_WRITE_OFTEN;
            ALOGI("internalGetParameter, OMX_IndexParamGetAndroidNativeBuffer %x",pganbp->nUsage);
            return OMX_ErrorNone;
        }

        default:
            return SprdSimpleOMXComponent::internalGetParameter(index, params);
    }
}

OMX_ERRORTYPE SPRDMPEG4Decoder::internalSetParameter(
        OMX_INDEXTYPE index, const OMX_PTR params) {
    switch (index) {
        case OMX_IndexParamStandardComponentRole:
        {
            const OMX_PARAM_COMPONENTROLETYPE *roleParams =
                (const OMX_PARAM_COMPONENTROLETYPE *)params;

            if (mMode == MODE_MPEG4) {
                if (strncmp((const char *)roleParams->cRole,
                            "video_decoder.mpeg4",
                            OMX_MAX_STRINGNAME_SIZE - 1)) {
                    return OMX_ErrorUndefined;
                }
            } else {
                if (strncmp((const char *)roleParams->cRole,
                            "video_decoder.h263",
                            OMX_MAX_STRINGNAME_SIZE - 1)) {
                    return OMX_ErrorUndefined;
                }
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamVideoPortFormat:
        {
            OMX_VIDEO_PARAM_PORTFORMATTYPE *formatParams =
                (OMX_VIDEO_PARAM_PORTFORMATTYPE *)params;

            if (formatParams->nPortIndex > 1) {
                return OMX_ErrorUndefined;
            }

            if (formatParams->nIndex != 0) {
                return OMX_ErrorNoMore;
            }

            return OMX_ErrorNone;
        }

        case OMX_IndexParamPortDefinition:
        {
            OMX_PARAM_PORTDEFINITIONTYPE *defParams =
                (OMX_PARAM_PORTDEFINITIONTYPE *)params;

            if (defParams->nPortIndex > 1
                    || defParams->nSize
                            != sizeof(OMX_PARAM_PORTDEFINITIONTYPE)) {
                return OMX_ErrorUndefined;
            }

            PortInfo *port = editPortInfo(defParams->nPortIndex);

            if (defParams->nBufferSize != port->mDef.nBufferSize) {
                CHECK_GE(defParams->nBufferSize, port->mDef.nBufferSize);
                port->mDef.nBufferSize = defParams->nBufferSize;
            }

            if (defParams->nBufferCountActual
                    != port->mDef.nBufferCountActual) {
                CHECK_GE(defParams->nBufferCountActual,
                         port->mDef.nBufferCountMin);

                port->mDef.nBufferCountActual = defParams->nBufferCountActual;
            }

            memcpy(&port->mDef.format.video, &defParams->format.video, sizeof(OMX_VIDEO_PORTDEFINITIONTYPE));

            if(defParams->nPortIndex == 1) {
                port->mDef.format.video.nStride = port->mDef.format.video.nFrameWidth;
                port->mDef.format.video.nSliceHeight = port->mDef.format.video.nFrameHeight;
                mWidth = port->mDef.format.video.nFrameWidth;
                mHeight = port->mDef.format.video.nFrameHeight;
                mCropRight = mWidth - 1;
                mCropBottom = mHeight -1;
                port->mDef.nBufferSize =(((mWidth + 15) & -16)* ((mHeight + 15) & -16) * 3) / 2;
            }

            return OMX_ErrorNone;
        }
		
		case OMX_IndexParamEnableAndroidBuffers:
        {
			EnableAndroidNativeBuffersParams *peanbp = (EnableAndroidNativeBuffersParams *)params;
			PortInfo *pOutPort = editPortInfo(OMX_DirOutput);
			if (peanbp->enable == OMX_FALSE) {
        			ALOGI("internalSetParameter, disable AndroidNativeBuffer");
        			iUseAndroidNativeBuffer[OMX_DirOutput] = OMX_FALSE;
//#ifdef YUV_THREE_PLANE
//    					pOutPort->mDef.format.video.eColorFormat = OMX_COLOR_FormatYUV420Planar;
//#else
    					pOutPort->mDef.format.video.eColorFormat = (OMX_COLOR_FORMATTYPE)0x7FA30C00;	
//#endif

    			} else {
        			ALOGI("internalSetParameter, enable AndroidNativeBuffer");
        			iUseAndroidNativeBuffer[OMX_DirOutput] = OMX_TRUE;
//#ifdef YUV_THREE_PLANE
//    					pOutPort->mDef.format.video.eColorFormat = (OMX_COLOR_FORMATTYPE)HAL_PIXEL_FORMAT_YV12;
//#else
    					pOutPort->mDef.format.video.eColorFormat = (OMX_COLOR_FORMATTYPE)HAL_PIXEL_FORMAT_YCbCr_420_SP;
//#endif
     			}
     			return OMX_ErrorNone;
	    }

        default:
            return SprdSimpleOMXComponent::internalSetParameter(index, params);
    }
}

OMX_ERRORTYPE SPRDMPEG4Decoder::getConfig(
        OMX_INDEXTYPE index, OMX_PTR params) {
    switch (index) {
        case OMX_IndexConfigCommonOutputCrop:
        {
            OMX_CONFIG_RECTTYPE *rectParams = (OMX_CONFIG_RECTTYPE *)params;

            if (rectParams->nPortIndex != 1) {
                return OMX_ErrorUndefined;
            }

            rectParams->nLeft = mCropLeft;
            rectParams->nTop = mCropTop;
            rectParams->nWidth = mCropRight - mCropLeft + 1;
            rectParams->nHeight = mCropBottom - mCropTop + 1;

            return OMX_ErrorNone;
        }

        default:
            return OMX_ErrorUnsupportedIndex;
    }
}

void SPRDMPEG4Decoder::onQueueFilled(OMX_U32 portIndex) {
    if (mSignalledError || mOutputPortSettingsChange != NONE) {
        return;
    }

    if(mChangeToSwDec){
    
        mChangeToSwDec = false;
        
        if(!openDecoder("libomx_m4vh263dec_sw_sprd.so")){
            ALOGE("onQueueFilled, open  libomx_m4vh263dec_sw_sprd.so failed.");
            notify(OMX_EventError, OMX_ErrorDynamicResourcesUnavailable, 0, NULL);
            mSignalledError = true;
            return;
        }
    }

    List<BufferInfo *> &inQueue = getPortQueue(0);
    List<BufferInfo *> &outQueue = getPortQueue(1);

    //while (!inQueue.empty() && outQueue.size() == kNumOutputBuffers) {
    while (!inQueue.empty() && outQueue.size() != 0) {
        BufferInfo *inInfo = *inQueue.begin();
        OMX_BUFFERHEADERTYPE *inHeader = inInfo->mHeader;

        PortInfo *port = editPortInfo(1);
#if 0
        OMX_BUFFERHEADERTYPE *outHeader =
            port->mBuffers.editItemAt(mNumSamplesOutput & 1).mHeader;
#else
        List<BufferInfo *>::iterator itBuffer = outQueue.begin();
//        OMX_BUFFERHEADERTYPE *outHeader = (*itBuffer)->mHeader;
//        ALOGI("mBuffer=0x%x, outHeader=0x%x",*itBuffer, outHeader);
#endif
        OMX_BUFFERHEADERTYPE *outHeader = NULL;
        BufferCtrlStruct *pBufCtrl = NULL;
        uint32 count = 0;
        do
        {
            if(count >= outQueue.size()){
                ALOGI("onQueueFilled, get outQueue buffer fail, return, count=%d, queue_size=%d",count, outQueue.size());
                return;
            }
            
            outHeader = (*itBuffer)->mHeader;
            pBufCtrl= (BufferCtrlStruct*)(outHeader->pOutputPortPrivate);
            if(pBufCtrl == NULL){
                ALOGE("onQueueFilled, pBufCtrl == NULL, fail");
                notify(OMX_EventError, OMX_ErrorUndefined, 0, NULL);
                mSignalledError = true;
                return;
            }

            itBuffer++;
            count++;
        }
        while(pBufCtrl->iRefCount > 0);

        ALOGI("%s, %d, mBuffer=0x%x, outHeader=0x%x, iRefCount=%d", __FUNCTION__, __LINE__, *itBuffer, outHeader, pBufCtrl->iRefCount);
        ALOGI("%s, %d, inHeader: 0x%x, len: %d, time: %lld, EOS: %d", __FUNCTION__, __LINE__,inHeader, inHeader->nFilledLen,inHeader->nTimeStamp,inHeader->nFlags & OMX_BUFFERFLAG_EOS);
        if (inHeader->nFlags & OMX_BUFFERFLAG_EOS) {
            inQueue.erase(inQueue.begin());
            inInfo->mOwnedByUs = false;
            notifyEmptyBufferDone(inHeader);

            ++mInputBufferCount;

            outHeader->nFilledLen = 0;
            outHeader->nFlags = OMX_BUFFERFLAG_EOS;

            List<BufferInfo *>::iterator it = outQueue.begin();
            while ((*it)->mHeader != outHeader) {
                ++it;
            }

            BufferInfo *outInfo = *it;
            outInfo->mOwnedByUs = false;
            outQueue.erase(it);
            outInfo = NULL;

            BufferCtrlStruct* pBufCtrl= (BufferCtrlStruct*)(outHeader->pOutputPortPrivate);
            pBufCtrl->iRefCount++;

            notifyFillBufferDone(outHeader);
            outHeader = NULL;
            return;
        }

        uint8_t *bitstream = inHeader->pBuffer + inHeader->nOffset;

//        ALOGI("%s, %d, %0x, %0x, %0x, %0x, %0x, %0x, %d", __FUNCTION__, __LINE__, bitstream[0],bitstream[1],bitstream[2],bitstream[3],bitstream[4],bitstream[5],inHeader->nFilledLen);
        //ALOGI("%s, %d, inHeader: 0x%x, len: %d", __FUNCTION__, __LINE__,inHeader, inHeader->nFilledLen);
        if (!mInitialized) {
            uint8_t *vol_data[1];
            int32_t vol_size = 0;

            vol_data[0] = NULL;

//            ALOGI("%s, %d, inHeader->nFlags: %d", __FUNCTION__, __LINE__, inHeader->nFlags);

            if (inHeader->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
                vol_data[0] = bitstream;
                vol_size = inHeader->nFilledLen;
            }

            MP4DecodingMode mode =
                (mMode == MODE_MPEG4) ? MPEG4_MODE : H263_MODE;


#if 0
            Bool success = PVInitVideoDecoder(
                    mHandle, vol_data, &vol_size, 1, mWidth, mHeight, mode);
#else

            MMDecVideoFormat video_format;

            video_format.i_extra = vol_size;

            if( video_format.i_extra>0)
            {
                memcpy(pbuf_stream, vol_data[0],vol_size);
        	video_format.p_extra = pbuf_stream;
                video_format.p_extra_phy = pbuf_stream_phy;
            }else{
        	video_format.p_extra = NULL;
                video_format.p_extra_phy = NULL;
            }
//            ALOGI("%s, %d, video_format.i_extra: %d", __FUNCTION__, __LINE__, video_format.i_extra);

            if(mode == H263_MODE)
            {
            	video_format.video_std = ITU_H263;
            }else if(mode == MPEG4_MODE)
            {
                video_format.video_std = MPEG4;
            }else
            {
            	video_format.video_std = FLV_V1;
            }

            video_format.frame_width = 0;
            video_format.frame_height = 0;
            video_format.uv_interleaved = 1;// todo jgdu

            MMDecRet ret = (*mMP4DecVolHeader)(mHandle, &video_format);

            ALOGI("%s, %d, ret: %d, width: %d, height: %d", __FUNCTION__, __LINE__, ret, video_format.frame_width, video_format.frame_height);
#endif        

            if (ret != MMDEC_OK) {
                ALOGW("PVInitVideoDecoder failed. Unsupported content?");

                notify(OMX_EventError, OMX_ErrorUndefined, 0, NULL);
                mSignalledError = true;
                return;
            }
#if 0
            int width = ((video_format.frame_width  + 15)>>4)<<4;
            int height = ((video_format.frame_height  + 15)>>4)<<4;

            if (!((width <= 720 && height <= 576) || (width <= 576 && height <= 720))){
                mDecoderSwFlag = true;
                mChangeToSwDec = true;
            }
#endif
            if (inHeader->nFlags & OMX_BUFFERFLAG_CODECCONFIG) {
                inInfo->mOwnedByUs = false;
                inQueue.erase(inQueue.begin());
                inInfo = NULL;
                notifyEmptyBufferDone(inHeader);
                inHeader = NULL;
            }

            mInitialized = true;
            
            if (mode == MPEG4_MODE && portSettingsChanged()) {
                return;
            }

            continue;
        }
#if 0
        if (!mFramesConfigured) {
            PortInfo *port = editPortInfo(1);
            OMX_BUFFERHEADERTYPE *outHeader = port->mBuffers.editItemAt(1).mHeader;

//            PVSetReferenceYUV(mHandle, outHeader->pBuffer);

            mFramesConfigured = true;
        }
#endif
        uint32_t useExtTimestamp = (inHeader->nOffset == 0);

        // decoder deals in ms, OMX in us.
        uint32_t timestamp =
            useExtTimestamp ? (inHeader->nTimeStamp + 500) / 1000 : 0xFFFFFFFF;

        outHeader->nTimeStamp = timestamp * 1000;

        int32_t bufferSize = inHeader->nFilledLen;

        // The PV decoder is lying to us, sometimes it'll claim to only have
        // consumed a subset of the buffer when it clearly consumed all of it.
        // ignore whatever it says...
        int32_t tmp = bufferSize;

        MMDecInput dec_in;
        MMDecOutput dec_out;
        
        int picPhyAddr = NULL;
        
        if(!mDecoderSwFlag){
            OMX_BUFFERHEADERTYPE *header = (OMX_BUFFERHEADERTYPE *)outHeader;

            picPhyAddr = 0;

            native_handle_t *pNativeHandle = (native_handle_t *)header->pBuffer;
            struct private_handle_t *private_h = (struct private_handle_t *)pNativeHandle;
            picPhyAddr = (uint32)(private_h->phyaddr);
        }

        GraphicBufferMapper &mapper = GraphicBufferMapper::get();
        if(iUseAndroidNativeBuffer[OMX_DirOutput]){
            OMX_PARAM_PORTDEFINITIONTYPE *def = &editPortInfo(OMX_DirOutput)->mDef;
    	    int width = def->format.video.nStride;
    	    int height = def->format.video.nSliceHeight;
    	    Rect bounds(width, height);
    	    void *vaddr;
            int usage;

            usage = GRALLOC_USAGE_SW_READ_OFTEN|GRALLOC_USAGE_SW_WRITE_OFTEN;

    	    if(mapper.lock((const native_handle_t*)outHeader->pBuffer, usage, bounds, &vaddr)){
                ALOGI("onQueueFilled, mapper.lock fail %x",outHeader->pBuffer);
                return ;
    	    }
    	    ALOGI("%s, %d, pBuffer: 0x%x, vaddr: 0x%x", __FUNCTION__, __LINE__, outHeader->pBuffer,vaddr);
	    (*mMP4DecSetCurRecPic)(mHandle,(uint8*)(vaddr), (uint8*)picPhyAddr, (void *)(outHeader)); 
        }

        memcpy(pbuf_stream, bitstream, bufferSize);
        dec_in.pStream= (uint8 *) bitstream;
        dec_in.pStream_phy= (uint8 *) pbuf_stream_phy;
        dec_in.dataLen = bufferSize;
        dec_in.beLastFrm = 0;
        dec_in.expected_IVOP = 0;
        dec_in.beDisplayed = 1;
        dec_in.err_pkt_num = 0;
      
        int ret;

        dec_out.VopPredType = -1;
        dec_out.frameEffective = 0;

//        ALOGI("%s, %d, video_format.i_extra: %d", __FUNCTION__, __LINE__, dec_in.dataLen);
//        dump_bs((uint8_t *)dec_in.pStream,  dec_in.dataLen);
                
        MMDecRet decRet =	(*mMP4DecDecode)( mHandle, &dec_in,&dec_out);
        ALOGI("%s, %d, decRet: %d, frameEffective: %d, pOutFrameY: %0x, pBufferHeader: %0x", __FUNCTION__, __LINE__, decRet, dec_out.frameEffective, dec_out.pOutFrameY, dec_out.pBufferHeader);


        if(iUseAndroidNativeBuffer[OMX_DirOutput]){
            if(mapper.unlock((const native_handle_t*)outHeader->pBuffer)){
                ALOGI("onQueueFilled, mapper.unlock fail %x",outHeader->pBuffer);
            }
	}

        if (decRet == MMDEC_OK || decRet == MMDEC_MEMORY_ALLOCED)
        {
            if (portSettingsChanged()) {
                return;
            }else if( decRet == MMDEC_MEMORY_ALLOCED)
            {
                continue;
            }
        }else if (decRet == MMDEC_FRAME_SEEK_IVOP)
        {
            inInfo->mOwnedByUs = false;
            inQueue.erase(inQueue.begin());
            inInfo = NULL;
            notifyEmptyBufferDone(inHeader);
            inHeader = NULL;

            continue;
        }else
        {
            ALOGE("failed to decode video frame.");

            notify(OMX_EventError, OMX_ErrorStreamCorrupt, 0, NULL);
            //mSignalledError = true;
            //return;
        }       

//        ALOGI("%s, %d", __FUNCTION__, __LINE__);

        // decoder deals in ms, OMX in us.
//        outHeader->nTimeStamp = timestamp * 1000;

//        bufferSize= dec_in.dataLen;
        ALOGI("%s, %d, bufferSize: %d, inHeader->nFilledLen: %d", __FUNCTION__, __LINE__, bufferSize, inHeader->nFilledLen);
        CHECK_LE(bufferSize, inHeader->nFilledLen);
        inHeader->nOffset += inHeader->nFilledLen - bufferSize;
        inHeader->nFilledLen -= bufferSize;

//        ALOGI("%s, %d, inHeader->nOffset: %d,inHeader->nFilledLen: %d , in->timestamp: %lld, timestamp: %d, out->timestamp: %lld", 
//            __FUNCTION__, __LINE__, inHeader->nOffset, inHeader->nFilledLen, inHeader->nTimeStamp, timestamp,outHeader->nTimeStamp);

        if (inHeader->nFilledLen == 0) {
            inInfo->mOwnedByUs = false;
            inQueue.erase(inQueue.begin());
            inInfo = NULL;
            notifyEmptyBufferDone(inHeader);
            inHeader = NULL;

//            ALOGI("%s, %d", __FUNCTION__, __LINE__);
        }

        ++mInputBufferCount;
//         ALOGI("%s, %d, mInputBufferCount: %d, dec_out.frameEffective: %d", __FUNCTION__, __LINE__, mInputBufferCount, dec_out.frameEffective);

        if (dec_out.frameEffective){
            outHeader = (OMX_BUFFERHEADERTYPE*)(dec_out.pBufferHeader);
            outHeader->nOffset = 0;
            outHeader->nFilledLen = (mWidth * mHeight * 3) / 2;
            outHeader->nFlags = 0;

            ALOGI("%s, %d, outHeader->nFilledLen: %d", __FUNCTION__, __LINE__, outHeader->nFilledLen);
 //           dump_yuv(outHeader->pBuffer, outHeader->nFilledLen);
        }else{
            //return;
            continue;
        }
        

        List<BufferInfo *>::iterator it = outQueue.begin();
//        ALOGI("%s, %d,mHeader=0x%x, outHeader=0x%x", __FUNCTION__, __LINE__,(*it)->mHeader,outHeader );
        while ((*it)->mHeader != outHeader) {
//        ALOGI("%s, %d, while,mHeader=0x%x, outHeader=0x%x", __FUNCTION__, __LINE__,(*it)->mHeader,outHeader );
            ++it;
        }

        BufferInfo *outInfo = *it;
        outInfo->mOwnedByUs = false;
        outQueue.erase(it);
        outInfo = NULL;

        BufferCtrlStruct* pOutBufCtrl= (BufferCtrlStruct*)(outHeader->pOutputPortPrivate);
        pOutBufCtrl->iRefCount++;

        notifyFillBufferDone(outHeader);
        outHeader = NULL;

        ++mNumSamplesOutput;
//        ALOGI("%s, %d, mNumSamplesOutput: %d", __FUNCTION__, __LINE__, mNumSamplesOutput);
    }
}

bool SPRDMPEG4Decoder::portSettingsChanged() {
    int32_t disp_width, disp_height;
#if 0    
    PVGetVideoDimensions(mHandle, &disp_width, &disp_height);
#else
    (*mMp4GetVideoDimensions)(mHandle, &disp_width, &disp_height);
#endif

    int32_t buf_width, buf_height;
#if 0
    PVGetBufferDimensions(mHandle, &buf_width, &buf_height);
#else
    (*mMp4GetBufferDimensions)(mHandle, &buf_width, &buf_height);
#endif


    ALOGI("%s, %d, disp_width = %d, disp_height = %d, buf_width = %d, buf_height = %d", __FUNCTION__, __LINE__, 
            disp_width, disp_height, buf_width, buf_height);

    CHECK_LE(disp_width, buf_width);
    CHECK_LE(disp_height, buf_height);

    if(mDecoderSwFlag){
        if (mCropRight != disp_width + SW_EXTENTION_SIZE - 1
                || mCropBottom != disp_height + SW_EXTENTION_SIZE - 1) {
            ALOGI("%s, %d, SwDecoder, mCropLeft: %d, mCropTop: %d, mCropRight: %d, mCropBottom: %d", __FUNCTION__, __LINE__, mCropLeft, mCropTop, mCropRight, mCropBottom);

            mCropLeft = SW_EXTENTION_SIZE - 1;
            mCropTop = SW_EXTENTION_SIZE - 1;
            mCropRight = disp_width + SW_EXTENTION_SIZE  - 1;
            mCropBottom = disp_height + SW_EXTENTION_SIZE - 1;

            notify(OMX_EventPortSettingsChanged,
                   1,
                   OMX_IndexConfigCommonOutputCrop,
                   NULL);
        }
    }else{
        if (mCropRight != disp_width - 1
                || mCropBottom != disp_height - 1) {
            ALOGI("%s, %d, mCropLeft: %d, mCropTop: %d, mCropRight: %d, mCropBottom: %d", __FUNCTION__, __LINE__, mCropLeft, mCropTop, mCropRight, mCropBottom);

            mCropLeft = 0;
            mCropTop = 0;
            mCropRight = disp_width - 1;
            mCropBottom = disp_height - 1;

            notify(OMX_EventPortSettingsChanged,
                   1,
                   OMX_IndexConfigCommonOutputCrop,
                   NULL);
        }
    }

    if (buf_width != mWidth || buf_height != mHeight) {
              ALOGI("%s, %d, mWidth: %d, mHeight: %d", __FUNCTION__, __LINE__, mWidth, mHeight);
        mWidth = buf_width;
        mHeight = buf_height;
#if 0        
    if(!mDecoderSwFlag){
        if (!((buf_width <= 1280 && buf_height <= 720) || (buf_width <= 720 && buf_height <= 1280))){
            mDecoderSwFlag = true;
            mChangeToSwDec = true;
        }
    }
#endif    
        updatePortDefinitions();

        if (mMode == MODE_H263) {
        }

        mFramesConfigured = false;

        notify(OMX_EventPortSettingsChanged, 1, 0, NULL);
        mOutputPortSettingsChange = AWAITING_DISABLED;
        return true;
    }

    return false;
}

void SPRDMPEG4Decoder::onPortFlushCompleted(OMX_U32 portIndex) {
    if (portIndex == 0 && mInitialized) {
//        CHECK_EQ((int)PVResetVideoDecoder(mHandle), (int)PV_TRUE);
    }
}

void SPRDMPEG4Decoder::onPortEnableCompleted(OMX_U32 portIndex, bool enabled) {
    if (portIndex != 1) {
        return;
    }

    switch (mOutputPortSettingsChange) {
        case NONE:
            break;

        case AWAITING_DISABLED:
        {
            CHECK(!enabled);
            mOutputPortSettingsChange = AWAITING_ENABLED;
            break;
        }

        default:
        {
            CHECK_EQ((int)mOutputPortSettingsChange, (int)AWAITING_ENABLED);
            CHECK(enabled);
            mOutputPortSettingsChange = NONE;
            break;
        }
    }
}

void SPRDMPEG4Decoder::onPortFlushPrepare(OMX_U32 portIndex) {
    if(portIndex == OMX_DirOutput){
        (*mMP4DecReleaseRefBuffers)(mHandle);
    }
}

void SPRDMPEG4Decoder::updatePortDefinitions() {
    OMX_PARAM_PORTDEFINITIONTYPE *def = &editPortInfo(0)->mDef;
    def->format.video.nFrameWidth = mWidth;
    def->format.video.nFrameHeight = mHeight;
    def->format.video.nStride = def->format.video.nFrameWidth;
    def->format.video.nSliceHeight = def->format.video.nFrameHeight;

    def = &editPortInfo(1)->mDef;
    def->format.video.nFrameWidth = mWidth;
    def->format.video.nFrameHeight = mHeight;
    def->format.video.nStride = def->format.video.nFrameWidth;
    def->format.video.nSliceHeight = def->format.video.nFrameHeight;

    if(mDecoderSwFlag){
        if(iUseAndroidNativeBuffer[OMX_DirOutput] == OMX_TRUE){
            def->format.video.eColorFormat = (OMX_COLOR_FORMATTYPE)HAL_PIXEL_FORMAT_YV12;
        }else{
            def->format.video.eColorFormat = OMX_COLOR_FormatYUV420Planar;
        }
    }

    def->nBufferSize =
        (((def->format.video.nFrameWidth + 15) & -16)
            * ((def->format.video.nFrameHeight + 15) & -16) * 3) / 2;
}

// static
int32_t SPRDMPEG4Decoder::extMemoryAllocWrapper(
        void *aUserData, unsigned int width,unsigned int height) {
    return static_cast<SPRDMPEG4Decoder *>(aUserData)->extMemoryAlloc(width, height);
}

// static
int32_t SPRDMPEG4Decoder::BindFrameWrapper(
        void *aUserData, void *pHeader, int flag) {
    return static_cast<SPRDMPEG4Decoder *>(aUserData)->VSP_bind_cb(pHeader, flag);
}

// static
int32_t SPRDMPEG4Decoder::UnbindFrameWrapper(
        void *aUserData, void *pHeader, int flag) {
    return static_cast<SPRDMPEG4Decoder *>(aUserData)->VSP_unbind_cb(pHeader, flag);
}

int SPRDMPEG4Decoder::extMemoryAlloc(unsigned int width,unsigned int height) {

    int32 Frm_width_align = ((width + 15) & (~15));
    int32 Frm_height_align = ((height + 15) & (~15));

#if 0 //removed it, bug 121132, xiaowei@2013.01.25
    mWidth = Frm_width_align;
    mHeight = Frm_height_align;
#endif

//    ALOGI("%s, %d, Frm_width_align: %d, Frm_height_align: %d", __FUNCTION__, __LINE__, Frm_width_align, Frm_height_align);

    int32 mb_num_x = Frm_width_align/16;
    int32 mb_num_y = Frm_height_align/16;
    int32 mb_num_total = mb_num_x * mb_num_y;
    int32 frm_size = (mb_num_total * 256);
    int32 i;
    MMCodecBuffer extra_mem;
    uint32 extra_mem_size;



//    if (mDecoderSwFlag)
//    {
//        extra_mem_size[HW_NO_CACHABLE] = 0;	
//        extra_mem_size[HW_CACHABLE] = 0;
//    }else
    {
        extra_mem_size = mb_num_total * (32 + 3 * 80) + 1024;
        iDecExtPmemHeap = new MemoryHeapIon(SPRD_ION_DEV, extra_mem_size, MemoryHeapBase::NO_CACHING, ION_HEAP_CARVEOUT_MASK);	
        int fd = iDecExtPmemHeap->getHeapID();
        if(fd>=0)
        {
            int ret,phy_addr, buffer_size;
            ret = iDecExtPmemHeap->get_phy_addr_from_ion(&phy_addr, &buffer_size);
            if(ret) 
            {
                ALOGE ("iDecExtPmemHeap get_phy_addr_from_ion fail %d",ret);
            }
    			
            iDecExtPhyAddr =(OMX_U32)phy_addr;
//            ALOGD ("%s: ext mem pmempool %x,%x,%x,%x\n", __FUNCTION__, iDecExtPmemHeap->getHeapID(),iDecExtPmemHeap->base(),phy_addr,buffer_size);
            iDecExtVAddr = (void *)iDecExtPmemHeap->base();
            extra_mem.common_buffer_ptr =(uint8 *) iDecExtVAddr;
            extra_mem.common_buffer_ptr_phy = (void *)iDecExtPhyAddr;
            extra_mem.size = extra_mem_size;
        }
    }

     (*mMP4DecMemInit)(/* (VideoDecControls *)decCtrl*/ ((SPRDMPEG4Decoder *)this)->mHandle, &extra_mem);

    return 1;
}

int SPRDMPEG4Decoder::VSP_bind_cb(void *pHeader,int flag)
{
    BufferCtrlStruct *pBufCtrl = (BufferCtrlStruct *)(((OMX_BUFFERHEADERTYPE *)pHeader)->pOutputPortPrivate);
    ALOGI("VSP_bind_cb, ref frame: 0x%x, %x; iRefCount=%d",
            ((OMX_BUFFERHEADERTYPE *)pHeader)->pBuffer, pHeader,pBufCtrl->iRefCount);
    pBufCtrl->iRefCount++;
    return 0;
}

int SPRDMPEG4Decoder::VSP_unbind_cb(void *pHeader,int flag)
{
    BufferCtrlStruct *pBufCtrl = (BufferCtrlStruct *)(((OMX_BUFFERHEADERTYPE *)pHeader)->pOutputPortPrivate);

    ALOGI("VSP_unbind_cb, ref frame: 0x%x, %x; iRefCount=%d",
            ((OMX_BUFFERHEADERTYPE *)pHeader)->pBuffer, pHeader,pBufCtrl->iRefCount);

    if (pBufCtrl->iRefCount  > 0)
    {   
        pBufCtrl->iRefCount--;
    }
    
    return 0;
}

OMX_ERRORTYPE SPRDMPEG4Decoder::getExtensionIndex(
        const char *name, OMX_INDEXTYPE *index) {

    ALOGI("getExtensionIndex, name: %s",name);
    if(strcmp(name, SPRD_INDEX_PARAM_ENABLE_ANB) == 0)
    {
    		ALOGI("getExtensionIndex:%s",SPRD_INDEX_PARAM_ENABLE_ANB);
		*index = (OMX_INDEXTYPE) OMX_IndexParamEnableAndroidBuffers;
		return OMX_ErrorNone;
    }else if (strcmp(name, SPRD_INDEX_PARAM_GET_ANB) == 0)
    {
     		ALOGI("getExtensionIndex:%s",SPRD_INDEX_PARAM_GET_ANB);   
		*index = (OMX_INDEXTYPE) OMX_IndexParamGetAndroidNativeBuffer;
		return OMX_ErrorNone;
    }	else if (strcmp(name, SPRD_INDEX_PARAM_USE_ANB) == 0)
    {
     		ALOGI("getExtensionIndex:%s",SPRD_INDEX_PARAM_USE_ANB);     
		*index = OMX_IndexParamUseAndroidNativeBuffer2;
		return OMX_ErrorNone;
    }

    return OMX_ErrorNotImplemented;
}

bool SPRDMPEG4Decoder::openDecoder(const char* libName)
{
    if(mLibHandle){
        dlclose(mLibHandle);
    }
    
    ALOGI("openDecoder, lib: %s",libName);

    mLibHandle = dlopen(libName, RTLD_NOW);
    if(mLibHandle == NULL){
        ALOGE("openDecoder, can't open lib: %s",libName);
        return false;
    }
    
    mMP4DecSetCurRecPic = (FT_MP4DecSetCurRecPic)dlsym(mLibHandle, "MP4DecSetCurRecPic");
    if(mMP4DecSetCurRecPic == NULL){
        ALOGE("Can't find MP4DecSetCurRecPic in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

//    mMP4DecMemCacheInit = (FT_MP4DecMemCacheInit)dlsym(mLibHandle, "MP4DecMemCacheInit");
//    if(mMP4DecMemCacheInit == NULL){
//        LOGE("Can't find MP4DecMemCacheInit in %s",libName);
//        dlclose(mLibHandle);
//        mLibHandle = NULL;
//        return false;
//    }

    mMP4DecInit = (FT_MP4DecInit)dlsym(mLibHandle, "MP4DecInit");
    if(mMP4DecInit == NULL){
        ALOGE("Can't find MP4DecInit in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4DecVolHeader = (FT_MP4DecVolHeader)dlsym(mLibHandle, "MP4DecVolHeader");
    if(mMP4DecVolHeader == NULL){
        ALOGE("Can't find MP4DecVolHeader in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4DecMemInit = (FT_MP4DecMemInit)dlsym(mLibHandle, "MP4DecMemInit");
    if(mMP4DecMemInit == NULL){
        ALOGE("Can't find MP4DecMemInit in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4DecDecode = (FT_MP4DecDecode)dlsym(mLibHandle, "MP4DecDecode");
    if(mMP4DecDecode == NULL){
        ALOGE("Can't find MP4DecDecode in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4DecRelease = (FT_MP4DecRelease)dlsym(mLibHandle, "MP4DecRelease");
    if(mMP4DecRelease == NULL){
        ALOGE("Can't find MP4DecRelease in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
    }

    mMp4GetVideoDimensions = (FT_Mp4GetVideoDimensions)dlsym(mLibHandle, "Mp4GetVideoDimensions");
    if(mMp4GetVideoDimensions == NULL){
        ALOGE("Can't find Mp4GetVideoDimensions in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMp4GetBufferDimensions = (FT_Mp4GetBufferDimensions)dlsym(mLibHandle, "Mp4GetBufferDimensions");
    if(mMp4GetBufferDimensions == NULL){
        ALOGE("Can't find Mp4GetBufferDimensions in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    mMP4DecReleaseRefBuffers = (FT_MP4DecReleaseRefBuffers)dlsym(mLibHandle, "MP4DecReleaseRefBuffers");
    if(mMP4DecReleaseRefBuffers == NULL){
        ALOGE("Can't find MP4DecReleaseRefBuffers in %s",libName);
        dlclose(mLibHandle);
        mLibHandle = NULL;
        return false;
    }

    return true;
}

}  // namespace android

android::SprdOMXComponent *createSprdOMXComponent(
        const char *name, const OMX_CALLBACKTYPE *callbacks,
        OMX_PTR appData, OMX_COMPONENTTYPE **component) {
    return new android::SPRDMPEG4Decoder(name, callbacks, appData, component);
}

