#define LOG_NDEBUG	0
#define LOG_TAG "MxdCMMBExtractor"

#include <utils/Log.h>


#include <arpa/inet.h>

#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "MxdCMMBExtractor.h"

#include "../include/ESDS.h"
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>


#include <utils/Timers.h>

#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>


namespace android {


#define VIDEO_TRACK_SEQ (0)
#define AUDIO_TRACK_SEQ (1)

#define MAX_VIDEO_UNIT (0xd800)
#define MAX_AUDIO_UNIT (1024+512)

#define TIMESTAMP_SCALE (22500)
#define VIDEO_WIDTH (320)
#define VIDEO_HEIGHT (240)
#define AUDIO_SAMPLERATE (24000)
#define AUDIO_NUM_CHANNELS (2)



	int   getAudioData(void *ph, void **p, unsigned long *timeStamp);
	int   getVideoData(void *ph, void **p, unsigned long *timeStamp);
	int   socketCacheByteNum(void *ph);
	void* connectCmmbServer(int port);
	void  teardownCmmbServer(void *ph);

	enum {
		MXD_ERROR_BASE = -5000,
		MXD_ERROR_UNKNOWN     = MXD_ERROR_BASE,    
		MXD_MXD_MXD_MXD_ERROR_UNSUPPORTED_TYPE     = MXD_ERROR_BASE - 1,   
		MXD_ERROR_NO_DATA     = MXD_ERROR_BASE - 2,
		MXD_ERROR_INVALIDDATA = MXD_ERROR_BASE - 3,
	};



	class MxdCMMBSource : public MediaSource {
	public:
		MxdCMMBSource(
			const sp<MetaData> &format,
			int32_t timeScale,
			uint8_t trackType,
			void *AVSock);

		virtual status_t start(MetaData *params = NULL);
		virtual status_t stop();    

		virtual sp<MetaData> getFormat();

		virtual status_t read(
			MediaBuffer **buffer, const ReadOptions *options = NULL);


	protected:
		virtual ~MxdCMMBSource();

	private:
		Mutex mLock;

		sp<MetaData> mFormat;

		int32_t mTimescale;


		bool mStarted;

		bool mWantsNALFragments;

		bool mStop;
		uint8_t mTrackType;

		MediaBufferGroup *mGroup;
		MediaBuffer *mBuffer;    

		void *mAVSock;
		int mTimeoutCount;
		unsigned long mLastFrameTs;

		MxdCMMBSource(const MxdCMMBSource &);
		MxdCMMBSource &operator=(const MxdCMMBSource &);
	};

	MxdCMMBExtractor::Track::Track(uint32_t trackType) 
		: meta(NULL), timestampscale(TIMESTAMP_SCALE)
	{
		//includes_expensive_metadata = false;
		meta = new MetaData;    
		if (trackType == VIDEO_TRACK_SEQ) {
			meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
		}else if (trackType == AUDIO_TRACK_SEQ) {
			meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
		}

		//mTrackType = trackType;
	}

	MxdCMMBExtractor::Track::~Track() {     
	}

	MxdCMMBExtractor::MxdCMMBExtractor(const char* filename)
		: mAVKeyInfo(false),
		mVideoTrack(NULL),
		mAudioTrack(NULL),
		mCmmbMetaData(new MetaData){
			strcpy(mFileName,filename);
			LOGI("MxdCMMBExtractor::MxdCMMBExtractor  success");

	}

	//init static member
	int MxdCMMBExtractor::mIsStreamDone = 0;
	void* MxdCMMBExtractor::gAudioClient = NULL;
	void* MxdCMMBExtractor::gVideoClient = NULL;

	MxdCMMBExtractor::~MxdCMMBExtractor() {


		if (mVideoTrack) {
			delete mVideoTrack;
		}
		if (mAudioTrack) {
			delete mAudioTrack;
		}
		deInit();
		LOGI("MxdCMMBExtractor::~MxdCMMBExtractor  success");

	}

	bool MxdCMMBExtractor::bufferingUpdateOk()
	{
		if(gAudioClient == NULL || gVideoClient == NULL )
		{
			LOGI("MxdCMMBExtractor bufferingUpdateOk --- 1");
			return false;
		}
		int a = socketCacheByteNum(gAudioClient);
		int v = socketCacheByteNum(gVideoClient);
		if(a>5000 && v > 20000)
		{
			LOGI("MxdCMMBExtractor bufferingUpdateOk --- 2 ok");
			return true;
		}
		LOGI("MxdCMMBExtractor bufferingUpdateOk --- 3");
		return false;
	}


	status_t MxdCMMBExtractor::init() {

		LOGI("init  begin ....");
		mIsStreamDone = 0;
		mVideoStream = NULL;
		mAudioStream = NULL;
		gVideoClient = NULL;
		gAudioClient = NULL;

		char vPortTmp[64] = {0};
		char aPortTmp[64] = {0};

		char *str1 = mFileName;
		char *str2 = str1+strlen("cmmb://mxd:");
		char *str3 = strstr(str2,":");
		char *str4 = strstr(str1, "/MobileTV");

		unsigned int len1 = (unsigned int)str3 - (unsigned int)str2;
		unsigned int len2 = (unsigned int)str4 - (unsigned int)str3;
		memcpy(vPortTmp,str2,len1);
		memcpy(aPortTmp,str3+1,len2-1);
		
		int vPort = atoi(vPortTmp);
		int aPort = atoi(aPortTmp);

		LOGI("vPort : %d  aPort : %d",vPort,aPort);

		mVideoStream = connectCmmbServer(vPort);
		gVideoClient = mVideoStream;
		if(mVideoStream == NULL) {
			LOGI("connet video server failed");
			return ERROR_NOT_CONNECTED;
		}


		mAudioStream = connectCmmbServer(aPort);
		gAudioClient = mAudioStream;
		if(mAudioStream == NULL) {
			LOGI("connet audio server failed");
			teardownCmmbServer(mVideoStream);
			mVideoStream = NULL;
			return ERROR_NOT_CONNECTED;
		}


		return OK;
	}


	status_t MxdCMMBExtractor::deInit() {

		LOGI("MxdCMMBExtractor::deInit() begin");
		if(mAudioStream)
		{
			teardownCmmbServer(mAudioStream);
			mAudioStream = NULL;
			gAudioClient = NULL;

		}
		if(mVideoStream)
		{
			teardownCmmbServer(mVideoStream);
			mVideoStream = NULL;
			gVideoClient = NULL;
		}
		return OK;
	}
	sp<MetaData> MxdCMMBExtractor::getMetaData() {
		status_t err;
		if ((err = prebuiltAVParam()) != OK) {
			return new MetaData;
		}

		return mCmmbMetaData;
	}

	size_t MxdCMMBExtractor::countTracks() {
		status_t err;
		if ((err = prebuiltAVParam()) != OK) {
			return 0;
		}

		size_t n = 0;
		if (mVideoTrack) {
			++n;
		}

		if (mAudioTrack) {
			++n;
		}

		return n;
	}

	sp<MetaData> MxdCMMBExtractor::getTrackMetaData(
		size_t index, uint32_t flags) {
			status_t err;
			if ((err = prebuiltAVParam()) != OK) {
				return NULL;
			}

			Track *track;


			if (index == 0) 
			{
				track = mVideoTrack;
			}else if (index == 1) {
				track = mAudioTrack;
			}else {
				return NULL;
			}


			if (track == NULL) {
				return NULL;
			}

			/*
			if ((flags & kIncludeExtensiveMetaData)
				&& !track->includes_expensive_metadata) {
					track->includes_expensive_metadata = true;


			}
			*/
			return track->meta;
	}

	status_t MxdCMMBExtractor::prebuiltAVParam() {
		if (mAVKeyInfo) {
			return OK;
		}

		status_t err; 

		if ((err = init()) != OK) {
			return err;
		}

		{

			{
				
				mVideoTrack = new Track(VIDEO_TRACK_SEQ);
				//LOGV("CMMB has video");
				mVideoTrack->meta->setInt32(kKeyWidth, VIDEO_WIDTH);
				mVideoTrack->meta->setInt32(kKeyHeight, VIDEO_HEIGHT);
				mVideoTrack->meta->setInt32(kKeyMaxInputSize, MAX_VIDEO_UNIT);
				mAVKeyInfo = true;
			}

			{
				
				mAudioTrack = new Track(AUDIO_TRACK_SEQ);
				//LOGV("CMMB has audio");
				mAudioTrack->meta->setInt32(kKeyChannelCount, AUDIO_NUM_CHANNELS);
				mAudioTrack->meta->setInt32(kKeySampleRate, AUDIO_SAMPLERATE);
				mAudioTrack->meta->setInt32(kKeyMaxInputSize, MAX_AUDIO_UNIT);


				uint8_t buffer[256] =
				{3,128,128,128,34,0,0,0,4,128,128,128,20,64,21,0,6,0,0,0,0,0,0,0,187,128,5,128,128,128,2,19,16,6,128,128,128,1,2};
				mAudioTrack->meta->setData(
					kKeyESDS, kTypeESDS, &buffer[0], 39);

				status_t err = setMP4AParam(&buffer[0], 39);

				if (err != OK) {
					return err;
				}
				mAVKeyInfo = true;
			}
		}

		if (mAVKeyInfo != true) {
			return MXD_ERROR_INVALIDDATA;
		}
		return OK;

	}

	sp<MediaSource> MxdCMMBExtractor::getTrack(size_t index) {
		status_t err;
		if ((err = prebuiltAVParam()) != OK) {
			return NULL;
		}

		MxdCMMBSource *cs = NULL;

		{
			if (index == VIDEO_TRACK_SEQ) {
				cs = new MxdCMMBSource(mVideoTrack->meta, mVideoTrack->timestampscale, index,  mVideoStream);
				if(cs) mVideoStream = NULL;
			}else if (index == AUDIO_TRACK_SEQ) {
				cs = new MxdCMMBSource(mAudioTrack->meta, mAudioTrack->timestampscale, index,  mAudioStream);
				if(cs) mAudioStream = NULL;
			}else {
				return NULL;
			}
			return cs;
		}
	}

	status_t MxdCMMBExtractor::setMP4AParam(
		const void *esds_data, size_t esds_size) {
			ESDS esds(esds_data, esds_size);

			uint8_t objectTypeIndication;
			if (esds.getObjectTypeIndication(&objectTypeIndication) != OK) {
				return ERROR_MALFORMED;
			}

			if (objectTypeIndication == 0xe1) {
				mAudioTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_QCELP);
				return OK;
			}

			const uint8_t *csd;
			size_t csd_size;
			if (esds.getCodecSpecificInfo(
				(const void **)&csd, &csd_size) != OK) {
					return ERROR_MALFORMED;
			}


			if (csd_size < 2) {
				return ERROR_MALFORMED;
			}

			uint32_t objectType = csd[0] >> 3;

			if (objectType == 31) {
				return ERROR_UNSUPPORTED;
			}

			uint32_t freqIndex = (csd[0] & 7) << 1 | (csd[1] >> 7);
			int32_t sampleRate = 0;
			int32_t numChannels = 0;
			if (freqIndex == 15) {
				if (csd_size < 5) {
					return ERROR_MALFORMED;
				}

				sampleRate = (csd[1] & 0x7f) << 17
					| csd[2] << 9
					| csd[3] << 1
					| (csd[4] >> 7);

				numChannels = (csd[4] >> 3) & 15;
			} else {
				static uint32_t kSamplingRate[] = {
					96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
					16000, 12000, 11025, 8000, 7350
				};

				if (freqIndex == 13 || freqIndex == 14) {
					return ERROR_MALFORMED;
				}

				sampleRate = kSamplingRate[freqIndex];
				numChannels = (csd[1] >> 3) & 15;
			}

			if (numChannels == 0) {
				return ERROR_UNSUPPORTED;
			}

			int32_t prevSampleRate;
			CHECK(mAudioTrack->meta->findInt32(kKeySampleRate, &prevSampleRate));


			if (prevSampleRate != sampleRate) {
				//LOGV("mpeg4 audio sample rate different from previous setting");
			}

			mAudioTrack->meta->setInt32(kKeySampleRate, 48000);

			int32_t prevChannelCount;
			CHECK(mAudioTrack->meta->findInt32(kKeyChannelCount, &prevChannelCount));

			if (prevChannelCount != numChannels) {
				//LOGV("mpeg4 audio channel count different from previous setting.");
			}

			mAudioTrack->meta->setInt32(kKeyChannelCount, numChannels);


			return OK;
	}

	////////////////////////////////////////////////////////////////////////////////
	MxdCMMBSource::MxdCMMBSource(
		const sp<MetaData> &format,
		int32_t timeScale,
		uint8_t trackType,
		void *AVSock)
		: mFormat(format),
		mTimescale(timeScale),
		mStarted(false),
		mWantsNALFragments(false),
		mStop(false),
		mTrackType(trackType),
		mGroup(NULL),
		mTimeoutCount(0), 
		mBuffer(NULL),
		mLastFrameTs(0),
		mAVSock(AVSock)
	{
		const char *mime;
		bool success = mFormat->findCString(kKeyMIMEType, &mime);
		CHECK(success);  

	}


	MxdCMMBSource::~MxdCMMBSource() {
		LOGI("MxdCMMBSource::~MxdCMMBSource() invoke");
		if (mStarted) {
			stop();
		}
	}

	status_t MxdCMMBSource::start(MetaData *params) {
		Mutex::Autolock autoLock(mLock);
		LOGI("MxdCMMBSource::start() invoke");
		CHECK(!mStarted);    

		int32_t val;
		if (params && params->findInt32(kKeyWantsNALFragments, &val)
			&& val != 0) {
				mWantsNALFragments = true;
		} else {
			mWantsNALFragments = false;
		}
		mGroup = new MediaBufferGroup;

		int32_t max_size;
		CHECK(mFormat->findInt32(kKeyMaxInputSize, &max_size));
		mGroup->add_buffer(new MediaBuffer(max_size));
		return OK;
	}

	status_t MxdCMMBSource::stop() {
		Mutex::Autolock autoLock(mLock);
		LOGI("MxdCMMBSource::stop() invoke");
		mStop = true;
		mStarted = false;
		if (mGroup) {
			delete mGroup;
			mGroup = NULL;
		}
		if (mAVSock) {            
			teardownCmmbServer(mAVSock);
			mAVSock = NULL;
			if(mTrackType == VIDEO_TRACK_SEQ)
			{
				MxdCMMBExtractor::gVideoClient = NULL;
			}
			else
			{
				MxdCMMBExtractor::gAudioClient = NULL;
			}
		}
		return OK;
	}

	sp<MetaData> MxdCMMBSource::getFormat() {
		Mutex::Autolock autoLock(mLock);
		return mFormat;
	}

	// read a decodable unit
	status_t MxdCMMBSource::read(
		MediaBuffer **out, const ReadOptions *options) {
			Mutex::Autolock autoLock(mLock);
			//CHECK(mStarted);

			*out = NULL;

			status_t err;
			int64_t seekTimeUs;

			ReadOptions::SeekMode mode;
			if (options && options->getSeekTo(&seekTimeUs, &mode)) {

				//not support seek
			}

			if (mBuffer == NULL) {  
				err = mGroup->acquire_buffer(&mBuffer);
				if (err != OK) {
					CHECK_EQ(mBuffer, NULL);
					return err;
				}
			}

			if (mBuffer) {
				uint8_t *dstData = (uint8_t *)mBuffer->data();        
				void *pBuf;
				uint8_t *pucData;
				uint32_t temp;
				int64_t timeStamp = 0;
				size_t size;

				void *pdata;
				unsigned long ts = 0;
				int len;


				if(MxdCMMBExtractor::mIsStreamDone == 1)
				{
					mBuffer->release();
					mBuffer = NULL;
					return ERROR_END_OF_STREAM;
				}

				if(mTrackType == AUDIO_TRACK_SEQ ) {

					int ll = 0;
					while(ll < 100 && socketCacheByteNum(mAVSock) < 5000)
					{
						if(true == mStop)
						{
							mBuffer->release();
							mBuffer = NULL;
							//return ERROR_CONNECTION_LOST;
							return ERROR_TIMEOUT;
						}

						if(MxdCMMBExtractor::mIsStreamDone == 1)
						{
							mBuffer->release();
							mBuffer = NULL;
							return ERROR_END_OF_STREAM;
						}
						ll++; usleep(10000);
					}
					if(ll >= 100) 
					{
						len = 0;
					}
					else
					{
						len = getAudioData(mAVSock, &pdata, &ts);
					}
				}
				else
				{
					len = getVideoData(mAVSock, &pdata, &ts);
				}
				timeStamp = (int64_t)ts;
				pucData = (uint8_t *)pdata;

				if (len < 0)
				{           
					MxdCMMBExtractor::mIsStreamDone = 1;
					mBuffer->release();
					mBuffer = NULL;

					return ERROR_END_OF_STREAM;
				}
				if(len == 0 ) 
				{
					if(mTrackType == VIDEO_TRACK_SEQ)
					{
						//fill a h264 FILL_FRAME
						size = 128;
						dstData[0] = 0x0c;
						for(int i=1;i<128;i++)
						{
							dstData[i] = 0xff;
						}
						timeStamp = (int64_t)mLastFrameTs;
						mBuffer->set_range(0, size);
						mBuffer->meta_data()->clear();
						mBuffer->meta_data()->setInt64(kKeyTime, (timeStamp * 1000000) / mTimescale);
						*out = mBuffer;
						mBuffer = NULL;
						return OK;
					}
					mTimeoutCount++;
					/*
					if(mTimeoutCount >5) 
					{
					LOGI("%d ===================================mTimeoutCount > 5", mTrackType);
					MxdCMMBExtractor::mIsStreamDone = 1;
					mBuffer->release();
					mBuffer = NULL;
					return ERROR_END_OF_STREAM;
					}
					*/

					mBuffer->release();
					mBuffer = NULL;
					return ERROR_TIMEOUT;
					//return ERROR_CONNECTION_LOST;
				}

				mLastFrameTs = ts;

				mTimeoutCount = 0;

				size = (size_t)len;

				if(mStarted == false)
					mStarted = true;        

				memcpy(dstData, pucData, size);
				mBuffer->set_range(0, size);
				mBuffer->meta_data()->clear();
				mBuffer->meta_data()->setInt64(kKeyTime, (timeStamp * 1000000) / mTimescale);
				*out = mBuffer;
				mBuffer = NULL;
				return OK;
			}

			return ERROR_IO;
	}



}  // namespace android




