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

//#define LOG_NDEBUG 0
#define LOG_TAG "IMAADPCMDecoder"
#include <utils/Log.h>

#include "IMAADPCMDecoder.h"

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>

static const size_t kMaxNumSamplesPerFrame = 32768*4;

namespace android {

IMAADPCMDecoder::IMAADPCMDecoder(const sp<MediaSource> &source)
    : mSource(source),
      mStarted(false),
      mBufferGroup(NULL) {
}

IMAADPCMDecoder::~IMAADPCMDecoder() {
    if (mStarted) {
        stop();
    }
}

status_t IMAADPCMDecoder::start(MetaData *params) {
    CHECK(!mStarted);

    const char *mime;
    CHECK(mSource->getFormat()->findCString(kKeyMIMEType, &mime));

    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_IMAADPCM)) {
    } else {
        return ERROR_UNSUPPORTED;
    }

    mBufferGroup = new MediaBufferGroup;
    mBufferGroup->add_buffer(
            new MediaBuffer(kMaxNumSamplesPerFrame * sizeof(int16_t) * 2));

    mSource->start();

    mStarted = true;

    return OK;
}

status_t IMAADPCMDecoder::stop() {
    CHECK(mStarted);

    delete mBufferGroup;
    mBufferGroup = NULL;

    mSource->stop();

    mStarted = false;

    return OK;
}

sp<MetaData> IMAADPCMDecoder::getFormat() {
    sp<MetaData> srcFormat = mSource->getFormat();

    int32_t numChannels;
    int32_t sampleRate;
    int32_t blockAlign;

    CHECK(srcFormat->findInt32(kKeyChannelCount, &numChannels));
    CHECK(srcFormat->findInt32(kKeySampleRate, &sampleRate));
    if (srcFormat->findInt32(kKeyBlockAlign, &blockAlign)) {
        CHECK(blockAlign%(numChannels*4)==0);
    }

    sp<MetaData> meta = new MetaData;
    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    meta->setInt32(kKeyChannelCount, numChannels);
    meta->setInt32(kKeySampleRate, sampleRate);

    int64_t durationUs;
    if (srcFormat->findInt64(kKeyDuration, &durationUs)) {
        meta->setInt64(kKeyDuration, durationUs);
    }

    meta->setCString(kKeyDecoderComponent, "IMAADPCMDecoder");

    return meta;
}

status_t IMAADPCMDecoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    status_t err;

    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        CHECK(seekTimeUs >= 0);
    } else {
        seekTimeUs = -1;
    }

    MediaBuffer *inBuffer;
    err = mSource->read(&inBuffer, options);

    if (err != OK) {
        return err;
    }

    sp<MetaData> srcFormat = mSource->getFormat();
    int32_t numChannels;
    int32_t blockAlign;
    int samples_per_frame;
    CHECK(srcFormat->findInt32(kKeyChannelCount, &numChannels));
    if (!srcFormat->findInt32(kKeyBlockAlign, &blockAlign)) {
        blockAlign = inBuffer->range_length();
    }
    samples_per_frame = ((blockAlign / numChannels - 4) << 1) + 1;

    if (inBuffer->range_length()%blockAlign != 0) {
        LOGW("WARNING! input buffer corrupt, len=%d, ba=%d", inBuffer->range_length(), blockAlign);
    }
    
    if (inBuffer->range_length()/blockAlign*samples_per_frame > kMaxNumSamplesPerFrame) {
        LOGE("input buffer too large (%d).", inBuffer->range_length());

        inBuffer->release();
        inBuffer = NULL;

        return ERROR_UNSUPPORTED;
    }

    int64_t timeUs;
    CHECK(inBuffer->meta_data()->findInt64(kKeyTime, &timeUs));

    const uint8_t *inputPtr =
        (const uint8_t *)inBuffer->data() + inBuffer->range_offset();
    int inputLength = inBuffer->range_length();

    MediaBuffer *outBuffer;
    CHECK_EQ(mBufferGroup->acquire_buffer(&outBuffer), OK);

    int16_t *outputPtr =
        static_cast<int16_t *>(outBuffer->data());
    int frames = 0;
    while (inputLength > 0)
    {
        DecodeIMAADPCM(
                outputPtr,
                inputPtr, numChannels, blockAlign);
        inputLength -= blockAlign;
        inputPtr += blockAlign;
        outputPtr += samples_per_frame * numChannels;
        frames += samples_per_frame;
    }

    // Each 8-bit byte is converted into a 16-bit sample.
    outBuffer->set_range(0, frames * 2 * numChannels);

    outBuffer->meta_data()->setInt64(kKeyTime, timeUs);

    inBuffer->release();
    inBuffer = NULL;

    *out = outBuffer;

    return OK;
}

static void _adpcm_decode_frame(int16_t **dst_ptrs,
			  int dst_step,
			  const uint8_t *src_frame_ptr,
			  int frames,
			  int channels
			  );

// static
void IMAADPCMDecoder::DecodeIMAADPCM(
        int16_t *out, const uint8_t *in, int channels, size_t inSize) {
        int frames = ((inSize / channels - 4) << 1) + 1;
        int16_t *dst[2];
        dst[0] = out;
        dst[1] = out+1;
        _adpcm_decode_frame(dst, channels, in, frames, channels);
}

/* First table lookup for Ima-ADPCM quantizer */
static const int8_t IndexAdjust[8] = { -1, -1, -1, -1, 2, 4, 6, 8 };

/* Second table lookup for Ima-ADPCM quantizer */
static const short StepSize[89] = {
	7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
	19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
	50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
	130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
	337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
	876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
	2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
	5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
	15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
};

typedef struct _ima_adpcm_state {
	int pred_val;		/* Calculated predicted value */
	int step_idx;		/* Previous StepSize lookup index */
} ima_adpcm_state_t;

static int adpcm_decoder(unsigned char code, ima_adpcm_state_t * state)
{
	short pred_diff;	/* Predicted difference to next sample */
	short step;		/* holds previous StepSize value */
	char sign;

	int i;

	/* Separate sign and magnitude */
	sign = code & 0x8;
	code &= 0x7;

	/*
	 * Computes pred_diff = (code + 0.5) * step / 4,
	 * but see comment in adpcm_coder.
	 */

	step = StepSize[state->step_idx];

	/* Compute difference and new predicted value */
	pred_diff = step >> 3;
	for (i = 0x4; i; i >>= 1, step >>= 1) {
		if (code & i) {
			pred_diff += step;
		}
	}
	state->pred_val += (sign) ? -pred_diff : pred_diff;

	/* Clamp output value */
	if (state->pred_val > 32767) {
		state->pred_val = 32767;
	} else if (state->pred_val < -32768) {
		state->pred_val = -32768;
	}

	/* Find new StepSize index value */
	state->step_idx += IndexAdjust[code];

	if (state->step_idx < 0) {
		state->step_idx = 0;
	} else if (state->step_idx > 88) {
		state->step_idx = 88;
	}
	return (state->pred_val);
}

static void _adpcm_decode_mono(int16_t *dst_ptr,
			  int dst_step,
			  const uint8_t *src_ptr,
			  unsigned int frames,
			  ima_adpcm_state_t *states)
{
    int srcbit = 0;
	while (frames-- > 0) {
		unsigned char v;
		if (!srcbit)
			v = *src_ptr & 0x0f;
		else
			v = (*src_ptr >> 4) & 0x0f;
		*dst_ptr = adpcm_decoder(v, states);
		srcbit ++;
		if (srcbit == 2) {
			src_ptr++;
			srcbit = 0;
		}
		dst_ptr += dst_step;
	}
}

static void _adpcm_decode_frame(int16_t **dst_ptrs,
			  int dst_step,
			  const uint8_t *src_frame_ptr,
			  int frames,
			  int channels
			  )
{
    ima_adpcm_state_t state[2];
    int16_t *dst_ptr[2];
    int i;
    assert(channels==1 || channels==2);
    // parse headers
    for (i=0; i<channels; i++)
    {
        state[i].pred_val = ((int16_t)(src_frame_ptr[0] | (src_frame_ptr[1]<<8)));
        src_frame_ptr+=2;
        state[i].step_idx = *src_frame_ptr;
        src_frame_ptr+=2;
        dst_ptr[i] = dst_ptrs[i];
        *dst_ptr[i] = state[i].pred_val;
        dst_ptr[i] += dst_step;
    }
    frames --;
    // decode samples
    while (frames>0)
    {
        for (i=0; i<channels; i++)
        {
            int decoded_fremes = frames > 8 ? 8 : frames;
            _adpcm_decode_mono(dst_ptr[i],
                      dst_step,
                      src_frame_ptr,
                      decoded_fremes, // max 8 samples per group
                      &state[i]);
            src_frame_ptr += 4;
            dst_ptr[i] += 8*dst_step;
        }
        frames -= 8;
    }
}

}  // namespace android

