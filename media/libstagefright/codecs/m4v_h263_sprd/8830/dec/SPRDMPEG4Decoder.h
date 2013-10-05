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

#ifndef SPRD_MPEG4_DECODER_H_

#define SPRD_MPEG4_DECODER_H_

#include "SprdSimpleOMXComponent.h"
#include "m4v_h263_dec_api.h"

#include <binder/MemoryHeapIon.h>

#define SPRD_ION_DEV "/dev/ion"

struct tagMP4Handle;

namespace android {


struct SPRDMPEG4Decoder : public SprdSimpleOMXComponent {
    SPRDMPEG4Decoder(const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component);

protected:
    virtual ~SPRDMPEG4Decoder();

    virtual OMX_ERRORTYPE internalGetParameter(
            OMX_INDEXTYPE index, OMX_PTR params);

    virtual OMX_ERRORTYPE internalSetParameter(
            OMX_INDEXTYPE index, const OMX_PTR params);

    virtual OMX_ERRORTYPE getConfig(OMX_INDEXTYPE index, OMX_PTR params);

    virtual void onQueueFilled(OMX_U32 portIndex);
    virtual void onPortFlushCompleted(OMX_U32 portIndex);
    virtual void onPortEnableCompleted(OMX_U32 portIndex, bool enabled);
    virtual void onPortFlushPrepare(OMX_U32 portIndex);

    virtual OMX_ERRORTYPE getExtensionIndex(
        const char *name, OMX_INDEXTYPE *index);

private:
    enum {
        kNumInputBuffers  = 8,
        kNumOutputBuffers = 5,
    };

    enum {
        MODE_MPEG4,
        MODE_H263,

    } mMode;

    tagMP4Handle *mHandle;

    size_t mInputBufferCount;

    int32_t mWidth, mHeight;
    int32_t mCropLeft, mCropTop, mCropRight, mCropBottom;

    bool mSignalledError;
    bool mInitialized;
    bool mFramesConfigured;

    int32_t mNumSamplesOutput;

    sp<MemoryHeapIon> pmem_inter;
    unsigned char* pbuf_inter;
    unsigned char* pbuf_inter_phy;

    sp<MemoryHeapIon> pmem_stream;
    unsigned char* pbuf_stream;
    unsigned char* pbuf_stream_phy;

    uint8_t *mCodecExtraBuffer;
    uint32_t mCodecExtraBufferSize;
    bool mCodecExtraBufferMalloced;
    OMX_BOOL iUseAndroidNativeBuffer[2];
	
    sp<MemoryHeapIon> iDecExtPmemHeap;
    void*  iDecExtVAddr;
    OMX_U32  iDecExtPhyAddr;

    sp<MemoryHeapIon> iCMDbufferPmemHeap;
    void*  iCMDbufferVAddr;
    OMX_U32  iCMDbufferPhyAddr;

    void* mLibHandle;
    bool mDecoderSwFlag;
    bool mChangeToSwDec;
    FT_MP4DecSetCurRecPic mMP4DecSetCurRecPic;
    FT_MP4DecMemCacheInit mMP4DecMemCacheInit;
    FT_MP4DecInit mMP4DecInit;
    FT_MP4DecVolHeader mMP4DecVolHeader;
    FT_MP4DecMemInit mMP4DecMemInit;
    FT_MP4DecDecode mMP4DecDecode;
    FT_MP4DecRelease mMP4DecRelease;
    FT_Mp4GetVideoDimensions mMp4GetVideoDimensions;
    FT_Mp4GetBufferDimensions mMp4GetBufferDimensions;        
    FT_MP4DecReleaseRefBuffers mMP4DecReleaseRefBuffers;

    static int32_t extMemoryAllocWrapper(void *userData, unsigned int width,unsigned int height); 
    static int32_t BindFrameWrapper(void *aUserData, void *pHeader, int flag);
    static int32_t UnbindFrameWrapper(void *aUserData, void *pHeader, int flag);

    int extMemoryAlloc(unsigned int width,unsigned int height) ;
    int VSP_bind_cb(void *pHeader,int flag);
    int VSP_unbind_cb(void *pHeader,int flag);

    enum {
        NONE,
        AWAITING_DISABLED,
        AWAITING_ENABLED
    } mOutputPortSettingsChange;

    void initPorts();
    status_t initDecoder();

    void updatePortDefinitions();
    bool portSettingsChanged();
    bool openDecoder(const char* libName);

    DISALLOW_EVIL_CONSTRUCTORS(SPRDMPEG4Decoder);
};

}  // namespace android

#endif  // SPRD_MPEG4_DECODER_H_


