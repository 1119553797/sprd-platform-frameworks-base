#define LOG_NDEBUG 0
#define LOG_TAG "VideoPhoneExtractor"
#include <utils/Log.h>
#include <utils/Singleton.h>

#include "include/VideoPhoneExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <stdio.h>
#include <fcntl.h>

#include <unistd.h> 
#include <math.h>

#include <errno.h>
#include <string.h>

#define SAFE_DELETE(p) if ( (p) != NULL) {delete (p); (p) =NULL;}
#define	SAFE_FREE(p)	if ( (p) != NULL) {free(p); (p) =NULL;}
#define	MAX_BUFFER_SIZE	(128*1024)
//#define DEBUG_FILE     "/data/vpin"
#define USE_DATA_DEVICE

namespace android {

class VideoPhoneSourceInterface
{
public:
    virtual int write(char* data, int nLen) = 0;
    VideoPhoneSourceInterface() {};
    virtual ~VideoPhoneSourceInterface() {};
};

class VideoPhoneDataDevice : public Singleton<VideoPhoneDataDevice>
{
public:
    FILE* m_fAVStream;
    status_t registerClient(VideoPhoneSourceInterface *client, sp<DataSource> dataSource);
    void unregisterClient(VideoPhoneSourceInterface *client);

private:
    friend class Singleton<VideoPhoneDataDevice>;

    VideoPhoneDataDevice();
    ~VideoPhoneDataDevice();
    static void *ThreadWrapper(void *);
    status_t threadFunc();
    status_t startThread();
    void stopThread();

    SortedVector<VideoPhoneSourceInterface *> mClients;
    sp<DataSource> mDataSource;
    pthread_t m_Thread;
    Mutex m_Lock;
    bool mStarted;
};

class VideoPhoneSource : public MediaSource,
                         public VideoPhoneSourceInterface
{
public:
	
    	VideoPhoneSource(const sp<MetaData> &format,
    	                const sp<DataSource> &dataSource);

    	virtual status_t start(MetaData *params = NULL);
	
    	virtual status_t stop();

    	virtual sp<MetaData> getFormat();

    	virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

	int write(char* data, int nLen);
protected:
	
    	virtual ~VideoPhoneSource();

private:
#ifndef USE_DATA_DEVICE	
	static void *	ThreadWrapper(void *);
	
    	status_t 		threadFunc();
#endif
	int			writeRingBuffer(char* data,int nLen);

	int			readRingBuffer(char* data, size_t nSize);
		
private:
	//bool				m_bFirstGet;
	
    	Mutex 			m_Lock;
		
	Condition 		m_DataGet;
	
    	sp<MetaData> 	m_Format;

	const char *		m_strMime;

    	sp<DataSource> 	m_DataSource;

    	bool 			m_bStarted;

	MediaBufferGroup*	m_pGroup;

	FILE*			m_fAVStream;

	int64_t			m_nStartSysTime;

	uint8_t*			m_RingBuffer;

	int				m_nDataStart;

	int				m_nDataEnd;	

	size_t			m_nRingBufferSize;

	pthread_t 		m_Thread;

	Condition			m_GetBuffer;

	int			m_nNum;	
};


VideoPhoneExtractor::VideoPhoneExtractor(const sp<DataSource> &source)
    : m_bHaveMetadata(false),
      mFileMetaData(new MetaData),
      mDataSource(source)
{    	
	LOGI("VideoPhoneExtractor::VideoPhoneExtractor");
	
	m_AVMeta	= 	new MetaData();
}

VideoPhoneExtractor::~VideoPhoneExtractor() 
{
	LOGI("VideoPhoneExtractor::~VideoPhoneExtractor");
}

sp<MetaData> VideoPhoneExtractor::getMetaData() 
{
	LOGI("VideoPhoneExtractor::getMetaData");
	status_t err;
	if ((err = readMetaData()) != OK)
		return NULL;

	return mFileMetaData;
}

size_t VideoPhoneExtractor::countTracks() 
{
	return 1;
}

sp<MetaData> VideoPhoneExtractor::getTrackMetaData(
        size_t index, uint32_t flags) 
{

	LOGI("VideoPhoneExtractor::getTrackMetaData START index = %d",index);
	if (index > 1)
		goto fail;
	
	status_t err;
	if ((err = readMetaData()) != OK)
		return NULL;
	return m_AVMeta;

fail:
	
	LOGE("VideoPhoneExtractor::getTrackMetaData FAIL");
	return NULL;
}

status_t VideoPhoneExtractor::readMetaData() 
{
	LOGI("VideoPhoneExtractor::readMetaData START");

    	if (m_bHaveMetadata)
        	goto success;

	mFileMetaData->setCString(kKeyMIMEType, "video/3gpp");
		
	m_AVMeta->setInt32(kKeyRotation, 0);
	m_AVMeta->setCString(kKeyMIMEType, "video/3gpp");
	m_AVMeta->setInt32(kKeyWidth, 176);
	m_AVMeta->setInt32(kKeyHeight, 144);


success:
	
	LOGI("VideoPhoneExtractor::readMetaData SUCCESS");
	m_bHaveMetadata = true;
    	return OK;

fail:
	
	LOGE("VideoPhoneExtractor::readMetaData FAIL");
	return UNKNOWN_ERROR;
}

sp<MediaSource> VideoPhoneExtractor::getTrack(size_t index) 
{		
	LOGI("VideoPhoneExtractor::getTrack START");
    	if (readMetaData()!= OK)
        	goto fail;
		
	if (index > 1)
		goto fail;

    	return new VideoPhoneSource(m_AVMeta, mDataSource);

fail:
	
	LOGE("VideoPhoneExtractor::getTrack FAIL");
	return NULL;
}

/////////////////////////////////////////////////////////////////////
#undef LOG_TAG
#define LOG_TAG "VideoPhoneSource"

VideoPhoneSource::VideoPhoneSource(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource)
    : m_Format(format),
      m_DataSource(dataSource),
      m_bStarted(false),
      m_pGroup(NULL),
      m_nNum(0)
{
	LOGI("VideoPhoneSource::VideoPhoneSource");
	m_fAVStream		= NULL;
}

VideoPhoneSource::~VideoPhoneSource() 
{	
	LOGI("VideoPhoneSource::~VideoPhoneSource");
	if (m_bStarted)
		stop();
}

status_t VideoPhoneSource::start(MetaData *params) 
{
	Mutex::Autolock autoLock(m_Lock);
		
	LOGI("VideoPhoneSource::start");
	//m_bFirstGet	= true;	
	status_t 		err 		= NO_MEMORY;
	bool			bRet	= false;
#ifndef USE_DATA_DEVICE	
	pthread_attr_t attr;
#endif

	if (m_bStarted)
		goto success;

	m_bStarted = true;

	m_pGroup = new MediaBufferGroup;
    	m_pGroup->add_buffer(new MediaBuffer(30000));

	bRet = m_Format->findCString(kKeyMIMEType, &m_strMime);

	if (!bRet)
		goto fail;

	m_RingBuffer 		= (uint8_t*)malloc(MAX_BUFFER_SIZE);
	m_nDataEnd		= 0;
	m_nDataStart		= MAX_BUFFER_SIZE - 1;
	m_nRingBufferSize	= MAX_BUFFER_SIZE;

#ifndef USE_DATA_DEVICE	
#ifdef DEBUG_FILE
	m_fAVStream = fopen(DEBUG_FILE,"r");
	if (m_fAVStream != NULL) fseek(m_fAVStream, 0, SEEK_SET);
	if (m_fAVStream == NULL)
	{
		LOGE("Cannot open file %s", DEBUG_FILE);
		goto fail;
	}
	LOGD("file opened %p", m_fAVStream);
#else
	if (m_DataSource->initCheck() != OK)
	{
		LOGE("Cannot open file");
		goto fail;
	}
#endif

	pthread_attr_init(&attr);
    	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    	pthread_create(&m_Thread, &attr, ThreadWrapper, this);
    	pthread_attr_destroy(&attr);
#else
    if (VideoPhoneDataDevice::getInstance().registerClient(this, m_DataSource) != OK)
    {
        LOGE("Cannot register client");
        goto fail;
    }
#endif		
		
success:

	LOGI("VideoPhoneSource::start SUCCESS!");
    	return OK;

fail:
	
	LOGE("***VideoPhoneSource::start FAIL***");
	if (m_RingBuffer != NULL) {
	SAFE_FREE(m_RingBuffer);
		free(m_RingBuffer);
		m_RingBuffer = NULL;
	}
	return err;
}

status_t VideoPhoneSource::stop() 
{
	Mutex::Autolock autoLock(m_Lock);
	
	status_t err;
	
	LOGI("VideoPhoneSource::stop");
    	if (!m_bStarted)
		goto success;

	m_bStarted = false;
	
	m_GetBuffer.signal();
	
	SAFE_DELETE(m_pGroup);

#ifdef DEBUG_FILE
	if (m_fAVStream)
	{
		fclose(m_fAVStream);
		m_fAVStream	= NULL;
	}
#endif

	SAFE_FREE(m_RingBuffer);
	//relese the share mem
	//........
	
success:
		LOGI("VideoPhoneSource::stop SUCCESS!");

    	return OK;

fail:

	return err;
}

sp<MetaData> VideoPhoneSource::getFormat() 
{
    Mutex::Autolock autoLock(m_Lock);

    return m_Format;
}

status_t VideoPhoneSource::read(
        MediaBuffer **out, const ReadOptions *options) 
{
    	Mutex::Autolock autoLock(m_Lock);
	
	//static	int		nNum;
	LOGI("VideoPhoneSource::read START nNum = %d",m_nNum);	
	
	uint32_t	nStart = 0;
	uint32_t	nEnd;
	status_t 	err	= UNKNOWN_ERROR;

	char 	cHeader[16];
	uint32_t	nSize;
	uint32_t	nPts = 0;//not used
	int 		type = 0;//not used

	MediaBuffer*		pMediaBuffer;

	if(!m_bStarted)
		goto fail;

	err = m_pGroup->acquire_buffer(&pMediaBuffer);
	if (err != OK)
		goto fail;
	
	nSize 	= readRingBuffer((char*)pMediaBuffer->data(), pMediaBuffer->size());
	if (nSize == 0)
		goto fail;
	
	//nEnd	= nSize - 1;
	nEnd	= nSize;

success:
	
	LOGI("VideoPhoneSource::read:  nNum = %d nStart = %d  nEnd = %d  nPts = %d   type = %d  mime = %s",
		m_nNum,nStart,nEnd,nPts,type,m_strMime);

	pMediaBuffer->set_range(nStart, nEnd);
	pMediaBuffer->meta_data()->clear();

	if (m_nNum == 0)
		m_nStartSysTime	= nanoseconds_to_milliseconds(systemTime());

	pMediaBuffer->meta_data()->setInt64(
                    kKeyTime,  
                    1000 * (nanoseconds_to_milliseconds(systemTime()) -m_nStartSysTime));
	
	*out = pMediaBuffer;
	LOGI("VideoPhoneSource::read OK");
	m_nNum++;
	return OK;

fail:
	
	LOGE("*****VideoPhoneSource::read FAIL!******");
	return err;
}

#ifndef USE_DATA_DEVICE	
void *VideoPhoneSource::ThreadWrapper(void *me) 
{
    	return (void *) static_cast<VideoPhoneSource *>(me)->threadFunc();
}

status_t VideoPhoneSource::threadFunc() 
{
	const int BUFFER_SIZE = 2000;
	status_t 	err = OK;
	char		cTempbuffer[BUFFER_SIZE];	
	int 		nLen;
	
	LOGI("enter threadFunc");
	while (m_bStarted) 
	{
		Mutex::Autolock autoLock(m_Lock);

#ifdef DEBUG_FILE
   	if (m_fAVStream == NULL)
			break;
#else
		if (m_DataSource->initCheck() != OK)
			break;
#endif

#ifdef DEBUG_FILE
		LOGI("before read %p", m_fAVStream);
    nLen = fread((void *)cTempbuffer,1, BUFFER_SIZE, m_fAVStream);
#else
		LOGI("before read");
		nLen = m_DataSource->readAt(0, (void *)cTempbuffer, BUFFER_SIZE);
#endif
		if (nLen == 0)
		{
			LOGW("read error %s", strerror(errno));
#ifdef DEBUG_FILE
			if (feof(m_fAVStream)) break;
#else
			break;
#endif
			usleep(1000*1000);
		}
		LOGI("after read %d", nLen);

		writeRingBuffer(cTempbuffer,nLen);
	}
	LOGI("exit threadFunc");
	
	if (err == ERROR_END_OF_STREAM)
		err	= OK;
		
	return err;
}
#endif

int	VideoPhoneSource::writeRingBuffer(char* data,int nLen)
{
	if (m_RingBuffer == NULL || nLen <= 0)
		return 0;

	int bChangeStart = false;
	
	if ((m_nDataEnd < m_nDataStart && m_nDataEnd + nLen > m_nDataStart)
		|| (m_nDataEnd > m_nDataStart && m_nDataEnd + nLen > m_nDataStart + m_nRingBufferSize))
	{
		LOGE("buffer is overrun!!!");
		bChangeStart = true;
	}

	int nTemp = nLen;
	
	if (nLen > m_nRingBufferSize - m_nDataEnd)
		nTemp =  m_nRingBufferSize - m_nDataEnd;
	
	memcpy(m_RingBuffer+m_nDataEnd,data,nTemp);
	data		+= nTemp;
	m_nDataEnd	+= nTemp;
	
	if ((nTemp = nLen - nTemp) > 0)
	{
		memcpy(m_RingBuffer,data ,nTemp);
		m_nDataEnd	= nTemp - 1;
	}

	if (bChangeStart)
		m_nDataStart = m_nDataEnd;

	LOGI("signal");
	m_GetBuffer.signal();
	
	return nLen;	
}

int	VideoPhoneSource::readRingBuffer(char* data, size_t nSize)
{
	LOGI("VideoPhoneSource::readRingBuffer START0");	

	if (m_RingBuffer == NULL)
		return 0;

	int	nNext = m_nDataStart;
	int	nLen;
	bool	bStartRead 	= false;
	bool	bIsMpege4	= false;
	int	nStart = m_nDataStart, nEnd = m_nDataStart;
	LOGI("VideoPhoneSource::readRingBuffer START1 %d, m_nDataStart: %d, m_nDataEnd: %d", m_bStarted, m_nDataStart, m_nDataEnd);
	while (m_bStarted)
	{
		nEnd	= nNext;
		nNext = (nNext + 1) % m_nRingBufferSize;
		
		if (nNext == m_nDataEnd)
		{
			LOGI("wait 1");
			m_GetBuffer.wait(m_Lock);
			LOGI("wait 2");
		}
		
		if (!m_bStarted)
			return 0;

		if (m_RingBuffer[nEnd] == 0x00 && 
			m_RingBuffer[(nEnd + 1) % m_nRingBufferSize] == 0x00)
		{
			if (m_RingBuffer[(nEnd + 2) % m_nRingBufferSize] == 0x01 &&
				m_RingBuffer[(nEnd + 3) % m_nRingBufferSize] == 0xb6)
			{
				if (!bStartRead)
				{
					LOGI("VideoPhoneSource::readRingBuffer START MEPGE4");
					nStart		= nEnd;
					bStartRead 	= true;
				}
				else
					break;
			}
			else if  ((m_RingBuffer[(nEnd + 2) % m_nRingBufferSize] & 0xFC) == 0x80 &&
				(m_RingBuffer[(nEnd + 3) % m_nRingBufferSize] & 0x03) == 0x02)
			{
				if (!bStartRead)
				{
					LOGI("VideoPhoneSource::readRingBuffer START VOP");
					nStart		= nEnd;
					bStartRead 	= true;
				}
				else
					break;
			}
		}
		//nNext++;
	}
	LOGI("find frame %d %d", nStart, nEnd);

#if 0
	nLen 	= ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize - 4;
	nStart 	= (nStart + 4) % m_nRingBufferSize;
#else
	nLen 	= ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize;
#endif

	if (nLen > nSize)
	{
		LOGE("nLen %d exceeds nSize %d", nLen, nSize);
		return 0;
	}
	int nTemp = nLen;

	if (nTemp > m_nRingBufferSize - nStart)
		nTemp = m_nRingBufferSize - nStart;
		
	memcpy(data,m_RingBuffer+nStart,nTemp);
	data += nTemp;
	
	if ((nTemp = nLen - nTemp) > 0)
		memcpy(data,m_RingBuffer ,nTemp);
	
	m_nDataStart	= nEnd;
	LOGI("VideoPhoneSource::readRingBuffer END");	
	return nLen;
}
	
int VideoPhoneSource::write(char* data,int nLen)
{
    return writeRingBuffer(data, nLen);
}

/////////////////////////////////////////////////////////////////////
#undef LOG_TAG
#define LOG_TAG "VideoPhoneDataDevice"

ANDROID_SINGLETON_STATIC_INSTANCE(VideoPhoneDataDevice);

VideoPhoneDataDevice::VideoPhoneDataDevice()
    : mDataSource(NULL)
{
    mClients.clear();
    LOGV("VideoPhoneDataDevice created");
}

VideoPhoneDataDevice::~VideoPhoneDataDevice()
{
    LOGV("VideoPhoneDataDevice destroyed");
}

status_t VideoPhoneDataDevice::startThread()
{
    pthread_attr_t attr;

    mStarted = true;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&m_Thread, &attr, ThreadWrapper, this);
    pthread_attr_destroy(&attr);

    return OK;
}

void VideoPhoneDataDevice::stopThread()
{
    mStarted = false;
}

status_t VideoPhoneDataDevice::registerClient(VideoPhoneSourceInterface *client, sp<DataSource> dataSource)
{
    Mutex::Autolock autoLock(m_Lock);
    if (mClients.size() == 0) {
        mDataSource = dataSource;
        if (mDataSource == NULL) {
            LOGE("first registrant should not have NULL dataSource");
            return BAD_VALUE;
        }
        if (mDataSource->initCheck() != OK)
        {
            LOGE("register initCheck fail");
            return BAD_VALUE;
        }
        startThread();
    }
    mClients.add(client);
    LOGV("register Client %p", client);
    return OK;
}

void VideoPhoneDataDevice::unregisterClient(VideoPhoneSourceInterface *client)
{
    Mutex::Autolock autoLock(m_Lock);
    mClients.remove(client);
    LOGV("unregister Client %p", client);
    if (mClients.size() == 0) {
        stopThread();
    }
}

void *VideoPhoneDataDevice::ThreadWrapper(void *me) 
{
    return (void *) static_cast<VideoPhoneDataDevice *>(me)->threadFunc();
}

status_t VideoPhoneDataDevice::threadFunc() 
{
    const int BUFFER_SIZE = 2000;
    status_t 	err = OK;
    char cTempbuffer[BUFFER_SIZE];	
    int nLen;
	
    LOGI("enter threadFunc");
    while (mStarted) 
    {
        Mutex::Autolock autoLock(m_Lock);

#ifdef DEBUG_FILE
   	if (m_fAVStream == NULL)
			break;
#else
		if (mDataSource->initCheck() != OK)
			break;
#endif

#ifdef DEBUG_FILE
		LOGI("before read %p", m_fAVStream);
    nLen = fread((void *)cTempbuffer,1, BUFFER_SIZE, m_fAVStream);
#else
		LOGI("before read");
		nLen = mDataSource->readAt(0, (void *)cTempbuffer, BUFFER_SIZE);
#endif
		if (nLen == 0)
		{
			LOGW("read error %s", strerror(errno));
#ifdef DEBUG_FILE
			if (feof(m_fAVStream)) break;
#else
			break;
#endif
			usleep(1000*1000);
		}
		LOGI("after read %d", nLen);

        for (int i = 0, n = mClients.size(); i < n; ++i) {
            static_cast<VideoPhoneSourceInterface *>(mClients[i])->write(cTempbuffer, nLen);
		//writeRingBuffer(cTempbuffer,nLen);
        }
    }
    LOGI("exit threadFunc");
	
    if (err == ERROR_END_OF_STREAM)
        err = OK;
		
    return err;
}

}  // namespace android

