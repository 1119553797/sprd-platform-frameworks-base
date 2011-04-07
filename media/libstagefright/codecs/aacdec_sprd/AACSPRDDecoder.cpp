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

#include "AACSPRDDecoder.h"
#define LOG_TAG "AACSPRDDecoder"

#include "../../include/ESDS.h"

#include "aac_decoder.h"

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MetaData.h>

namespace android {

AACSPRDDecoder::AACSPRDDecoder(const sp<MediaSource> &source)
    : mSource(source),
      mStarted(false),
      mBufferGroup(NULL),
      mDecoderBuf(NULL),
      mCodec_specific_data(NULL),
      mCodec_specific_data_size(0),
      mIsLATM(false),
      mPcm_out_l(NULL),
      mPcm_out_r(NULL),
      mAnchorTimeUs(0),
      mNumSamplesOutput(0),
      mInputBuffer(NULL){

    sp<MetaData> srcFormat = mSource->getFormat();

    int32_t sampleRate;
    CHECK(srcFormat->findInt32(kKeySampleRate, &sampleRate));
    mSamplingRate = sampleRate;
    LOGI("AACSPRDDecoder sampleRate %d\n",sampleRate);
    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);

    // We'll always output stereo, regardless of how many channels are
    // present in the input due to decoder limitations.
    mMeta->setInt32(kKeyChannelCount, 2);
    mMeta->setInt32(kKeySampleRate, sampleRate);

    int64_t durationUs;
    if (srcFormat->findInt64(kKeyDuration, &durationUs)) {
        mMeta->setInt64(kKeyDuration, durationUs);
    }
    mMeta->setCString(kKeyDecoderComponent, "AACSPRDDecoder");

    mInitCheck = initCheck();
}

status_t AACSPRDDecoder::initCheck() {

    if(AAC_MemoryAlloc(&mDecoderBuf))
    {
    	return NO_MEMORY;
    }

    uint32_t type;
    const void *data;
    size_t size;
    sp<MetaData> meta = mSource->getFormat();
    if (meta->findData(kKeyESDS, &type, &data, &size)) {
        ESDS esds((const char *)data, size);
        CHECK_EQ(esds.InitCheck(), OK);

        const void *codec_specific_data;
        size_t codec_specific_data_size;
        esds.getCodecSpecificInfo(
                &codec_specific_data, &codec_specific_data_size);

        if(codec_specific_data_size>0){
        	mCodec_specific_data = new uint32_t[(codec_specific_data_size+8+3)/4];
		memset(mCodec_specific_data,0,codec_specific_data_size+8);
        	memcpy(mCodec_specific_data,codec_specific_data,codec_specific_data_size);
        	mCodec_specific_data_size = codec_specific_data_size;
        }
    }
	
    const char *mime;
    if( meta->findCString(kKeyMIMEType, &mime))
    {
        LOGE("AACSPRDDecoder mime %s\n",mime);
    }
    return OK;
}

AACSPRDDecoder::~AACSPRDDecoder() {
    if (mStarted) {
        stop();
    }
}

status_t AACSPRDDecoder::start(MetaData *params) {
    CHECK(!mStarted);

    mBufferGroup = new MediaBufferGroup;
    mBufferGroup->add_buffer(new MediaBuffer(4096 * 2));

    mPcm_out_l = new uint16_t[2048*2];
    mPcm_out_r = new uint16_t[2048*2];	

    mSource->start();

    mAnchorTimeUs = 0;
    mNumSamplesOutput = 0;
    mStarted = true;
    mNumDecodedBuffers = 0;

    return OK;
}

status_t AACSPRDDecoder::stop() {
    CHECK(mStarted);

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    AAC_MemoryFree(&mDecoderBuf);
    mDecoderBuf = NULL;

    delete mBufferGroup;
    mBufferGroup = NULL;

    delete []mPcm_out_l;
    mPcm_out_l = NULL;
    delete []mPcm_out_r;
    mPcm_out_r = NULL;	

    if(mCodec_specific_data){
	delete []mCodec_specific_data;	
	mCodec_specific_data = NULL;
    }
		
    mSource->stop();

    mStarted = false;

    return OK;
}

sp<MetaData> AACSPRDDecoder::getFormat() {
    return mMeta;
}

status_t AACSPRDDecoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    status_t err;

    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        CHECK(seekTimeUs >= 0);

        mNumSamplesOutput = 0;

        if (mInputBuffer) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }

        // Make sure that the next buffer output does not still
        // depend on fragments from the last one decoded.
        AAC_DecStreamBufferUpdate(1,mDecoderBuf);
    } else {
        seekTimeUs = -1;
    }

    if (mInputBuffer == NULL) {
        err = mSource->read(&mInputBuffer, options);

        if (err != OK) {
            return err;
        }

        int64_t timeUs;
        if (mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs)) {
            mAnchorTimeUs = timeUs;
            mNumSamplesOutput = 0;
        } else {
            // We must have a new timestamp after seeking.
            CHECK(seekTimeUs < 0);
        }
    }

    MediaBuffer *buffer;
    CHECK_EQ(mBufferGroup->acquire_buffer(&buffer), OK);

    uint16_t    frm_pcm_len;
    uint8_t      *pInputBuffer =  (uint8_t *)mInputBuffer->data() + mInputBuffer->range_offset();
    uint32_t     inputBufferCurrentLength =  mInputBuffer->range_length();

    if(mNumDecodedBuffers == 0){
	int16_t initRet;
        if(mIsLATM){
	    int8_t latm[] = { 'L', 'A', 'T' , 'M',1};
	    initRet = AAC_DecInit(latm,sizeof(latm),mSamplingRate,1,mDecoderBuf);		
        }else{
           if(mCodec_specific_data){   
	LOGI("mCodec_specific_data_size %d\n",mCodec_specific_data_size);	
	for(int i=0;i<mCodec_specific_data_size;i++)
	LOGI("0x%x",((int8_t *)mCodec_specific_data)[i]);
	
                initRet = AAC_DecInit((int8_t *)mCodec_specific_data,mCodec_specific_data_size,mSamplingRate,0,mDecoderBuf);	
           }else{
                initRet = AAC_DecInit((int8_t *)pInputBuffer,inputBufferCurrentLength,mSamplingRate,0,mDecoderBuf);
           }
        }
	if(initRet){
           LOGW("AAC decoder init returned error %d", initRet);	
	   buffer->release();
           mInputBuffer->release();
           mInputBuffer = NULL;
           return ERROR_UNSUPPORTED;	   
	}
        // Check on the sampling rate to see whether it is changed.
        int32_t sampleRate = AAC_RetrieveSampleRate(mDecoderBuf);
        if (mSamplingRate != sampleRate) {
                mMeta->setInt32(kKeySampleRate, sampleRate);
                LOGW("Sample rate was %d Hz, but now is %d Hz",
                        sampleRate,mSamplingRate);
		mSamplingRate = sampleRate;		
                buffer->release();
                mInputBuffer->release();
                mInputBuffer = NULL;
                return INFO_FORMAT_CHANGED;		
         }	
    }

   // LOGI("AACDecoder %d:0x%x,0x%x,0x%x,0x%x,0x%x,0x%x,0x%x,0x%x\n",(int)mNumDecodedBuffers,pInputBuffer[0],pInputBuffer[1],pInputBuffer[2],pInputBuffer[3],pInputBuffer[4],pInputBuffer[5],pInputBuffer[6],pInputBuffer[7]);
    int16_t decoderRet = AAC_FrameDecode(pInputBuffer,inputBufferCurrentLength,mPcm_out_l,mPcm_out_r,&frm_pcm_len,mDecoderBuf,1);
	
    mNumDecodedBuffers++;
	
    if((decoderRet!=0)&&(decoderRet!=1)){ //decoder error
        LOGW("AAC decoder returned error %d, substituting silence", decoderRet);
		
        // Discard input buffer.
        mInputBuffer->release();
        mInputBuffer = NULL;

        // fall through
    }else if(decoderRet==0){
    	if (mInputBuffer != NULL){
	    mInputBuffer->release();
            mInputBuffer = NULL;
    	}
    }
    	
    size_t numOutBytes =  frm_pcm_len * sizeof(int16_t) *2;
    uint16_t * pOutputBuffer = static_cast<uint16_t *>(buffer->data());
    for(int i=0;i<frm_pcm_len;i++){
		pOutputBuffer[2*i] = mPcm_out_l[i];
		pOutputBuffer[2*i+1] = mPcm_out_r[i];		
    }
	
    buffer->set_range(0, numOutBytes);
    buffer->meta_data()->setInt64(
            kKeyTime,
            mAnchorTimeUs
                + (mNumSamplesOutput * 1000000) / mSamplingRate);

    mNumSamplesOutput += frm_pcm_len;

    *out = buffer;

    return OK;
}

}  // namespace android
