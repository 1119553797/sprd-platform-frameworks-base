#ifndef CMMB_EXTRACTOR_H_

#define CMMB_EXTRACTOR_H_

#include <media/stagefright/MediaExtractor.h>
#include <utils/Vector.h>

#include <sys/types.h>
#include <stdint.h>

#include <media/stagefright/MediaErrors.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <media/stagefright/Innofidei.h>
//#include "AVBuffer.h"


// media service interface
/*
#include <libcmmbservice/include/CmmbSystem.h>
#include <libcmmbservice/include/ICmmbService.h>
#include <libcmmbservice/include/IEventNotifier.h>
#include <libcmmbservice/include/ILogicalChannel.h>
#include <libcmmbservice/include/IDataAvailableListener.h>
#include <libcmmbservice/include/IDemuxResultListener.h>
#include <libcmmbservice/include/IAvStreamHandle.h>
//#include <libcmmbservice/include/EventNotifierBase.h>
#include <libcmmbservice/include/DataAvailableListenerBase.h>
//#include <libcmmbservice/include/DemuxResultListenerBase.h>
#include <libcmmbservice/include/AvHandle.h>
*/

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryBase.h>

#include <utils/threads.h>
//#include "CMMBDemuxResultListener.h"

namespace android {

//#define CMMB_DEBUG
//#define CMMB_PROFILE
//#define DUMP_MFS_FILE
//#define DUMP_NAL_FILE

class DataSource;
class String8;


// The following parameters is fixed
#define CMMB_TIME_SCALE 22500
#define CMMB_VIDEO_WIDTH 320
#define CMMB_VIDEO_HEIGHT 240
#define CMMB_AAC_SAMPLE_RATE 24000
#define CMMB_AAC_NUM_CHANNELS 2

#define CMMB_DRA_SAMPLE_RATE 48000
#define CMMB_DRA_NUM_CHANNELS 2

#define CMMB_SAMPLE_MAX_SIZE 0xd800

#define MEDIA_SERVICE_BUFFER_INITIAL_WAIT_MS 1500
#define MEDIA_SERVICE_BUFFER_WAIT_PERIOD_MS 10
#define MEDIA_SERVICE_BUFFER_WAIT_TIME_OUT_MS 4000

#define CMMB_BUFFER_SECONDS 2
#define CMMB_CHECK_INTERVAL_MS 200  //200ms

class CMMBExtractor : public MediaExtractor {
public:
    // Extractor assumes ownership of "source".
    CMMBExtractor(const sp<DataSource> &source, bool draType = false);
    enum {
        VideoTrack        = 0,
        AudioTrack       = 1,
        DataTrack        = 2,
        DraAudioTrack		 = 3,
    };

    virtual size_t countTracks();
    virtual sp<MediaSource> getTrack(size_t index);
    virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);

    virtual sp<MetaData> getMetaData();

    void onMfsDataAvailable();

protected:
    virtual ~CMMBExtractor();

private:
    class Track {
    public:
        Track(uint32_t trackType);         
        sp<MetaData> meta;
        uint32_t timescale;
        uint8_t mTrackType;
        bool includes_expensive_metadata;
        bool skipTrack;
        virtual ~Track();
    };    

    sp<DataSource> mDataSource;
    bool mHaveMetadata;
    bool mHasVideo;
    bool mHasAudio;
    bool mHasData;

    Track *mVideoTrack, *mAudioTrack;
 
    sp<MetaData> mFileMetaData;

    Vector<uint32_t> mPath;

//    sp<ILogicalChannel> mCh;   
//    sp<AvHandle> mAudioHandle;
 //   sp<AvHandle> mVideoHandle;
    void * mVideoSock;
    void *mAudioSock;

#ifdef DUMP_MFS_FILE
    sp<DataAvailableListenerBase> mListener;
    int mFd;
#endif

    status_t readMetaData();    
    
    status_t parseMetaData(off_t offset, size_t size);
    status_t updateAudioTrackInfoFromESDS_MPEG4Audio(
            const void *esds_data, size_t esds_size);
    status_t initialize();
    status_t stop();

    CMMBExtractor(const CMMBExtractor &);
    CMMBExtractor &operator=(const CMMBExtractor &);
    bool mDraType;
};

}  // namespace android

#endif  // CMMB_EXTRACTOR_H_
