#include <media/stagefright/VideoPhoneWriter.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/mediarecorder.h>
#include <sys/prctl.h>
#include <sys/resource.h>

namespace android {

VideoPhoneWriter::VideoPhoneWriter(int handle)
    : m_nHandle(handle),
      m_nInitCheck(m_nHandle > 0 ? OK : NO_INIT),
      m_bStarted(false)
{
}

VideoPhoneWriter::~VideoPhoneWriter() 
{
    	if (m_bStarted) 
        	stop();

    	if (m_File != NULL) 
	{
        	fclose(m_File);
        	m_File = NULL;
    	}
}

status_t VideoPhoneWriter::initCheck() const 
{
    	return m_nInitCheck;
}

status_t VideoPhoneWriter::addSource(const sp<MediaSource> &source) 
{
	status_t		err = m_nInitCheck;    	
	const char*	mime;
    	int32_t 		nVideoWidth;
    	int32_t 		nVideoHeight;
	sp<MetaData>meta;	
	
	if (m_nInitCheck != OK)
        	goto over;

    	if (m_MediaSource != NULL)
    	{
        	err	= UNKNOWN_ERROR;
		goto over;
    	}

    	meta = source->getFormat();

    	CHECK(meta->findCString(kKeyMIMEType, &mime));
    	CHECK(meta->findInt32(kKeyWidth, &nVideoWidth));
    	CHECK(meta->findInt32(kKeyHeight, &nVideoHeight));

    	m_MediaSource = source;
		
	err = OK;
	
over:
	
    	return err;
}

status_t VideoPhoneWriter::start(MetaData *params) 
{
	status_t 		err =	m_nInitCheck;
	pthread_attr_t	attr;

    	if (m_nInitCheck != OK)
        	goto fail;

    	if (m_MediaSource == NULL)
    	{
        	err =  UNKNOWN_ERROR;
		goto fail;
    	}

    	if (m_bStarted)
        	goto success;

    	if ((err = m_MediaSource->start()) != OK)
		goto fail;

	m_bStarted = true;
    	pthread_attr_init(&attr);
    	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    	pthread_create(&m_Thread, &attr, ThreadWrapper, this);
    	pthread_attr_destroy(&attr);
	
success:
	
	LOGI("VideoPhoneWriter::start SUCCESS!");
    	return OK;

fail:

	LOGE("VideoPhoneWriter::start FAIL!");
	return err;

}

status_t VideoPhoneWriter::stop() 
{
	status_t err 	= OK;
	m_bStarted 	= false;

	if (!m_bStarted)
        	goto over;

    	void *dummy;
    	pthread_join(m_Thread, &dummy);

    	if ((err = (status_t) dummy) != OK)
    	{
    		m_MediaSource->stop();
		goto over;
    	}
		
       err = m_MediaSource->stop();

over:
	
	LOGI("VideoPhoneWriter::stop()  err = %d",err);
	
	if (err == ERROR_END_OF_STREAM) 
		err = OK;
	
   	return err;
}

status_t VideoPhoneWriter::pause()
{
	return stop();
}
	
// static
void *VideoPhoneWriter::ThreadWrapper(void *me) 
{
    	return (void *) static_cast<VideoPhoneWriter *>(me)->threadFunc();
}

status_t VideoPhoneWriter::threadFunc() 
{
	status_t err = OK;

    	prctl(PR_SET_NAME, (unsigned long)"VideoPhoneWriter", 0, 0, 0);
		
    	while (m_bStarted) 
	{
        	MediaBuffer *buffer;
        	err = m_MediaSource->read(&buffer);

        	if (err != OK)
            		break;

		ssize_t nLen = (ssize_t)buffer->range_length();
		
        	ssize_t n = write(m_nHandle,
                	(const uint8_t *)buffer->data() + buffer->range_offset(),
                	nLen);

		buffer->release();
            	buffer = NULL;
					
        	if (n < nLen) 
            		break;
    	}

    	//flush(m_nHandle);
    	/*fclose(m_File);
    	m_File 			= NULL;*/
    	m_bReachedEOS 	= true;
	
    	if (err == ERROR_END_OF_STREAM)
        	err	= OK;
	
   	return err;
}

bool VideoPhoneWriter::reachedEOS() 
{
	return m_bReachedEOS;
}

}  // namespace android

