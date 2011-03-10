#define LOG_TAG "VideoPhone"
#include <utils/Log.h>

#include "include/VideoPhoneExtractor.h"

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <stdio.h>

#define 	SAFE_DELETE(p) if ( (p) != NULL) {delete (p); (p) =NULL;}
#define	SAFE_FREE(p)	if ( (p) != NULL) {free(p); (p) =NULL;}

namespace android {

class VideoPhoneSource : public MediaSource 
{
public:
	
    	VideoPhoneSource(const sp<MetaData> &format);

    	virtual status_t start(MetaData *params = NULL);
	
    	virtual status_t stop();

    	virtual sp<MetaData> getFormat();

    	virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
	
    	virtual ~VideoPhoneSource();
	
private:

	bool				m_bFirstGet;
	
    	Mutex 			m_Lock;
		
	Condition 		m_DataGet;
	
    	sp<MetaData> 	m_Format;

	const char *		m_strMime;

    	sp<DataSource> 	m_DataSource;

    	bool 			m_bStarted;

	MediaBufferGroup*	m_pGroup;

	FILE*			m_fAVStream;

};


VideoPhoneExtractor::VideoPhoneExtractor(const sp<DataSource> &source)
    : m_bHaveMetadata(false),
      	mFileMetaData(new MetaData)
  
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

	mFileMetaData->setCString(kKeyMIMEType, "video/mpeg4");
		
	m_AVMeta->setCString(kKeyMIMEType, "video/mpeg4");
	m_AVMeta->setInt32(kKeyWidth, 176);
	m_AVMeta->setInt32(kKeyHeight, 144);


success:
	
	LOGI("CMMBExtractor::readMetaData SUCCESS");
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

    	return new VideoPhoneSource(m_AVMeta);

fail:
	
	LOGE("VideoPhoneExtractor::getTrack FAIL");
	return NULL;
}

/////////////////////////////////////////////////////////////////////

VideoPhoneSource::VideoPhoneSource(
        const sp<MetaData> &format)
    : m_Format(format),
      m_bStarted(false),
      	m_pGroup(NULL)
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
		
	m_bFirstGet	= true;	
	status_t 		err 		= NO_MEMORY;
	bool			bRet	= false;

	if (m_bStarted)
		goto success;

	m_pGroup = new MediaBufferGroup;
    	m_pGroup->add_buffer(new MediaBuffer(30000));

	bRet = m_Format->findCString(kKeyMIMEType, &m_strMime);

	if (!bRet)
		goto fail;
	
   	m_fAVStream = fopen("/mnt/sdcard/video.dat","r");

	if (m_fAVStream == NULL)
	{
        	LOGE("Cannot open file");
		goto fail;
	}

	//open the share mem of pMem
		
success:

	LOGI("VideoPhoneSource::start SUCCESS!");
	m_bStarted = true;
    	return OK;

fail:
	
	LOGE("***VideoPhoneSource::start FAIL***");
	return err;
}

status_t VideoPhoneSource::stop() 
{
	Mutex::Autolock autoLock(m_Lock);
	
	status_t err;
	
    	if (!m_bStarted)
		goto success;
	
	SAFE_DELETE(m_pGroup);

	if (m_fAVStream)
	{
		fclose(m_fAVStream);
		m_fAVStream	= NULL;
	}
	
	//relese the share mem
	//........
	
success:
	
	m_bStarted = false;
    	return OK;

fail:

	return err;
}

sp<MetaData> VideoPhoneSource::getFormat() 
{
    Mutex::Autolock autoLock(m_Lock);

    return m_Format;
}

static uint32_t	VAL32(uint32_t   x)
{
  	u_char   *s   =   (u_char   *)&x; 
	return   (uint32_t)(s[0] << 24 | s[1]   << 16 | s[2] << 8 | s[3]); 
}


status_t VideoPhoneSource::read(
        MediaBuffer **out, const ReadOptions *options) 
{
    	Mutex::Autolock autoLock(m_Lock);
	
	static	int		nNum;
	nNum++;
	LOGI("VideoPhoneSource::read START nNum = %d",nNum);	
	
	uint32_t	nStart;
	uint32_t	nEnd;
	status_t 	err	= UNKNOWN_ERROR;

	char 	cHeader[16];
	uint32_t	nSize;
	uint32_t	nPts;
	int 		type;

	MediaBuffer*		pMediaBuffer;

	if(!m_bStarted)
		goto fail;

	err = m_pGroup->acquire_buffer(&pMediaBuffer);
	if (err != OK)
		goto fail;
	
	fread(cHeader,1,16,m_fAVStream);
	nPts		= VAL32(*(uint32_t*)(char*)(cHeader + 4));
	nSize 	= VAL32(*(uint32_t*)(char*)(cHeader + 12));
	
	if (fread((uint8_t *)pMediaBuffer->data(),1,nSize,m_fAVStream) == 0)
	{
		pMediaBuffer->release();
		err 	= ERROR_END_OF_STREAM;
		LOGI("*****CMMBSource::read: It's end!******");
		goto fail;
	}
	
	nEnd	= nSize - 1;

success:
	
	LOGI("VideoPhoneSource::read:  nNum = %d nStart = %d  nEnd = %d  nPts = %d   type = %d  mime = %s",
		nNum,nStart,nEnd,nPts,type,m_strMime);

	pMediaBuffer->set_range(nStart, nEnd);
	pMediaBuffer->meta_data()->clear();
	pMediaBuffer->meta_data()->setInt64(
                    kKeyTime, (int64_t)nPts);
	*out = pMediaBuffer;
	LOGI("VideoPhoneSource::read OK");
	usleep(40000);
	return OK;

fail:
	
	LOGE("*****VideoPhoneSource::read FAIL!******");
	return err;
}

}  // namespace android

