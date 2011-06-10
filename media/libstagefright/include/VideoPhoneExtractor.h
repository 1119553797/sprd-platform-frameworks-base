#ifndef VIDEOPHONE_EXTRACTOR_H_

#define VIDEOPHONE_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>

#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>

#include <stdio.h>
#include <fcntl.h>
#include <utils/Singleton.h>

#define USE_DATA_DEVICE


namespace android {

class DataSource;

class VideoPhoneSourceInterface
{
public:
    virtual int write(char* data, int nLen) = 0;
	virtual void stopCB() = 0;
    VideoPhoneSourceInterface() {};
    virtual ~VideoPhoneSourceInterface() {};
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
		void stopCB();
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
		
    	bool 			m_bForeStop;

	MediaBufferGroup*	m_pGroup;

	FILE*			m_fAVStream;

	int64_t			m_nStartSysTime;
        int64_t			m_nInitialDelayUs;
		
	uint8_t*			m_RingBuffer;

	int				m_nDataStart;

	int				m_nDataEnd;	

	size_t			m_nRingBufferSize;

	pthread_t 		m_Thread;

	Condition			m_GetBuffer;

	int			m_nNum;	
};

class VideoPhoneExtractor : public MediaExtractor 
{

public:
	
    // Extractor assumes ownership of "source".
    VideoPhoneExtractor(const sp<DataSource> &source, int decodeType);

    virtual 	size_t countTracks();
	
    virtual 	sp<MediaSource> getTrack(size_t index);
	
    virtual 	sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual 	sp<MetaData> getMetaData();

protected:
	
    virtual 	~VideoPhoneExtractor();

private:
	
	status_t	readMetaData();

private:

	uint32_t			m_Status;
	
	sp<MetaData> 	m_AVMeta;
		
	bool				m_bHaveMetadata;
	int					m_decodeType;

	sp<MetaData>	mFileMetaData;
    sp<DataSource> mDataSource;

};

class VideoPhoneDataDevice : public Singleton<VideoPhoneDataDevice>
{
public:
    FILE* m_fAVStream;
    status_t registerClient(VideoPhoneSourceInterface *client, sp<DataSource> dataSource);
    void unregisterClient(VideoPhoneSourceInterface *client);
	void stop();
	status_t start();

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

}  // namespace android
#endif
