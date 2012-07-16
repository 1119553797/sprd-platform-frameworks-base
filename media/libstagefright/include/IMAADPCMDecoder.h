/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef IMAADPCM_DECODER_H_

#define IMAADPCM_DECODER_H_

#include <media/stagefright/MediaSource.h>

namespace android {

struct MediaBufferGroup;

struct IMAADPCMDecoder : public MediaSource {
    IMAADPCMDecoder(const sp<MediaSource> &source);

    virtual status_t start(MetaData *params);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options);

protected:
    virtual ~IMAADPCMDecoder();

private:
    sp<MediaSource> mSource;
    bool mStarted;
    enum {IMAADPCM};
    int mFormat;

    MediaBufferGroup *mBufferGroup;

    // return samples decoded
    static int DecodeIMAADPCM(int16_t *out, const uint8_t *in, int channels, size_t inSize);

    IMAADPCMDecoder(const IMAADPCMDecoder &);
    IMAADPCMDecoder &operator=(const IMAADPCMDecoder &);
};

}  // namespace android

#endif  // IMAADPCM_DECODER_H_
