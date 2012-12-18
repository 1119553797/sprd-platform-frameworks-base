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
#define LOG_TAG "AVCSPRDDecoder"
#include <utils/Log.h>

#include "AVCSPRDDecoder.h"

#include "avc_dec_api.h"

//#include "avcdec_api.h"
//#include "avcdec_int.h"

#include <OMX_Component.h>

#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <media/stagefright/foundation/hexdump.h>

/**
This enumeration follows Table 7-1 for NAL unit type codes.
This may go to avccommon_api.h later (external common).
@publishedAll
*/
typedef enum
{
    AVC_NALTYPE_SLICE = 1,  /* non-IDR non-data partition */
    AVC_NALTYPE_DPA = 2,    /* data partition A */
    AVC_NALTYPE_DPB = 3,    /* data partition B */
    AVC_NALTYPE_DPC = 4,    /* data partition C */
    AVC_NALTYPE_IDR = 5,    /* IDR NAL */
    AVC_NALTYPE_SEI = 6,    /* supplemental enhancement info */
    AVC_NALTYPE_SPS = 7,    /* sequence parameter set */
    AVC_NALTYPE_PPS = 8,    /* picture parameter set */
    AVC_NALTYPE_AUD = 9,    /* access unit delimiter */
    AVC_NALTYPE_EOSEQ = 10, /* end of sequence */
    AVC_NALTYPE_EOSTREAM = 11, /* end of stream */
    AVC_NALTYPE_FILL = 12   /* filler data */
} AVCNalUnitType;


namespace android {
    
#define H264_DECODER_INTERNAL_BUFFER_SIZE (200*1024) 
#define H264_DECODER_STREAM_BUFFER_SIZE 1024*1024

static const char kStartCode[4] = { 0x00, 0x00, 0x00, 0x01 };

static int32_t Malloc(void *userData, int32_t size, int32_t attrs) {
    return reinterpret_cast<int32_t>(malloc(size));
}

static void Free(void *userData, int32_t ptr) {
    free(reinterpret_cast<void *>(ptr));
}

AVCSPRDDecoder::AVCSPRDDecoder(const sp<MediaSource> &source)
    : mSource(source),
      mStarted(false),
#if 0      
      mHandle(new tagAVCHandle),
#endif      
      mInputBuffer(NULL),
      mAnchorTimeUs(0),
      mNumSamplesOutput_h(0),
      mPendingSeekTimeUs(-1),
      mPendingSeekMode(MediaSource::ReadOptions::SEEK_CLOSEST_SYNC),
      mTargetTimeUs(-1),
      mSPSSeen(false),
      mPPSSeen(false),
      mCodecExtraBufferMalloced(false){
#if 0      
    memset(mHandle, 0, sizeof(tagAVCHandle));
    mHandle->AVCObject = NULL;
    mHandle->userData = this;
    mHandle->CBAVC_DPBAlloc = ActivateSPSWrapper;
    mHandle->CBAVC_FrameBind = BindFrameWrapper;
    mHandle->CBAVC_FrameUnbind = UnbindFrame;
    mHandle->CBAVC_Malloc = Malloc;
    mHandle->CBAVC_Free = Free;
#endif
    H264Dec_RegBufferCB(BindFrameWrapper,UnbindFrame,(void *)this);
    H264Dec_RegSPSCB( ActivateSPSWrapper);

    mCodecInterBufferSize = H264_DECODER_INTERNAL_BUFFER_SIZE;
    mCodecInterBuffer = (uint8 *)malloc(mCodecInterBufferSize);

//    LOGI("%s, %d: AVCSPRDDecoder", __FUNCTION__, __LINE__);
    mFormat = new MetaData;
    mFormat->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
    int32_t width, height;
    CHECK(mSource->getFormat()->findInt32(kKeyWidth, &width));
    CHECK(mSource->getFormat()->findInt32(kKeyHeight, &height));
    mFormat->setInt32(kKeyWidth, width);
    mFormat->setInt32(kKeyHeight, height);
    mFormat->setInt32(kKeyColorFormat, OMX_COLOR_FormatYUV420Planar);
    mFormat->setCString(kKeyDecoderComponent, "AVCSPRDDecoder");

    int64_t durationUs;
    if (mSource->getFormat()->findInt64(kKeyDuration, &durationUs)) {
        mFormat->setInt64(kKeyDuration, durationUs);
    }
}

AVCSPRDDecoder::~AVCSPRDDecoder() {
    if (mStarted) {
        stop();
    }
#if 0
    PVAVCCleanUpDecoder(mHandle);
#endif
    free(mCodecInterBuffer);
    mCodecInterBuffer = NULL;

     if (mCodecExtraBufferMalloced)
    {
        free(mCodecExtraBuffer);
        mCodecExtraBuffer = NULL;
    }
}

status_t AVCSPRDDecoder::start(MetaData *) {
    CHECK(!mStarted);

    uint32_t type;
    const void *data;
    size_t size;
    sp<MetaData> meta = mSource->getFormat();
    if (meta->findData(kKeyAVCC, &type, &data, &size)) {
        // Parse the AVCDecoderConfigurationRecord

        const uint8_t *ptr = (const uint8_t *)data;

        CHECK(size >= 7);
        CHECK_EQ(ptr[0], 1);  // configurationVersion == 1
        uint8_t profile = ptr[1];
        uint8_t level = ptr[3];

        LOGI("%s, %d, profile: %0x, level: %d", __FUNCTION__, __LINE__, profile, level);

        // There is decodable content out there that fails the following
        // assertion, let's be lenient for now...
        // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

        size_t lengthSize = 1 + (ptr[4] & 3);

        // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
        // violates it...
        // CHECK((ptr[5] >> 5) == 7);  // reserved

        size_t numSeqParameterSets = ptr[5] & 31;

        LOGI("%s, %d, numSeqParameterSets: %d", __FUNCTION__, __LINE__, numSeqParameterSets);

        ptr += 6;
        size -= 6;

        for (size_t i = 0; i < numSeqParameterSets; ++i) {
            CHECK(size >= 2);
            size_t length = U16_AT(ptr);

            ptr += 2;
            size -= 2;

            CHECK(size >= length);

            addCodecSpecificData(ptr, length);

            ptr += length;
            size -= length;
        }

        CHECK(size >= 1);
        size_t numPictureParameterSets = *ptr;

        LOGI("%s, %d, numPictureParameterSets: %d", __FUNCTION__, __LINE__, numPictureParameterSets);
    
        ++ptr;
        --size;

        for (size_t i = 0; i < numPictureParameterSets; ++i) {
            CHECK(size >= 2);
            size_t length = U16_AT(ptr);

            ptr += 2;
            size -= 2;

            CHECK(size >= length);

            addCodecSpecificData(ptr, length);

            ptr += length;
            size -= length;
        }
    }

    MMCodecBuffer codec_buf;
    MMDecVideoFormat video_format;
    
    codec_buf.int_buffer_ptr = (uint8 *)( mCodecInterBuffer);
    codec_buf.int_size = mCodecInterBufferSize;

    video_format.video_std = H264;
    video_format.frame_width = 0;
    video_format.frame_height = 0;	
    video_format.p_extra = NULL;
    video_format.i_extra = 0;

    MMDecRet ret = H264DecInit(&codec_buf,&video_format);

    LOGI("%s, %d, init_ret: %d", __FUNCTION__, __LINE__, ret);
		
//    if(ret!=MMDEC_OK)
//		return OMX_FALSE;

    mSource->start();

    mAnchorTimeUs = 0;
    mNumSamplesOutput_h = 0;
    mPendingSeekTimeUs = -1;
    mPendingSeekMode = ReadOptions::SEEK_CLOSEST_SYNC;
    mTargetTimeUs = -1;
    mSPSSeen = false;
    mPPSSeen = false;
    mStarted = true;

    return OK;
}

void AVCSPRDDecoder::addCodecSpecificData(const uint8_t *data, size_t size) {
    MediaBuffer *buffer = new MediaBuffer(size + 4);
    memcpy(buffer->data(), kStartCode, 4);
    memcpy((uint8_t *)buffer->data() + 4, data, size);
    buffer->set_range(0, size + 4);

    mCodecSpecificData.push(buffer);
}

status_t AVCSPRDDecoder::stop() {
    CHECK(mStarted);

    for (size_t i = 0; i < mCodecSpecificData.size(); ++i) {
        (*mCodecSpecificData.editItemAt(i)).release();
    }
    mCodecSpecificData.clear();

    if (mInputBuffer) {
        mInputBuffer->release();
        mInputBuffer = NULL;
    }

    mSource->stop();

    releaseFrames();

    mStarted = false;

    return OK;
}

sp<MetaData> AVCSPRDDecoder::getFormat() {
    return mFormat;
}

static void findNALFragment(
        const MediaBuffer *buffer, const uint8_t **fragPtr, size_t *fragSize) {
    const uint8_t *data =
        (const uint8_t *)buffer->data() + buffer->range_offset();

    size_t size = buffer->range_length();

    CHECK(size >= 4);
    CHECK(!memcmp(kStartCode, data, 4));

    size_t offset = 4;
    while (offset + 3 < size && memcmp(kStartCode, &data[offset], 4)) {
        ++offset;
    }

    *fragPtr = &data[4];
    if (offset + 3 >= size) {
        *fragSize = size - 4;
    } else {
        *fragSize = offset - 4;
    }
}

MediaBuffer *AVCSPRDDecoder::drainOutputBuffer() {
    int32_t index;
    int32_t Release;
#if 0    
    AVCFrameIO Output;
    Output.YCbCr[0] = Output.YCbCr[1] = Output.YCbCr[2] = NULL;
#endif    
#if 0    
    AVCDec_Status status = PVAVCDecGetOutput(mHandle, &index, &Release, &Output);

    if (status != AVCDEC_SUCCESS) {
        LOGV("PVAVCDecGetOutput returned error %d", status);
        return NULL;
    }
#else
    index = mNumSamplesOutput_h;
#endif
    CHECK(index >= 0);
    CHECK(index < (int32_t)mFrames.size());

    MediaBuffer *mbuf = mFrames.editItemAt(index);

    bool skipFrame = false;

    LOGI("%s, %d, mTargetTimeUs: %d", __FUNCTION__, __LINE__, mTargetTimeUs);

    if (mTargetTimeUs >= 0) {
        int64_t timeUs;
        CHECK(mbuf->meta_data()->findInt64(kKeyTime, &timeUs));
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
    ++mNumSamplesOutput_h;

    if (mNumSamplesOutput_h > (int32_t)mFrames.size())
    {
        mNumSamplesOutput_h = 0;
    }

    if (!skipFrame) {
        mbuf->set_range(0, mbuf->size());
        mbuf->add_ref();

        return mbuf;
    }

    return new MediaBuffer(0);
}

void dump_bs( uint8* pBuffer,int32 aInBufSize)
{
	FILE *fp = fopen("/data/video_es.m4v","ab");
	fwrite(pBuffer,1,aInBufSize,fp);
	fclose(fp);
}

status_t AVCSPRDDecoder::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        LOGV("seek requested to %lld us (%.2f secs)", seekTimeUs, seekTimeUs / 1E6);

      //  CHECK(seekTimeUs >= 0);
        if (seekTimeUs < 0)
			seekTimeUs = 0 ; 
        mPendingSeekTimeUs = seekTimeUs;
        mPendingSeekMode = mode;

        if (mInputBuffer) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        }
#if 0
        PVAVCDecReset(mHandle);
#endif
    }

    LOGI("%s, %d", __FUNCTION__, __LINE__);

    if (mInputBuffer == NULL) {
        LOGV("fetching new input buffer.");

        bool seeking = false;
        LOGI("%s, %d", __FUNCTION__, __LINE__);

        if (!mCodecSpecificData.isEmpty()) {
            mInputBuffer = mCodecSpecificData.editItemAt(0);
            mCodecSpecificData.removeAt(0);
        } else {
            for (;;) {
                if (mPendingSeekTimeUs >= 0) {
                    LOGV("reading data from timestamp %lld (%.2f secs)",
                         mPendingSeekTimeUs, mPendingSeekTimeUs / 1E6);
                }

                ReadOptions seekOptions;
                if (mPendingSeekTimeUs >= 0) {
                    seeking = true;

                    seekOptions.setSeekTo(mPendingSeekTimeUs, mPendingSeekMode);
                    mPendingSeekTimeUs = -1;
                }
                status_t err = mSource->read(&mInputBuffer, &seekOptions);
                seekOptions.clearSeekTo();

                if (err != OK) {
                    *out = drainOutputBuffer();
                    return (*out == NULL)  ? err : (status_t)OK;
                }

                if (mInputBuffer->range_length() > 0) {
                    break;
                }

                mInputBuffer->release();
                mInputBuffer = NULL;
            }
        }

        if (seeking) {
            int64_t targetTimeUs;
            if (mInputBuffer->meta_data()->findInt64(kKeyTargetTime, &targetTimeUs)
                    && targetTimeUs >= 0) {
                mTargetTimeUs = targetTimeUs;
	        LOGI("%s, %d", __FUNCTION__, __LINE__);
            } else {
                mTargetTimeUs = -1;
	        LOGI("%s, %d", __FUNCTION__, __LINE__);
            }
        }
    }

    const uint8_t *fragPtr;
    size_t fragSize;
    findNALFragment(mInputBuffer, &fragPtr, &fragSize);

    LOGI("%s, %d, fragSize: %d", __FUNCTION__, __LINE__, fragSize);

    bool releaseFragment = true;
    status_t err = UNKNOWN_ERROR;

    int nalType;
    int nalRefIdc;

    MMDecRet res = H264DecGetNALType(
                const_cast<uint8_t *>(fragPtr), fragSize,
                &nalType, &nalRefIdc);

    if (res != MMDEC_OK) {
        LOGI("cannot determine nal type");
    } else if (nalType == AVC_NALTYPE_SPS || nalType == AVC_NALTYPE_PPS
                || (mSPSSeen && mPPSSeen)) 
    {
        LOGI("%s, %d, fragSize: %d, nalType: %d, nalRefIdc: %d, ret: %d", __FUNCTION__, __LINE__,fragSize, nalType, nalRefIdc, res);
    
        MMDecInput dec_in;
        MMDecOutput dec_out;

        int32 iSkipToIDR = 1;

        dec_in.pStream = (uint8 *)(fragPtr - 4); //  4 for startcode.
        dec_in.dataLen = fragSize+4;
        dec_in.beLastFrm = 0;
        dec_in.expected_IVOP = iSkipToIDR;
        dec_in.beDisplayed = 1;
        dec_in.err_pkt_num = 0;

        dec_out.frameEffective = 0;	

 //       dump_bs(dec_in.pStream, dec_in.dataLen);

        MMDecRet decRet = H264DecDecode(&dec_in,&dec_out);
        LOGI("%s, %d, decRet: %d, dec_out.frameEffective: %d", __FUNCTION__, __LINE__, decRet, dec_out.frameEffective);
        
        switch (nalType) {
            case AVC_NALTYPE_SPS:
            {
                mSPSSeen = true;

                int32_t aligned_width, aligned_height;
                H264GetBufferDimensions(&aligned_width, &aligned_height) ;

                int32_t oldWidth, oldHeight;
                CHECK(mFormat->findInt32(kKeyWidth, &oldWidth));
                CHECK(mFormat->findInt32(kKeyHeight, &oldHeight));

                if (oldWidth != aligned_width || oldHeight != aligned_height) {
                    mFormat->setInt32(kKeyWidth, aligned_width);
                    mFormat->setInt32(kKeyHeight, aligned_height);

                    err = INFO_FORMAT_CHANGED;
                } else {
                    *out = new MediaBuffer(0);
                    err = OK;
                }
               break;
            }
            case AVC_NALTYPE_PPS:
            {
                mPPSSeen = true;
            }
            case AVC_NALTYPE_SLICE:
            case AVC_NALTYPE_IDR:
            case AVC_NALTYPE_SEI:
            case AVC_NALTYPE_AUD:
            case AVC_NALTYPE_FILL:
            case AVC_NALTYPE_EOSEQ:
            {
                    if (dec_out.frameEffective)
                    {
                        MediaBuffer *mbuf = drainOutputBuffer();
                        if (mbuf == NULL) {
                            break;
                        }
                        *out = mbuf;

                        // Do _not_ release input buffer yet.

                        releaseFragment = false;
                    }else
                    {
                       *out = new MediaBuffer(0);
                    }
                err = OK;
                break;
            }
            default:
            {
                LOGE("Should not be here, unknown nalType %d", nalType);
                CHECK(!"Should not be here");
                break;
            }
        }
    } else {
        // We haven't seen SPS or PPS yet.

        *out = new MediaBuffer(0);
        err = OK;
    }

    if (releaseFragment) {
        size_t offset = mInputBuffer->range_offset();
        if (fragSize + 4 == mInputBuffer->range_length()) {
            mInputBuffer->release();
            mInputBuffer = NULL;
        } else {
            mInputBuffer->set_range(
                    offset + fragSize + 4,
                    mInputBuffer->range_length() - fragSize - 4);
        }
    }

    return err;
}

// static
int32_t AVCSPRDDecoder::ActivateSPSWrapper(
        void *userData, unsigned int width,unsigned int height, unsigned int numBuffers) {
    return static_cast<AVCSPRDDecoder *>(userData)->activateSPS(width, height, numBuffers);
}

// static
int32_t AVCSPRDDecoder::BindFrameWrapper(
        void *userData/*, int32_t index*/, uint8_t **yuv) {
    return static_cast<AVCSPRDDecoder *>(userData)->bindFrame(/*index,*/ yuv);
}

// static
void AVCSPRDDecoder::UnbindFrame(void *userData, int32_t index) {
}

int32_t AVCSPRDDecoder::activateSPS(
        unsigned int width,unsigned int height, unsigned int numBuffers) {
    CHECK(mFrames.isEmpty());

    MMCodecBuffer codec_buf;
    uint32 mb_x = width/16;
    uint32 mb_y = height/16;
    uint32 sizeInMbs = mb_x * mb_y;

    LOGI("%s, %d", __FUNCTION__, __LINE__);

    mCodecExtraBufferSize = (2*+mb_y)*mb_x*8 /*MB_INFO*/
				+ (mb_x*mb_y*16) /*i4x4pred_mode_ptr*/
				+ (mb_x*mb_y*16) /*direct_ptr*/
				+ (mb_x*mb_y*24) /*nnz_ptr*/
				+ (mb_x*mb_y*2*16*2*2) /*mvd*/
				+ 3*4*17 /*fs, fs_ref, fs_ltref*/
				+ 17*(7*4+(23+150*2*17)*4+mb_x*mb_y*16*(2*2*2 + 1 + 1 + 4 + 4)+((mb_x*16+48)*(mb_y*16+48)*3/2)) /*dpb_ptr*/
				+ mb_x*mb_y /*g_MbToSliceGroupMap*/
				+10*1024; //rsv
    if (mCodecExtraBufferMalloced)
    {
        free(mCodecExtraBuffer);
        mCodecExtraBuffer = NULL;
    }
    mCodecExtraBuffer = (uint8 *)malloc(mCodecExtraBufferSize);
    mCodecExtraBufferMalloced = true;

    codec_buf.common_buffer_ptr = mCodecExtraBuffer;
    codec_buf.size = mCodecExtraBufferSize;

    H264DecMemCacheInit(&codec_buf);

    size_t frameSize = (sizeInMbs << 7) * 3;
    for (unsigned int i = 0; i < numBuffers; ++i) {
        MediaBuffer *buffer = new MediaBuffer(frameSize);
        buffer->setObserver(this);

        mFrames.push(buffer);
    }

    return 1;
}

int32_t AVCSPRDDecoder::bindFrame(/*int32_t index,*/ uint8_t **yuv) {
	int32_t index = mNumSamplesOutput_h;
    CHECK(index >= 0);
    CHECK(index < (int32_t)mFrames.size());

    CHECK(mInputBuffer != NULL);
    int64_t timeUs;
    CHECK(mInputBuffer->meta_data()->findInt64(kKeyTime, &timeUs));
    mFrames[index]->meta_data()->setInt64(kKeyTime, timeUs);

    *yuv = (uint8_t *)mFrames[index]->data();

    LOGI("%s, %d,index: %d,  yuv:%0x", __FUNCTION__, __LINE__, index, *yuv);

    return 1;
}

void AVCSPRDDecoder::releaseFrames() {
    for (size_t i = 0; i < mFrames.size(); ++i) {
        MediaBuffer *buffer = mFrames.editItemAt(i);

        buffer->setObserver(NULL);
        buffer->release();
    }
    mFrames.clear();
}

void AVCSPRDDecoder::signalBufferReturned(MediaBuffer *buffer) {
}

}  // namespace android
