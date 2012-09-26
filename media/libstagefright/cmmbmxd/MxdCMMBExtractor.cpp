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
#define RESERVED_SPACE (20*1024)


	int   getAudioData(void *ph, void **p, unsigned long *timeStamp);
	int   getVideoData(void *ph, void **p, unsigned long *timeStamp);
	int   socketCacheByteNum(void *ph);
	void* connectCmmbServer(int port);
	void  teardownCmmbServer(void *ph);

	void *mem_malloc(void *p, unsigned long size);
	void mem_free(void *p);

	extern int g_MxdSocketRecvBufSize;

	static int g_VideoSocketRecvBufferSize = 0;


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
		unsigned char* mLastVideoFrame;
		int mLastVideoFrameSize;
//add922		
	  int64_t lastTimeVideo ;
	   int64_t baseTimeVideo ;
	   int64_t lastTimeAudio ;
	   int64_t baseTimeAudio ;
	   int64_t maxTimeVideo ;
	   int64_t maxTimeAudio ;
		   int countVideo ;
		   int 	countAudio;
	   int timeStampError ;
//add922	
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
			LOGI("MxdCMMBExtractor::MxdCMMBExtractor  success ---- Ver.1.2");

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
		g_VideoSocketRecvBufferSize = g_MxdSocketRecvBufSize == 0? 200*1024 : g_MxdSocketRecvBufSize;
		LOGI("recv buffer size = %d",g_VideoSocketRecvBufferSize);
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
		mLastVideoFrame(NULL),
		mLastVideoFrameSize(0),
		mAVSock(AVSock)
	{
		const char *mime;
		bool success = mFormat->findCString(kKeyMIMEType, &mime);
		CHECK(success);  
		
 lastTimeVideo=0;
 baseTimeVideo=0;
	 lastTimeAudio=0;
	 baseTimeAudio=0;
	 maxTimeVideo=0;
 maxTimeAudio=0;	
	 countVideo=0;
	 countAudio=0;
	 timeStampError=0;
	}


	MxdCMMBSource::~MxdCMMBSource() {
		LOGI("MxdCMMBSource::~MxdCMMBSource() invoke");
		if(mLastVideoFrame != NULL)
		{
			mem_free(mLastVideoFrame);
			mLastVideoFrame = NULL;
		}
		mLastVideoFrameSize = 0;

		stop();
		
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

		mStarted = true;
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


	static const unsigned char gDUMMYAAC[171] = 
	{
		0x21,	0x19,	0x53,	0xED,	0x84,	0xFE,	0x10,	0x18,	0x84,	0x1A,	0x1C,	0x05,	0x0A,	0x5A,	0xB6,	0x01,
		0x56,	0x28,	0x99,	0x79,	0x20,	0x31,	0xAA,	0xFC,	0x3A,	0xAB,	0xF2,	0xDD,	0x39,	0xA9,	0x9E,	0xD6,
		0xD8,	0xA5,	0x92,	0x21,	0x70,	0x44,	0xA0,	0x02,	0x40,	0xB9,	0x70,	0x00,	0x0A,	0xC8,	0x0A,	0xDC,
		0x71,	0x9A,	0x70,	0x81,	0xB0,	0x82,	0x85,	0x41,	0x07,	0xB0,	0x00,	0x50,	0x51,	0x6A,	0xFB,	0xD0,
		0x24,	0x00,	0x2E,	0xDE,	0x03,	0xBB,	0xC0,	0x04,	0x20,	0x00,	0x00,	0x00,	0x00,	0x00,	0xE1,	0x9A,
		0x07,	0x78,	0x0C,	0x01,	0xBD,	0x18,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,
		0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,
		0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,
		0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,
		0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,
		0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x00,	0x03,	0xCF	
	};



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

				
				int hold = g_VideoSocketRecvBufferSize>RESERVED_SPACE ? (g_VideoSocketRecvBufferSize-RESERVED_SPACE) : 200*1024;
				if(socketCacheByteNum(MxdCMMBExtractor::gVideoClient) >hold  )
				{
					MxdCMMBExtractor::mIsStreamDone = 1;
					mBuffer->release();
					mBuffer = NULL;

					return ERROR_END_OF_STREAM;
				}

				if(mTrackType == AUDIO_TRACK_SEQ ) {

					if(socketCacheByteNum(mAVSock) < 2000)
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
					if(socketCacheByteNum(mAVSock) < 8)
					{
						len = 0;
					}
					else
					{
						len = getVideoData(mAVSock, &pdata, &ts);
					}
				}
			//	timeStamp = (int64_t)ts;
			//add922
			if(len<=0)
				{
					timeStamp = (int64_t)ts;
				}
		else{
					if(mTrackType == AUDIO_TRACK_SEQ){
			
						timeStamp = (int64_t)ts;
						//	LOGD(" liwk=========a==ts=%x=timeStamp=%lx-baseTimeAudio=%lx--lastTimeAudio=%lx=======",ts,timeStamp,baseTimeAudio,lastTimeAudio);
							if( (timeStamp + baseTimeAudio) < lastTimeAudio ){
							maxTimeAudio = lastTimeAudio;
			
							LOGD(" liwk===============maxTimeAudio========%lx", maxTimeAudio);
						}
						if((timeStamp + baseTimeAudio)  < maxTimeAudio){
							countAudio++;
							LOGI(" liwk===============countAudio========%d", countAudio);
						}
						if(countAudio >= 3){
							 baseTimeAudio = maxTimeAudio - timeStamp ;
							baseTimeVideo = baseTimeAudio;
							countAudio = 0;
							LOGI(" liwk===============baseTimeAudio========%lx", baseTimeAudio);
						}
						timeStamp = timeStamp + baseTimeAudio;	
						lastTimeAudio = timeStamp;				
										
					}
					else{
				 		
						timeStamp = (int64_t)ts;	
						if( (timeStamp + baseTimeVideo) < lastTimeVideo ){
							maxTimeVideo = lastTimeVideo;
			
							LOGD(" liwk===============maxTimeVideo========%lx", maxTimeVideo);
						}
						if((timeStamp + baseTimeVideo)  < maxTimeVideo){
							countVideo++;
							LOGI(" liwk===============countVideo========%d", countVideo);
						}
						if(countVideo >= 3){
							baseTimeVideo = maxTimeVideo - timeStamp ;
							baseTimeAudio = baseTimeVideo;
							countVideo = 0;
							LOGI(" liwk===============baseTimeVideo========%lx", baseTimeVideo);
						}
						timeStamp = timeStamp + baseTimeVideo;	
						lastTimeVideo = timeStamp;
						//			LOGD(" liwk========v==ts=%x=timeStamp=%lx--baseTimeVideo=%lx--lastTimeVideo=%lx=======",ts,timeStamp,baseTimeVideo,lastTimeVideo);		
					}
		 		}
			//add922   
			
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
						if(mLastVideoFrameSize != 0 && mLastVideoFrame != NULL)
						{
							size = mLastVideoFrameSize;
							memcpy(dstData, mLastVideoFrame, size);
						}
						else
						{

							size = 128;
							dstData[0] = 0x0c;
							for(int i=1;i<127;i++)
							{
								dstData[i] = 0xff;
							}
							dstData[127] = 0x80;
						}
						timeStamp = (int64_t)mLastFrameTs;
						//mLastFrameTs = timeStamp;
						mBuffer->set_range(0, size);
						mBuffer->meta_data()->clear();
						mBuffer->meta_data()->setInt64(kKeyTime, (timeStamp * 1000000) / mTimescale);
						*out = mBuffer;
						mBuffer = NULL;
						return OK;
					}
					else
					{
						//fill a aac FILL_FRAME
						size = 171;

						for(int i=0;i<171;i++)
						{
							dstData[i] = gDUMMYAAC[i];
						}
						timeStamp = (int64_t)mLastFrameTs;
						//mLastFrameTs = timeStamp;
						mBuffer->set_range(0, size);
						mBuffer->meta_data()->clear();
						mBuffer->meta_data()->setInt64(kKeyTime, (timeStamp * 1000000) / mTimescale);
						*out = mBuffer;
						mBuffer = NULL;
						return OK;

					}

				}
/*
				if ((int64_t)ts -(int64_t)mLastFrameTs < -0xfffffff)
				{           
					MxdCMMBExtractor::mIsStreamDone = 1;
					mBuffer->release();
					mBuffer = NULL;

					return ERROR_END_OF_STREAM;
				}

				mLastFrameTs = ts;
				*/
				mLastFrameTs = timeStamp;
//add922
/*
LOGI("**timeVideo-TimeAudio**=%lld", (lastTimeVideo-lastTimeAudio));
	 //check timestamp for synchronization, |vedioTime-audiotime|>10s
	if(((lastTimeVideo-lastTimeAudio)>22500*10) || ((lastTimeAudio-lastTimeVideo)>22500*10))
	{	
		timeStampError++;
		LOGI("==timeStampError== %d", timeStampError);
	}
	else
	{
		timeStampError=0;
	}

	if (timeStampError >100)
	{
		timeStampError = 0;
		LOGI("==timeStampError > 100=="); 
					MxdCMMBExtractor::mIsStreamDone = 1;
					mBuffer->release();
					mBuffer = NULL;

					return ERROR_END_OF_STREAM;
	}*/
//add922
				mTimeoutCount = 0;

				size = (size_t)len;
  
#if 0
				if(mTrackType == VIDEO_TRACK_SEQ &&  (pucData[0] & 0x1f) == 5   )
				{
					mLastVideoFrame = (unsigned char*)mem_malloc(mLastVideoFrame,size);
					memcpy(mLastVideoFrame,pucData,size);
					mLastVideoFrameSize = size;
				}
#endif
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




