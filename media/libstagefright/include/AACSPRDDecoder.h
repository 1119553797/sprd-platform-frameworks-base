/*
 * Copyright (C) 2009 The Android Open Source Project
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

#ifndef AAC_SPRD_DECODER_H_

#define AAC_SPRD_DECODER_H_

#include <media/stagefright/MediaSource.h>

namespace android {

struct MediaBufferGroup;
struct MetaData;

struct AACSPRDDecoder : public MediaSource {
    AACSPRDDecoder(const sp<MediaSource> &source);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

protected:
    virtual ~AACSPRDDecoder();

private:
    sp<MetaData>    mMeta;
    sp<MediaSource> mSource;
    bool mStarted;

    MediaBufferGroup *mBufferGroup;

    void *mDecoderBuf;
    uint32_t *mCodec_specific_data;	
    int32_t mCodec_specific_data_size;
    bool mIsLATM;
    int32_t mSamplingRate;
    uint16_t *mPcm_out_l;	
    uint16_t *mPcm_out_r;
    uint32_t *mStreamBuf;	
	
    int64_t mAnchorTimeUs;
    int64_t mNumSamplesOutput;
    status_t mInitCheck;
    int64_t  mNumDecodedBuffers;

    MediaBuffer *mInputBuffer;

    status_t initCheck();
    AACSPRDDecoder(const AACSPRDDecoder &);
    AACSPRDDecoder &operator=(const AACSPRDDecoder &);
};

}  // namespace android

#endif  // AAC_SPRD_DECODER_H_
