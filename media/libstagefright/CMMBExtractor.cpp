#define LOG_TAG "CMMB"
#include <utils/Log.h>

#include "include/CMMBExtractor.h"

#include <multimedia_il.h>

#include <media/stagefright/DataSource.h>
 #include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <stdio.h>
#include <utils/threads.h>

#include <stdint.h>
#include <stddef.h>
#include <unistd.h>
#include <netinet/in.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>

#define 	SAFE_DELETE(p) if ( (p) != NULL) {delete (p); (p) =NULL;}
#define	SAFE_FREE(p)	if ( (p) != NULL) {free(p); (p) =NULL;}

namespace android {

class CMMBSource : public MediaSource 
{
public:
	
    	CMMBSource(const sp<MetaData> &format);

    	virtual status_t start(MetaData *params = NULL);
	
    	virtual status_t stop();

    	virtual sp<MetaData> getFormat();

    	virtual status_t read(
            MediaBuffer **buffer, const ReadOptions *options = NULL);

protected:
	
    	virtual ~CMMBSource();
private:

	enum AVC_FRAME_TYPE
	{	
		I_FRAME	= 5,
		SPS_FRAME 	= 7,
		PPS_FRAME,
	};
	
	struct CMMBAVData
	{
		int			nId;
		uint32_t		nTs;
		uint16_t 		nUnitLen;
		char*		buffer;
		int			frame_type;
	};
	
private:
	
	static void *	ThreadWrapper(void *);
	
    	status_t 		threadFunc();

	int			read(CMMBAVData &buffer);
	
	void			release(CMMBAVData buffer);
	
	int 			addData(const uint8_t*const  data, size_t size, uint32_t ts);
	
private:

	bool				m_bFirstGet;
	bool 			m_bGetSPS;
	bool 			m_bGetPPS;
	bool				m_bGetIFrame;
	
    	Mutex 			m_Lock;
		
	Condition 		m_BufferGet;
	
    	sp<MetaData> 	m_Format;

	const char *		m_strMime;

    	sp<DataSource> 	m_DataSource;

    	bool 			m_bStarted;

	MediaBufferGroup*	m_pGroup;

	FILE*			m_fAVStream;

	pthread_t 		m_Thread;

	DPLAYER_STRM_DATA_T	m_StreamData;

	Vector<CMMBAVData>		m_DataBuffers;
	
	uint16_t			m_nBufferNum;
	
	uint16_t			m_nDataStart;
	
	uint16_t			m_nDataEnd;
	
	int 				m_sockRev;

	sockaddr_in		m_addrSock;
		
};

#define VIDEO_SHARE_MEM 	"/video"
#define AUDIO_SHARE_MEM 	"/audio"

CMMBExtractor::CMMBExtractor(const sp<DataSource> &source)
    : 	m_Status(5),
      	m_bHaveMetadata(false),
      	mFileMetaData(new MetaData)
  
{    	
	LOGI("CMMBExtractor::CMMBExtractor");
	
	m_AVMeta[0] 	= 	new MetaData();
      	m_AVMeta[1]	=	new MetaData();

	MultimediaIL_Init(NULL);
}

CMMBExtractor::~CMMBExtractor() 
{
	//SAFE_DELETE(m_AVMeta[0]);
	//SAFE_DELETE(m_AVMeta[1]);
	MultimediaIL_Exit();
	LOGI("CMMBExtractor::~CMMBExtractor");
}

sp<MetaData> CMMBExtractor::getMetaData() 
{
	LOGI("CMMBExtractor::getMetaData");
    	status_t err;
    	if ((err = readMetaData()) != OK)
        	return NULL;

    	return mFileMetaData;
}

size_t CMMBExtractor::countTracks() 
{
	int		nTracksNum = 0;
	LOGI("CMMBExtractor::countTracks START");

    	if (readMetaData() != OK) 
       	 goto over;

	if (m_Status & TV)
		nTracksNum =  1;
	else
		nTracksNum =  1;

over:
	LOGI("CMMBExtractor::countTracks OVER");
	return nTracksNum;
}

sp<MetaData> CMMBExtractor::getTrackMetaData(
        size_t index, uint32_t flags) 
{

	LOGI("CMMBExtractor::getTrackMetaData START index = %d",index);
	if (index > 1)
		goto fail;
	
   	if (index > 0 && 
		!(m_Status & TV))
		goto fail;
	
	LOGI("CMMBExtractor::getTrackMetaData OK");

    	return m_AVMeta[index];

fail:
	LOGE("CMMBExtractor::getTrackMetaData FAIL");
	return NULL;
}

status_t CMMBExtractor::readMetaData() 
{
	LOGI("CMMBExtractor::readMetaData START");

    	if (m_bHaveMetadata)
        	goto success;

	if (!(m_Status & START))
		goto fail;
	
	if (m_Status & TV)
	{
		mFileMetaData->setCString(kKeyMIMEType, "video/mp4");
		
		m_AVMeta[AUDIO_META]->setCString(kKeyMIMEType, "audio/mp4a-latm");
		m_AVMeta[AUDIO_META]->setInt32(kKeyChannelCount, 2);
        	m_AVMeta[AUDIO_META]->setInt32(kKeySampleRate, 48000);
		
		m_AVMeta[VIDEO_META]->setCString(kKeyMIMEType, "video/avc");
		m_AVMeta[VIDEO_META]->setInt32(kKeyWidth, 320);
		m_AVMeta[VIDEO_META]->setInt32(kKeyHeight, 240);
	}
	else
	{
		mFileMetaData->setCString(kKeyMIMEType, "audio/dra");
		m_AVMeta[AUDIO_META]->setCString(kKeyMIMEType, "audio/dra");
	}

success:
	
	LOGI("CMMBExtractor::readMetaData SUCCESS");
	m_bHaveMetadata = true;
    	return OK;

fail:
	
	LOGE("CMMBExtractor::readMetaData FAIL");
	return UNKNOWN_ERROR;
}

sp<MediaSource> CMMBExtractor::getTrack(size_t index) 
{		
	LOGI("CMMBExtractor::getTrack START");
    	if (readMetaData()!= OK)
        	goto fail;
		
	if (index > 1)
		goto fail;
	
   	if (index > 0 && 
		!(m_Status & TV))
		goto fail;

    	return new CMMBSource(m_AVMeta[index]);

fail:
	LOGE("CMMBExtractor::getTrack FAIL");
	return NULL;
}

/////////////////////////////////////////////////////////////////////

CMMBSource::CMMBSource(
        const sp<MetaData> &format)
    : m_Format(format),
      m_bStarted(false),
      	m_pGroup(NULL),
      	m_sockRev(-1)
{
	LOGI("CMMBSource::CMMBSource");
	m_fAVStream		= NULL;
	m_nBufferNum	= 100;
}

CMMBSource::~CMMBSource() 
{	
	LOGI("CMMBSource::~CMMBSource");
    	if (m_bStarted)
        	stop();
}

status_t CMMBSource::start(MetaData *params) 
{
    	Mutex::Autolock autoLock(m_Lock);
		
	m_bFirstGet	= true;	
	status_t 		err 		= NO_MEMORY;
	bool			bRet	= false;
	pthread_attr_t	attr;
	int 			recvsize		=1024*512;
	int 			getsizetemp	=0;
	int 			lentemp		=sizeof(int);
	int 			flags;
	
	if (m_bStarted)
		goto success;
	
	m_pGroup = new MediaBufferGroup;
    	m_pGroup->add_buffer(new MediaBuffer(30000));

	bRet = m_Format->findCString(kKeyMIMEType, &m_strMime);

	if (!bRet)
		goto fail;
	
	if (!strncasecmp("video/", m_strMime, 6))
	{
		CMMBAVData	tempData;
		for (int i =0;i < m_nBufferNum;i++)
		{
			tempData.nId	= i;
			if (tempData.buffer = (char*)malloc(30000))
				m_DataBuffers.add(tempData);
			else
				goto fail;
		}	
		m_sockRev	= socket(AF_INET, SOCK_DGRAM, 0);
    		if (m_sockRev < 0) 
    		{
    			LOGE("CMMBSource::start  socket 's cread is fail! mime = %s",m_strMime);
        		goto fail;
    		}

    		bzero(&m_addrSock, sizeof(m_addrSock));
    		m_addrSock.sin_family			= AF_INET;
    		m_addrSock.sin_port 			= htons(6970);
    		m_addrSock.sin_addr.s_addr 	=inet_addr("127.0.0.1");
		if (bind(m_sockRev,(sockaddr*)&m_addrSock,sizeof(m_addrSock)) < 0)
		{
			LOGE("CMMBSource::start  video's socket is fail!");
			goto fail;
		}
	}
	else
	{
		CMMBAVData	tempData;
		for (int i =0;i < m_nBufferNum;i++)
		{
			tempData.nId	= i;
			if (tempData.buffer = (char*)malloc(500))
				m_DataBuffers.add(tempData);
			else
				goto fail;
		}
		m_sockRev	= socket(AF_INET, SOCK_DGRAM, 0);
    		if (m_sockRev < 0) 
    		{
    			LOGE("CMMBSource::start  socket 's cread is fail! mime = %s",m_strMime);
        		goto fail;
    		}

    		bzero(&m_addrSock, sizeof(m_addrSock));
    		m_addrSock.sin_family			= AF_INET;
    		m_addrSock.sin_port 			= htons(6972);
    		m_addrSock.sin_addr.s_addr 	= inet_addr("127.0.0.1");
		if (bind(m_sockRev,(sockaddr*)&m_addrSock,sizeof(m_addrSock)) < 0)
		{
			LOGE("CMMBSource::start  audio's socket is fail!");
			goto fail;
		}
	}

	 if(setsockopt(m_sockRev,SOL_SOCKET ,SO_RCVBUF,(char*)&recvsize,sizeof(int) )==-1)
	 {
	 	 LOGE((0, "CMMBSource::start setopt  socket failed,mime = %s",m_strMime));
		 goto fail;
	 }

	if(getsockopt(m_sockRev,SOL_SOCKET ,SO_RCVBUF,(void *)&getsizetemp,&lentemp )==-1)
	{
		LOGE((0, "CMMBSource::start getopt  socket failed,mime = %s",m_strMime));
		goto fail;
	}
	  
	if (((flags = fcntl(m_sockRev, 3, 0)) < 0) || (fcntl(m_sockRev, 4, flags | 4) < 0) )
	{
                LOGE((0, "CMMBSource::start set   socket non-blocking failed,mime = %s",m_strMime));
                goto fail;
	}         
	
	m_nDataEnd		= 0;
	m_nDataStart		= m_nBufferNum - 1;
	
    	pthread_attr_init(&attr);
    	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    	pthread_create(&m_Thread, &attr, ThreadWrapper, this);
    	pthread_attr_destroy(&attr);
		
	/*if (m_fAVStream == NULL)
	{
        	LOGE("Cannot open file");
		goto fail;
	}

	MultimediaIL_Reset(0);*/
		
success:

	LOGI("CMMBSource::start SUCCESS!");
	m_bStarted = true;
    	return OK;

fail:
	
	LOGE("***CMMBSource::start FAIL***");
	return err;
}

status_t CMMBSource::stop() 
{
	Mutex::Autolock autoLock(m_Lock);
	
	status_t err;
	void *dummy;

    	if (!m_bStarted)
		goto success;
		
	m_bStarted = false;
	
	pthread_join(m_Thread, &dummy);

	SAFE_DELETE(m_pGroup);
	
	//clear av data
	for (int i = 0; i < m_DataBuffers.size(); i++)
	{
		CMMBAVData *tempData = &m_DataBuffers.editItemAt(i);
		if (tempData != NULL &&  tempData->buffer != NULL)
			SAFE_FREE(tempData->buffer);
	}
	m_DataBuffers.clear();
	
	if (m_sockRev >= 0) 
	{
		close(m_sockRev);
		m_sockRev = -1;
	}
	/*if (m_fAVStream)
	{
		fclose(m_fAVStream);
		m_fAVStream	= NULL;
	}*/
	
	//relese the share mem
	//........
	
success:
	
    	return OK;

fail:

	return err;
}

int CMMBSource::addData(const uint8_t*const  data, size_t size, uint32_t ts)
{	
	Mutex::Autolock lock(m_Lock);
	
	if (!m_bStarted)
		return 0;
	
	if( m_nDataEnd  == m_nDataStart)
	{
		LOGE("****CMMBSource::GetFreeBuffer()! cannot get a free buffer! type = %s****",m_strMime);
		return 0;
	}
	
	/*TV_LOGI("====================================== SIZE = %d",size);
	if (m_Params.pType == MEDIA_MIMETYPE_VIDEO_AVC)
	{
		for (int i = 0;i < 5;i++)
			TV_LOGI("%x",*(data+i));
	}
	TV_LOGI("====================================== SIZE = %d",size);*/
	
	CMMBAVData	*temp = &m_DataBuffers.editItemAt(m_nDataEnd);
	int	type=(*(char*)data) & 0x1F;
	memcpy(temp->buffer,data,size);
	temp->nUnitLen 	= size;
	temp->nTs		= ts;
	temp->frame_type	= type;
	
	m_nDataEnd			= (m_nDataEnd + 1) % m_nBufferNum;
	m_BufferGet.signal();
	
	return size;
}

sp<MetaData> CMMBSource::getFormat() 
{
    Mutex::Autolock autoLock(m_Lock);

    return m_Format;
}

static uint32_t	VAL32(uint32_t   x)
{
  	u_char   *s   =   (u_char   *)&x; 
	return   (uint32_t)(s[0] << 24 | s[1]   << 16 | s[2] << 8 | s[3]); 
}

static int64_t	To64(uint32_t   x,uint32_t y)
{
	return   (int64_t)(y<<32 | x); 
}

// static
void *CMMBSource::ThreadWrapper(void *me) 
{
    	return (void *) static_cast<CMMBSource *>(me)->threadFunc();
}

status_t CMMBSource::threadFunc()
{
	status_t 	err = OK;
	uint8_t*  	cTempMem = (uint8_t*)malloc(30000);
	int		nLen;
	uint32_t	nSize;
	int64_t	nPts;
	LOGI("CMMBSource::threadFunc() start");
    	while (m_bStarted) 
	{
		if (m_sockRev >= 0) 
			nLen = recv(m_sockRev,cTempMem,30000,MSG_DONTWAIT);

		if (m_bStarted && nLen > 0)
		{
			nPts		= VAL32(*(uint32_t*)(char*)(cTempMem + 4));
			nSize 	= VAL32(*(uint32_t*)(char*)(cTempMem + 12));
			LOGI("CMMBSource::threadFunc():nPts = %d, nSize = %d",nPts,nSize);
			addData(cTempMem + 16 ,nSize,nPts);
		}
    	}
	LOGI("CMMBSource::threadFunc() end");	
   	return err;
}

status_t CMMBSource::read(
        MediaBuffer **out, const ReadOptions *options) 
{
    	Mutex::Autolock autoLock(m_Lock);
	
	static	int		nNum;
	nNum++;
	LOGI("CMMBSource::read START nNum = %d",nNum);	
	
	uint32_t	nStart;
	uint32_t	nEnd;
	status_t 	err	= UNKNOWN_ERROR;

	//char 	cHeader[16];
	uint32_t	nSize;
	int64_t	nPts;
	int 		type;

	MediaBuffer*		pMediaBuffer;
	CMMBAVData 		tempData;

	if(!m_bStarted)
		goto fail;

	err = m_pGroup->acquire_buffer(&pMediaBuffer);
	
	if (err != OK)
		goto fail;
	
	m_StreamData.data_ptr = (uint8*)pMediaBuffer->data();
	
	if (m_bFirstGet && !strncasecmp("audio/", m_strMime, 6))
	{
		uint8_t  temp[5]  = {19,16,86,229,152};
		memcpy(pMediaBuffer->data(),temp,5);
		m_bFirstGet	= false;
		nSize	= 5;
		nPts		= 0;
		nStart 	= 0;
		nEnd	= 4;
		goto success;
	}

	/*if (!strncasecmp("video/", m_strMime, 6))
		err = MultimediaIL_GetFrame(NULL,DPLY_STRM_VID_FRAME,&m_StreamData);
	else
		err = MultimediaIL_GetFrame(NULL,DPLY_STRM_AUD_FRAME,&m_StreamData);*/

	err = read(tempData);
	if (err != OK)
		goto fail;

	memcpy(pMediaBuffer->data(),tempData.buffer,tempData.nUnitLen);
	release(tempData);
	
	nPts		= tempData.nTs;
	nSize	= tempData.nUnitLen;	
	type		= tempData.frame_type;
	
	/*nPts 	= To64(m_StreamData.data_pos.pos_low32, 
		m_StreamData.data_pos.pos_up32);
	nSize	= m_StreamData.data_len;
	type		= m_StreamData.data_type;
	fread(cHeader,1,16,m_fAVStream);
	nPts		= VAL32(*(uint32_t*)(char*)(cHeader + 4));
	nSize 	= VAL32(*(uint32_t*)(char*)(cHeader + 12));
	
	if (fread((uint8_t *)pMediaBuffer->data(),1,nSize,m_fAVStream) == 0)
	{
		LOGI("*****CMMBSource::read: It's end!******");
		err 	= NOT_ENOUGH_DATA;
		goto fail;
	}*/
		
	nStart 	= 0;
	nEnd	= nSize - 1;
		
success:
	
	LOGI("CMMBSource::read:  nNum = %d nStart = %d  nEnd = %d  nPts = %d   type = %d  mime = %s",
		nNum,nStart,nEnd,nPts,type,m_strMime);

	pMediaBuffer->set_range(nStart, nEnd);
	pMediaBuffer->meta_data()->clear();
	pMediaBuffer->meta_data()->setInt64(
                    kKeyTime, (int64_t)nPts);
	*out = pMediaBuffer;
	LOGI("CMMBSource::read OK");

	return OK;

fail:
	
	LOGE("***CMMBSource::read FAIL***");
	return err;
}

int CMMBSource::read(CMMBAVData &buffer)
{
	Mutex::Autolock lock(m_Lock);
	
	if (!m_bStarted)
	{
		LOGI("CMMBSource::read m_bStarted == false");
		return -1;
	}
			
	int	nNext;
	
	while (m_bStarted)
	{
		nNext = (m_nDataStart + 1) % m_nBufferNum;
		
		if (nNext == m_nDataEnd)
			m_BufferGet.wait(m_Lock);
		
		if (!m_bStarted)
		{
			LOGI("CMMBSource::read m_bWorking == false");
			return -1;
		}

		nNext 	= (m_nDataStart + 1) % m_nBufferNum;
		buffer 	= m_DataBuffers.editItemAt(nNext);
		
		return OK;
	}
	
	LOGI("CMMBSource::read m_bWorking == false");
	return -1;

}

void	CMMBSource::release(CMMBAVData buffer)
{
	Mutex::Autolock lock(m_Lock);
	m_nDataStart = (m_nDataStart + 1) % m_nBufferNum;
}

}  // namespace android
