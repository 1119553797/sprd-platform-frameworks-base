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

#ifndef SPRD_MPEG4_ENCODER_H_
#define SPRD_MPEG4_ENCODER_H_

#include "SprdSimpleOMXComponent.h"
#include "m4v_h263_enc_api.h"

namespace android {

struct SPRDMPEG4Encoder : public SprdSimpleOMXComponent {
    SPRDMPEG4Encoder(
            const char *name,
            const OMX_CALLBACKTYPE *callbacks,
            OMX_PTR appData,
            OMX_COMPONENTTYPE **component);

    // Override SimpleSoftOMXComponent methods
    virtual OMX_ERRORTYPE internalGetParameter(
            OMX_INDEXTYPE index, OMX_PTR params);

    virtual OMX_ERRORTYPE internalSetParameter(
            OMX_INDEXTYPE index, const OMX_PTR params);

    virtual void onQueueFilled(OMX_U32 portIndex);

virtual OMX_ERRORTYPE getExtensionIndex(
            const char *name, OMX_INDEXTYPE *index);
            
protected:
    virtual ~SPRDMPEG4Encoder();

private:
    enum {
        kNumBuffers = 2,
    };

    // OMX input buffer's timestamp and flags
    typedef struct {
        int64_t mTimeUs;
        int32_t mFlags;
    } InputBufferInfo;


    OMX_BOOL mStoreMetaData;
    unsigned char* mYUVIn;
    sp<MemoryHeapIon> mYUVInPmemHeap;

    unsigned char* mBuf_inter;
    unsigned char* mBuf_extra;
    sp<MemoryHeapIon> mPmem_extra;

    int32_t mIsH263;
    MMEncVideoInfo mEncInfo;

    int32_t  mVideoWidth;
    int32_t  mVideoHeight;
    int32_t  mVideoFrameRate;
    int32_t  mVideoBitRate;
    int32_t  mVideoColorFormat;
    int32_t  mIDRFrameRefreshIntervalInSec;

    int64_t  mNumInputFrames;
    bool     mStarted;
    bool     mSawInputEOS;
    bool     mSignalledError;

    tagvideoEncControls   *mHandle;
    MMEncConfig *mEncConfig;
    Vector<InputBufferInfo> mInputBufferInfoVec;

    void* mLibHandle;
    FT_MP4EncInit        mMP4EncInit;
    FT_MP4EncSetConf        mMP4EncSetConf;
    FT_MP4EncGetConf        mMP4EncGetConf;
    FT_MP4EncStrmEncode        mMP4EncStrmEncode;
    FT_MP4EncGenHeader        mMP4EncGenHeader;
    FT_MP4EncRelease        mMP4EncRelease;
    
    void initPorts();
    OMX_ERRORTYPE initEncParams();
    OMX_ERRORTYPE initEncoder();
    OMX_ERRORTYPE releaseEncoder();
    bool openEncoder(const char* libName);

    DISALLOW_EVIL_CONSTRUCTORS(SPRDMPEG4Encoder);
};

}  // namespace android

#endif  // SPRD_MPEG4_ENCODER_H_
