
#define LOG_TAG "INNO/CMMBExtractor/CMMBExtractor"
#define LOG_NDEBUG	0
#include <utils/Log.h>

#include "../include/CMMBExtractor.h"

#include <arpa/inet.h>

#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>


#include <media/stagefright/DataSource.h>
#include <media/stagefright/CmmbUriSource.h>
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

void *sock_connectAVServer(int port);
void sock_disconnectAVServer(void *ph);
int sock_sendSIDtoAVServer(void *ph, int serviceid);
int sock_readAudioData(void *ph, void **p, 
    unsigned long *pid, 
    unsigned long *timeStamp, 
    unsigned char *unittype, 
    unsigned short *rate,
    unsigned short *flag);
int sock_readVideoData(void *ph, void **p, 
    unsigned long *pid, 
    unsigned long *timeStamp, 
    unsigned short *flag);
int sock_checkReadble(void *ph);

//#define CMMB_SET_FREQ_BYSELF

#ifdef CMMB_SET_FREQ_BYSELF
	#define CMMB_LOGICAL_CHANNEL_ID 0
#else
	#define CMMB_LOGICAL_CHANNEL_ID 2
#endif



class ProcessState;

#ifdef DUMP_MFS_FILE
class FileDumper : public DataAvailableListenerBase
{
public:
	FileDumper(int fd = -1) : mFd(fd) { }

	virtual void onMfsDataAvailable(const void* buffer, size_t size)
	{
		LOGD("onMfsDataAvailable, size(0x%x)", size);
		uint8_t* p = (uint8_t*)buffer;
		dump(p, size);
	}

private:
	int	mFd;
	void dump(void *buffer, size_t len)
	{
		uint8_t *p = (uint8_t*)buffer;
		
		if (mFd != -1) {
			write(mFd, buffer, len);
		}else {	
			for (int i=0; i<32; i++) {
				printf("%02x ", p[i]);
				if ( (i+1) % 16 == 0)
					printf("\n");
			}
		}
	}
};
#endif

class CMMBSource : public MediaSource {
public:
    CMMBSource(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource,
        int32_t timeScale,
        uint8_t trackType,
        const sp<CMMBExtractor> &extractor,
        void *AVSock);

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();    

    virtual sp<MetaData> getFormat();
  
    virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

    //status_t update();

//    virtual bool cmmbDataResumed();
    int connectToDataServer();

protected:
    virtual ~CMMBSource();

private:
    Mutex mLock;

    sp<MetaData> mFormat;
    sp<DataSource> mDataSource;

    int32_t mTimescale;
     
    bool mIsAVC;

    bool mStarted;

    //bellow is just for AVC video
    bool mSPSInitialized;
    bool mPPSInitialized;

    bool mWantsNALFragments;

    bool mStop;
    uint8_t mTrackType;

    MediaBufferGroup *mGroup;
    MediaBuffer *mBuffer;    

    sp<CMMBExtractor> mExtractor;
    void *mAVSock;
    int mTimeoutCount;

///////////////lg add 2012-06-13////////
	static  int64_t lastTimeVideo ;
	static  int64_t baseTimeVideo ;
	static  int64_t lastTimeAudio ;
	static  int64_t baseTimeAudio ;
	static  int64_t maxTimeVideo ;
	static  int countVideo ;
	static  int timeStampError ;//lg add 2012-09-06
/////////////////////////////////////////

 //   LOGI("class CMMBSource : public MediaSource ");
    CMMBSource(const CMMBSource &);
    CMMBSource &operator=(const CMMBSource &);
};


///////////////lg add 2012-06-13////////
	int64_t CMMBSource::lastTimeVideo=0;
	int64_t CMMBSource::baseTimeVideo=0;
	int64_t CMMBSource::lastTimeAudio=0;
	int64_t CMMBSource::baseTimeAudio=0;
	int64_t CMMBSource::maxTimeVideo=0;
	int CMMBSource::countVideo=0;
	int CMMBSource::timeStampError=0;//lg add 2012-09-06
/////////////////////////////////////////



CMMBExtractor::Track::Track(uint32_t trackType) 
    : meta(NULL), timescale(CMMB_TIME_SCALE), includes_expensive_metadata(false), skipTrack(false)
{
    meta = new MetaData;    
    if (trackType == VideoTrack) {
        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
    }else if (trackType == AudioTrack) {
        meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
    }else if (trackType == DraAudioTrack) {
    	meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_DRA);
    }

    meta->setInt32(kKeyIsCmmbData, true);
    mTrackType = trackType;
    LOGV("Initialize CMMBExtractor::Track(%s) successful!\n",(trackType == VideoTrack)?"Video":(trackType == AudioTrack)?"Audio":"Data");
}

CMMBExtractor::Track::~Track() {     
}

CMMBExtractor::CMMBExtractor(const sp<DataSource> &source, bool draType)
    : mDataSource(source),
      mHaveMetadata(false),
      mHasVideo(false),
      mVideoTrack(NULL),
      mAudioTrack(NULL),
      mFileMetaData(new MetaData),
      mDraType(draType) {
    LOGI("Create CMMBExtractor sucessful!, %s channel \n", (mDraType ? "DRA" : "Video") );
}

CMMBExtractor::~CMMBExtractor() {


	if (mVideoTrack) {
        delete mVideoTrack;
    }
    if (mAudioTrack) {
        delete mAudioTrack;
    }
    stop();
    LOGI("===============~CMMBExtractor() is called");

}


status_t CMMBExtractor::initialize() {


	//cmmb system
//    ProcessState::self()->startThreadPool(); 



#ifdef DUMP_MFS_FILE
    const char* dumpfile = "/data/data/dump.mfs";
    mFd = -1;
    mFd = open(dumpfile, O_RDWR | O_CREAT | O_TRUNC);
    LOGD("dump to %s",  dumpfile);   
#endif

    mVideoSock = NULL;
    mAudioSock = NULL;

 	LOGI("=============CMMBExtractor ver2.0.5================");
	
	
    LOGI("===============CMMBExtractor:initialize() is called");
    CmmbUriSource *cms = (CmmbUriSource *)mDataSource.get();
    LOGI("==0=============CMMBExtractor:initialize() is called %x", (int)cms);
    if(cms == NULL) {
        LOGI("===============CMMBExtractor::initialize() failed: no uri");
        return ERROR_NOT_CONNECTED;
    }
    char *str = cms->mUri;//"cmmb://serviceid:-1";//
    LOGI("1===============CMMBExtractor:initialize() is called  %s", str);
    str = strstr(str, "serviceid:");
    if(str == NULL) {
        LOGI("===============CMMBExtractor::initialize() failed: no service id ub uri");
        return ERROR_NOT_CONNECTED;
    }
    str += strlen("serviceid:");
    LOGI("2===============CMMBExtractor:initialize() is called  %s", str);
    mVideoSock = sock_connectAVServer(7654);
    if(mVideoSock == NULL) {
        LOGI("2===============sock_connectAVServer failed");
        return ERROR_NOT_CONNECTED;
    }
    if(sock_sendSIDtoAVServer(mVideoSock, atoi(str)) == -1) {
        sock_disconnectAVServer(mVideoSock);
        LOGI("2===============sock_sendSIDtoAVServer failed");
        return ERROR_NOT_CONNECTED;
    }
    mAudioSock = sock_connectAVServer(7655);
    if(mAudioSock == NULL) {
        LOGI("2===============sock_connectAVServer failed");
        sock_disconnectAVServer(mVideoSock);
        mVideoSock = NULL;
        return ERROR_NOT_CONNECTED;
    }
    if(sock_sendSIDtoAVServer(mAudioSock, atoi(str)) == -1) {
        LOGI("2===============sock_sendSIDtoAVServer failed");
        sock_disconnectAVServer(mVideoSock);
        sock_disconnectAVServer(mAudioSock);
        mVideoSock = NULL;
        mAudioSock = NULL;
        return ERROR_NOT_CONNECTED;
    }
    /*
       */
 /*
    mCh = CmmbSystem::getLogicalChannel(CMMB_LOGICAL_CHANNEL_ID); // Required by CMMB player
    if (mCh == 0) {
        LOGE("Get Logical Channel 2 failed!");
        CmmbSystem::releaseCtrlLock();
        return ERROR_NOT_CONNECTED;
    }

#ifdef DUMP_MFS_FILE        
        mListener = new FileDumper(mFd);
        CmmbSystem::registerDataAvailableListener(mCh, mListener);
#endif
        // for getting clear audio data, open an audio stream handle firstly.
        mAudioHandle = CmmbSystem::openAudioStream(mCh);
        mVideoHandle = CmmbSystem::openVideoStream(mCh);
        // start to receive
        CmmbSystem::startPlay(mCh);    
//        LOGI("Initial wait %d ms for buffer in cmmbserver", MEDIA_SERVICE_BUFFER_INITIAL_WAIT_MS);
//        usleep(MEDIA_SERVICE_BUFFER_INITIAL_WAIT_MS * 1000);
 */

	LOGI("Initial wait %d ms for buffer in cmmbserver", MEDIA_SERVICE_BUFFER_INITIAL_WAIT_MS);
    usleep(MEDIA_SERVICE_BUFFER_INITIAL_WAIT_MS * 1000);

    return OK;
}


status_t CMMBExtractor::stop() {
    /*
    if (mCh != 0) {
        // we stop to receive
        CmmbSystem::stopPlay(mCh);
#ifdef DUMP_MFS_FILE         
        CmmbSystem::unregisterDataAvailableListener(mCh, mListener);
        if(mFd != -1)
            close(mFd);
#endif

        // release the logical channel lock
        CmmbSystem::unlockLogicalChannel(mCh);
        // free the logical channel
        mCh.clear();
        IPCThreadState::self()->flushCommands();
    }
    */
    LOGI("===============CMMBExtractor:stop() is called");
    if(mAudioSock)
	sock_disconnectAVServer(mAudioSock);
    if(mVideoSock)
	sock_disconnectAVServer(mVideoSock);
    return OK;
}
sp<MetaData> CMMBExtractor::getMetaData() {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return new MetaData;
    }

    return mFileMetaData;
}

size_t CMMBExtractor::countTracks() {
    status_t err;
    if ((err = readMetaData()) != OK) {
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

sp<MetaData> CMMBExtractor::getTrackMetaData(
        size_t index, uint32_t flags) {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return NULL;
    }

    Track *track;

    if(mDraType)
    {
    	if(index == 0)
    	{
    		track = mAudioTrack;
    	}
    	else
    	{
    		return NULL;
    	}
    }
    else
    {
		if (index == 0) {
			track = mVideoTrack;
		}else if (index == 1) {
			track = mAudioTrack;
		}else {
			return NULL;
		}
    }
    
    if (track == NULL) {
        return NULL;
    }

    if ((flags & kIncludeExtensiveMetaData)
            && !track->includes_expensive_metadata) {
        track->includes_expensive_metadata = true;

        // reserved

    }
    return track->meta;
}

status_t CMMBExtractor::readMetaData() {
    if (mHaveMetadata) {
        return OK;
    }

    LOGI("================================Start to parse meta data...\n");
    status_t err; 

    if ((err = initialize()) != OK) {
        return err;
    }
    

#if 1
//    IAvStreamHandle::AvMetaData avMetaData;
    if(mDraType)
    {
//    	LOGV("readMetaData() DRA type");if (mAudioHandle->readMetaData(avMetaData) == true)
		{
    	        mHasAudio = true;
    	        mAudioTrack = new Track(DraAudioTrack);
    	        LOGV("CMMB has dra audio");
    	        mAudioTrack->meta->setInt32(kKeyChannelCount, CMMB_DRA_NUM_CHANNELS);
    	        mAudioTrack->meta->setInt32(kKeySampleRate, CMMB_DRA_SAMPLE_RATE);
    	        mAudioTrack->meta->setInt32(kKeyMaxInputSize, CMMB_SAMPLE_MAX_SIZE);
    	        mHaveMetadata = true;
    	}
    }
    else
    {
		//if (mVideoHandle->readMetaData(avMetaData) == true)
		{
			mHasVideo = true;
			mVideoTrack = new Track(VideoTrack);
			LOGV("CMMB has video");
			mVideoTrack->meta->setInt32(kKeyWidth, CMMB_VIDEO_WIDTH);
			mVideoTrack->meta->setInt32(kKeyHeight, CMMB_VIDEO_HEIGHT);
			mVideoTrack->meta->setInt32(kKeyMaxInputSize, CMMB_SAMPLE_MAX_SIZE);
			mHaveMetadata = true;
		}

		//if (mAudioHandle->readMetaData(avMetaData) == true)
		{
			mHasAudio = true;
			mAudioTrack = new Track(AudioTrack);
			LOGV("CMMB has audio");
			mAudioTrack->meta->setInt32(kKeyChannelCount, CMMB_AAC_NUM_CHANNELS);
			mAudioTrack->meta->setInt32(kKeySampleRate, CMMB_AAC_SAMPLE_RATE);
			mAudioTrack->meta->setInt32(kKeyMaxInputSize, 1536);

			// below is send fixed ESDS for AAC Decoder
			uint8_t buffer[256] =
				{3,128,128,128,34,0,0,0,4,128,128,128,20,64,21,0,6,0,0,0,0,0,0,0,187,128,5,128,128,128,2,19,16,6,128,128,128,1,2};
			mAudioTrack->meta->setData(
						kKeyESDS, kTypeESDS, &buffer[0], 39);
        LOGI("Set data kKeyESDS:");
			 // Information from the ESDS must be relied on for proper
			// setup of sample rate and channel count for MPEG4 Audio.
			// The generic header appears to only contain generic
			// information...
			status_t err = updateAudioTrackInfoFromESDS_MPEG4Audio(
					&buffer[0], 39);

			if (err != OK) {
				return err;
			}
			LOGV("updateAudioTrackInfoFromESDS_MPEG4Audio() successful");
			mHaveMetadata = true;
		}
    }
  
    if (mHaveMetadata != true) {
        LOGE("No video/audio meta data in MUX FRAME");
        return ERROR_INVALIDDATA;
    }else {        
        size_t max_size;

        if (mHasVideo) {
            mFileMetaData->setCString(kKeyMIMEType, "video/cmmb");
        }else if (mHasAudio) {
            mFileMetaData->setCString(kKeyMIMEType, "audio/cmmb");
        }
    }
    return OK;
#else    
    if (mVideoTrack == NULL)
    {
        mVideoTrack = new Track(VideoTrack);         
        mHasVideo = true;
    }
    if (mHasVideo == true)
    {      
        LOGV("CMMB has video");
        mVideoTrack->meta->setInt32(kKeyWidth, CMMB_VIDEO_WIDTH);
        mVideoTrack->meta->setInt32(kKeyHeight, CMMB_VIDEO_HEIGHT);         
        mVideoTrack->meta->setInt32(kKeyMaxInputSize, CMMB_SAMPLE_MAX_SIZE);
        mHaveMetadata = true;
   }
 

    if (mAudioTrack == NULL)
    {
        mAudioTrack = new Track(AudioTrack);  
        mHasAudio = true;
    }
    if (mHasAudio == true) {
        LOGV("CMMB has audio");
        mAudioTrack->meta->setInt32(kKeyChannelCount, CMMB_AAC_NUM_CHANNELS);
        mAudioTrack->meta->setInt32(kKeySampleRate, CMMB_AAC_SAMPLE_RATE);
        mAudioTrack->meta->setInt32(kKeyMaxInputSize, CMMB_SAMPLE_MAX_SIZE);

        // below is send fixed ESDS for AAC Decoder
        uint8_t buffer[256] = 
            {3,128,128,128,34,0,0,0,4,128,128,128,20,64,21,0,6,0,0,0,0,0,0,0,187,128,5,128,128,128,2,19,16,6,128,128,128,1,2};
        mAudioTrack->meta->setData(
                    kKeyESDS, kTypeESDS, &buffer[0], 39);
        LOGI("Set data kKeyESDS:");
         // Information from the ESDS must be relied on for proper
        // setup of sample rate and channel count for MPEG4 Audio.
        // The generic header appears to only contain generic
        // information...
        status_t err = updateAudioTrackInfoFromESDS_MPEG4Audio(
                &buffer[0], 39);
        
        if (err != OK) {
            return err;
        }
        LOGV("updateAudioTrackInfoFromESDS_MPEG4Audio() successful");          
        mHaveMetadata = true;
    }


    if (mHaveMetadata != true) {
        LOGE("No video/audio meta data in MUX FRAME");
        return ERROR_UNKNOWN;
    }else {        
        size_t max_size;

        if (mHasVideo) {
            mFileMetaData->setCString(kKeyMIMEType, "video/cmmb");
        }else if (mHasAudio) {
            mFileMetaData->setCString(kKeyMIMEType, "audio/cmmb");
        }else if (mHasData) {
            mFileMetaData->setCString(kKeyMIMEType, "data/cmmb");
        }
    }

    return OK;
#endif    
}

sp<MediaSource> CMMBExtractor::getTrack(size_t index) {
    status_t err;
    if ((err = readMetaData()) != OK) {
        return NULL;
    }

    CMMBSource *cs = NULL;
    if(mDraType)
    {
        	if(index == 0)
        	{
              cs = new CMMBSource(mAudioTrack->meta, mDataSource, mAudioTrack->timescale, DraAudioTrack, this, mAudioSock);
              if(cs) mAudioSock = NULL;
    			return cs;
        	}
        	else
        	{
        		return NULL;
        	}
    }
    else
    {
		if (index == VideoTrack) {
              cs = new CMMBSource(mVideoTrack->meta, mDataSource, mVideoTrack->timescale, index, this,  mVideoSock);
              if(cs) mVideoSock = NULL;
		}else if (index == AudioTrack) {
              cs = new CMMBSource(mAudioTrack->meta, mDataSource, mAudioTrack->timescale, index, this,  mAudioSock);
              if(cs) mAudioSock = NULL;
		}else {
			return NULL;
		}
         return cs;
    }
}

status_t CMMBExtractor::updateAudioTrackInfoFromESDS_MPEG4Audio(
        const void *esds_data, size_t esds_size) {
    ESDS esds(esds_data, esds_size);

    uint8_t objectTypeIndication;
    if (esds.getObjectTypeIndication(&objectTypeIndication) != OK) {
        return ERROR_MALFORMED;
    }

    if (objectTypeIndication == 0xe1) {
        // This isn't MPEG4 audio at all, it's QCELP 14k...
        mAudioTrack->meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_QCELP);
        return OK;
    }

    const uint8_t *csd;
    size_t csd_size;
    if (esds.getCodecSpecificInfo(
                (const void **)&csd, &csd_size) != OK) {
        return ERROR_MALFORMED;
    }

#if 0
    printf("ESD of size %d\n", csd_size);
    hexdump(csd, csd_size);
#endif

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

    LOGI("==============New sample rate is = %d", sampleRate);
    if (prevSampleRate != sampleRate) {
        LOGV("mpeg4 audio sample rate different from previous setting. "
             "was: %d, now: %d", prevSampleRate, sampleRate);
    }

    mAudioTrack->meta->setInt32(kKeySampleRate, 48000);

    int32_t prevChannelCount;
    CHECK(mAudioTrack->meta->findInt32(kKeyChannelCount, &prevChannelCount));

    if (prevChannelCount != numChannels) {
        LOGV("mpeg4 audio channel count different from previous setting. "
             "was: %d, now: %d", prevChannelCount, numChannels);
    }

    mAudioTrack->meta->setInt32(kKeyChannelCount, numChannels);
    LOGV("New numChannels is = %d", numChannels);

    return OK;
}

////////////////////////////////////////////////////////////////////////////////
CMMBSource::CMMBSource(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource,
        int32_t timeScale,
        uint8_t trackType,
        const sp<CMMBExtractor> &extractor,
        void *AVSock)
    : mFormat(format),
      mDataSource(dataSource),
      mTimescale(timeScale),
      mStarted(false),
      mSPSInitialized(false),
      mPPSInitialized(false),
      mWantsNALFragments(false),
      mStop(false),
      mTrackType(trackType),
      mGroup(NULL),
      mTimeoutCount(0), 
      mBuffer(NULL),
      mExtractor(extractor),
      mAVSock(AVSock)
      {
    const char *mime;
    bool success = mFormat->findCString(kKeyMIMEType, &mime);
    CHECK(success);

    mIsAVC = !strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC);    
    
    LOGI("Initialized media type: %s\n", mime);
}


CMMBSource::~CMMBSource() {
    if (mStarted) {
        stop();
    }
}

status_t CMMBSource::start(MetaData *params) {
    Mutex::Autolock autoLock(mLock);

    LOGV("Starting CMMB source...");
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
    
    LOGV("CMMB source started!");
    return OK;
}

status_t CMMBSource::stop() {
    Mutex::Autolock autoLock(mLock);

    LOGV("%s source stop is called", (mTrackType == CMMBExtractor::VideoTrack)?"Video":"Audio");   
    mStop = true;
    mStarted = false;
    if (mGroup) {
        delete mGroup;
        mGroup = NULL;
    }
    if (mAVSock) {            
        sock_disconnectAVServer(mAVSock);
        mAVSock = NULL;
    }
    return OK;
}

sp<MetaData> CMMBSource::getFormat() {
    Mutex::Autolock autoLock(mLock);
    return mFormat;
}

// read a decodable unit
status_t CMMBSource::read(
        MediaBuffer **out, const ReadOptions *options) {
    Mutex::Autolock autoLock(mLock);
    //CHECK(mStarted);
    
    *out = NULL;

    status_t err;
    int64_t seekTimeUs;

    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        //return err; // now random seek is unsupported
        LOGE("Random seek is not supported now");
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
        unsigned long pid;
        unsigned char unittype;
        unsigned short rate;
        unsigned short flag;

RETRY:
        if(connectToDataServer() != OK)
            return ERROR_IO;
        if(mTrackType == CMMBExtractor::AudioTrack || mTrackType == CMMBExtractor::DraAudioTrack) {
            // we need to make sure that the socket recv buffer has 5000 bytes of data left, 
            // this will keep the audioplayer reading the audio data in a onstant speed. otherwise it 
            // may read too fast and cause the audio and video's playback not synchronized.
            int ll = 0;
            while(ll < 100 && sock_checkReadble(mAVSock) < 8000) {
                ll++; usleep(10000);
            }
            if(ll >= 100) 
                len = 0;
            else
                len = sock_readAudioData(mAVSock,  &pdata, &pid, &ts, &unittype, &rate, &flag);
        }
        else {
            len = sock_readVideoData(mAVSock, &pdata, &pid, &ts, &flag);
        }
      // timeStamp = (int64_t)ts;//lg delete
	/////////////////////////////////lg add 2012-06-13///////////////////////////
	
		if(mTrackType == CMMBExtractor::AudioTrack || mTrackType == CMMBExtractor::DraAudioTrack){

			timeStamp = (int64_t)ts;
			timeStamp = timeStamp + baseTimeAudio;	
			lastTimeAudio = timeStamp;
			
		}

		else{
			timeStamp = (int64_t)ts;	
			if( (timeStamp + baseTimeVideo) < lastTimeVideo ){
				maxTimeVideo = lastTimeVideo;
				LOGI(" ===============timeStamp<lastTimeStampVideo========");
				LOGI(" ===============maxTimeVideo========%llx", maxTimeVideo);
			}
			if((timeStamp + baseTimeVideo)  < maxTimeVideo){
				countVideo++;
				LOGI(" ===============countVideo========%d", countVideo);
			}
			if(countVideo >= 3){
				baseTimeVideo = maxTimeVideo - timeStamp ;
				baseTimeAudio = baseTimeVideo;
				countVideo = 0;
				LOGI(" ===============baseTimeVideo========%llx", baseTimeVideo);
			}
			timeStamp = timeStamp + baseTimeVideo;	
			lastTimeVideo = timeStamp;
		}
		 
	/////////////////////////////////////////////////////////////////////////////
        pucData = (uint8_t *)pdata;

        if (len < 0) {            
            sock_disconnectAVServer(mAVSock);
            mAVSock = NULL;
            goto RETRY;
        }
        if(len == 0) {
            mBuffer->release();
            mBuffer = NULL;
            mTimeoutCount++;
            if(mTimeoutCount >5) {
                LOGI("%d ===================================mTimeoutCount > 5", mTrackType);
                return ERROR_CONNECTION_LOST;
            }
            LOGI("%d ===================================ERROR_TIMEOUT", mTrackType);
            return ERROR_TIMEOUT;
        }
        mTimeoutCount = 0;

///////////////////////////////lg add 2012-09-06//////////////////////////////
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
       		mAVSock = NULL;
		mBuffer->release();
       		mBuffer = NULL;
		return ERROR_CONNECTION_LOST;
	}
//////////////////////////////////////////////////////////////////////////



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

int CMMBSource::connectToDataServer()
{
    char *type = NULL;
    if(mTrackType == CMMBExtractor::AudioTrack || mTrackType == CMMBExtractor::DraAudioTrack) {
        type = "audio";
    }
    else {
        type = "video";
    }
    if(!mAVSock) {
        LOGI("%s===============CMMBSource:initialize() is called", type);
        CmmbUriSource *cms = (CmmbUriSource *)mDataSource.get();
        LOGI("%s==0=============CMMBSource:initialize() is called %x", type, (int)cms);
        if(cms == NULL) {
            LOGI("===============CMMBSource::initialize() failed: no uri");
            mBuffer->release();
            mBuffer = NULL;
            return ERROR_IO;
        }
        char *str = cms->mUri;//"cmmb://serviceid:-1";//
        LOGI("%s 1===============CMMBSource:initialize() is called  %s", type, str);
        str = strstr(str, "serviceid:");
        str += strlen("serviceid:");
        LOGI("%s 2===============CMMBSource:initialize() is called  %s", type, str);
        if(mTrackType == CMMBExtractor::AudioTrack || mTrackType == CMMBExtractor::DraAudioTrack)
            mAVSock = sock_connectAVServer(7655);
        else
            mAVSock = sock_connectAVServer(7654);
        if(mAVSock== NULL) {
            LOGI("%s 2===============sock_connectAVServer failed", type);
            mBuffer->release();
            mBuffer = NULL;
            return ERROR_IO;
        }
        if(sock_sendSIDtoAVServer(mAVSock, atoi(str)) == -1) {
            sock_disconnectAVServer(mAVSock);
            LOGI("%s 2===============sock_sendSIDtoAVServer failed", type);
            mAVSock = NULL;
            mBuffer->release();
            mBuffer = NULL;
            return ERROR_IO;
        }
    }
    return OK;
}

/////////////////////////////////////////////////////////////////////////////////////////

// Given a time in seconds since Jan 1 1904, produce a human-readable string.
static void convertTimeToDate(int64_t time_1904, String8 *s) {
    time_t time_1970 = time_1904 - (((66 * 365 + 17) * 24) * 3600);

    char tmp[32];
    strftime(tmp, sizeof(tmp), "%Y%m%dT%H%M%S.000Z", gmtime(&time_1970));

    s->setTo(tmp);
}

}  // namespace android




