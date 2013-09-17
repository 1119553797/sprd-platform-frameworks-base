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

//#define LOG_NDEBUG 0
#define LOG_TAG "FMExtractor"
#include <utils/Log.h>

#include "include/FMExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <utils/String8.h>

#include "FMHalSource.h"

namespace android {

const int FMExtractor::kNumChannels = 2;
const int FMExtractor::kSampleRate = 32000;
const int FMExtractor::kBitRate=32000*2*16;
const int FMExtractor::kDuration=1600000000;

struct FMSource : public MediaSource {
    FMSource(const sp<MetaData> &meta);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~FMSource();

private:
    static const size_t kMaxFrameSize;

    sp<MetaData> mMeta;
    bool mStarted;
    MediaBufferGroup *mGroup;

    sp<FMHalSource>  mFmSource;
    int mOffset;
    int mCurrentPos;
    int mSampleRate;
    int mBitRate;
    int mNumChannels;
    int bytesPerSample;
    FMSource(const FMSource &);
    FMSource &operator=(const FMSource &);
};

FMExtractor::FMExtractor(const char *uri)
    : mValidFormat(false) {
    mInitCheck = init();
}

FMExtractor::~FMExtractor() {
}

sp<MetaData> FMExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    LOGD("getMetaData %d", mInitCheck);
    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, "audio/fm");

    return meta;
}

uint32_t FMExtractor::flags() const {
    return CAN_PAUSE;
}

size_t FMExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<MediaSource> FMExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return new FMSource(mTrackMeta);
}

sp<MetaData> FMExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    if (mInitCheck != OK || index > 0) {
        return NULL;
    }

    return mTrackMeta;
}

status_t FMExtractor::init() {
    mTrackMeta = new MetaData;
    mTrackMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    mTrackMeta->setInt32(kKeyChannelCount, kNumChannels);
    mTrackMeta->setInt32(kKeySampleRate, kSampleRate);
    mTrackMeta->setInt32(kKeyBitRate, kBitRate);
    mTrackMeta->setInt32(kKeyDuration, kDuration);
    return OK;
}

const size_t FMSource::kMaxFrameSize = 32*1024;

FMSource::FMSource(const sp<MetaData> &meta)
: mMeta(meta),mStarted(false) {
    int channels=0;
     int samplerate=0;
    mCurrentPos=0;
    mSampleRate=samplerate;
    mOffset=0;
    mMeta->findInt32(kKeyChannelCount,&channels);
    mMeta->findInt32(kKeySampleRate,&samplerate);
    mMeta->setInt32(kKeyMaxInputSize, kMaxFrameSize);
    mFmSource = new FMHalSource(channels,samplerate);
    mCurrentPos=0;
    mNumChannels=channels;
    mSampleRate=samplerate;
    mOffset=0;
    bytesPerSample=2;
}

FMSource::~FMSource() {
    if (mStarted) {
        stop();
    }
}

status_t FMSource::start(MetaData *params) {
    LOGE("FMSource::start");

    CHECK(!mStarted);
    LOGD("FMSource:: start 1");
    mGroup = new MediaBufferGroup;
    LOGD("FMSource:: Start 3");
    mGroup->add_buffer(new MediaBuffer(kMaxFrameSize));
    LOGD("FMSource:: start 2");
    mFmSource->start();
    mStarted = true;
    LOGD("FMSource:: start ok");
    return OK;
}

status_t FMSource::stop() {
    LOGD("FMSource::stop");

    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;
    mFmSource->stop();
    mStarted = false;

    return OK;
}

sp<MetaData> FMSource::getFormat() {
    LOGD("FMSource::getFormat");

    return mMeta;
}

status_t FMSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options != NULL && options->getSeekTo(&seekTimeUs, &mode)) {
        //TODO: handle seek here, and should be disabled
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        LOGE("FMSource: can't get buffer %d", err);
        return err;
    }

    const size_t maxBytesToRead = kMaxFrameSize;

    ssize_t n = mFmSource->read((uint8_t *)buffer->data(),1024);

    buffer->set_range(0, n);

    //TODO: calc timestamp here
    buffer->meta_data()->setInt64(
            kKeyTime,
            1000000LL * (mCurrentPos - mOffset)
            / (mNumChannels * bytesPerSample) / mSampleRate);

    mCurrentPos += n;

    *out = buffer;

    return OK;
}

}  // namespace android

