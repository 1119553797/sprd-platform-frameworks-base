#define LOG_NDEBUG 0
#define LOG_TAG "VideoPhoneExtractor"
#include <utils/Log.h>
#include <cutils/properties.h>

#include "include/VideoPhoneExtractor.h"

#include <unistd.h> 
#include <math.h>

#include <errno.h>
#include <string.h>

#include <time.h>
#include <stdio.h>
#include <fcntl.h>

#define SAFE_DELETE(p) if ( (p) != NULL) {delete (p); (p) =NULL;}
#define	SAFE_FREE(p)	if ( (p) != NULL) {free(p); (p) =NULL;}
#define	MAX_BUFFER_SIZE	(128*1024)
#define MIME_H263 "video/3gpp"
#define MIME_MPEG4 "video/mp4v-es"

//#define DEBUG_FILE     "/data/vpin"
//#define DUMP_FILE
//#define DUMP_FIXFILE "/data/vpout"
#ifdef DUMP_FIXFILE
#define DUMP_FIXFILELEN "/data/vpout-len.txt"
#endif //DUMP_FIXFILE
#define FEATURE_COMBINE_MPEG4_HEADER

namespace android {

static bool s_bDebug = false;
#define DEBUG_LOGD if(s_bDebug)LOGV

VideoPhoneExtractor::VideoPhoneExtractor(const sp<DataSource> &source, int decodeType)
    : m_bHaveMetadata(false),
      mFileMetaData(new MetaData),
      mDataSource(source),
      m_decodeType(decodeType)
{    	
	LOGI("VideoPhoneExtractor::VideoPhoneExtractor, decodeType: %d", decodeType);
	
	char propBuf[PROPERTY_VALUE_MAX];  
        property_get("debug.videophone", propBuf, "");	
	LOGI("property_get: %s.", propBuf);
	if (!strcmp(propBuf, "1")) {
		s_bDebug = true;
	} else {
		s_bDebug = false;
	}
	
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
		//EsdsGenerator::generateEsds(m_AVMeta);
	}
		
	m_AVMeta->setInt32(kKeyRotation, 0);
	m_AVMeta->setInt32(kKeyWidth, 176);
	m_AVMeta->setInt32(kKeyHeight, 144);


success:
	
	LOGV("VideoPhoneExtractor::readMetaData SUCCESS");
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

    {
        VideoPhoneSource* pSource = new VideoPhoneSource(m_AVMeta, mDataSource);
        if (NULL != pSource) {
            pSource->setID(VideoPhoneDataDevice::DISPLAY_CLIENT);
        }
        return pSource;
    }
    
fail:
	
	LOGE("VideoPhoneExtractor::getTrack FAIL");
	return NULL;
}

/////////////////////////////////////////////////////////////////////
#undef LOG_TAG
#define LOG_TAG "VideoPhoneSource"

uint8_t VideoPhoneSource::m_Mpeg4Header[100] = {0};
int VideoPhoneSource::m_iMpeg4Header_size = 0;

static FILE* m_fWrite = 0;
static FILE* m_fLen = 0;

VideoPhoneSource::VideoPhoneSource(
        const sp<MetaData> &format,
        const sp<DataSource> &dataSource)
    : m_Format(format),
      m_DataSource(dataSource),
      m_bDataAvailable(false),
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
#ifndef DUMP_FIXFILE
	if (m_fWrite != NULL){
		fclose(m_fWrite);
	}
	if (m_fLen != NULL){
		fclose(m_fLen);
	}
#endif //DUMP_FIXFILE
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
	LOGV("file opened %p", m_fAVStream);
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
	LOGV("[%p]VideoPhoneSource::start 2", this);
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
	LOGV("[%p]VideoPhoneSource::stop SUCCESS!", this);

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
	DEBUG_LOGD("[%p]VideoPhoneSource::read START nNum = %d",this, m_nNum);	
	
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
	
	nSize = readRingBuffer(pMediaBuffer);
	if (nSize == 0){
		err = ERROR_END_OF_STREAM;
		goto fail;
	}
	
	nEnd = nSize;

success:
	
	DEBUG_LOGD("[%p]VideoPhoneSource::read:  nNum = %d nStart = %d  nEnd = %d  nPts = %d   type = %d  mime = %s",
		this, m_nNum,nStart,nEnd,nPts,type,m_strMime);

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
			//LOGV("VideoPhoneSource::read find I vop");
			pMediaBuffer->meta_data()->setInt32(kKeyIsSyncFrame, 1);
			// report i-frame to modem		
			int vt_pipe = -1;
			if (vt_pipe < 0) vt_pipe = open("/dev/rpipe/ril.vt.2", O_RDWR);
			if (vt_pipe > 0) {
				ssize_t size = ::write(vt_pipe, "0", 2);
				LOGV("write vt_pipe, size: %d", size);
				close(vt_pipe);
			}else {
    		    LOGE("open vt_pipe failed: %d", vt_pipe);
                LOGE("vt_pipe errno: %d, %s", errno, strerror(errno));
            }
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
	//LOGV("[%p]VideoPhoneSource::read OK", this);
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
		LOGV("before read %p", m_fAVStream);
    nLen = fread((void *)cTempbuffer,1, BUFFER_SIZE, m_fAVStream);
#else
		//LOGV("before read");
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
		//LOGV("after read %d", nLen);

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

	//LOGV("signal");
	m_bDataAvailable = true;
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
#ifdef DUMP_FIXFILE
	m_fWrite = fopen(DUMP_FIXFILE,"ab");
	m_fLen = fopen(DUMP_FIXFILELEN,"ab");
#else
	if (m_fWrite == NULL){
		sprintf(fn_fWrite, "/data/videocall%02d%02d%02d.3gp", p->tm_hour, p->tm_min, p->tm_sec);
		LOGV("fn_fWrite: %s", fn_fWrite);
		m_fWrite = fopen(fn_fWrite,"ab");
	}
	if (m_fLen == NULL){
		sprintf(fn_fLen, "/data/videocall-len%02d%02d%02d.txt", p->tm_hour, p->tm_min, p->tm_sec);
		LOGV("fn_fLen: %s", fn_fLen);
		m_fLen = fopen(fn_fLen,"ab");
	}
#endif //DUMP_FIXFILE

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
#ifdef DUMP_FIXFILE	
	if (m_fWrite != NULL){
		fclose(m_fWrite);
	}
	if (m_fLen != NULL){
		fclose(m_fLen);
	}
#endif
	return;

fail:
	LOGE("fhy: fail out");
}
#endif

int	VideoPhoneSource::readRingBuffer(MediaBuffer *pMediaBuffer)
{
	//LOGV("[%p]VideoPhoneSource::readRingBuffer START0", this);	
	char* data = (char*)pMediaBuffer->data();
	size_t nSize = pMediaBuffer->size();

	if (m_RingBuffer == NULL)
		return 0;

	int	nNext = m_nDataStart;
	int	nLen = 0, nExtraLen = 0;
	bool bStartRead = false;
	bool bIsMpege4 = false;
	bool bMpeg4Header = false;
	int	nStart = m_nDataStart, nEnd = m_nDataStart;
	//LOGV("[%p]VideoPhoneSource::readRingBuffer START1 %d, m_nDataStart: %d, m_nDataEnd: %d", this, m_bStarted, m_nDataStart, m_nDataEnd);
	while (m_bStarted)
	{
		nEnd	= nNext;
		nNext = (nNext + 1) % m_nRingBufferSize;

wait_again:		
		if (nNext == m_nDataEnd)
		{
			//LOGV("[%p]wait 1", this);
			m_GetBuffer.wait(m_Lock);
			//LOGV("[%p]wait 2", this);
		}
		
		if ((!m_bStarted) || m_bForeStop)
			return 0;

        if (!m_bDataAvailable){
            LOGV("[%p]VideoPhoneSource::readRingBuffer goto wait_again", this);  
            m_bDataAvailable = false;
            goto wait_again;
        }

		if (m_RingBuffer[nEnd] == 0x00 && 
			m_RingBuffer[(nEnd + 1) % m_nRingBufferSize] == 0x00)
		{
			if (m_RingBuffer[(nEnd + 2) % m_nRingBufferSize] == 0x01 &&
				m_RingBuffer[(nEnd + 3) % m_nRingBufferSize] == 0xb6)
			{
				if (!bStartRead)
				{
					DEBUG_LOGD("[%p]VideoPhoneSource::readRingBuffer START MEPGE4", this);
					if (bMpeg4Header) {
						bMpeg4Header = false;
						m_bHasMpeg4Header = true;
						m_iMpeg4Header_size = ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize;
						memcpy(m_Mpeg4Header,m_RingBuffer+nStart,m_iMpeg4Header_size);
						DEBUG_LOGD("FrameHeader intermedially follow mpeg4 header, Header_size: %d", m_iMpeg4Header_size);
					} else {
						nStart		= nEnd;
					}
#ifndef FEATURE_COMBINE_MPEG4_HEADER
					nStart		= nEnd;
#endif
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
					DEBUG_LOGD("[%p]VideoPhoneSource::readRingBuffer START VOP", this);
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
					LOGI("[%p]VideoPhoneSource::readRingBuffer START MEPGE4 Header", this);
					nStart		= nEnd;
					bMpeg4Header = true;
				}
				else
					break;
			}
		}
	}
	nLen = ((nEnd - nStart) + m_nRingBufferSize) % m_nRingBufferSize;
	
	DEBUG_LOGD("[%p]find frame size: %d, 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x", this, nLen, m_RingBuffer[nStart + 2], m_RingBuffer[nStart + 3], m_RingBuffer[nStart + 4], m_RingBuffer[nStart + 5]
	, m_RingBuffer[nStart + 6], m_RingBuffer[nStart + 7], m_RingBuffer[nStart + 8], m_RingBuffer[nStart + 9]);

	if (nLen > nSize)
	{
		LOGE("nLen %d exceeds nSize %d", nLen, nSize);
		return 0;
	}
	
	char* pOrginData = data;
#ifdef FEATURE_COMBINE_MPEG4_HEADER
	//LOGV("m_nNum %d, m_bHasMpeg4Header %d", m_nNum, m_bHasMpeg4Header);
	if ((m_nNum == 0) && (!m_bHasMpeg4Header)){
		if (!strcmp(m_strMime, MIME_MPEG4)){
			memcpy(data, m_Mpeg4Header, m_iMpeg4Header_size);
			data += m_iMpeg4Header_size;
			nExtraLen = m_iMpeg4Header_size;
			LOGI("add mpeg4 header");
		}
	}
#endif //FEATURE_COMBINE_MPEG4_HEADER

	int nTemp = nLen;

	if (nTemp > m_nRingBufferSize - nStart)
		nTemp = m_nRingBufferSize - nStart;
		
	memcpy(data,m_RingBuffer+nStart,nTemp);
	data += nTemp;
	
	if ((nTemp = nLen - nTemp) > 0)
		memcpy(data,m_RingBuffer ,nTemp);

#ifdef DUMP_FILE
#ifndef FEATURE_COMBINE_MPEG4_HEADER
	if ((m_nNum == 0) && m_bHasMpeg4Header) {
		LOGV("[%p]dumpToFile 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x", this, m_Mpeg4Header[0], m_Mpeg4Header[1], m_Mpeg4Header[ 2], m_Mpeg4Header[3]
			, m_Mpeg4Header[4], m_Mpeg4Header[4], m_Mpeg4Header[5], m_Mpeg4Header[6]);
		dumpToFile((char*)m_Mpeg4Header, m_iMpeg4Header_size);
	}
#endif
	LOGV("[%p]dumpToFile 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x 0x%02x", this, pOrginData[0], pOrginData[1], pOrginData[ 2], pOrginData[3]
		, pOrginData[4], pOrginData[4], pOrginData[5], pOrginData[6]);
	dumpToFile(pOrginData, (nLen + nExtraLen));
#endif

	m_nDataStart	= nEnd;
	//LOGV("[%p]VideoPhoneSource::readRingBuffer END", this);	
	return (nLen + nExtraLen);
}
	
int VideoPhoneSource::write(char* data,int nLen)
{
    DEBUG_LOGD("[%p]VideoPhoneSource::write nLen: %d", this, nLen);	
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

void VideoPhoneSource::setID(int id)
{
	LOGI("[%p]setID, id: %d", this, id);
	
	m_id = id;
}

int VideoPhoneSource::getID()
{
	LOGI("[%p]getID, id: %d", this, m_id);
	return m_id;
}

/////////////////////////////////////////////////////////////////////
#undef LOG_TAG
#define LOG_TAG "VideoPhoneDataDevice"

ANDROID_SINGLETON_STATIC_INSTANCE(VideoPhoneDataDevice);

VideoPhoneDataDevice::VideoPhoneDataDevice()
    : mDataSource(NULL),
      mStarted(false)
{
    mClients.clear();
    LOGI("[%p]VideoPhoneDataDevice created", this);
}

VideoPhoneDataDevice::~VideoPhoneDataDevice()
{
    LOGI("[%p]VideoPhoneDataDevice destroyed", this);
}

status_t VideoPhoneDataDevice::start()
{
	if (mStarted){
		LOGI("[%p]VideoPhoneDataDevice already started", this);
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
			LOGV("file opened %p", m_fAVStream);
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
    LOGI("[%p]stopThread", this);
    mStarted = false;
}

void VideoPhoneDataDevice::stop()
{
    LOGI("[%p]stop", this);
	stopThread();
}

void VideoPhoneDataDevice::clearClients()
{
    Mutex::Autolock autoLock(m_Lock);
    LOGI("[%p]clearClients", this);
    if (mStarted){
		LOGI("[%p]Cann't clear clients during running", this);
	} else {
	    // now device is stopped, so needn't stop clients, just directly clear them.
        mClients.clear();
	}
}

void VideoPhoneDataDevice::stopClient(int id)
{
    Mutex::Autolock autoLock(m_Lock);
    LOGI("[%p]stopClient %d", this, id);
    for (int i=0; i < mClients.size(); i++) {
        if (mClients[i]->getID() == id) {
            static_cast<VideoPhoneSourceInterface *>(mClients[i])->stopCB();
            return;
        }
    }
    LOGE("[%p]stopClient, cann't find %d", id);
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
    LOGI("[%p]register Client %p", this, client);
    return OK;
}

void VideoPhoneDataDevice::unregisterClient(VideoPhoneSourceInterface *client)
{
    Mutex::Autolock autoLock(m_Lock);
    mClients.remove(client);
    LOGI("[%p]unregister Client %p", this, client);
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
	
    LOGI("[%p]enter threadFunc", this);
    while (mStarted) 
    {
        Mutex::Autolock autoLock(m_Lock);

#ifdef DEBUG_FILE
   		if (m_fAVStream == NULL)
			break;
#else
		if (mDataSource->initCheck() != OK){			
			LOGE("initCheck fail,break");
			break;
		}
#endif

#ifdef DEBUG_FILE
		LOGV("before read %p", m_fAVStream);
    	nLen = fread((void *)cTempbuffer,1, BUFFER_SIZE, m_fAVStream);
#else
		//LOGV("before read");
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
			//LOGV("read nothing, mStarted: %d, nLen: %d, error: %s", mStarted, nLen, strerror(errno));
			if (mStarted){
                m_Lock.unlock();
				usleep(1000);
				continue;
			} else {
				DEBUG_LOGD("read nothing,break");
				break;
			}
#endif
		} else {
#ifdef DEBUG_FILE
			usleep(100 * 1000);
#endif
		}
		//LOGV("after read %d", nLen);

        for (int i = 0, n = mClients.size(); i < n; ++i) {
            static_cast<VideoPhoneSourceInterface *>(mClients[i])->write(cTempbuffer, nLen);
		//writeRingBuffer(cTempbuffer,nLen);
        }
    }
	
    for (int i = 0, n = mClients.size(); i < n; ++i) {
        static_cast<VideoPhoneSourceInterface *>(mClients[i])->stopCB();
    }
    LOGI("[%p]exit threadFunc", this);
	
    if (err == ERROR_END_OF_STREAM)
        err = OK;
		
    return err;
}

/////////////////////////////////////////////////////////////////////
#undef LOG_TAG
#define LOG_TAG "EsdsGenerator"

/*uint8_t EsdsGenerator::m_Mpeg4Header[100] = {0x00, 0x00, 0x01, 0xb0, 0x05, 0x00, 0x00, 0x01, 0xb5, 0x09, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
 0x01, 0x20, 0x00, 0x84, 0x40, 0x07, 0xa8, 0x2c, 0x20, 0x90, 0xa2, 0x8f};
{0x00, 0x00, 0x01, 0xb0, 0x01, 0x00, 0x00, 0x01, 0xb5, 0x09, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
 0x01, 0x20, 0x00, 0x84, 0x5d, 0x4c, 0x28, 0x2c, 0x20, 0x90, 0xa2, 0x1f};*/
/*{0x00, 0x00, 0x01, 0xb0, 0x14, 0x00, 0x00, 0x01, 0xb5, 0x09, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
 0x01, 0x20, 0x00, 0x84, 0x40, 0xfa, 0x28, 0x2c, 0x20, 0x90, 0xa2, 0x1f, 0x00, 0x00, 0x00, 0x00};
int EsdsGenerator::m_iMpeg4Header_size = 28;*/

static EsdsGenerator* g_Esds = NULL;

EsdsGenerator::EsdsGenerator()
{
	memset(m_EsdsBuffer, 0, 150);
	m_iEsds_size = 0;
}

EsdsGenerator::~EsdsGenerator()
{
}

void EsdsGenerator::generateEsds(sp<MetaData> AVMeta)
{
	LOGI("generateEsds()");

	g_Esds = new EsdsGenerator();
	
	//writeInt32(0);			 // version=0, flags=0
	g_Esds->writeInt8(0x03);  // ES_DescrTag
	g_Esds->writeInt8(23 + VideoPhoneSource::m_iMpeg4Header_size);
	g_Esds->writeInt16(0x0000);  // ES_ID
	g_Esds->writeInt8(0x1f);
	g_Esds->writeInt8(0x04);  // DecoderConfigDescrTag
	g_Esds->writeInt8(15 + VideoPhoneSource::m_iMpeg4Header_size);
	g_Esds->writeInt8(0x20);  // objectTypeIndication ISO/IEC 14492-2
	g_Esds->writeInt8(0x11);  // streamType VisualStream
	
	static const uint8_t kData[] = {
		0x01, 0x77, 0x00,
		0x00, 0x03, 0xe8, 0x00,
		0x00, 0x03, 0xe8, 0x00
	};
	g_Esds->writeEsds(kData, sizeof(kData));
	
	g_Esds->writeInt8(0x05);  // DecoderSpecificInfoTag
	
	g_Esds->writeInt8(VideoPhoneSource::m_iMpeg4Header_size);
	g_Esds->writeEsds(VideoPhoneSource::m_Mpeg4Header,VideoPhoneSource::m_iMpeg4Header_size);
	
	static const uint8_t kData2[] = {
		0x06,  // SLConfigDescriptorTag
		0x01,
		0x02
	};
	g_Esds->writeEsds(kData2, sizeof(kData2));
	
	AVMeta->setData(
				kKeyESDS, 0,
				g_Esds->m_EsdsBuffer, g_Esds->m_iEsds_size);	
	delete g_Esds;
}

void EsdsGenerator::writeEsds(const void *ptr, size_t size)
{
	if (m_iEsds_size + size > 1500) {
		LOGE("writeEsds(), buffer overflow");
		return;
	}

	memcpy(m_EsdsBuffer + m_iEsds_size, ptr, size);
	m_iEsds_size += size;
	LOGV("writeEsds(), size: %d, m_iEsds_size: %d", size, m_iEsds_size);
}

void EsdsGenerator::writeInt8(int8_t x)
{
	LOGV("writeInt8(), x: %d, 0x%x", x, x);
	writeEsds(&x, 1);
}

void EsdsGenerator::writeInt16(int16_t x)
{
	LOGV("writeInt16(), x: %d, 0x%x", x, x);
	writeEsds(&x, 2);
}

void EsdsGenerator::writeInt32(int32_t x)
{
	LOGV("writeInt32(), x: %d, 0x%x", x, x);
	writeEsds(&x, 4);
}

}  // namespace android

