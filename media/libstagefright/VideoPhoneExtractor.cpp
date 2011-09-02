//#define LOG_NDEBUG 0
#define LOG_TAG "VideoPhoneExtractor"
#include <utils/Log.h>

#include "include/VideoPhoneExtractor.h"

#include <unistd.h> 
#include <math.h>

#include <errno.h>
#include <string.h>

#include <time.h>
#include <stdio.h>

#define SAFE_DELETE(p) if ( (p) != NULL) {delete (p); (p) =NULL;}
#define	SAFE_FREE(p)	if ( (p) != NULL) {free(p); (p) =NULL;}
#define	MAX_BUFFER_SIZE	(128*1024)
#define MIME_H263 "video/3gpp"
#define MIME_MPEG4 "video/mp4v-es"

//#define DEBUG_FILE     "/data/vpin"
//#define DUMP_FILE	"/data/vpout"

namespace android {
VideoPhoneExtractor::VideoPhoneExtractor(const sp<DataSource> &source, int decodeType)
    : m_bHaveMetadata(false),
      mFileMetaData(new MetaData),
      mDataSource(source),
      m_decodeType(decodeType)
{    	
	LOGI("VideoPhoneExtractor::VideoPhoneExtractor, decodeType: %d", decodeType);
	
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
	LOGI("VideoPhoneExtractor::readMetaData START, %d", m_decodeType);

    	if (m_bHaveMetadata)
        	goto success;

	if (m_decodeType == 1){
		mFileMetaData->setCString(kKeyMIMEType, MIME_H263);
		m_AVMeta->setCString(kKeyMIMEType, MIME_H263);
	} else {
		mFileMetaData->setCString(kKeyMIMEType, MIME_MPEG4);
		m_AVMeta->setCString(kKeyMIMEType, MIME_MPEG4);
	}
		
	m_AVMeta->setInt32(kKeyRotation, 0);
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

uint8_t VideoPhoneSource::m_Mpeg4Header[1024] = {0};
int VideoPhoneSource::m_iMpeg4Header_size = 0;
static FILE* m_fWrite = 0;
static FILE* m_fLen = 0;

VideoPhoneSource::VideoPhoneSource(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource)
    : m_Format(format),
      m_DataSource(dataSource),
      m_bStarted(false),
      m_bForeStop(false),
      m_pGroup(NULL),
      m_nNum(0),
      m_bHasMpeg4Header(false)
{
	LOGI("[%p]VideoPhoneSource::VideoPhoneSource", this);
	m_fAVStream		= NULL;
	m_fWrite = NULL;
	m_fLen = NULL;
}

VideoPhoneSource::~VideoPhoneSource() 
{	
	LOGI("[%p]VideoPhoneSource::~VideoPhoneSource", this);
	if (m_bStarted)
		stop();
	if (m_fWrite != NULL){
		fclose(m_fWrite);
	}
	if (m_fLen != NULL){
		fclose(m_fLen);
	}
}

status_t VideoPhoneSource::start(MetaData *params) 
{
	Mutex::Autolock autoLock(m_Lock);
		
	LOGI("[%p]VideoPhoneSource::start", this);
	//m_bFirstGet	= true;	
	status_t 		err 		= NO_MEMORY;
	bool			bRet	= false;
#ifndef USE_DATA_DEVICE	
	pthread_attr_t attr;
#endif

	if (m_bStarted)
		goto success;

	m_bStarted = true;
	m_bForeStop = false;
	
	m_nInitialDelayUs = 300000; //300 um to syc with audio

	m_pGroup = new MediaBufferGroup;
    m_pGroup->add_buffer(new MediaBuffer(30000));

	bRet = m_Format->findCString(kKeyMIMEType, &m_strMime);
	LOGI("[%p]VideoPhoneSource::start 1, bRet: %d, m_strMime: %s", this, bRet, m_strMime);

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
	LOGI("[%p]VideoPhoneSource::start 2", this);
    if (VideoPhoneDataDevice::getInstance().registerClient(this, m_DataSource) != OK)
    {
        LOGE("[%p]Cannot register client", this);
        goto fail;
    }
	VideoPhoneDataDevice::getInstance().start();
#endif		
		
success:

	LOGI("[%p]VideoPhoneSource::start SUCCESS!", this);
    	return OK;

fail:
	
	LOGE("[%p]***VideoPhoneSource::start FAIL***", this);
	SAFE_FREE(m_RingBuffer);
	return err;
}

status_t VideoPhoneSource::stop() 
{
	Mutex::Autolock autoLock(m_Lock);
	
	status_t err;
	
	LOGI("[%p]VideoPhoneSource::stop", this);
    if (!m_bStarted)
		goto success;

	m_bStarted = false;
	
	m_GetBuffer.signal();
	
    VideoPhoneDataDevice::getInstance().unregisterClient(this);
	
	//SAFE_DELETE(m_pGroup);

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
	LOGI("[%p]VideoPhoneSource::stop SUCCESS!", this);

    return OK;

fail:

	return err;
}

sp<MetaData> VideoPhoneSource::getFormat() 
{
    Mutex::Autolock autoLock(m_Lock);

    return m_Format;
}

typedef struct{
	unsigned char *start;
	unsigned char *curent;
	unsigned char   current_byte;
	int current_bit;
	int length;
}video_srteam_t;

static int video_stream_init(video_srteam_t *pStream,unsigned char *start,int length)
{
	if(length<=0)
		return 1;
	pStream->start = start;
	pStream->curent = start;
	pStream->current_bit = 0;
	pStream->length = length;
	return 0;
}

static unsigned int show_video_bits(video_srteam_t *pStream,int num)//num<=32
{
	unsigned int first32bits;
	unsigned int firstByte = 0;
	unsigned int secondByte = 0;	
	unsigned int thirdByte = 0;	
	unsigned int fourthByte = 0;
	unsigned int fifthByte = 0;	
	if((pStream->curent)<(pStream->start+pStream->length ))
		firstByte =  *pStream->curent;
	if((pStream->curent+1)<(pStream->start+pStream->length ))
		secondByte =  *(pStream->curent+1);	
	if((pStream->curent+2)<(pStream->start+pStream->length ))
		thirdByte =  *(pStream->curent+2);
	if((pStream->curent+3)<(pStream->start+pStream->length ))
		fourthByte =  *(pStream->curent+3);	
	if((pStream->curent+4)<(pStream->start+pStream->length ))
		fifthByte =  *(pStream->curent+4);	

	first32bits = (firstByte<<24)|(secondByte<<16)|(thirdByte<<8)|(fourthByte);
	if(pStream->current_bit!=0)
		first32bits = (first32bits<<pStream->current_bit)|(fifthByte>>(8-pStream->current_bit));
	
	return first32bits>>(32-num);
}

static void  flush_video_bits(video_srteam_t *pStream,int num)//num<=32
{
	pStream->curent += (pStream->current_bit+num)/8; 
	pStream->current_bit = (pStream->current_bit+num)%8; 
}
static unsigned int read_video_bits(video_srteam_t *pStream,int num)//num<=32
{
	unsigned int tmp = show_video_bits(pStream,num);
	flush_video_bits(pStream,num);
	return tmp;
}

static int decode_h263_header(video_srteam_t *pStream,int *is_I_vop)
{
	unsigned int tmpVar = show_video_bits(pStream,22);
	if(0x20 != tmpVar)
		return 1;
	flush_video_bits(pStream,22);
	tmpVar = read_video_bits(pStream,9);
	if(!(tmpVar&0x1))
		return 1;
	tmpVar = read_video_bits(pStream,7);	
	if(tmpVar>>3)
		return 1;
	tmpVar = tmpVar&0x07;
	if(tmpVar==7) //do not  support  EXTENDED_PTYPE
		return 1;
	tmpVar = read_video_bits(pStream,11);	
	if((tmpVar>>10)==0){
		*is_I_vop = 1;
	}else{
		*is_I_vop = 0;		
	}
	return 0;
}

static int decode_mpeg4_header(video_srteam_t *pStream,int *is_I_vop)
{
	unsigned int tmpVar, uStartCode = 0;
	int loopNum = 0;
	int vopType;
	while(uStartCode!=0x1B6){
		uStartCode = show_video_bits(pStream,32);
		if(0x1B6 == uStartCode){
			tmpVar = read_video_bits(pStream,32);	
			tmpVar =  read_video_bits(pStream,3);
			vopType = tmpVar>>1;
			if(vopType==0){
				*is_I_vop = 1;
			}else{
				*is_I_vop = 0;		
			}
		}else{
			read_video_bits(pStream,8);	
		}	
		loopNum++;
		if(loopNum>2048)
			return 1;
	}
	return 0;
}

static int get_video_stream_info(video_srteam_t *pStream,int *is_I_vop)
{
	int is_h263;
	*is_I_vop = 0;
	is_h263 = (show_video_bits(pStream,21)==0x10);
	if(is_h263){
		return decode_h263_header(pStream,is_I_vop);
	}else{
		return decode_mpeg4_header(pStream,is_I_vop);
	}	
}

status_t VideoPhoneSource::read(
        MediaBuffer **out, const ReadOptions *options) 
{
    Mutex::Autolock autoLock(m_Lock);
	//LOGI("[%p]VideoPhoneSource::read START nNum = %d",this, m_nNum);	
	
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
	
	nSize = readRingBuffer((char*)pMediaBuffer->data(), pMediaBuffer->size());
	if (nSize == 0){
		err = NOT_ENOUGH_DATA;
		goto fail;
	}
	
	nEnd = nSize;

success:
	
	//LOGI("[%p]VideoPhoneSource::read:  nNum = %d nStart = %d  nEnd = %d  nPts = %d   type = %d  mime = %s",
		//this, m_nNum,nStart,nEnd,nPts,type,m_strMime);

	pMediaBuffer->set_range(nStart, nEnd);
	pMediaBuffer->meta_data()->clear();

	if (m_nNum == 0)
		m_nStartSysTime	= nanoseconds_to_milliseconds(systemTime());

	{
		video_srteam_t streambuf;
		int ret = video_stream_init(&streambuf,(unsigned char *)pMediaBuffer->data(),nSize);
		if(ret!=0)
			goto fail;
		int is_I_vop;
		ret =get_video_stream_info(&streambuf,&is_I_vop);
		if((ret==0)&&(is_I_vop==1)){
			//LOGI("VideoPhoneSource::read find I vop");
			pMediaBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);		
		}
		if(m_nNum ==0)
			pMediaBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);			
	}
#ifdef DEBUG_FILE
	pMediaBuffer->meta_data()->setInt64(
                    kKeyTime,  
		   m_nInitialDelayUs + m_nNum*60*1000);
	usleep(50*1000);
#else
	pMediaBuffer->meta_data()->setInt64(
                    kKeyTime,  
                    1000 * (nanoseconds_to_milliseconds(systemTime()) -m_nStartSysTime));	
#endif

    *out = pMediaBuffer;
	//LOGI("[%p]VideoPhoneSource::read OK", this);
	m_nNum++;
	return OK;

fail:
	//err = ERROR_UNSUPPORTED;
	if (pMediaBuffer != NULL){
		*out = pMediaBuffer;
		pMediaBuffer->set_range(0, 0);
		pMediaBuffer->meta_data()->clear();
		pMediaBuffer->meta_data()->setInt64(
	                    kKeyTime,  
	                    1000 * (nanoseconds_to_milliseconds(systemTime()) -m_nStartSysTime));	
	}
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
		//LOGI("before read");
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
		//LOGI("after read %d", nLen);

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

	//LOGI("signal");
	m_GetBuffer.signal();
	
	return nLen;	
}

#ifdef DUMP_FILE
void dumpToFile(char *w_ptr, int w_len)
{
	char fn_fWrite[100] = {0};
	char fn_fLen[100] = {0};
	char buf[10] = {0};
	time_t timep;
	struct tm *p;
	time(&timep);
	p=gmtime(&timep);
	//printf(“%d%d%d”,(1900+p->tm_year), (1+p->tm_mon),p->tm_mday);
	if (m_fWrite == NULL){
		sprintf(fn_fWrite, "/data/videocall%02d%02d%02d.3gp", p->tm_hour, p->tm_min, p->tm_sec);
		LOGI("fn_fWrite: %s", fn_fWrite);
		m_fWrite = fopen(fn_fWrite,"ab");
	}
	if (m_fLen == NULL){
		sprintf(fn_fLen, "/data/videocall-len%02d%02d%02d.txt", p->tm_hour, p->tm_min, p->tm_sec);
		LOGI("fn_fLen: %s", fn_fLen);
		m_fLen = fopen(fn_fLen,"ab");
	}

	if (m_fWrite == NULL){	
		LOGE("fhy: fopen() failed, m_fWrite: 0x%p", m_fWrite);	
		goto fail;
	}
	if (m_fLen == NULL){	
		LOGE("fhy: fopen() failed, m_fLen: 0x%p", m_fLen);	
		goto fail;
	}
	
	LOGE("fhy: fwrite(), w_len: %d", w_len);	
	fwrite(w_ptr,1,w_len,m_fWrite);
	sprintf(buf, "%d\n", w_len);
	LOGE("fhy: fwrite(), buf: %s", buf);	
	fwrite(buf, 1, strlen(buf)+1, m_fLen);
	return;

fail:
	LOGE("fhy: fail out");
}
#endif

int	VideoPhoneSource::readRingBuffer(char* data, size_t nSize)
{
	//LOGI("[%p]VideoPhoneSource::readRingBuffer START0", this);	

	if (m_RingBuffer == NULL)
		return 0;

	int	nNext = m_nDataStart;
	int	nLen = 0, nExtraLen = 0;
	bool bStartRead = false;
	bool bIsMpege4 = false;
	bool bMpeg4Header = false;
	int	nStart = m_nDataStart, nEnd = m_nDataStart;
	//LOGI("[%p]VideoPhoneSource::readRingBuffer START1 %d, m_nDataStart: %d, m_nDataEnd: %d", this, m_bStarted, m_nDataStart, m_nDataEnd);
	while (m_bStarted)
	{
		nEnd	= nNext;
		nNext = (nNext + 1) % m_nRingBufferSize;
		
		if (nNext == m_nDataEnd)
		{
			//LOGI("[%p]wait 1", this);
			m_GetBuffer.wait(m_Lock);
			//LOGI("[%p]wait 2", this);
		}
		
		if ((!m_bStarted) || m_bForeStop)
			return 0;

		if (m_RingBuffer[nEnd] == 0x00 && 
			m_RingBuffer[(nEnd + 1) % m_nRingBufferSize] == 0x00)
		{
			if (m_RingBuffer[(nEnd + 2) % m_nRingBufferSize] == 0x01 &&
				m_RingBuffer[(nEnd + 3) % m_nRingBufferSize] == 0xb6)
			{
				if (!bStartRead)
				{
					//LOGI("VideoPhoneSource::readRingBuffer START MEPGE4");
					if (bMpeg4Header) {
						bMpeg4Header = false;
						m_bHasMpeg4Header = true;
						m_iMpeg4Header_size = ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize;
						memcpy(m_Mpeg4Header,m_RingBuffer+nStart,m_iMpeg4Header_size);
						LOGI("FrameHeader intermedially follow mpeg4 header, Header_size: %d", m_iMpeg4Header_size);
					} else {
						nStart		= nEnd;
					}
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
					//LOGI("VideoPhoneSource::readRingBuffer START VOP");
					nStart		= nEnd;
					bStartRead 	= true;
				}
				else
					break;
			}
			else if (m_RingBuffer[(nEnd + 2) % m_nRingBufferSize] == 0x01 &&
				m_RingBuffer[(nEnd + 3) % m_nRingBufferSize] == 0xb0)
			{
				if (!bStartRead)
				{
					LOGI("VideoPhoneSource::readRingBuffer START MEPGE4 Header");
					nStart		= nEnd;
					bMpeg4Header = true;
				}
				else
					break;
			}
		}
	}
	LOGI("[%p]find frame 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x", this, m_RingBuffer[nStart + 2], m_RingBuffer[nStart + 3], m_RingBuffer[nStart + 4], m_RingBuffer[nStart + 5]
	, m_RingBuffer[nStart + 6], m_RingBuffer[nStart + 7], m_RingBuffer[nStart + 8], m_RingBuffer[nStart + 9]);

	nLen = ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize;

	if (nLen > nSize)
	{
		LOGE("nLen %d exceeds nSize %d", nLen, nSize);
		return 0;
	}
#ifdef DUMP_FILE
	dumpToFile(data, nLen);
#endif
	
	//LOGI("m_nNum %d, m_bHasMpeg4Header %d", m_nNum, m_bHasMpeg4Header);
	if ((m_nNum == 0) && (!m_bHasMpeg4Header)){
		if (!strcmp(m_strMime, MIME_MPEG4)){
			memcpy(data, m_Mpeg4Header, m_iMpeg4Header_size);
			data += m_iMpeg4Header_size;
			nExtraLen = m_iMpeg4Header_size;
			LOGI("add mpeg4 header");
		}
	}

	int nTemp = nLen;

	if (nTemp > m_nRingBufferSize - nStart)
		nTemp = m_nRingBufferSize - nStart;
		
	memcpy(data,m_RingBuffer+nStart,nTemp);
	data += nTemp;
	
	if ((nTemp = nLen - nTemp) > 0)
		memcpy(data,m_RingBuffer ,nTemp);
	
	m_nDataStart	= nEnd;
	//LOGI("[%p]VideoPhoneSource::readRingBuffer END", this);	
	return (nLen + nExtraLen);
}
	
int VideoPhoneSource::write(char* data,int nLen)
{
    LOGI("[%p]VideoPhoneSource::write nLen: %d", this, nLen);	
    return writeRingBuffer(data, nLen);
}

void VideoPhoneSource::stopCB()
{
	LOGI("[%p]stopCB, m_bForeStop: %d", this, m_bForeStop);
	
    if ((!m_bStarted) || m_bForeStop)
		return;

	m_bForeStop = true;
	
	m_GetBuffer.signal();
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

status_t VideoPhoneDataDevice::start()
{
	if (mStarted){
		LOGV("VideoPhoneDataDevice already started");
		return OK;
	} else {
		return startThread();
	}
}

status_t VideoPhoneDataDevice::startThread()
{
#ifdef DEBUG_FILE
		m_fAVStream = fopen(DEBUG_FILE,"r");
		if (m_fAVStream != NULL) fseek(m_fAVStream, 0, SEEK_SET);
		if (m_fAVStream == NULL)
		{
			LOGE("Cannot open file %s", DEBUG_FILE);
		} else {
			LOGD("file opened %p", m_fAVStream);
		}
#endif


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
    LOGI("stopThread");
    mStarted = false;
}

void VideoPhoneDataDevice::stop()
{
    LOGI("stop");
	stopThread();
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
		if (mDataSource->initCheck() != OK){			
			LOGI("initCheck fail,break");
			break;
		}
#endif

#ifdef DEBUG_FILE
		LOGI("before read %p", m_fAVStream);
    	nLen = fread((void *)cTempbuffer,1, BUFFER_SIZE, m_fAVStream);
#else
		//LOGI("before read");
		nLen = mDataSource->readAt(0, (void *)cTempbuffer, BUFFER_SIZE);
#endif
		if (nLen <= 0)
		{
#ifdef DEBUG_FILE
			LOGW("read error %s", strerror(errno));
			if (feof(m_fAVStream)) break;
			usleep(1000*1000);
			break;
#else
			//LOGI("read nothing, mStarted: %d, nLen: %d, error: %s", mStarted, nLen, strerror(errno));
			if (mStarted){
				usleep(1000);
				continue;
			} else {
				LOGI("read nothing,break");
				break;
			}
#endif
		} else {
#ifdef DEBUG_FILE
			usleep(100 * 1000);
#endif
		}
		//LOGI("after read %d", nLen);

        for (int i = 0, n = mClients.size(); i < n; ++i) {
            static_cast<VideoPhoneSourceInterface *>(mClients[i])->write(cTempbuffer, nLen);
		//writeRingBuffer(cTempbuffer,nLen);
        }
    }
	
    for (int i = 0, n = mClients.size(); i < n; ++i) {
        static_cast<VideoPhoneSourceInterface *>(mClients[i])->stopCB();
    }
    LOGI("exit threadFunc");
	
    if (err == ERROR_END_OF_STREAM)
        err = OK;
		
    return err;
}

}  // namespace android

