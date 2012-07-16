#ifndef MxdCMMBExtractor_H_
#define MxdCMMBExtractor_H_

#include <media/stagefright/MediaExtractor.h>
#include <utils/Vector.h>

#include <sys/types.h>
#include <stdint.h>

#include <media/stagefright/MediaErrors.h>
#include <utils/RefBase.h>
#include <utils/threads.h>

#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryBase.h>


namespace android {

	class MxdCMMBExtractor : public MediaExtractor {
	public:
		MxdCMMBExtractor(const char* filename);
		

		virtual size_t countTracks();
		virtual sp<MediaSource> getTrack(size_t index);
		virtual sp<MetaData> getTrackMetaData(size_t index, uint32_t flags);
		virtual sp<MetaData> getMetaData();

		static bool bufferingUpdateOk();
		static int mIsStreamDone;
		static void* gVideoClient;
		static void* gAudioClient;

	private:
		status_t init();
		status_t deInit();
		status_t prebuiltAVParam();    
		status_t setMP4AParam(const void *p, size_t len);

	protected:
		virtual ~MxdCMMBExtractor();
	private:

		char mFileName[256];
		bool mAVKeyInfo;
		sp<MetaData> mCmmbMetaData;
		void * mVideoStream;
		void *mAudioStream;

		class Track {
		public:
			Track(uint32_t type);    
			virtual ~Track();
			sp<MetaData> meta;
			uint32_t timestampscale;
			
		};    

		Track *mVideoTrack;
		Track *mAudioTrack;

	};

}  // namespace android

#endif  // MxdCMMBExtractor_H_
