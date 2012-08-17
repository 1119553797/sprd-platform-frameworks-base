
#ifndef FAKECAMERA_SOURCE_H_

#define FAKECAMERA_SOURCE_H_

#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaSource.h>
#include <utils/List.h>
#include <utils/RefBase.h>

namespace android {

class IMemory;

class FakeCameraSource : public MediaSource, public MediaBufferObserver {
public:
    /**
     * Factory method to create a new CameraSource using the current
     * settings (such as video size, frame rate, color format, etc)
     * from the default camera.
     *
     * @return NULL on error.
     */
    static FakeCameraSource *Create();

    virtual ~FakeCameraSource();

    virtual status_t start(MetaData *params = NULL);
    virtual status_t stop();
    virtual status_t read(MediaBuffer **buffer, const ReadOptions *options = NULL);

    /**
     * Returns the MetaData associated with the CameraSource,
     * including:
     * kKeyColorFormat: YUV color format of the video frames
     * kKeyWidth, kKeyHeight: dimension (in pixels) of the video frames
     * kKeySampleRate: frame rate in frames per second
     * kKeyMIMEType: always fixed to be MEDIA_MIMETYPE_VIDEO_RAW
     */
    virtual sp<MetaData> getFormat();
    virtual void signalBufferReturned(MediaBuffer* buffer);

protected:
    // isBinderAlive needs linkToDeath to work.
    class DeathNotifier: public IBinder::DeathRecipient {
    public:
        DeathNotifier() {}
        virtual void binderDied(const wp<IBinder>& who);
    };

    int32_t  mVideoHeight;
    int32_t  mVideoWidth;
    int32_t  mVideoFrameRate;
    int32_t  mColorFormat;

    sp<DeathNotifier> mDeathNotifier;
    sp<MetaData> mMeta;

    int64_t mStartTimeUs;
    int64_t mLastFrameTimestampUs;
    int32_t mNumFrames;
    bool mStarted;
    status_t mInitCheck;

    FakeCameraSource(int32_t videoWidth, int32_t vidoeHeight, int32_t frameRate);

private:

    Mutex mLock;
    List<int64_t> mFrameTimes;

    int64_t mFirstFrameTimeUs;

    FakeCameraSource(const FakeCameraSource &);
    FakeCameraSource &operator=(const FakeCameraSource &);
    status_t init();
};

}  // namespace android

#endif  // FAKECAMERA_SOURCE_H_
