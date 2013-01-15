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
#define LOG_TAG "M4vH263SPRDDecoder"
#include <utils/Log.h>
#include <stdlib.h> // for free
#include "ESDS.h"
#include "M4vH263SPRDDecoder.h"

#include "m4v_h263_dec_api.h"

#include <OMX_Component.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

typedef enum
{
	H263_MODE = 0,MPEG4_MODE,
	FLV_MODE,
	UNKNOWN_MODE	
}MP4DecodingMode;

#define MPEG4_DECODER_INTERNAL_BUFFER_SIZE 10*1024

namespace android {

M4vH263SPRDDecoder::M4vH263SPRDDecoder(const sp<MediaSource> &source)
    : mSource(source),
    mStarted(false),
    mHandle(new tagvideoDecControls),
    mInputBuffer(NULL),
    mNumSamplesOutput_(0),
    mTargetTimeUs(-1),
    mCodecExtraBufferMalloced(false){
    
    memset(mHandle, 0, sizeof(tagvideoDecControls));
    mFormat = new MetaData;
    mFormat->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);

    // CHECK(mSource->getFormat()->findInt32(kKeyWidth, &mWidth));
    // CHECK(mSource->getFormat()->findInt32(kKeyHeight, &mHeight));

    // We'll ignore the dimension advertised by the source, the decoder
    // appears to require us to always start with the default dimensions
    // of 352 x 288 to operate correctly and later react to changes in
    // the dimensions as needed.
    mWidth = 352;
    mHeight = 288;

    mFormat->setInt32(kKeyWidth, mWidth);
    mFormat->setInt32(kKeyHeight, mHeight);
    mFormat->setInt32(kKeyColorFormat, OMX_COLOR_FormatYUV420Planar);
    mFormat->setCString(kKeyDecoderComponent, "M4vH263SPRDDecoder");

    mCodecInterBufferSize = MPEG4_DECODER_INTERNAL_BUFFER_SIZE;
    mCodecInterBuffer = (uint8 *)malloc(mCodecInterBufferSize);
}

M4vH263SPRDDecoder::~M4vH263SPRDDecoder() {
    if (mStarted) {
        stop();
    }

    delete mHandle;
    mHandle = NULL;
    
    free(mCodecInterBuffer);
    mCodecInterBuffer = NULL;

     if (mCodecExtraBufferMalloced)
    {
        free(mCodecExtraBuffer);
        mCodecExtraBuffer = NULL;
    }
}

void M4vH263SPRDDecoder::allocateFrames(int32_t width, int32_t height) {
    size_t frameSize =
        (((width + 15) & - 16) * ((height + 15) & - 16) * 3) / 2;

//    LOGI("%s, frm_size %d, width %d, height %d", __FUNCTION__, frameSize, width, height);

    for (uint32_t i = 0; i < 2; ++i) {
        mFrames[i] = new MediaBuffer(frameSize);
        mFrames[i]->setObserver(this);
    }
}


status_t M4vH263SPRDDecoder::start(MetaData *) {
    CHECK(!mStarted);

    const char *mime = NULL;
    sp<MetaData> meta = mSource->getFormat();
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    MP4DecodingMode mode;
    if (!strcasecmp(MEDIA_MIMETYPE_VIDEO_MPEG4, mime)) {
        mode = MPEG4_MODE;
    } else {
        CHECK(!strcasecmp(MEDIA_MIMETYPE_VIDEO_H263, mime));
        mode = H263_MODE;
    }
//    LOGI("%s, %d, %s", __FUNCTION__, __LINE__, mime);
 
    uint32_t type;
    const void *data = NULL;
    size_t size = 0;
    uint8_t *vol_data[1] = {0};
    int32_t vol_size = 0;
    if (meta->findData(kKeyESDS, &type, &data, &size)) {
        ESDS esds((const uint8_t *)data, size);
        CHECK_EQ(esds.InitCheck(), (status_t)OK);

        const void *codec_specific_data;
        size_t codec_specific_data_size;
        esds.getCodecSpecificInfo(
                &codec_specific_data, &codec_specific_data_size);

        vol_data[0] = (uint8_t *) malloc(codec_specific_data_size);
        memcpy(vol_data[0], codec_specific_data, codec_specific_data_size);
        vol_size = codec_specific_data_size;
    } else {
        vol_data[0] = NULL;
        vol_size = 0;

    }
    LOGI("%s, %d, mode: %d, vol_size: %d", __FUNCTION__, __LINE__, mode, vol_size);

#if 0
    Bool success = PVInitVideoDecoder(
            mHandle, vol_data, &vol_size, 1, mWidth, mHeight, mode);
#else
    MMCodecBuffer codec_buf;
    MMDecVideoFormat video_format;
	
    codec_buf.int_buffer_ptr = (uint8 *)( mCodecInterBuffer);
    codec_buf.int_size = mCodecInterBufferSize;

    video_format.i_extra = vol_size;

    if( video_format.i_extra>0)
    {
	video_format.p_extra =(void *)(vol_data[0]);
    }else{
	video_format.p_extra = NULL;
    }

    if(mode == H263_MODE)
    {
    	video_format.video_std = ITU_H263;
    }else if(mode == MPEG4_MODE)
    {
        video_format.video_std = MPEG4;
    }else
    {
    	video_format.video_std = FLV_V1;
    }

    video_format.frame_width = 0;
    video_format.frame_height = 0;

    video_format.uv_interleaved = 1;// todo jgdu
    uint8_t success = MP4DecInit( mHandle, &codec_buf,&video_format);

    LOGI("%s, %d, success: %d, width: %d, height: %d", __FUNCTION__, __LINE__, success, video_format.frame_width, video_format.frame_height);
#endif        

    if (vol_data[0]) free(vol_data[0]);

    if (success != MMDEC_OK) {
        LOGW("PVInitVideoDecoder failed. Unsupported content?");
        return ERROR_UNSUPPORTED;
    }

//    MP4DecodingMode actualMode = PVGetDecBitstreamMode(mHandle);
//    CHECK_EQ((int)mode, (int)actualMode);

//    PVSetPostProcType((VideoDecControls *) mHandle, 0);

    int32_t width, height;
#if 0
    PVGetVideoDimensions(mHandle, &width, &height);
#else
    width = video_format.frame_width;
    height = video_format.frame_height;
#endif
    if (mode == H263_MODE && (width == 0 || height == 0)) {
        width = 352;
        height = 288;
    }
    allocateFrames(width, height);

    int32 Frm_width_align = ((width + 15) & (~15));
    int32 Frm_height_align = ((height + 15) & (~15));

    int32 mb_x = Frm_width_align/16;
    int32 mb_y = Frm_height_align/16;
    int32 total_mb_num = mb_x * mb_y;
    int32 ext_size_y = (mb_x * 16 + 16*2) * (mb_y * 16 + 16*2);
    int32 ext_size_c = ext_size_y >> 2;
    int32 i;

    mCodecExtraBufferSize = total_mb_num * 6 * 2* sizeof(int32); 	//mb_info
    mCodecExtraBufferSize += 4 * 8 * sizeof(int16);				//pLeftCoeff
    mCodecExtraBufferSize += 6 * 64 * sizeof(int16);				//coef_block
    mCodecExtraBufferSize += 1024;		
    mCodecExtraBufferSize += 4*8*mb_x*sizeof(int16);	//pTopCoeff
    mCodecExtraBufferSize += ((( 64*4*sizeof(int8) + 255) >>8)<<8);	//mb_cache_ptr->pMBBfrY 
    mCodecExtraBufferSize += ((( 64*1*sizeof(int8) + 255) >>8)<<8);	//mb_cache_ptr->pMBBfrU
    mCodecExtraBufferSize += ((( 64*1*sizeof(int8) + 255) >>8)<<8);	//mb_cache_ptr->pMBBfrV 
#ifndef YUV_THREE_PLANE	
    for (i = 0; i < 3; i++)
    {			
        mCodecExtraBufferSize += ((( ext_size_y + 255) >>8)<<8);	//imgYUV[0]
        mCodecExtraBufferSize += ((( ext_size_c + 255) >>8)<<8);	//imgYUV[1]
        mCodecExtraBufferSize += ((( ext_size_c + 255) >>8)<<8);	//imgYUV[2]
    }
#endif	

    if (mCodecExtraBufferMalloced)
    {
        free(mCodecExtraBuffer);
        mCodecExtraBuffer = NULL;
    }
    mCodecExtraBuffer = (uint8 *)malloc(mCodecExtraBufferSize);
    mCodecExtraBufferMalloced = true;

    codec_buf.common_buffer_ptr = mCodecExtraBuffer;
    codec_buf.size = mCodecExtraBufferSize;

    MP4DecMemCacheInit( mHandle,&codec_buf);

    mSource->start();

    mNumSamplesOutput_ = 0;
    mTargetTimeUs = -1;
    mStarted = true;

    return OK;
}

status_t M4vH263SPRDDecoder::stop() {
    CHECK(mStarted);

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }
    
    mSource->stop();

    releaseFrames();

    mStarted = false;
    return (MP4DecRelease(mHandle) == MMDEC_OK)? OK: UNKNOWN_ERROR;
//	return OK;
}

sp<MetaData> M4vH263SPRDDecoder::getFormat() {
    return mFormat;
}

status_t M4vH263SPRDDecoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    bool seeking = false;
    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        seeking = true;
//        CHECK_EQ((int)PVResetVideoDecoder(mHandle), PV_TRUE);
    }

    MediaBuffer *inputBuffer = NULL;
    status_t err = mSource->read(&inputBuffer, options);
    if (err != OK) {
        return err;
    }

    if (seeking) {
        int64_t targetTimeUs;
        if (inputBuffer->meta_data()->findInt64(kKeyTargetTime, &targetTimeUs)
                && targetTimeUs >= 0) {
            mTargetTimeUs = targetTimeUs;
        } else {
            mTargetTimeUs = -1;
        }
    }

    uint8_t *bitstream =
        (uint8_t *) inputBuffer->data() + inputBuffer->range_offset();

    uint32_t timestamp = 0xFFFFFFFF;
    int32_t bufferSize = inputBuffer->range_length();
    uint32_t useExtTimestamp = 0;
#if 0	
    if (PVDecodeVideoFrame(
                mHandle, &bitstream, &timestamp, &bufferSize,
                &useExtTimestamp,
                (uint8_t *)mFrames[mNumSamplesOutput_ & 0x01]->data())
            != PV_TRUE) {
        LOGE("failed to decode video frame.");

        inputBuffer->release();
        inputBuffer = NULL;

        return UNKNOWN_ERROR;
    }
#else

    if (bufferSize < 0)
    {
        LOGE("failed to decode video frame.");

        inputBuffer->release();
        inputBuffer = NULL;

        return UNKNOWN_ERROR;
    }
    
    MMDecInput dec_in;
    MMDecOutput dec_out;
	
//    LOGI("%s, %d, mNumSamplesOutput_: %d", __FUNCTION__, __LINE__, mNumSamplesOutput_);
    MP4DecSetCurRecPic( mHandle, (uint8_t *)mFrames[mNumSamplesOutput_ & 0x01]->data());

    dec_in.pStream = (uint8 *) bitstream;
    dec_in.dataLen = bufferSize;
    dec_in.beLastFrm = 0;
    dec_in.expected_IVOP = 0;
    dec_in.beDisplayed = 1;
    dec_in.err_pkt_num = 0;
  
    int ret;

    dec_out.VopPredType = -1;
    dec_out.frameEffective = 0;	
    MMDecRet decRet =	MP4DecDecode( mHandle, &dec_in,&dec_out);
    LOGI("%s, %d, decRet: %d", __FUNCTION__, __LINE__, decRet);
//    Status =  (decRet == MMDEC_OK)?OMX_TRUE : OMX_FALSE;
#endif	

    int32_t disp_width, disp_height;
#if 0
    PVGetVideoDimensions(mHandle, &disp_width, &disp_height);
#else
    Mp4GetVideoDimensions( mHandle, &disp_width, &disp_height);
#endif
    int32_t buf_width, buf_height;
#if 0
    PVGetBufferDimensions(mHandle, &buf_width, &buf_height);
#else
    Mp4GetBufferDimensions( mHandle, &buf_width, &buf_height);
#endif
    if (buf_width != mWidth || buf_height != mHeight) 
    {
//        LOGI("%s, %d, mNumSamplesOutput_: %d", __FUNCTION__, __LINE__, mNumSamplesOutput_);
        ++mNumSamplesOutput_;  // The client will never get to see this frame.
//        LOGI("%s, %d, mNumSamplesOutput_: %d", __FUNCTION__, __LINE__, mNumSamplesOutput_);
        
//        LOGI("%s, %d, buf_width: %d, mWidth: %d, buf_height: %d, buf_height: %d, mNumSamplesOutput_: %d", 
//            __FUNCTION__, __LINE__, buf_width, mWidth, buf_height, mHeight, mNumSamplesOutput_);

        inputBuffer->release();
        inputBuffer = NULL;

        mWidth = buf_width;
        mHeight = buf_height;
        mFormat->setInt32(kKeyWidth, mWidth);
        mFormat->setInt32(kKeyHeight, mHeight);

        CHECK_LE(disp_width, buf_width);
        CHECK_LE(disp_height, buf_height);
        
        return INFO_FORMAT_CHANGED;
    }

//    LOGI("%s, %d, disp_width: %d, disp_height: %d, buf_width: %d, buf_height: %d", 
//            __FUNCTION__, __LINE__, disp_width, disp_height, buf_width, buf_height);

    int64_t timeUs;
    CHECK(inputBuffer->meta_data()->findInt64(kKeyTime, &timeUs));

    inputBuffer->release();
    inputBuffer = NULL;

    bool skipFrame = false;

    if (mTargetTimeUs >= 0) {
        CHECK(timeUs <= mTargetTimeUs);

        if (timeUs < mTargetTimeUs) {
            // We're still waiting for the frame with the matching
            // timestamp and we won't return the current one.
            skipFrame = true;

            LOGI("skipping frame at %lld us", timeUs);
        } else {
            LOGI("found target frame at %lld us", timeUs);

            mTargetTimeUs = -1;
        }
    }
    ++mNumSamplesOutput_;

//    LOGI("%s, %d, mNumSamplesOutput_: %d", __FUNCTION__, __LINE__, mNumSamplesOutput_);

//    LOGI("%s, %d, skipFrame: %d", __FUNCTION__, __LINE__, skipFrame);
    if (skipFrame || !dec_out.frameEffective) {
//        LOGI("%s, %d", __FUNCTION__, __LINE__);
        *out = new MediaBuffer(0);
    } else {
//        LOGI("%s, %d, mNumSamplesOutput_: %d, frm_addr: %0x, out_frm_addr: %0x", __FUNCTION__, __LINE__, mNumSamplesOutput_, (uint8_t *)mFrames[mNumSamplesOutput_ & 0x01]->data(), dec_out.pOutFrameY);
        *out = mFrames[mNumSamplesOutput_ & 0x01];
        (*out)->add_ref();
        (*out)->meta_data()->setInt64(kKeyTime, timeUs);
    }

    return OK;
}

void M4vH263SPRDDecoder::releaseFrames() {
    for (size_t i = 0; i < sizeof(mFrames) / sizeof(mFrames[0]); ++i) {
        MediaBuffer *buffer = mFrames[i];

        buffer->setObserver(NULL);
        buffer->release();

        mFrames[i] = NULL;
    }
}

void M4vH263SPRDDecoder::signalBufferReturned(MediaBuffer *buffer) {
    LOGV("signalBufferReturned");
}


}  // namespace android
