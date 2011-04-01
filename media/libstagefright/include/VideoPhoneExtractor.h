#ifndef VIDEOPHONE_EXTRACTOR_H_

#define VIDEOPHONE_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>

namespace android {

class DataSource;

class VideoPhoneExtractor : public MediaExtractor 
{

public:
	
    // Extractor assumes ownership of "source".
    VideoPhoneExtractor(const sp<DataSource> &source);

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

	sp<MetaData>	mFileMetaData;
    sp<DataSource> mDataSource;

};

}  // namespace android
#endif
