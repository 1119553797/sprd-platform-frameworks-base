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
#define LOG_TAG "AACExtractor"
#include <utils/Log.h>

#include "include/AACExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>

namespace android {

/***
* There are three profiles identified in the MPEG-2 AAC standard.
* A two bit field indicates the profile :main profile(0), LC(1),
* SSR(2), Reserved(3). sprd decoder only support LC profile now.
***/
#define AAC_PROFILE_LC   (1)
#define AAC_ADTS_HEADER_SIZE 7
#define FRAME_NUM_BITRATE 100
#define SYNC_COUNT 1000
static const uint32_t kMask = 0xfff00000;

static int Resync(
  const sp<DataSource> &source, off64_t *pos, off64_t *post_id3_pos) {
    if (post_id3_pos != NULL) {
        *post_id3_pos = 0;
    }

    if (*pos == 0) {
        // Skip an optional ID3 header if syncing at the very beginning
        // of the datasource.

        for (;;) {
            uint8_t id3header[10];
            if (source->readAt(*pos, id3header, sizeof(id3header))
                    < (ssize_t)sizeof(id3header)) {
                // If we can't even read these 10 bytes, we might as well bail
                // out, even if there _were_ 10 bytes of valid mp3 audio data...
                return false;
            }

            if (memcmp("ID3", id3header, 3)) {
                break;
            }

            // Skip the ID3v2 header.

            size_t len =
                ((id3header[6] & 0x7f) << 21)
                | ((id3header[7] & 0x7f) << 14)
                | ((id3header[8] & 0x7f) << 7)
                | (id3header[9] & 0x7f);

            len += 10;

            *pos += len;

            LOGI("AAC,skipped ID3 tag, new starting offset is %lld (0x%08llx)",
                 *pos, *pos);
        }

        if (post_id3_pos != NULL) {
            *post_id3_pos = *pos;
        }
    }
    return 0;
}


class AACSource : public MediaSource {
public:
    AACSource(const sp<DataSource> &source,
              const sp<MetaData> &meta, off64_t first_frame_pos, int bitrate);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();

    virtual sp<MetaData> getFormat();

    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
    virtual ~AACSource();

private:
    sp<DataSource> mDataSource;
    sp<MetaData> mMeta;

    off_t mOffset;
    int64_t mCurrentTimeUs;
    bool mStarted;
    MediaBufferGroup *mGroup;

    int mBitrate;

    AACSource(const AACSource &);
    AACSource &operator=(const AACSource &);
};

////////////////////////////////////////////////////////////////////////////////
const int mpeg4audio_sample_rates[16] = {
    96000, 88200, 64000, 48000, 44100, 32000,
    24000, 22050, 16000, 12000, 11025, 8000, 7350
};

static  int aac_parse_header(const sp<DataSource> &source, size_t offset, int  *sr, int *chnum, int *sample)
{ 
    char buf[8];
    if (source->readAt(offset, buf, sizeof(buf)) != sizeof(buf)) {
        return -1;
    }   
    uint32_t header1 = U32_AT((const uint8_t *)buf);
    uint32_t header2 = U32_AT((const uint8_t *)(buf+4));

    if((header1&kMask)!=kMask)
        return -1;

    //Check the profile, only support LC.
    if ((header1&0xFF00)>>14 != AAC_PROFILE_LC) {
        LOGE("Profile unsupported!(%d)", (header1&0xFF00)>>14);
        return -1;
    }
    int  sampling_index = (header1>>10)&0xf;
    int  sample_rate = mpeg4audio_sample_rates[sampling_index];
    if(!sample_rate)
        return -1;

    int chan_config = (header1>>6)&0x7;

    int size = ((header1&0x3)<<11)|(header2>>21);
    if(size < AAC_ADTS_HEADER_SIZE)
    	return -1;
		
    int num_aac_frames = (header2>>8)&0x3;
    //num_aac_frames += 1;

    int samples = (num_aac_frames+1)*1024;

    if(sr)
        *sr = sample_rate;

    if(chnum)
        *chnum = chan_config;

    if(sample)
        *sample = samples;

    return size;
}



AACExtractor::AACExtractor(const sp<DataSource> &source)
    : mDataSource(source),
      mInitCheck(NO_INIT) {

    int size;
    int sampleRate;
    int channelCount;
    int sampleNum;
    off64_t pos = 0;
    off64_t post_id3_pos = 0;

    Resync(source, &pos, &post_id3_pos); 
    mFirstFramePos = post_id3_pos;
    
    size = aac_parse_header(source, mFirstFramePos, &sampleRate, &channelCount, &sampleNum);
	
    mMeta = new MetaData;
    mMeta->setCString(
            kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
    mMeta->setInt32(kKeyChannelCount, channelCount);
    mMeta->setInt32(kKeySampleRate, sampleRate);
	
    LOGI("sampleRate %d,channelCount %d",sampleRate,channelCount);
	
    //estimate bit rate
    int totalSampleNum=0;
    int totalSize = mFirstFramePos;
    for(int i=0;i<FRAME_NUM_BITRATE;i++)
    {
    	size = aac_parse_header(source, totalSize, NULL, &channelCount, &sampleNum);
	if(size>0){
		totalSampleNum += sampleNum;
		totalSize += size;
	}else{
		break;
	}
    }
	
    mBitrate = (int)(totalSize*8.0/((float)totalSampleNum/sampleRate));
    LOGI("estimated Bitrate %d",mBitrate);

    off_t streamSize;
    if (mDataSource->getSize(&streamSize) == OK) {
        mMeta->setInt64(kKeyDuration, ((int64_t)streamSize - mFirstFramePos)*1000000*8/mBitrate);
        LOGI("streamSize %d,duration %lld us",
              streamSize,((int64_t)streamSize - mFirstFramePos)*1000000*8/mBitrate);
    }

    mInitCheck = OK;
}

AACExtractor::~AACExtractor() {
}

sp<MetaData> AACExtractor::getMetaData() {
    sp<MetaData> meta = new MetaData;

    if (mInitCheck != OK) {
        return meta;
    }

    meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);

    return meta;
}

size_t AACExtractor::countTracks() {
    return mInitCheck == OK ? 1 : 0;
}

sp<MediaSource> AACExtractor::getTrack(size_t index) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

    return new AACSource(mDataSource, mMeta, mFirstFramePos, mBitrate);
}

sp<MetaData> AACExtractor::getTrackMetaData(size_t index, uint32_t flags) {
    if (mInitCheck != OK || index != 0) {
        return NULL;
    }

    return mMeta;
}

////////////////////////////////////////////////////////////////////////////////

AACSource::AACSource(
        const sp<DataSource> &source, const sp<MetaData> &meta, off64_t first_frame_pos,int bitrate)
    : mDataSource(source),
      mMeta(meta),
      mOffset(first_frame_pos),
      mCurrentTimeUs(0),
      mStarted(false),
      mGroup(NULL),
      mBitrate(bitrate){
}

AACSource::~AACSource() {
    if (mStarted) {
        stop();
    }
}

status_t AACSource::start(MetaData *params) {
    CHECK(!mStarted);

    mOffset = 0;
    mCurrentTimeUs = 0;
    mGroup = new MediaBufferGroup;
    mGroup->add_buffer(new MediaBuffer(4096*2));
    mStarted = true;

    return OK;
}

status_t AACSource::stop() {
    CHECK(mStarted);

    delete mGroup;
    mGroup = NULL;

    mStarted = false;
    return OK;
}

sp<MetaData> AACSource::getFormat() {
    return mMeta;
}

status_t AACSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    *out = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        mCurrentTimeUs = seekTimeUs;
        mOffset = mBitrate*mCurrentTimeUs/1000000/8;
	 LOGI("seek mCurrentTimeUs %lld,mOffset %d",mCurrentTimeUs,mOffset);
    }

    int size;
    int count = 0;
    for(int i=0; i<SYNC_COUNT;i++){//try 1000 times
              count++;
		size = aac_parse_header(mDataSource,mOffset,NULL,NULL,NULL);
		if(size<0){
			mOffset++;
		}else{
			break;
		}
    }
	
    size_t frameSize = 0;
    ssize_t n = 0;
    uint8_t header;
    n = mDataSource->readAt(mOffset, &header, 1);

    if (n < 1) {
        return ERROR_END_OF_STREAM;
    }
	
    if(count>=SYNC_COUNT){
	return ERROR_MALFORMED;
    }

    MediaBuffer *buffer;
    status_t err = mGroup->acquire_buffer(&buffer);
    if (err != OK) {
        return err;
    }

    int framesize0 = size;	
    frameSize = size;	
    n = mDataSource->readAt(mOffset, buffer->data(), frameSize);
	
    if (n != (ssize_t)frameSize) {
        buffer->release();
        buffer = NULL;

        return ERROR_IO;
    }

    buffer->set_range(0, frameSize);
    buffer->meta_data()->setInt64(kKeyTime, mCurrentTimeUs);
    buffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);

    mOffset += size;
    mCurrentTimeUs = (int64_t)mOffset*8*1000000/mBitrate;
//for latm test	
/*
    size = aac_parse_header(mDataSource,mOffset,NULL,NULL,NULL);
    frameSize = size;	
    LOGI("framesize0 %d,%d",framesize0,framesize0+frameSize-7);
    n = mDataSource->readAt(mOffset+7, (void *)((uint8_t *)buffer->data()+framesize0), frameSize-7);
	
    if (n != (ssize_t)frameSize-7) {
        buffer->release();
        buffer = NULL;

        return ERROR_IO;
    }
    buffer->set_range(0, framesize0+frameSize-7);	
    mOffset += size;
    mCurrentTimeUs = (int64_t)mOffset*8*1000000/mBitrate;
*/	
    //LOGI("mCurrentTimeUs %lld,mOffset %d",mCurrentTimeUs,mOffset);
	 
    *out = buffer;

    return OK;
}

////////////////////////////////////////////////////////////////////////////////

bool SniffAAC(
        const sp<DataSource> &source, String8 *mimeType, float *confidence,
        sp<AMessage> *) {
    off64_t pos = 0;
    off64_t post_id3_pos = 0;

    Resync(source, &pos, &post_id3_pos);
    
    int size1 = aac_parse_header(source,post_id3_pos,NULL,NULL,NULL);
    if(size1<=0)
	return false;
    int size2 = aac_parse_header(source,(size_t)size1+post_id3_pos,NULL,NULL,NULL);
    if(size2<=0)
	return false;

    *mimeType = MEDIA_MIMETYPE_AUDIO_AAC;
    *confidence = 0.2;
    return true;
}

}  // namespace android
