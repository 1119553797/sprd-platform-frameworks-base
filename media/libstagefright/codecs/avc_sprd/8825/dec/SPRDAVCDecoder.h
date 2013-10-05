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

#ifndef SPRD_AVC_DECODER_H_

#define SPRD_AVC_DECODER_H_

#include "SprdSimpleOMXComponent.h"
#include <utils/KeyedVector.h>

#include <binder/MemoryHeapIon.h>

#define SPRD_ION_DEV "/dev/ion"

#include "avc_dec_api.h"
//#include "basetype.h"

struct tagAVCHandle;

namespace android {

struct SPRDAVCDecoder : public SprdSimpleOMXComponent {
    SPRDAVCDecoder(const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component);

protected:
    virtual ~SPRDAVCDecoder();

    virtual OMX_ERRORTYPE internalGetParameter(
            OMX_INDEXTYPE index, OMX_PTR params);

    virtual OMX_ERRORTYPE internalSetParameter(
            OMX_INDEXTYPE index, const OMX_PTR params);

    virtual OMX_ERRORTYPE getConfig(OMX_INDEXTYPE index, OMX_PTR params);

    virtual void onQueueFilled(OMX_U32 portIndex);
    virtual void onPortFlushCompleted(OMX_U32 portIndex);
    virtual void onPortEnableCompleted(OMX_U32 portIndex, bool enabled);
    virtual void onPortFlushPrepare(OMX_U32 portIndex);
    virtual OMX_ERRORTYPE getExtensionIndex(const char *name, OMX_INDEXTYPE *index);

private:
    enum {
        kInputPortIndex   = 0,
        kOutputPortIndex  = 1,
        kNumInputBuffers  = 8,
        kNumOutputBuffers = 10,
    };

    enum EOSStatus {
        INPUT_DATA_AVAILABLE,
        INPUT_EOS_SEEN,
        OUTPUT_FRAMES_FLUSHED,
    };

    tagAVCHandle *mHandle;

    size_t mInputBufferCount;

    uint8_t *mCodecInterBuffer;
    uint32_t mCodecInterBufferSize;
    uint8_t *mCodecExtraBuffer;
    uint32_t mCodecExtraBufferSize;
    bool mCodecExtraBufferMalloced;

    uint32_t mWidth, mHeight, mPictureSize;
    uint32_t mCropLeft, mCropTop;
    uint32_t mCropWidth, mCropHeight;

    bool ipMemBufferWasSet;
    sp<MemoryHeapIon> iDecExtPmemHeap;
    void*  iDecExtVAddr;
    OMX_U32  iDecExtPhyAddr;

    sp<MemoryHeapIon> iCMDbufferPmemHeap;
    void*  iCMDbufferVAddr;
    OMX_U32  iCMDbufferPhyAddr;

    OMX_BOOL iUseAndroidNativeBuffer[2];

    void* mLibHandle;
    bool mDecoderSwFlag;
    bool mChangeToSwDec;
    FT_H264DecGetNALType mH264DecGetNALType;
    FT_H264GetBufferDimensions mH264GetBufferDimensions;
    FT_H264DecGetInfo mH264DecGetInfo;
    FT_H264DecInit mH264DecInit;
    FT_H264DecMemInit mH264DecMemInit;
    FT_H264DecDecode mH264DecDecode;
    FT_H264_DecReleaseDispBfr mH264_DecReleaseDispBfr;
    FT_H264DecRelease mH264DecRelease;
    FT_H264Dec_SetCurRecPic  mH264Dec_SetCurRecPic;
    FT_H264Dec_GetLastDspFrm  mH264Dec_GetLastDspFrm;
    FT_H264Dec_ReleaseRefBuffers  mH264Dec_ReleaseRefBuffers;

#if 0
    uint8_t *mFirstPicture;
    int32_t mFirstPictureId;
#endif

    int32_t mPicId;  // Which output picture is for which input buffer?

    // OMX_BUFFERHEADERTYPE may be overkill, but it is convenient
    // for tracking the following fields: nFlags, nTimeStamp, etc.
    KeyedVector<int32_t, OMX_BUFFERHEADERTYPE *> mPicToHeaderMap;
    bool mHeadersDecoded;

    EOSStatus mEOSStatus;

    enum OutputPortSettingChange {
        NONE,
        AWAITING_DISABLED,
        AWAITING_ENABLED
    };
    OutputPortSettingChange mOutputPortSettingsChange;

    bool mSignalledError;

    void initPorts();
    status_t initDecoder();
    void updatePortDefinitions();
    bool drainAllOutputBuffers();
    void drainOneOutputBuffer(int32_t picId, void* pBufferHeader);
#if 0    
    void saveFirstOutputBuffer(int32_t pidId, uint8_t *data);
#endif
    bool handleCropRectEvent(const CropParams* crop);
    bool handlePortSettingChangeEvent(const H264SwDecInfo *info);

    static int32_t flushCacheWrapper(void* aUserData,int* vaddr,int* paddr,int size); 
    static int32_t ActivateSPSWrapper(void *userData, unsigned int width,unsigned int height, unsigned int numBuffers) ;
    static int32_t BindFrameWrapper(void *aUserData, void *pHeader);
    static int32_t UnbindFrameWrapper(void *aUserData, void *pHeader);

    int activateSPS(unsigned int width,unsigned int height, unsigned int numBuffers);
    int VSP_bind_cb(void *pHeader);
    int VSP_unbind_cb(void *pHeader);
    int flushCache (int* vaddr,int* paddr,int size);
    bool openDecoder(const char* libName);

    DISALLOW_EVIL_CONSTRUCTORS(SPRDAVCDecoder);
};

}  // namespace android

#endif  // SPRD_AVC_DECODER_H_

