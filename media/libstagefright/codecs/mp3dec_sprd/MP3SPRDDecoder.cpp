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
#define LOG_TAG "MP3SPRDDecoder"

#include "mp3_dec_api.h"
#include "MP3SPRDDecoder.h"

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>

//#define SPRD_MP3_DUMP_PCM

#define MP3_MAX_DATA_FRAME_LEN  (1536)  //unit by bytes
#define MP3_DEC_FRAME_LEN       (1152)  //pcm samples number

namespace android {

#ifdef SPRD_MP3_DUMP_PCM
static FILE * s_FileOut = 0;
#endif
MP3Decoder::MP3Decoder(const sp<MediaSource> &source)
    : mSource(source),
      mNumChannels(0),
      mStarted(false),
      mBufferGroup(NULL),
      mSamplingRate(0),
      mBitRate(0),
      mNextMdBegin(0),
      mDecoderBuf(NULL),
      mAnchorTimeUs(0),
      mNumFramesOutput(0),
      mMaxFrameBuf(NULL),
      mInputBuffer(NULL) {
    int ret = 0;
    init();
    ret = MP3_ARM_DEC_Construct(&mMP3DecHandle);
    LOGW("MP3_ARM_DEC_Construct=%d", ret);
}

void MP3Decoder::init() {
    sp<MetaData> srcFormat = mSource->getFormat();

    CHECK(srcFormat->findInt32(kKeyChannelCount, &mNumChannels));
    CHECK(srcFormat->findInt32(kKeySampleRate, &mSamplingRate));
    
    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
    mMeta->setInt32(kKeyChannelCount, mNumChannels);
    mMeta->setInt32(kKeySampleRate, mSamplingRate);
    
    int64_t durationUs;
    if (srcFormat->findInt64(kKeyDuration, &durationUs)) {
        mMeta->setInt64(kKeyDuration, durationUs);
    }

    mMeta->setCString(kKeyDecoderComponent, "MP3Decoder");
}

MP3Decoder::~MP3Decoder() {
    if (mStarted) {
        stop();
    }

}

status_t MP3Decoder::start(MetaData *params) {
    CHECK(!mStarted);

    mBufferGroup = new MediaBufferGroup;
    mBufferGroup->add_buffer(new MediaBuffer(4608 * 2));

    //Output buffer
    mLeftBuf = new uint16_t[MP3_DEC_FRAME_LEN];
    mRightBuf = new uint16_t[MP3_DEC_FRAME_LEN]; 

    //Temporary source data frame buffer.
    mMaxFrameBuf = new uint8_t[MP3_MAX_DATA_FRAME_LEN];
    
    //Init sprd mp3 decoder 
    MP3_ARM_DEC_InitDecoder(mMP3DecHandle);
    
    mSource->start();

    mAnchorTimeUs = 0;
    mNumFramesOutput = 0;
    mStarted = true;

#ifdef SPRD_MP3_DUMP_PCM
    s_FileOut = fopen("/data/MP3Out.pcm", "wb");
    LOGW("s_FileOut = 0x%x", s_FileOut);
#endif
    return OK;
}

status_t MP3Decoder::stop() {
    CHECK(mStarted);

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    delete mBufferGroup;
    mBufferGroup = NULL;

    delete mLeftBuf;
    mLeftBuf = NULL;

    delete mRightBuf;
    mRightBuf = NULL;

    delete mMaxFrameBuf;
    mMaxFrameBuf = NULL;
    
    mSource->stop();

    MP3_ARM_DEC_Deconstruct((void const **)&mMP3DecHandle);
	
    mStarted = false;

#ifdef SPRD_MP3_DUMP_PCM
    fclose(s_FileOut);
#endif
    return OK;
}

sp<MetaData> MP3Decoder::getFormat() {
    return mMeta;
}

uint32_t MP3Decoder::getCurFrameBitRate(uint8_t *frameBuf)
{
    uint32_t header = 0;
    uint32_t bitrate = 0;
    
    if (frameBuf){    
        header = (frameBuf[0]<<24)|(frameBuf[1]<<16)|(frameBuf[2]<<8)|frameBuf[3];

        unsigned layer = (header >> 17) & 3;
        unsigned bitrate_index = (header >> 12) & 0x0f;
        unsigned version = (header >> 19) & 3;
        
        if (layer == 3) {
            // layer I
            static const int kBitrateV1[] = {
                32, 64, 96, 128, 160, 192, 224, 256,
                288, 320, 352, 384, 416, 448
            };

            static const int kBitrateV2[] = {
                32, 48, 56, 64, 80, 96, 112, 128,
                144, 160, 176, 192, 224, 256
            };

            bitrate =
                (version == 3 /* V1 */)
                    ? kBitrateV1[bitrate_index - 1]
                    : kBitrateV2[bitrate_index - 1];
            
        }else {
            // layer II or III
            static const int kBitrateV1L2[] = {
                32, 48, 56, 64, 80, 96, 112, 128,
                160, 192, 224, 256, 320, 384
            };

            static const int kBitrateV1L3[] = {
                32, 40, 48, 56, 64, 80, 96, 112,
                128, 160, 192, 224, 256, 320
            };

            static const int kBitrateV2[] = {
                8, 16, 24, 32, 40, 48, 56, 64,
                80, 96, 112, 128, 144, 160
            };

            if (version == 3 /* V1 */) {
                bitrate = (layer == 2 /* L2 */)
                    ? kBitrateV1L2[bitrate_index - 1]
                    : kBitrateV1L3[bitrate_index - 1];
            } else {
                // V2 (or 2.5)
                bitrate = kBitrateV2[bitrate_index - 1];
            }
        }
        //LOGW("header=0x%x, bitrate=%d", header, bitrate);
        //LOGV("layer(%d), bitrate_index(%d), version(%d)", layer, bitrate_index, version);
    }
    return bitrate;
}
uint32_t MP3Decoder::getNextMdBegin(uint8_t *frameBuf)
{
    uint32_t header = 0;
    uint32_t result = 0;
    uint32_t offset = 0;

    if (frameBuf){    
        header = (frameBuf[0]<<24)|(frameBuf[1]<<16)|(frameBuf[2]<<8)|frameBuf[3];
        offset += 4;

        unsigned layer = (header >> 17) & 3;

        if (layer == 1){
            //only for layer3, else next_md_begin = 0.
        
            if ((header & 0xFFE60000L) == 0xFFE20000L)
            {
               if (!(header & 0x00010000L))
               {
                   offset += 2;
				   if (header & 0x00080000L)
	               {
	                   result = ((uint32_t)frameBuf[7]>>7)|((uint32_t)frameBuf[6]<<1);  
	               }
	               else
	               {
	                   result = frameBuf[6];
	               }
               }
			   else
			   {
	               if (header & 0x00080000L)
	               {
	                   result = ((uint32_t)frameBuf[5]>>7)|((uint32_t)frameBuf[4]<<1);  
	               }
	               else
	               {
	                   result = frameBuf[4];
	               }
			   }
            }
        }
    }
    return result;
}
status_t MP3Decoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    status_t err;
    FRAME_DEC_T inputParam ;
    OUTPUT_FRAME_T outputFrame ;
	
    *out = NULL;
    memset(&inputParam, 0, sizeof(FRAME_DEC_T));
    memset(&outputFrame, 0, sizeof(OUTPUT_FRAME_T));

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        CHECK(seekTimeUs >= 0);

        mNumFramesOutput = 0;

        if (mInputBuffer) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }
		mNextMdBegin = 0;
        // Make sure that the next buffer output does not still
        // depend on fragments from the last one decoded.

		if (mLeftBuf) memset(mLeftBuf, 0, MP3_DEC_FRAME_LEN<<1);
		if (mRightBuf) memset(mRightBuf, 0, MP3_DEC_FRAME_LEN<<1);
        MP3_ARM_DEC_InitDecoder(mMP3DecHandle);
    } else {
        seekTimeUs = -1;
    }

    if (mInputBuffer == NULL) {
        err = mSource->read(&mInputBuffer, options);

        if (err != OK) {
            LOGE("err=%d", err);
            return err;
        }

        int64_t timeUs;
        if (mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs)) {
            mAnchorTimeUs = timeUs;
            mNumFramesOutput = 0;
        } else {
            // We must have a new timestamp after seeking.
            CHECK(seekTimeUs < 0);
        }
    }

    //Config input frame params.
    inputParam.frame_len = mInputBuffer->range_length();
    if (mMaxFrameBuf && inputParam.frame_len <= MP3_MAX_DATA_FRAME_LEN){
        memcpy(mMaxFrameBuf,(uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset(),inputParam.frame_len); 
    }
    inputParam.frame_buf_ptr = mMaxFrameBuf;
	
    //Get current frame bitrate.
    mBitRate = getCurFrameBitRate(inputParam.frame_buf_ptr);
    
    //Get next frame main data begin.
    if (mInputBuffer){
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    if (mInputBuffer == NULL){
        err = mSource->read(&mInputBuffer, NULL);

        if (err != OK) {
            LOGE("err2=%d, mInputBuffer=%d", err, (int)mInputBuffer);
            mNextMdBegin = 0;
            //return err;
        }else{
        	
            int64_t timeUs;
            if (mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs)) {
                mAnchorTimeUs = timeUs;
                mNumFramesOutput = 0;
            } else {
                // We must have a new timestamp after seeking.
                CHECK(seekTimeUs < 0);
            }
            mNextMdBegin = getNextMdBegin((uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset());
        }
    }

    inputParam.next_begin = mNextMdBegin;
    inputParam.bitrate = mBitRate; //kbps
    
    //LOGW("frame_len=%d, bitrate=%d, next_begin=%d", inputParam.frame_len, mBitRate,inputParam.next_begin);
    //Config decoded output frame params.
    outputFrame.pcm_data_l_ptr = mLeftBuf;
    outputFrame.pcm_data_r_ptr = mRightBuf;

    MediaBuffer *buffer;
    CHECK_EQ(mBufferGroup->acquire_buffer(&buffer), OK);
    
    uint32_t decoderRet = 0;
    MP3_ARM_DEC_DecodeFrame(mMP3DecHandle, &inputParam,&outputFrame, &decoderRet);

    if(decoderRet != MP3_ARM_DEC_ERROR_NONE){ //decoder error
        LOGE("MP3 decoder returned error %d, substituting silence", decoderRet);        
        outputFrame.pcm_bytes = 1152; //samples number
    }
    //LOGW("decoderRet=%d,pcm_samples=%d", decoderRet,outputFrame.pcm_bytes);
    
    size_t numOutBytes = 0;
    uint16_t * pOutputBuffer = static_cast<uint16_t *>(buffer->data());
    for(int i=0; i<outputFrame.pcm_bytes; i++){
		if(decoderRet != MP3_ARM_DEC_ERROR_NONE){
			pOutputBuffer[2*i] = 0;
			pOutputBuffer[2*i+1] = 0;
			numOutBytes = outputFrame.pcm_bytes * sizeof(int16_t) *2;
		}else{
		    if(2 == mNumChannels)
		    {
		        numOutBytes = outputFrame.pcm_bytes * sizeof(int16_t) *2;
			    pOutputBuffer[2*i] = mLeftBuf[i];
			    pOutputBuffer[2*i+1] = mRightBuf[i];
		    }else{
		        numOutBytes = outputFrame.pcm_bytes * sizeof(int16_t);
		        pOutputBuffer[i] = mLeftBuf[i];
		    }    
		}
    }

#ifdef SPRD_MP3_DUMP_PCM
    fwrite((uint8_t *)pOutputBuffer, numOutBytes, 1, s_FileOut);
#endif
    buffer->set_range(
            0, numOutBytes);
    
    buffer->meta_data()->setInt64(
            kKeyTime,
            mAnchorTimeUs
                + (mNumFramesOutput * 1000000) / mSamplingRate);

    mNumFramesOutput += outputFrame.pcm_bytes;

    *out = buffer;

    return OK;
}

}  // namespace android
