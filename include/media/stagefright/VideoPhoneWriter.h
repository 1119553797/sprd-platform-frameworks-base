
#ifndef VIDEOPHONE_WRITER_H_

#define VIDEOPHONE_WRITER_H_

#include <stdio.h>

#include <media/stagefright/MediaWriter.h>
#include <utils/threads.h>

namespace android {

struct MediaSource;
struct MetaData;

struct VideoPhoneWriter : public MediaWriter 
{

    	VideoPhoneWriter(int	handle);

    	status_t 			initCheck() const;

    	virtual status_t 	addSource(const sp<MediaSource> &source);
		
    	virtual bool 		reachedEOS();
		
    	virtual status_t 	start(MetaData *params = NULL);
		
    	virtual status_t 	stop();

	virtual status_t 	pause();

protected:
	
    	virtual ~VideoPhoneWriter();

private:
	
	static void *	ThreadWrapper(void *);
	
    	status_t 		threadFunc();
		
private:
	
    	FILE*			m_File;

	int				m_nHandle;
	
    	status_t 			m_nInitCheck;
	
    	sp<MediaSource> 	m_MediaSource;
	
    	bool 				m_bStarted;
	
    	volatile bool 		m_bReachedEOS;
	
    	pthread_t 			m_Thread;
	
    	int64_t 			m_nEstimatedSizeBytes;
	
    	int64_t 			m_nEstimatedDurationUs;

};

}  // namespace android

#endif  // AMR_WRITER_H_

