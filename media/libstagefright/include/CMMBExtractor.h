#ifndef CMMB_EXTRACTOR_H_

#define CMMB_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>

namespace android {

class DataSource;

class CMMBExtractor : public MediaExtractor 
{

public:
	
    // Extractor assumes ownership of "source".
    CMMBExtractor(const sp<DataSource> &source);

    virtual 	size_t countTracks();
	
    virtual 	sp<MediaSource> getTrack(size_t index);
	
    virtual 	sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual 	sp<MetaData> getMetaData();

protected:
	
    virtual 	~CMMBExtractor();

private:
	
	status_t	readMetaData();

private:

	enum ChannelStatus
	{
		START			= 0x1,
		TV				= 0x4,
		BACKGROUND	= 0x8,
	};
	
	enum AVMetaName
	{
		AUDIO_META,
		VIDEO_META,
	};

	uint32_t			m_Status;
	
	sp<MetaData> 	m_AVMeta[2];
		
	bool				m_bHaveMetadata;

	sp<MetaData>	mFileMetaData;

};

}  // namespace android

#endif
