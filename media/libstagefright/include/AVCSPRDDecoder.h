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

#ifndef AVC_SPRD_DECODER_H_

#define AVC_SPRD_DECODER_H_

#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaSource.h>
#include <utils/Vector.h>

//struct tagAVCHandle;

namespace android {

struct AVCSPRDDecoder : public MediaSource,
                    public MediaBufferObserver {
    AVCSPRDDecoder(const sp<MediaSource> &source);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

    virtual void signalBufferReturned(MediaBuffer *buffer);

protected:
    virtual ~AVCSPRDDecoder();

private:
    sp<MediaSource> mSource;
    bool mStarted;

    sp<MetaData> mFormat;

    Vector<MediaBuffer *> mCodecSpecificData;

//    tagAVCHandle *mHandle;
    Vector<MediaBuffer *> mFrames;
    MediaBuffer *mInputBuffer;

    uint8_t *mCodecInterBuffer;
    uint32_t mCodecInterBufferSize;
    uint8_t *mCodecExtraBuffer;
    uint32_t mCodecExtraBufferSize;
    bool mCodecExtraBufferMalloced;

    int64_t mAnchorTimeUs;
    int32_t mNumSamplesOutput_h;
    int64_t mPendingSeekTimeUs;
    MediaSource::ReadOptions::SeekMode mPendingSeekMode;

    int64_t mTargetTimeUs;

    bool mSPSSeen;
    bool mPPSSeen;

    void addCodecSpecificData(const uint8_t *data, size_t size);

    static int32_t ActivateSPSWrapper(
            void *userData, unsigned int width,unsigned int height, unsigned int numBuffers);

    static int32_t BindFrameWrapper(
            void *userData/*, int32_t index*/, uint8_t **yuv);

    static void UnbindFrame(void *userData, int32_t index);

    int32_t activateSPS(
            unsigned int width,unsigned int height, unsigned int numBuffers);

    int32_t bindFrame(/*int32_t index,*/ uint8_t **yuv);

    void releaseFrames();

    MediaBuffer *drainOutputBuffer();

    AVCSPRDDecoder(const AVCSPRDDecoder &);
    AVCSPRDDecoder &operator=(const AVCSPRDDecoder &);
};

}  // namespace android

#endif  // AVC_SPRD_DECODER_H_
