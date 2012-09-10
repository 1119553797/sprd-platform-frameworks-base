/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "AwesomePlayer"
#include <utils/Log.h>

#include <dlfcn.h>

#include "include/ARTSPController.h"
#include "include/AwesomePlayer.h"
#include "include/LiveSource.h"
#include "include/SoftwareRenderer.h"
#include "include/NuCachedSource2.h"
#include "include/ThrottledSource.h"
#include "include/MPEG2TSExtractor.h"
#include "include/ThreadedSource.h"

#include "ARTPSession.h"
#include "APacketSource.h"
#include "ASessionDescription.h"
#include "UDPPusher.h"

#include <binder/IPCThreadState.h>
#include <media/stagefright/AudioPlayer.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/FileSource.h>
#include <media/stagefright/CharDeviceSource.h>//sprd vt must
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <media/stagefright/CmmbUriSource.h>//cmmb


#include "MxdCMMBExtractor.h"


#include <surfaceflinger/ISurface.h>

#include <media/stagefright/foundation/ALooper.h>

#include <ctype.h>
#include <cutils/properties.h>
#ifdef USE_GETFRAME
#include <private/media/VideoFrame.h>
#endif

#define _SYNC_USE_SYSTEM_TIME_
namespace android {

static int64_t kLowWaterMarkUs = 500000ll;  // 2secs @hong
static int64_t kHighWaterMarkUs = 2500000ll;  // 10secs @hong
static int64_t kStartLowWaterMarkUs = 80000ll;
static const size_t kLowWaterMarkBytes = 40000;
static const size_t kHighWaterMarkBytes = 200000;

struct AwesomeEvent : public TimedEventQueue::Event {
    AwesomeEvent(
            AwesomePlayer *player,
            void (AwesomePlayer::*method)())
        : mPlayer(player),
          mMethod(method) {
    }

protected:
    virtual ~AwesomeEvent() {}

    virtual void fire(TimedEventQueue *queue, int64_t /* now_us */) {
        (mPlayer->*mMethod)();
    }

private:
    AwesomePlayer *mPlayer;
    void (AwesomePlayer::*mMethod)();

    AwesomeEvent(const AwesomeEvent &);
    AwesomeEvent &operator=(const AwesomeEvent &);
};

struct AwesomeRemoteRenderer : public AwesomeRenderer {
    AwesomeRemoteRenderer(const sp<IOMXRenderer> &target)
        : mTarget(target) {
    }

    virtual status_t initCheck() const {
        return OK;
    }

    virtual void render(MediaBuffer *buffer) {
        void *id;
        if (buffer->meta_data()->findPointer(kKeyBufferID, &id)) {
            mTarget->render((IOMX::buffer_id)id);
        }
    }

private:
    sp<IOMXRenderer> mTarget;

    AwesomeRemoteRenderer(const AwesomeRemoteRenderer &);
    AwesomeRemoteRenderer &operator=(const AwesomeRemoteRenderer &);
};

struct AwesomeLocalRenderer : public AwesomeRenderer {
    AwesomeLocalRenderer(
            bool previewOnly,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            const sp<ISurface> &surface,
            size_t displayWidth, size_t displayHeight,
            size_t decodedWidth, size_t decodedHeight,
            int32_t rotationDegrees)
        : mInitCheck(NO_INIT),
          mTarget(NULL),
          mLibHandle(NULL) {
            mInitCheck = init(previewOnly, componentName,
                 colorFormat, surface, displayWidth,
                 displayHeight, decodedWidth, decodedHeight,
                 rotationDegrees);
    }

    virtual status_t initCheck() const {
        return mInitCheck;
    }

    virtual void render(MediaBuffer *buffer) {
        render((const uint8_t *)buffer->data() + buffer->range_offset(),
               buffer->range_length());
    }

    void render(const void *data, size_t size) {
        mTarget->render(data, size, NULL);
    }

protected:
    virtual ~AwesomeLocalRenderer() {
        delete mTarget;
        mTarget = NULL;

        if (mLibHandle) {
            dlclose(mLibHandle);
            mLibHandle = NULL;
        }
    }

private:
    status_t mInitCheck;
    VideoRenderer *mTarget;
    void *mLibHandle;

    status_t init(
            bool previewOnly,
            const char *componentName,
            OMX_COLOR_FORMATTYPE colorFormat,
            const sp<ISurface> &surface,
            size_t displayWidth, size_t displayHeight,
            size_t decodedWidth, size_t decodedHeight,
            int32_t rotationDegrees);

    AwesomeLocalRenderer(const AwesomeLocalRenderer &);
    AwesomeLocalRenderer &operator=(const AwesomeLocalRenderer &);;
};

status_t AwesomeLocalRenderer::init(
        bool previewOnly,
        const char *componentName,
        OMX_COLOR_FORMATTYPE colorFormat,
        const sp<ISurface> &surface,
        size_t displayWidth, size_t displayHeight,
        size_t decodedWidth, size_t decodedHeight,
        int32_t rotationDegrees) {
    if (!previewOnly) {
        // We will stick to the vanilla software-color-converting renderer
        // for "previewOnly" mode, to avoid unneccessarily switching overlays
        // more often than necessary.

        mLibHandle = dlopen("libstagefrighthw.so", RTLD_NOW);

        if (mLibHandle) {
            typedef VideoRenderer *(*CreateRendererWithRotationFunc)(
                    const sp<ISurface> &surface,
                    const char *componentName,
                    OMX_COLOR_FORMATTYPE colorFormat,
                    size_t displayWidth, size_t displayHeight,
                    size_t decodedWidth, size_t decodedHeight,
                    int32_t rotationDegrees);

            typedef VideoRenderer *(*CreateRendererFunc)(
                    const sp<ISurface> &surface,
                    const char *componentName,
                    OMX_COLOR_FORMATTYPE colorFormat,
                    size_t displayWidth, size_t displayHeight,
                    size_t decodedWidth, size_t decodedHeight);

            CreateRendererWithRotationFunc funcWithRotation =
                (CreateRendererWithRotationFunc)dlsym(
                        mLibHandle,
                        "_Z26createRendererWithRotationRKN7android2spINS_8"
                        "ISurfaceEEEPKc20OMX_COLOR_FORMATTYPEjjjji");

            if (funcWithRotation) {
                mTarget =
                    (*funcWithRotation)(
                            surface, componentName, colorFormat,
                            displayWidth, displayHeight,
                            decodedWidth, decodedHeight,
                            rotationDegrees);
            } else {
                if (rotationDegrees != 0) {
                    LOGW("renderer does not support rotation.");
                }

                CreateRendererFunc func =
                    (CreateRendererFunc)dlsym(
                            mLibHandle,
                            "_Z14createRendererRKN7android2spINS_8ISurfaceEEEPKc20"
                            "OMX_COLOR_FORMATTYPEjjjj");

                if (func) {
                    mTarget =
                        (*func)(surface, componentName, colorFormat,
                            displayWidth, displayHeight,
                            decodedWidth, decodedHeight);
                }
            }
        }
    }

    if (mTarget != NULL) {
        return OK;
    }

    mTarget = new SoftwareRenderer(
            colorFormat, surface, displayWidth, displayHeight,
            decodedWidth, decodedHeight, rotationDegrees);

    return ((SoftwareRenderer *)mTarget)->initCheck();
}

AwesomePlayer::AwesomePlayer()
    : mQueueStarted(false),
      mTimeSource(NULL),
      mVideoRendererIsPreview(false),
      mNewSurfaceIsSet(false),
      mAudioPlayer(NULL),
      mFlags(0),
      mExtractorFlags(0),
      mCMMBLab(false),  //hong
      mSkipTimeUs(-1),
      mLastVideoBuffer(NULL),
      mVideoBuffer(NULL),
      mSuspensionState(NULL),
      mIsVideoPhoneStream(false),//sprd vt must
#ifdef USE_GETFRAME
      mVideoFrame(NULL), 
#endif

      mSeeking (false){
    CHECK_EQ(mClient.connect(), OK);

    DataSource::RegisterDefaultSniffers();

    mVideoEvent = new AwesomeEvent(this, &AwesomePlayer::onVideoEvent);
    mVideoEventPending = false;
    mStreamDoneEvent = new AwesomeEvent(this, &AwesomePlayer::onStreamDone);
    mStreamDoneEventPending = false;
    mBufferingEvent = new AwesomeEvent(this, &AwesomePlayer::onBufferingUpdate);
    mBufferingEventPending = false;

    mCheckAudioStatusEvent = new AwesomeEvent(
            this, &AwesomePlayer::onCheckAudioStatus);

    mAudioStatusEventPending = false;

    char value[16];
    property_get("ro.hisense.cmcc.test", value, "Unknown");
    if (!strcmp(value, "1") || !strcmp(value, "true"))
    {
	mCMMBLab = true;
	LOGV("CMMB set to CMCC Test Mode");
    }

    reset();
}

AwesomePlayer::~AwesomePlayer() {
    if (mQueueStarted) {
        mQueue.stop();
    }

    reset();

    mClient.disconnect();
}

void AwesomePlayer::cancelPlayerEvents(bool keepBufferingGoing) {
    mQueue.cancelEvent(mVideoEvent->eventID());
    mVideoEventPending = false;
    mQueue.cancelEvent(mStreamDoneEvent->eventID());
    mStreamDoneEventPending = false;
    mQueue.cancelEvent(mCheckAudioStatusEvent->eventID());
    mAudioStatusEventPending = false;

    if (!keepBufferingGoing) {
        mQueue.cancelEvent(mBufferingEvent->eventID());
        mBufferingEventPending = false;
    }
}

void AwesomePlayer::setListener(const wp<MediaPlayerBase> &listener) {
    Mutex::Autolock autoLock(mLock);
    mListener = listener;
}

status_t AwesomePlayer::setDataSource(
        const char *uri, const KeyedVector<String8, String8> *headers) {
LOGV("setdatasource11 ");
    Mutex::Autolock autoLock(mLock);
    return setDataSource_l(uri, headers);
}

status_t AwesomePlayer::setDataSource_l(
        const char *uri, const KeyedVector<String8, String8> *headers) {
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
LOGV("setdatasource 22 time:%d s",tv.tv_sec*1000 + tv.tv_usec/1000);
    reset_l();

    mUri = uri;

    if (headers) {
        mUriHeaders = *headers;
    }

    // The actual work will be done during preparation in the call to
    // ::finishSetDataSource_l to avoid blocking the calling thread in
    // setDataSource for any significant time.
	    gettimeofday(&tv, NULL);
LOGV("setdatasource 33 time:%d s",tv.tv_sec);
    return OK;
}

status_t AwesomePlayer::setDataSource(
        int fd, int64_t offset, int64_t length) {
	LOGV("setdatasource ");

    Mutex::Autolock autoLock(mLock);

    reset_l();

    sp<DataSource> dataSource = new FileSource(fd, offset, length);

    status_t err = dataSource->initCheck();

    if (err != OK) {
        return err;
    }

    mFileSource = dataSource;

    return setDataSource_l(dataSource);
}

status_t AwesomePlayer::setDataSource_l(
        const sp<DataSource> &dataSource) {
    sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource);

    if (extractor == NULL) {
        return UNKNOWN_ERROR;
    }

    return setDataSource_l(extractor);
}

status_t AwesomePlayer::setDataSource_l(const sp<MediaExtractor> &extractor) {
    // Attempt to approximate overall stream bitrate by summing all
    // tracks' individual bitrates, if not all of them advertise bitrate,
    // we have to fail.
struct timeval tv;  //@hong add timecheck.
    int64_t totalBitRate = 0;

    for (size_t i = 0; i < extractor->countTracks(); ++i) {
        sp<MetaData> meta = extractor->getTrackMetaData(i);

        int32_t bitrate;
        if (!meta->findInt32(kKeyBitRate, &bitrate)) {
            totalBitRate = -1;
            break;
        }

        totalBitRate += bitrate;
    }

    mBitrate = totalBitRate;

    LOGV("mBitrate = %lld bits/sec", mBitrate);

    bool haveAudio = false;
    bool haveVideo = false;
    for (size_t i = 0; i < extractor->countTracks(); ++i) {
        sp<MetaData> meta = extractor->getTrackMetaData(i);

        const char *mime;
        CHECK(meta->findCString(kKeyMIMEType, &mime));

        if (!haveVideo && !strncasecmp(mime, "video/", 6)) {
            setVideoSource(extractor->getTrack(i));
            haveVideo = true;
        } else if (!haveAudio && !strncasecmp(mime, "audio/", 6)) {
            setAudioSource(extractor->getTrack(i));
            haveAudio = true;

            if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_VORBIS)) {
                // Only do this for vorbis audio, none of the other audio
                // formats even support this ringtone specific hack and
                // retrieving the metadata on some extractors may turn out
                // to be very expensive.
                sp<MetaData> fileMeta = extractor->getMetaData();
                int32_t loop;
                if (fileMeta != NULL
                        && fileMeta->findInt32(kKeyAutoLoop, &loop) && loop != 0) {
                    mFlags |= AUTO_LOOPING;
                }
            }
        }

        if (haveAudio && haveVideo) {
            break;
        }
    }

    if (!haveAudio && !haveVideo) {
        return UNKNOWN_ERROR;
    }

    mExtractorFlags = extractor->flags();

	    gettimeofday(&tv, NULL);

	LOGV("setdatasource_l end time:%d s ",tv.tv_sec*1000 + tv.tv_usec/1000);

    return OK;
}

void AwesomePlayer::reset() {
    LOGV("reset");

	 //@hong to handle stop failed. 2011-8-15
    if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mUri.string(),35)) 
    {
       if (mFlags & PREPARING) {
          abortPrepare(UNKNOWN_ERROR);
	  	}
    }
   
    Mutex::Autolock autoLock(mLock);
     LOGI("reset_l wait");
    reset_l();
}

void AwesomePlayer::reset_l() {
    if (mFlags & PREPARING) {
        mFlags |= PREPARE_CANCELLED;
        if (mConnectingDataSource != NULL) {
            LOGI("interrupting the connection process");
            mConnectingDataSource->disconnect();
        } else if (mConnectingRTSPController != NULL) {
            LOGI("interrupting the connection process");
            mConnectingRTSPController->disconnect();
        }

        if (mFlags & PREPARING_CONNECTED) {
            // We are basically done preparing, we're just buffering
            // enough data to start playback, we can safely interrupt that.
            finishAsyncPrepare_l();
        }
    }
	LOGI("--reset_l wait");
    while (mFlags & PREPARING) { 
        LOGI("--prepare Condition.wait");
        mPreparedCondition.wait(mLock);
    }

	LOGI("--cancel event");
    cancelPlayerEvents();

    mCachedSource.clear();
    mAudioTrack.clear();
    mVideoTrack.clear();
LOGI("--cancel clear ok");
    // Shutdown audio first, so that the respone to the reset request
    // appears to happen instantaneously as far as the user is concerned
    // If we did this later, audio would continue playing while we
    // shutdown the video-related resources and the player appear to
    // not be as responsive to a reset request.
    if (mAudioPlayer == NULL && mAudioSource != NULL) {
        // If we had an audio player, it would have effectively
        // taken possession of the audio source and stopped it when
        // _it_ is stopped. Otherwise this is still our responsibility.
        mAudioSource->stop();
    }
    mAudioSource.clear();
LOGI("--cancel source ok");
    mTimeSource = NULL;

    delete mAudioPlayer;
    mAudioPlayer = NULL;

    mVideoRenderer.clear();
LOGI("--mVideoRenderer clear ok");
    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }
LOGI("--mLastVideoBuffer release ok");
    if (mVideoBuffer) {
        mVideoBuffer->release();
        mVideoBuffer = NULL;
    }
LOGI("--mVideoBuffer->release release ok");

    if (mRTSPController != NULL) {
        mRTSPController->disconnect();
        mRTSPController.clear();
    }
LOGI("--mRTSPController->disconnect ok");

    mRTPPusher.clear();
    mRTCPPusher.clear();
    mRTPSession.clear();
LOGI("--mRTP clear ok");

    if (mVideoSource != NULL) {
        mVideoSource->stop();
LOGI("--mVideoSource stop ok");

        // The following hack is necessary to ensure that the OMX
        // component is completely released by the time we may try
        // to instantiate it again.
        wp<MediaSource> tmp = mVideoSource;
        mVideoSource.clear();
LOGI("--mVideoSource.clear ok");
        while (tmp.promote() != NULL) {
            usleep(1000);
        }
LOGI("--flushCommands");

        IPCThreadState::self()->flushCommands();
    }
LOGI("--flushCommands ok");
    mDurationUs = -1;
    mFlags = 0;
    mExtractorFlags = 0;
    mVideoWidth = mVideoHeight = -1;
    mTimeSourceDeltaUs = 0;
    mVideoTimeUs = 0;
#ifdef _SYNC_USE_SYSTEM_TIME_
    mSystemTimeSourceForSync.reset();//@jgdu
#endif	
LOGI("--mSystemTimeSourceForSync reset ok");
    mSeeking = false;
    mSeekNotificationSent = false;
    mSeekTimeUs = 0;

    mUri.setTo("");
    mUriHeaders.clear();

    mFileSource.clear();
LOGI("--mFileSource clear ok");

    delete mSuspensionState;
    mSuspensionState = NULL;

    mBitrate = -1;
LOGI("--ok..reset");

}

void AwesomePlayer::notifyListener_l(int msg, int ext1, int ext2) {
    if (mListener != NULL) {
        sp<MediaPlayerBase> listener = mListener.promote();

        if (listener != NULL) {
            listener->sendEvent(msg, ext1, ext2);
        }
    }
}

bool AwesomePlayer::getBitrate(int64_t *bitrate) {
    off_t size;
    if (mDurationUs >= 0 && mCachedSource != NULL
            && mCachedSource->getSize(&size) == OK) {
        *bitrate = size * 8000000ll / mDurationUs;  // in bits/sec
        return true;
    }

    if (mBitrate >= 0) {
        *bitrate = mBitrate;
        return true;
    }

    *bitrate = 0;

    return false;
}

// Returns true iff cached duration is available/applicable.
bool AwesomePlayer::getCachedDuration_l(int64_t *durationUs, bool *eos) {
    int64_t bitrate;

    if (mRTSPController != NULL) {
        *durationUs = mRTSPController->getQueueDurationUs(eos);
        return true;
    } else if (mCachedSource != NULL && getBitrate(&bitrate)) {
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(eos);
        *durationUs = cachedDataRemaining * 8000000ll / bitrate;
        return true;
    }

    return false;
}

void AwesomePlayer::onBufferingUpdate() {
    Mutex::Autolock autoLock(mLock);
    if (!mBufferingEventPending) {
        return;
    }

   if (mFlags & CACHE_UNDERRUN)
    LOGI("Buffering...");

    mBufferingEventPending = false;

    if (mCachedSource != NULL) {
        bool eos;
        size_t cachedDataRemaining = mCachedSource->approxDataRemaining(&eos);

        if (eos) {
            notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
            if (mFlags & PREPARING) {
                LOGV("cache has reached EOS, prepare is done.");
                finishAsyncPrepare_l();
            }
        } else {
            int64_t bitrate;
            if (getBitrate(&bitrate)) {
                size_t cachedSize = mCachedSource->cachedSize();
                int64_t cachedDurationUs = cachedSize * 8000000ll / bitrate;

                int percentage = 100.0 * (double)cachedDurationUs / mDurationUs;
                if (percentage > 100) {
                    percentage = 100;
                }

                notifyListener_l(MEDIA_BUFFERING_UPDATE, percentage);
            } else {
                // We don't know the bitrate of the stream, use absolute size
                // limits to maintain the cache.

                if ((mFlags & PLAYING) && !eos
                        && (cachedDataRemaining < kLowWaterMarkBytes)) {
                    LOGI("cache is running low (< %d) , pausing.",
                         kLowWaterMarkBytes);
                    mFlags |= CACHE_UNDERRUN;
                    pause_l();
                    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START);
                } else if (eos || cachedDataRemaining > kHighWaterMarkBytes) {
                    if (mFlags & CACHE_UNDERRUN) {
                        LOGI("cache has filled up (> %d), resuming.",
                             kHighWaterMarkBytes);
                        mFlags &= ~CACHE_UNDERRUN;
                        play_l();
                        notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END);
                    } else if (mFlags & PREPARING) {
                        LOGV("cache has filled up (> %d), prepare is done",
                             kHighWaterMarkBytes);
                        finishAsyncPrepare_l();
                    }
                }
            }
        }
    }

    int64_t cachedDurationUs;
    bool eos;
    if (getCachedDuration_l(&cachedDurationUs, &eos)) {

		 int percentage = 100.0 * cachedDurationUs/((mHighWaterMarkUs+mLowWaterMarkUs)/2);
		 if (percentage > 100 ||eos ) {
		   percentage = 100;
		 }

		 if(mFlags&CACHE_UNDERRUN)
		 {
		   notifyListener_l(MEDIA_BUFFERING_UPDATE,percentage);
         }
        if ((mFlags & PLAYING) && !eos
                && (cachedDurationUs < mLowWaterMarkUs)) {
            LOGI("cache is running low (%.2f secs) , pausing.",
                 cachedDurationUs / 1E6);
            mFlags |= CACHE_UNDERRUN;
            pause_l();
		    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START); 
			notifyListener_l(MEDIA_BUFFERING_UPDATE, 0);
   
          } else // if (eos || cachedDurationUs > kHighWaterMarkUs) 
             {
			 
            if ((eos || cachedDurationUs > mHighWaterMarkUs) && (mFlags & CACHE_UNDERRUN)) { 
                LOGI("cache has filled up (%.2f secs), resuming.",
                     cachedDurationUs / 1E6);
                mFlags &= ~CACHE_UNDERRUN;
                play_l();
				notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
	            notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END); 
            } else if ((eos ||(cachedDurationUs > mStartLowWaterMarkUs) || (!mCMMBLab && cachedDurationUs > (mHighWaterMarkUs+mLowWaterMarkUs)/2) ) 
                        && (mFlags & PREPARING)) { 
                LOGV("cache has filled up (%.2f secs), prepare is done",
                     cachedDurationUs / 1E6);
                // finishAsyncPrepare_l();// remove for fast prepare
			     notifyListener_l(MEDIA_BUFFERING_UPDATE,percentage);
                 finishAsyncPrepare_l();
            }
        }
    }

    postBufferingEvent_l();
}

void AwesomePlayer::partial_reset_l() {
    // Only reset the video renderer and shut down the video decoder.
    // Then instantiate a new video decoder and resume video playback.

    mVideoRenderer.clear();

    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }

    if (mVideoBuffer) {
        mVideoBuffer->release();
        mVideoBuffer = NULL;
    }

    if (mVideoSource != NULL){
        mVideoSource->stop();

        // The following hack is necessary to ensure that the OMX
        // component is completely released by the time we may try
        // to instantiate it again.
        wp<MediaSource> tmp = mVideoSource;
        mVideoSource.clear();
	LOGV("partial_reset_l waiting...");
        while (tmp.promote() != NULL) {
            usleep(1000);
        }
	LOGV("partial_reset_l waiting..ok.");
    }
    IPCThreadState::self()->flushCommands();

    CHECK_EQ(OK, initVideoDecoder(OMXCodec::kIgnoreCodecSpecificData));
}

void AwesomePlayer::onStreamDone() {
    // Posted whenever any stream finishes playing.

    Mutex::Autolock autoLock(mLock);
    if (!mStreamDoneEventPending) {
        return;
    }
    mStreamDoneEventPending = false;

    if (mStreamDoneStatus == INFO_DISCONTINUITY) {
        // This special status is returned because an http live stream's
        // video stream switched to a different bandwidth at this point
        // and future data may have been encoded using different parameters.
        // This requires us to shutdown the video decoder and reinstantiate
        // a fresh one.

        LOGV("INFO_DISCONTINUITY");

       // CHECK(mVideoSource != NULL);

        partial_reset_l();
	   
	    if (mVideoSource != NULL) {
            postVideoEvent_l();
	    }
        return;
    } else if (ERROR_END_OF_STREAM != mStreamDoneStatus ) {
        LOGV("MEDIA_ERROR %d", mStreamDoneStatus);

        notifyListener_l(
                MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, mStreamDoneStatus);

        pause_l(true /* at eos */);

        mFlags |= AT_EOS;
        return;
    }

    const bool allDone =
        (mVideoSource == NULL || (mFlags & VIDEO_AT_EOS))
            && (mAudioSource == NULL || (mFlags & AUDIO_AT_EOS));

    if (!allDone) {
        return;
    }

    if (mFlags & (LOOPING | AUTO_LOOPING)) {
        seekTo_l(0);

        if (mVideoSource != NULL) {
            postVideoEvent_l();
        }
    } else {
        LOGV("MEDIA_PLAYBACK_COMPLETE");
        notifyListener_l(MEDIA_PLAYBACK_COMPLETE);

        pause_l(true /* at eos */);

        mFlags |= AT_EOS;
    }
}

status_t AwesomePlayer::play() {
    LOGE("play ,mSeeking = %d",mSeeking);

    Mutex::Autolock autoLock(mLock);

	mFlags &= ~CACHE_UNDERRUN;
	 if (mRTSPController != NULL) {
		 {
			 if (mFlags & PLAYING) {
			 	  LOGE("play correct status 835"); 
				  return OK;
			 }
			 mRTSPController->playAsync(mVideoTimeUs, OnRTSPResumeDoneWrapper, this);
		 }
	 }
     return play_l();
}

status_t AwesomePlayer::play_l() {
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
LOGV("play_l enter time:%d s",tv.tv_sec*1000+tv.tv_usec/1000);

    if (mFlags & PLAYING) {
	   LOGE("play_l correct status850"); 
       return OK;
    }

    if (!(mFlags & PREPARED)) {
        status_t err = prepare_l();

        if (err != OK) {
            return err;
        }
    }

    mFlags |= PLAYING;
    mFlags |= FIRST_FRAME;
    bool deferredAudioSeek = false;

    if (mAudioSource != NULL) {
        if (mAudioPlayer == NULL) {
            if (mAudioSink != NULL) {
                mAudioPlayer = new AudioPlayer(mAudioSink, this);
                mAudioPlayer->setSource(mAudioSource);

                // We've already started the MediaSource in order to enable
                // the prefetcher to read its data.
                status_t err = mAudioPlayer->start(
                        true /* sourceAlreadyStarted */);

                if (err != OK) {
                    delete mAudioPlayer;
                    mAudioPlayer = NULL;

                    mFlags &= ~(PLAYING | FIRST_FRAME);

                    return err;
                }

                mTimeSource = mAudioPlayer;

                deferredAudioSeek = true;

                mWatchForAudioSeekComplete = false;
                mWatchForAudioEOS = true;
            }
        } else {
            mAudioPlayer->resume();
			mBufferingEventPending = false;
			postBufferingEvent_l();
        }
    }
#ifdef _SYNC_USE_SYSTEM_TIME_
    if(0== (mFlags & NOT_FIRST_PLAY))
		mSystemTimeSourceForSync.reset();
    mFlags |= 	NOT_FIRST_PLAY;
    mSystemTimeSourceForSync.resume();//@jgdu
#endif	
    if (mTimeSource == NULL && mAudioPlayer == NULL) {
        mTimeSource = &mSystemTimeSource;
    }

    if (mVideoSource != NULL) {
        // Kick off video playback
        postVideoEvent_l();
    }

    if (deferredAudioSeek) {
        // If there was a seek request while we were paused
        // and we're just starting up again, honor the request now.
        seekAudioIfNecessary_l();
    }

    if (mFlags & AT_EOS) {
        // Legacy behaviour, if a stream finishes playing and then
        // is started again, we play from the start...
        seekTo_l(0);
    }

    return OK;
}

status_t AwesomePlayer::initRenderer_l() {
    if (mISurface == NULL) {
	 LOGI("mISurface == NULL");
        return OK;
    }

    sp<MetaData> meta = mVideoSource->getFormat();

    int32_t format;
    const char *component;
    int32_t decodedWidth, decodedHeight;
    CHECK(meta->findInt32(kKeyColorFormat, &format));
    CHECK(meta->findCString(kKeyDecoderComponent, &component));
    CHECK(meta->findInt32(kKeyWidth, &decodedWidth));
    CHECK(meta->findInt32(kKeyHeight, &decodedHeight));

    int32_t rotationDegrees;
    if (!mVideoTrack->getFormat()->findInt32(
                kKeyRotation, &rotationDegrees)) {
        rotationDegrees = 0;
    }

    mVideoRenderer.clear();

    LOGI("rotationDegrees = %d",rotationDegrees);
	
    // Must ensure that mVideoRenderer's destructor is actually executed
    // before creating a new one.
    IPCThreadState::self()->flushCommands();

    if (!strncmp("OMX.", component, 4)) {
        // Our OMX codecs allocate buffers on the media_server side
        // therefore they require a remote IOMXRenderer that knows how
        // to display them.

        sp<IOMXRenderer> native =
            mClient.interface()->createRenderer(
                    mISurface, component,
                    (OMX_COLOR_FORMATTYPE)format,
                    decodedWidth, decodedHeight,
                    mVideoWidth, mVideoHeight,
                    rotationDegrees);

        if (native == NULL) {
            return NO_INIT;
        }

        mVideoRenderer = new AwesomeRemoteRenderer(native);
    } else {
        // Other decoders are instantiated locally and as a consequence
        // allocate their buffers in local address space.
        mVideoRenderer = new AwesomeLocalRenderer(
            false,  // previewOnly
            component,
            (OMX_COLOR_FORMATTYPE)format,
            mISurface,
            mVideoWidth, mVideoHeight,
            decodedWidth, decodedHeight, rotationDegrees);
    }

    return mVideoRenderer->initCheck();
}

void AwesomePlayer::clearRender(){
	Mutex::Autolock autoLock(mLock);
	 mVideoRenderer.clear();
}
#ifdef USE_GETFRAME
#define RGB565(r,g,b)       ((unsigned short)((((unsigned char)(r)>>3)|((unsigned short)(((unsigned char)(g)>>2))<<5))|(((unsigned short)((unsigned char)(b>>3)))<<11)))
void yuv420_to_rgb565(int width, int height, unsigned char *src, unsigned short *dst)
{
    int frameSize = width * height;
    unsigned char *yuv420sp = src;
    for (int j = 0, yp = 0; j < height; j++) {
        int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
        for (int i = 0; i < width; i++, yp++) {
            int y = (0xff & ((int) yuv420sp[yp])) - 16;
            if (y < 0) y = 0;
            if ((i & 1) == 0) {
                v = (0xff & yuv420sp[uvp++]) - 128;
                u = (0xff & yuv420sp[uvp++]) - 128;
            }

            int y1192 = 1192 * y;
            int r = (y1192 + 1634 * v);
            int g = (y1192 - 833 * v - 400 * u);
            int b = (y1192 + 2066 * u);

            if (r < 0) r = 0; else if (r > 262143) r = 262143;
            if (g < 0) g = 0; else if (g > 262143) g = 262143;
            if (b < 0) b = 0; else if (b > 262143) b = 262143;

            //int rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            dst[yp] = RGB565((((r << 6) & 0xff0000)>>16), (((g >> 2) & 0xff00)>>8), (((b >> 10) & 0xff)));
        }
    } 
}

status_t AwesomePlayer::getFrameAt(int msec, VideoFrame** pvframe)
{
    if(!mLastVideoBuffer)
        return UNKNOWN_ERROR;
    if(!mVideoFrame)
        mVideoFrame = new VideoFrame();
    if (!mVideoFrame) {
       LOGE("failed to allocate memory for a VideoFrame object");
       return UNKNOWN_ERROR;
    }
    mVideoFrame->mWidth = mVideoWidth;
    mVideoFrame->mHeight = mVideoWidth;
        
    mVideoFrame->mDisplayWidth  = mVideoWidth;
    mVideoFrame->mDisplayHeight = mVideoHeight;
    mVideoFrame->mSize = mVideoFrame->mWidth * mVideoFrame->mHeight* 2;//RGB 565
    
    mVideoFrame->mData = new unsigned char[mVideoFrame->mSize];
    if (!mVideoFrame->mData) {
       LOGE("cannot allocate buffer to hold SkBitmap pixels");
       delete mVideoFrame; mVideoFrame = NULL;
       return UNKNOWN_ERROR;
    }
    yuv420_to_rgb565(mVideoWidth, mVideoHeight, (unsigned char *)mLastVideoBuffer->data(), (unsigned short *)mVideoFrame->mData);
    *pvframe = mVideoFrame;
    LOGI("[INNO/socketinterface/AwesomePlayer.cpp] AwesomePlayer::getFrameAt OK");
    return OK;
}
#endif
status_t AwesomePlayer::pause() {
    LOGE("pause mSeeking = %d",mSeeking); 
	Mutex::Autolock autoLock(mLock);
    mFlags &= ~CACHE_UNDERRUN;

	if (mRTSPController != NULL) {

		if (!(mFlags & PLAYING))
		{
			LOGE("pause correct status1074"); 
            return OK;
		}
  		mRTSPController->pauseAsync(mVideoTimeUs, OnRTSPPauseDoneWrapper, this);
	}
    return pause_l();
}

status_t AwesomePlayer::pause_l(bool at_eos) {
    if (!(mFlags & PLAYING)) {
		LOGE("pause correct status1084"); 
        return OK;
    }

    cancelPlayerEvents(true /* keepBufferingGoing */);

    if (mAudioPlayer != NULL) {
        if (at_eos) {
            // If we played the audio stream to completion we
            // want to make sure that all samples remaining in the audio
            // track's queue are played out.
            mAudioPlayer->pause(true /* playPendingSamples */);
        } else {
            mAudioPlayer->pause();
        }
    }

    mFlags &= ~PLAYING;
#ifdef _SYNC_USE_SYSTEM_TIME_
    mSystemTimeSourceForSync.pause();//@jgdu
#endif    
    return OK;
}

bool AwesomePlayer::isPlaying() const {
        return (mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN);
}

void AwesomePlayer::setISurface(const sp<ISurface> &isurface) {
    Mutex::Autolock autoLock(mLock);
    bool inull;
    mISurface = isurface;
	inull = (mISurface == NULL)?0:1;
    LOGI("setISurface %d", inull);
    mNewSurfaceIsSet = true;
}

void AwesomePlayer::setAudioSink(
        const sp<MediaPlayerBase::AudioSink> &audioSink) {
    LOGV("setAudioSink");    
    Mutex::Autolock autoLock(mLock);

    mAudioSink = audioSink;
}

status_t AwesomePlayer::setLooping(bool shouldLoop) {
    Mutex::Autolock autoLock(mLock);

    mFlags = mFlags & ~LOOPING;

    if (shouldLoop) {
        mFlags |= LOOPING;
    }

    return OK;
}

status_t AwesomePlayer::getDuration(int64_t *durationUs) {
    Mutex::Autolock autoLock(mMiscStateLock);

    if (mDurationUs < 0) {
        return UNKNOWN_ERROR;
    }

    *durationUs = mDurationUs;

    return OK;
}

status_t AwesomePlayer::getPosition(int64_t *positionUs) {
#if 0
	if (mRTSPController != NULL) {
		if(mSeeking)
		{
			*positionUs = mSeekTimeUs;
			 LOGE("AwesomePlayer::getPosition seeking %lld",mSeekTimeUs);	
		}
		else
		{
			*positionUs = mRTSPController->getNormalPlayTimeUs();	
		}
    }
    else
#endif		
	if (mSeeking) {
        *positionUs = mSeekTimeUs;
    } else if (mVideoSource != NULL) {
        Mutex::Autolock autoLock(mMiscStateLock);
        *positionUs = mVideoTimeUs;
	 if(mFlags&VIDEO_AT_EOS){
	 	if((mDurationUs>0)&&(mFlags&AT_EOS)){
	 		*positionUs = mDurationUs;
	 	}else if((mAudioPlayer != NULL)&&((mFlags&AUDIO_AT_EOS)==0)){
	 		*positionUs = mAudioPlayer->getMediaTimeUs();
	 	}
	 }
    } else if (mAudioPlayer != NULL) {
        *positionUs = mAudioPlayer->getMediaTimeUs();
	 if((mDurationUs>0)&&(mFlags&AT_EOS)){
	 	*positionUs = mDurationUs;
	 }		
    } else {
        *positionUs = 0;
    }

    return OK;
}

status_t AwesomePlayer::seekTo(int64_t timeUs) {
    LOGV("seekTo %lld, mSeekstate %d",timeUs,mSeeking);	
    if (mExtractorFlags & MediaExtractor::CAN_SEEK) {
        Mutex::Autolock autoLock(mLock);
	
   		return seekTo_l(timeUs);
    }

    return OK;
}

// static
void AwesomePlayer::OnRTSPSeekDoneWrapper(void *cookie,int32_t status) {
    static_cast<AwesomePlayer *>(cookie)->onRTSPSeekDone(status);
}

void AwesomePlayer::onRTSPSeekDone(int32_t status) {
	 LOGI("onRTSPSeekDone %d,playing %d",status,mFlags & PLAYING);  
	 if(status != OK )
	 {
	 	notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, status);
		return ;
	 }
	 if(mFlags & PLAYING)
	 {
		mAudioPlayer->resume();
#ifdef _SYNC_USE_SYSTEM_TIME_
		if(0== (mFlags & NOT_FIRST_PLAY))
				 mSystemTimeSourceForSync.reset();
		mFlags |=	 NOT_FIRST_PLAY;
		mSystemTimeSourceForSync.resume();//@jgdu
#endif	
		mBufferingEventPending = false;
	    postBufferingEvent_l();
		postVideoEvent_l();
		
	    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_START); 
	 	notifyListener_l(MEDIA_BUFFERING_UPDATE, 100);
	    notifyListener_l(MEDIA_INFO, MEDIA_INFO_BUFFERING_END); 
		
	 }
	 mSeeking = true;
	 mSeekNotificationSent = true; 
	 notifyListener_l(MEDIA_SEEK_COMPLETE);
}

void AwesomePlayer::OnRTSPPauseDoneWrapper(void *cookie,int32_t status) {
    static_cast<AwesomePlayer *>(cookie)->onRTSPPauseDone(status);

}

void AwesomePlayer::onRTSPPauseDone(int32_t status) {
     LOGE("AwesomePlayer::onRTSPPauseDone status =%d mSeeking = %d,,mSeekTimeUs =%lld",status,mSeeking,mSeekTimeUs);
     if(status != OK )
	 {
	 	notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, status);
		return ;
	 }
}

void AwesomePlayer::OnRTSPResumeDoneWrapper(void *cookie,int32_t status) {
    static_cast<AwesomePlayer *>(cookie)->onRTSPResumeDone(status);
}


void AwesomePlayer::onRTSPResumeDone(int32_t status) {

    LOGE("AwesomePlayer::onRTSPResumeDone ,status = %d ,mSeeking = %d,mSeekTimeUs =%lld",status,mSeeking,mSeekTimeUs);
	if(status != OK )
	{
	   notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, status);
	   return ;
	}
}

status_t AwesomePlayer::seekTo_l(int64_t timeUs) {
	if (mRTSPController != NULL) {

		if (!mRTSPController->getSeekable())
		{
		   LOGE("liveing streaming ,so can not seek");  
		   return OK;
		}
		if (mFlags & CACHE_UNDERRUN) {
			mFlags &= ~CACHE_UNDERRUN;
			play_l();
		}
	   int32_t mSeekingflag = ((mFlags & PLAYING) || (mFlags & CACHE_UNDERRUN));
	    LOGE("seekTo_l mSeekingflag %d",mSeekingflag);
        if(mSeekingflag)
        {
			//pause_l();
			mFlags &= ~CACHE_UNDERRUN;
			cancelPlayerEvents(false/* keepBufferingGoing */);

			if (mAudioPlayer != NULL) {
	             mAudioPlayer->pause();
	        }
	       #ifdef _SYNC_USE_SYSTEM_TIME_
	           mSystemTimeSourceForSync.pause();//@jgdu
	       #endif    
        }
	    mSeekTimeUs = timeUs;
	    mVideoTimeUs = timeUs ; 
        mRTSPController->seekAsync(timeUs, OnRTSPSeekDoneWrapper, this);
        return OK;
    }

    if (mFlags & CACHE_UNDERRUN) {
        mFlags &= ~CACHE_UNDERRUN;
        play_l();
    }

    mSeeking = true;
    mSeekNotificationSent = false;
    mSeekTimeUs = timeUs;
    mFlags &= ~(AT_EOS | AUDIO_AT_EOS | VIDEO_AT_EOS);

    seekAudioIfNecessary_l();

    if (!(mFlags & PLAYING)) {
        LOGV("seeking while paused, sending SEEK_COMPLETE notification"
             " immediately.");

        notifyListener_l(MEDIA_SEEK_COMPLETE);
        mSeekNotificationSent = true;
    }

    return OK;
}

void AwesomePlayer::seekAudioIfNecessary_l() {
    if (mSeeking && mVideoSource == NULL && mAudioPlayer != NULL) {
        mAudioPlayer->seekTo(mSeekTimeUs);

        mWatchForAudioSeekComplete = true;
        mWatchForAudioEOS = true;
        mSeekNotificationSent = false;
    }
}

status_t AwesomePlayer::getVideoDimensions(
        int32_t *width, int32_t *height) const {
    Mutex::Autolock autoLock(mLock);

    if (mVideoWidth < 0 || mVideoHeight < 0) {
        return UNKNOWN_ERROR;
    }
    *width = mVideoWidth;
    *height = mVideoHeight;

    return OK;
}

void AwesomePlayer::setAudioSource(sp<MediaSource> source) {
    LOGV("setAudioSource");	
    CHECK(source != NULL);

    mAudioTrack = source;
}

status_t AwesomePlayer::initAudioDecoder() {
    sp<MetaData> meta = mAudioTrack->getFormat();

    const char *mime;
    CHECK(meta->findCString(kKeyMIMEType, &mime));

    if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW)) {
        mAudioSource = mAudioTrack;
    } else {
    
	if (!strncasecmp("rtsp://", mUri.string(), 7)){
	  mAudioSource = new ThreadedSource(OMXCodec::Create(
			   mClient.interface(), mAudioTrack->getFormat(),
			   false, // createEncoder
			   mAudioTrack),1);
	}else{

		mAudioSource = OMXCodec::Create(
                mClient.interface(), mAudioTrack->getFormat(),
                false, // createEncoder
                mAudioTrack);
		}
    }

    if (mAudioSource != NULL) {
        int64_t durationUs;
        if (mAudioTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
	     LOGI("audio duration %lld",durationUs);	
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        status_t err = mAudioSource->start();

        if (err != OK) {
            mAudioSource.clear();
            return err;
        }
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_QCELP)) {
        // For legacy reasons we're simply going to ignore the absence
        // of an audio decoder for QCELP instead of aborting playback
        // altogether.
        return OK;
    }

    return mAudioSource != NULL ? OK : UNKNOWN_ERROR;
}

void AwesomePlayer::setVideoSource(sp<MediaSource> source) {
    CHECK(source != NULL);

    mVideoTrack = source;
}

status_t AwesomePlayer::initVideoDecoder(uint32_t flags) {
	LOGI("initVideoDecoder, mIsVideoPhoneStream: %d", mIsVideoPhoneStream);
    if (mIsVideoPhoneStream){
	    mVideoSource = new ThreadedSource(OMXCodec::Create(
	            mClient.interface(), mVideoTrack->getFormat(),
	            false, // createEncoder
	            mVideoTrack,
	            NULL, flags, 2, 3));
	} else {

		if (!strncasecmp("rtsp://", mUri.string(), 7)){
				mVideoSource = new ThreadedSource(OMXCodec::Create(
					mClient.interface(), mVideoTrack->getFormat(),
					false, // createEncoder
					mVideoTrack,
					NULL, flags),1);
			}
		    else
			{
			mVideoSource = OMXCodec::Create(
	            mClient.interface(), mVideoTrack->getFormat(),
	            false, // createEncoder
	            mVideoTrack,
	            NULL, flags);
			}
	}

    if (mVideoSource != NULL) {
        int64_t durationUs;
	    const char *mime;
		sp<MetaData> meta = mVideoTrack->getFormat();
		CHECK(meta->findCString(kKeyMIMEType, &mime));
	    LOGI("initVideoDecoder, mime %s",mime);
		
        if (mVideoTrack->getFormat()->findInt64(kKeyDuration, &durationUs)) {
	     LOGI("video duration %lld",durationUs);
            Mutex::Autolock autoLock(mMiscStateLock);
            if (mDurationUs < 0 || durationUs > mDurationUs) {
                mDurationUs = durationUs;
            }
        }

        CHECK(mVideoTrack->getFormat()->findInt32(kKeyWidth, &mVideoWidth));
        CHECK(mVideoTrack->getFormat()->findInt32(kKeyHeight, &mVideoHeight));

        LOGI("mVideoWidth =%d mVideoHeight =%d ",mVideoWidth,mVideoHeight);
        bool videooutsize = false ;

        if (mVideoWidth > mVideoHeight )
        {
               if (mVideoWidth >= 1920 ||mVideoHeight >= 1080 )
                        videooutsize = true ;
        }
        else
        {
              if (mVideoWidth >= 1080 ||mVideoHeight >= 1920 )
                        videooutsize = true ;
        }
        if (videooutsize)
        {
          LOGI("vide size is not susport play audio only ");
          mVideoSource.clear();
          return OK;
        }

        if (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC))
        {
            int32_t profile ;

            mVideoTrack->getFormat()->findInt32(kKeyVideoProfile, &profile);

            LOGI("avc profile 0x%x",profile);
			
		    if (mVideoWidth > mVideoHeight )
	        {
	               if (mVideoWidth >= 1280 ||mVideoHeight >= 720 )
	                        videooutsize = true ;
	        }
	        else
	        {
	              if (mVideoWidth >= 720 ||mVideoHeight >= 1280 )
	                        videooutsize = true ;
	        }

            if(profile > 0x64 || videooutsize)
            {
                mVideoSource.clear();
                return OK;
            }
        }

        status_t err = mVideoSource->start();

        if (err != OK) {
            mVideoSource.clear();
            return err;
        }
    }

    return mVideoSource != NULL ? OK : UNKNOWN_ERROR;
}

#ifndef max
#define max(a, b) (((a) > (b)) ? (a) : (b))
#endif
#ifndef min
#define min(a, b) (((a) < (b)) ? (a) : (b))
#endif

void AwesomePlayer::finishSeekIfNecessary(int64_t videoTimeUs) {
    if (!mSeeking) {
        return;
    }

    if (mAudioPlayer != NULL) {
        LOGV("seeking audio to %lld us (%.2f secs).", videoTimeUs, videoTimeUs / 1E6);

        // If we don't have a video time, seek audio to the originally
        // requested seek time instead.
       if (mRTSPController == NULL)
       {
	       mAudioPlayer->seekTo(videoTimeUs < 0 ? mSeekTimeUs : videoTimeUs);
		   mAudioPlayer->resume();
       }
	   mWatchForAudioSeekComplete = true;
       mWatchForAudioEOS = true;
    } else if (!mSeekNotificationSent) {
        // If we're playing video only, report seek complete now,
        // otherwise audio player will notify us later.
        notifyListener_l(MEDIA_SEEK_COMPLETE);
    }

    mFlags |= FIRST_FRAME;
    mSeeking = false;
    mSeekNotificationSent = false;
   if (mRTSPController == NULL)
   {
	#ifdef _SYNC_USE_SYSTEM_TIME_	
	mSystemTimeSourceForSync.resume();//@jgdu
	#endif    
   }
}

void AwesomePlayer::onVideoEvent() {
    Mutex::Autolock autoLock(mLock);
    if (!mVideoEventPending) {
        // The event has been cancelled in reset_l() but had already
        // been scheduled for execution at that time.
        return;
    }
    mVideoEventPending = false;

    if (mSeeking) {
        if (mLastVideoBuffer) {
            mLastVideoBuffer->release();
            mLastVideoBuffer = NULL;
        }

        if (mVideoBuffer) {
            mVideoBuffer->release();
            mVideoBuffer = NULL;
        }

        if (mCachedSource != NULL && mAudioSource != NULL) {
            // We're going to seek the video source first, followed by
            // the audio source.
            // In order to avoid jumps in the DataSource offset caused by
            // the audio codec prefetching data from the old locations
            // while the video codec is already reading data from the new
            // locations, we'll "pause" the audio source, causing it to
            // stop reading input data until a subsequent seek.

            if (mAudioPlayer != NULL) {
                mAudioPlayer->pause();
            }
            mAudioSource->pause();
        }
    }

    if (!mVideoBuffer) {
        MediaSource::ReadOptions options;
        if (mSeeking) {
            LOGV("seeking to %lld us (%.2f secs)", mSeekTimeUs, mSeekTimeUs / 1E6);

            options.setSeekTo(
                    mSeekTimeUs, MediaSource::ReadOptions::SEEK_CLOSEST_SYNC);
        }
        if (mSkipTimeUs >= 0) {//jgdu
            options.setSkipFrame(mSkipTimeUs);
	     mSkipTimeUs = -1;
        }		
        for (;;) {
            status_t err = mVideoSource->read(&mVideoBuffer, &options);
            options.clearSeekTo();

            if (err != OK) {
                CHECK_EQ(mVideoBuffer, NULL);

                if (err == INFO_FORMAT_CHANGED) {
                    LOGV("VideoSource signalled format change.");

                    if (mVideoRenderer != NULL) {
                        mVideoRendererIsPreview = false;
                        mNewSurfaceIsSet = false;
                        err = initRenderer_l();

                        if (err == OK) {
                            continue;
                        }

                        // fall through
                    } else {
                        continue;
                    }
                }
                if (err == ERROR_TIMEOUT) {//cmmb
                    LOGI("[INNO/socketinterface/AwesomePlayer.cpp] onVideoEvent ERROR_TIMEOUT");
                    postVideoEvent_l(100000ll);
                    return;
                }
                // So video playback is complete, but we may still have
                // a seek request pending that needs to be applied
                // to the audio track.
                if (mSeeking) {
                    LOGE("video stream ended while seeking!err %d",err);
                }
                finishSeekIfNecessary(-1);

                mFlags |= VIDEO_AT_EOS;
		LOGV("video stream ended err2:%d !",err);

                postStreamDoneEvent_l(err);
                return;
            }

            if (mVideoBuffer->range_length() == 0) {
                // Some decoders, notably the PV AVC software decoder
                // return spurious empty buffers that we just want to ignore.

                mVideoBuffer->release();
                mVideoBuffer = NULL;
                continue;
            }

            break;
        }
    }

    int64_t timeUs;
    CHECK(mVideoBuffer->meta_data()->findInt64(kKeyTime, &timeUs));

    {
        Mutex::Autolock autoLock(mMiscStateLock);
        mVideoTimeUs = timeUs;
    }

    bool wasSeeking = mSeeking;
    finishSeekIfNecessary(timeUs);

#ifdef _SYNC_USE_SYSTEM_TIME_   
    TimeSource *ts; //@jgdu
    if(mFlags & AUDIO_AT_EOS)
	ts =   &mSystemTimeSource;
    else
	ts =   &mSystemTimeSourceForSync;	
#else   
    TimeSource *ts = (mFlags & AUDIO_AT_EOS) ? &mSystemTimeSource : mTimeSource;
#endif

    int is_first_frame = 0;
    uint32_t tmp_mFlags = mFlags;
    if (mFlags & FIRST_FRAME) {
        mFlags &= ~FIRST_FRAME;
		
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
	LOGV("First Frame time:%d s",tv.tv_sec*1000 + tv.tv_usec/1000);
	is_first_frame = 1;	
	   
        mTimeSourceDeltaUs = ts->getRealTimeUs() - timeUs;
    }

    int64_t realTimeUs, mediaTimeUs;
    if (!(mFlags & AUDIO_AT_EOS) && mAudioPlayer != NULL
        && mAudioPlayer->getMediaTimeMapping(&realTimeUs, &mediaTimeUs)) {
        mTimeSourceDeltaUs = realTimeUs - mediaTimeUs;
    }

#ifdef _SYNC_USE_SYSTEM_TIME_  
   int64_t nowUs;//@jgdu
   if((mAudioPlayer != NULL)&&(0==(mFlags & AUDIO_AT_EOS))){
        int64_t  sysRealTimeUs =  ts->getRealTimeUs();  	
	int64_t  AudioLatencyUs =  mAudioPlayer->getAudioLatencyUs();
        if((realTimeUs-sysRealTimeUs)>max(500000,AudioLatencyUs*5/10))
             mSystemTimeSourceForSync.increaseRealTimeUs(realTimeUs-sysRealTimeUs -max(500000,AudioLatencyUs*5/10)); 	  	
	if((realTimeUs-sysRealTimeUs)<min(-300000,-AudioLatencyUs*3/10))	
             mSystemTimeSourceForSync.increaseRealTimeUs(min(-300000,-AudioLatencyUs*3/10)); 		
   	nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs -AudioLatencyUs + 40000 ; //+400000 -> 40000 for syn;//assume display latency  400ms
   }else{
   	nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs;
   }
#else
   int64_t nowUs = ts->getRealTimeUs() - mTimeSourceDeltaUs;
#endif

    int64_t latenessUs = nowUs - timeUs;
    if(latenessUs > 60000 || latenessUs< -60000){
    	LOGI("video timestamp %lld,%lld,%lld:%lld,%lld,%lld",nowUs,timeUs,latenessUs,realTimeUs,mediaTimeUs,realTimeUs - mediaTimeUs);
    }
    if(latenessUs > 2000000 || latenessUs< -2000000){//jgdu 2s
	LOGI("onVideoEvent time info:mTimeSourceDeltaUs:%lld realTimeUs:%lld mediaTimeUs:%lld", mTimeSourceDeltaUs, realTimeUs, mediaTimeUs);
	LOGI("onVideoEvent time info2:getRealTimeUs:%lld nowUs:%lld latenessUs:%lld timeUs:%lld", ts->getRealTimeUs(), nowUs,latenessUs, timeUs);	
    }
	
    if (wasSeeking) {
        // Let's display the first frame after seeking right away.
        latenessUs = 0;
		if(mRTSPController != NULL)
		{
			mSystemTimeSourceForSync.reset();
		}
    }

    if ((mRTPSession != NULL) || mIsVideoPhoneStream) {//sprd vt must
        // We'll completely ignore timestamps for gtalk videochat
        // and we'll play incoming video as fast as we get it.
        latenessUs = 0;
    }

    if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mUri.string(),35)) //@Hong. SpeedupCMMB
    {
    	if(is_first_frame){
    		latenessUs = 0;
		LOGI("cmmb is_first_frame");
    	}
	if((latenessUs - kLowWaterMarkUs) > 1000000){
		mSkipTimeUs = timeUs + (latenessUs - kLowWaterMarkUs);
		LOGI("mSkipTimeUs %lld,timeUs %lld",mSkipTimeUs,timeUs);
	}else{
		mSkipTimeUs = -1;
	}
    }

    if (latenessUs > 60000) {
        // We're more than 40ms late.
        LOGV("we're late by %lld us (%.2f secs)", latenessUs, latenessUs / 1E6);

        mVideoBuffer->release();
        mVideoBuffer = NULL;

        postVideoEvent_l();
        return;
    }

    if (latenessUs < -10000) {
  		LOGI("we're early by %lld us (%.2f secs)", latenessUs, latenessUs / 1E6);
        postVideoEvent_l(10000);
        return;
    }

    if (mVideoRendererIsPreview || mVideoRenderer == NULL || mNewSurfaceIsSet) {
        mVideoRendererIsPreview = false;
        mNewSurfaceIsSet = false;
        status_t err = initRenderer_l();

        if (err != OK) {
            finishSeekIfNecessary(-1);

            mFlags |= VIDEO_AT_EOS;
		LOGV("video stream ended err1:%d !",err);			
            postStreamDoneEvent_l(err);
            return;
        }
    }

    if (mVideoRenderer != NULL) {
        mVideoRenderer->render(mVideoBuffer);
    }else{
    	if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mUri.string(),35)){ //@Hong. SpeedupCMMB	
       	if(tmp_mFlags& FIRST_FRAME){
	   		mFlags |= FIRST_FRAME;
       	}
	 postVideoEvent_l(10000);
        return;
    	}
    	LOGI("mVideoRenderer is NULL");	
    }

    if (mLastVideoBuffer) {
        mLastVideoBuffer->release();
        mLastVideoBuffer = NULL;
    }
    mLastVideoBuffer = mVideoBuffer;
    mVideoBuffer = NULL;

    postVideoEvent_l();
}

void AwesomePlayer::postVideoEvent_l(int64_t delayUs) {
    if (mVideoEventPending) {
		LOGI("AwesomePlayer::postVideoEvent_l mVideoEventPending so return"); 
		return;
    }

    mVideoEventPending = true;
    mQueue.postEventWithDelay(mVideoEvent, delayUs < 0 ? 10000 : delayUs);
}

void AwesomePlayer::postStreamDoneEvent_l(status_t status) {
    if (mStreamDoneEventPending) {
        return;
    }
    mStreamDoneEventPending = true;

    mStreamDoneStatus = status;
    mQueue.postEvent(mStreamDoneEvent);
}

void AwesomePlayer::postBufferingEvent_l() {
    if (mBufferingEventPending) {
        return;
    }
    mBufferingEventPending = true;
    mQueue.postEventWithDelay(mBufferingEvent, 1000000ll);
}

void AwesomePlayer::postCheckAudioStatusEvent_l() {
    if (mAudioStatusEventPending) {
        return;
    }
    mAudioStatusEventPending = true;
    mQueue.postEvent(mCheckAudioStatusEvent);
}

void AwesomePlayer::onCheckAudioStatus() {
    Mutex::Autolock autoLock(mLock);
    if (!mAudioStatusEventPending) {
        // Event was dispatched and while we were blocking on the mutex,
        // has already been cancelled.
        return;
    }

    mAudioStatusEventPending = false;

    if (mWatchForAudioSeekComplete && (mAudioPlayer != NULL) && !mAudioPlayer->isSeeking()) {
        mWatchForAudioSeekComplete = false;

        if (!mSeekNotificationSent) {
            notifyListener_l(MEDIA_SEEK_COMPLETE);
            mSeekNotificationSent = true;
        }
		if (mRTSPController == NULL)
		{
   		  mSeeking = false;
		}
    }

    status_t finalStatus;
    if (mWatchForAudioEOS && (mAudioPlayer != NULL) && mAudioPlayer->reachedEOS(&finalStatus)) {
        mWatchForAudioEOS = false;
        mFlags |= AUDIO_AT_EOS;
        mFlags |= FIRST_FRAME;
		LOGV("audio stream ended err:%x !",finalStatus);		
        postStreamDoneEvent_l(finalStatus);
    }
}

status_t AwesomePlayer::prepare() {
	    struct timeval tv;  //@hong add timecheck.

	    gettimeofday(&tv, NULL);
LOGV("prepare enter time:%d s",tv.tv_sec*1000 + tv.tv_usec/1000);
	
   LOGV("prepare");
    Mutex::Autolock autoLock(mLock);
    return prepare_l();
}

status_t AwesomePlayer::prepare_l() {
   LOGV("prepare_l");
    if (mFlags & PREPARED) {
        return OK;
    }

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;
    }

    mIsAsyncPrepare = false;
    status_t err = prepareAsync_l();

    if (err != OK) {
        return err;
    }
LOGV("prepare_l waiting...");

    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }
LOGV("prepare_l waiting.ok..");
    return mPrepareResult;
}

status_t AwesomePlayer::prepareAsync() {
   LOGV("prepareAsync");	
    Mutex::Autolock autoLock(mLock);

    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    mIsAsyncPrepare = true;
    return prepareAsync_l();
}

status_t AwesomePlayer::prepareAsync_l() {
    if (mFlags & PREPARING) {
        return UNKNOWN_ERROR;  // async prepare already pending
    }

    if (!mQueueStarted) {
        mQueue.start();
        mQueueStarted = true;
    }

    mFlags |= PREPARING;
    mAsyncPrepareEvent = new AwesomeEvent(
            this, &AwesomePlayer::onPrepareAsyncEvent);

    mQueue.postEvent(mAsyncPrepareEvent);

    return OK;
}

char* strsplit(const char* src, char c, char* dest)
{
	char *temp = strchr(src, c);
	int len = 0;

	if ((src == NULL)||(dest == NULL)) return NULL;
	if (temp == NULL) return NULL;
	
	len = temp - src;
	memcpy(dest, src, len);
	LOGV("strsplit(%s, %c, %s)\n", src, c, dest);
	return dest;
}

status_t AwesomePlayer::finishSetDataSource_l() {
    sp<DataSource> dataSource;
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
LOGV("finishSetDataSource_l enter time:%d s",tv.tv_sec*1000 + tv.tv_usec/1000);

    if (!strncasecmp("http://", mUri.string(), 7)) {

		mLowWaterMarkUs = 2000000;
	    mHighWaterMarkUs = 10000000; 
	    mStartLowWaterMarkUs = 6000000 ;

		
        mConnectingDataSource = new NuHTTPDataSource;

        mLock.unlock();
        status_t err = mConnectingDataSource->connect(mUri, &mUriHeaders);
        mLock.lock();

        if (err != OK) {
            mConnectingDataSource.clear();

            LOGI("mConnectingDataSource->connect() returned %d", err);
            return err;
        }

#if 0
        mCachedSource = new NuCachedSource2(
                new ThrottledSource(
                    mConnectingDataSource, 50 * 1024 /* bytes/sec */));
#else
        mCachedSource = new NuCachedSource2(mConnectingDataSource);
#endif
        mConnectingDataSource.clear();

        dataSource = mCachedSource;

        // We're going to prefill the cache before trying to instantiate
        // the extractor below, as the latter is an operation that otherwise
        // could block on the datasource for a significant amount of time.
        // During that time we'd be unable to abort the preparation phase
        // without this prefill.

        mLock.unlock();

        for (;;) {
            bool eos;
            size_t cachedDataRemaining =
                mCachedSource->approxDataRemaining(&eos);

            if (eos || cachedDataRemaining >= kHighWaterMarkBytes
                    || (mFlags & PREPARE_CANCELLED)) {
                break;
            }

            usleep(200000);
        }

        mLock.lock();

        if (mFlags & PREPARE_CANCELLED) {
            LOGI("Prepare cancelled while waiting for initial cache fill.");
            return UNKNOWN_ERROR;
        }
    }
    else if (!strncasecmp(mUri.string(), "videophone://", 13)) //sprd vt must
    {
    	mIsVideoPhoneStream = true;
    	char buf[30] = {0};
		strsplit(mUri.string() + 13, ';', buf);
        sp<DataSource> source = new CharDeviceSource(buf);
        sp<MediaExtractor> extractor = NULL;
		int nLen = strlen(mUri.string());
		LOGV("mUri: %s, nLen: %d", mUri.string(), nLen);
		if (!strncasecmp((mUri.string() + nLen - 5), "mpeg4", 5)){
			LOGV("mpeg4");
			extractor =
            MediaExtractor::Create(source, MEDIA_MIMETYPE_CONTAINER_VIDEOPHONE_MPEG4);
		} else {
			LOGV("3gpp");
        	extractor =
            MediaExtractor::Create(source, MEDIA_MIMETYPE_CONTAINER_VIDEOPHONE_H263);
		}
        return setDataSource_l(extractor);
    }
	else if (!strncasecmp(mUri.string(), "httplive://", 11)) {
        String8 uri("http://");
        uri.append(mUri.string() + 11);

        sp<LiveSource> liveSource = new LiveSource(uri.string());

        mCachedSource = new NuCachedSource2(liveSource);
        dataSource = mCachedSource;

        sp<MediaExtractor> extractor =
            MediaExtractor::Create(dataSource, MEDIA_MIMETYPE_CONTAINER_MPEG2TS);

        static_cast<MPEG2TSExtractor *>(extractor.get())
            ->setLiveSource(liveSource);

        return setDataSource_l(extractor);
    } else if (!strncmp("rtsp://gtalk/", mUri.string(), 13)) {
        if (mLooper == NULL) {
            mLooper = new ALooper;
            mLooper->setName("gtalk rtp");
            mLooper->start(
                    false /* runOnCallingThread */,
                    false /* canCallJava */,
                    PRIORITY_HIGHEST);
        }

        const char *startOfCodecString = &mUri.string()[13];
        const char *startOfSlash1 = strchr(startOfCodecString, '/');
        if (startOfSlash1 == NULL) {
            return BAD_VALUE;
        }
        const char *startOfWidthString = &startOfSlash1[1];
        const char *startOfSlash2 = strchr(startOfWidthString, '/');
        if (startOfSlash2 == NULL) {
            return BAD_VALUE;
        }
        const char *startOfHeightString = &startOfSlash2[1];

        String8 codecString(startOfCodecString, startOfSlash1 - startOfCodecString);
        String8 widthString(startOfWidthString, startOfSlash2 - startOfWidthString);
        String8 heightString(startOfHeightString);

#if 0
        mRTPPusher = new UDPPusher("/data/misc/rtpout.bin", 5434);
        mLooper->registerHandler(mRTPPusher);

        mRTCPPusher = new UDPPusher("/data/misc/rtcpout.bin", 5435);
        mLooper->registerHandler(mRTCPPusher);
#endif

        mRTPSession = new ARTPSession;
        mLooper->registerHandler(mRTPSession);

#if 0
        // My AMR SDP
        static const char *raw =
            "v=0\r\n"
            "o=- 64 233572944 IN IP4 127.0.0.0\r\n"
            "s=QuickTime\r\n"
            "t=0 0\r\n"
            "a=range:npt=0-315\r\n"
            "a=isma-compliance:2,2.0,2\r\n"
            "m=audio 5434 RTP/AVP 97\r\n"
            "c=IN IP4 127.0.0.1\r\n"
            "b=AS:30\r\n"
            "a=rtpmap:97 AMR/8000/1\r\n"
            "a=fmtp:97 octet-align\r\n";
#elif 1
        String8 sdp;
        sdp.appendFormat(
            "v=0\r\n"
            "o=- 64 233572944 IN IP4 127.0.0.0\r\n"
            "s=QuickTime\r\n"
            "t=0 0\r\n"
            "a=range:npt=0-315\r\n"
            "a=isma-compliance:2,2.0,2\r\n"
            "m=video 5434 RTP/AVP 97\r\n"
            "c=IN IP4 127.0.0.1\r\n"
            "b=AS:30\r\n"
            "a=rtpmap:97 %s/90000\r\n"
            "a=cliprect:0,0,%s,%s\r\n"
            "a=framesize:97 %s-%s\r\n",

            codecString.string(),
            heightString.string(), widthString.string(),
            widthString.string(), heightString.string()
            );
        const char *raw = sdp.string();

#endif

        sp<ASessionDescription> desc = new ASessionDescription;
        CHECK(desc->setTo(raw, strlen(raw)));

        CHECK_EQ(mRTPSession->setup(desc), (status_t)OK);

        if (mRTPPusher != NULL) {
            mRTPPusher->start();
        }

        if (mRTCPPusher != NULL) {
            mRTCPPusher->start();
        }

        CHECK_EQ(mRTPSession->countTracks(), 1u);
        sp<MediaSource> source = mRTPSession->trackAt(0);

#if 0
        bool eos;
        while (((APacketSource *)source.get())
                ->getQueuedDuration(&eos) < 5000000ll && !eos) {
            usleep(100000ll);
        }
#endif

        const char *mime;
        CHECK(source->getFormat()->findCString(kKeyMIMEType, &mime));

        if (!strncasecmp("video/", mime, 6)) {
            setVideoSource(source);
        } else {
            CHECK(!strncasecmp("audio/", mime, 6));
            setAudioSource(source);
        }

        mExtractorFlags = MediaExtractor::CAN_PAUSE;

        return OK;
    } else if (!strncasecmp("rtsp://", mUri.string(), 7)) {
        if (mLooper == NULL) {
            mLooper = new ALooper;
            mLooper->setName("rtsp");
 //           mLooper->start();
 	   mLooper->start(false /* runOnCallingThread */,
                          false /* canCallJava */,
                          ANDROID_PRIORITY_DISPLAY); //@hong
			
        }
        mRTSPController = new ARTSPController(mLooper);
	    mConnectingRTSPController = mRTSPController;
		
        mLock.unlock();
        status_t err = mRTSPController->connect(mUri.string());
        mLock.lock();

        mConnectingRTSPController.clear();
        LOGI("ARTSPController::connect returned %d", err);
	    gettimeofday(&tv, NULL);
	LOGV("finishSetDataSource_l over time:%d s",tv.tv_sec);

        if (err != OK) {
            mRTSPController.clear();
            return err;
        }
		
	   	if (!strncasecmp("rtsp://127.0.0.1:8554/CMMBAudioVideo",mUri.string(),35)) //@Hong. SpeedupCMMB
			{
				mLowWaterMarkUs = kLowWaterMarkUs;
				mHighWaterMarkUs = kHighWaterMarkUs;
				mStartLowWaterMarkUs =kStartLowWaterMarkUs ;
			}
			else
			{
				mLowWaterMarkUs = 2000000;
				mHighWaterMarkUs = 10000000;
				mStartLowWaterMarkUs = 6000000 ;
				mCMMBLab = true ;
			}

        sp<MediaExtractor> extractor = mRTSPController.get();
        return setDataSource_l(extractor);
    }//cmmb
    else if(!strncasecmp("cmmb://mxd", mUri.string(), 10)) {
	LOGI("Create MxdCMMBExtractor");
	MxdCMMBExtractor* tmpEx = 	new MxdCMMBExtractor(mUri.string());
	sp<MediaExtractor> extractor = tmpEx;
	if (extractor == NULL) {
		return UNKNOWN_ERROR;
	}
	LOGI(" Create MxdCMMBExtractor successful");
	return setDataSource_l(extractor);
    }//cmmb

    else if(!strncasecmp("cmmb-audio://", mUri.string(), 13)) {
              sp<CmmbUriSource> uriSource;
              uriSource = new CmmbUriSource(mUri.string());
    		sp<MediaExtractor> extractor = MediaExtractor::Create(uriSource, "audio/cmmb");
    		if (extractor == NULL) {
    			return UNKNOWN_ERROR;
    		}
    		LOGV("Create CMMBExtractor DRA successful");
    		return setDataSource_l(extractor);
    	}
    else if(!strncasecmp("cmmb://", mUri.string(), 7)) {
        sp<CmmbUriSource> uriSource;
         uriSource = new CmmbUriSource(mUri.string());
		
		sp<MediaExtractor> extractor = MediaExtractor::Create(uriSource, "video/cmmb");
		if (extractor == NULL) {
			return UNKNOWN_ERROR;
		}
		LOGI("[INNO/socketinterface/AwesomePlayer.cpp] Create CMMBExtractor successful");
		return setDataSource_l(extractor);
        }
//added by innofidei end
	else {
        dataSource = DataSource::CreateFromURI(mUri.string(), &mUriHeaders);
    }

    if (dataSource == NULL) {
        return UNKNOWN_ERROR;
    }

    sp<MediaExtractor> extractor = MediaExtractor::Create(dataSource);

    if (extractor == NULL) {
        return UNKNOWN_ERROR;
    }

    return setDataSource_l(extractor);
}

void AwesomePlayer::abortPrepare(status_t err) {
    CHECK(err != OK);

    if (mIsAsyncPrepare) {
        notifyListener_l(MEDIA_ERROR, MEDIA_ERROR_UNKNOWN, err);
    }

    mPrepareResult = err;
    mFlags &= ~(PREPARING|PREPARE_CANCELLED|PREPARING_CONNECTED);
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();
}

// static
bool AwesomePlayer::ContinuePreparation(void *cookie) {
    AwesomePlayer *me = static_cast<AwesomePlayer *>(cookie);

    return (me->mFlags & PREPARE_CANCELLED) == 0;
}

void AwesomePlayer::onPrepareAsyncEvent() {
    Mutex::Autolock autoLock(mLock);

    if (mFlags & PREPARE_CANCELLED) {
        LOGI("prepare was cancelled before doing anything");
        abortPrepare(UNKNOWN_ERROR);
        return;
    }

    if (mUri.size() > 0) {
        status_t err = finishSetDataSource_l();

        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mVideoTrack != NULL && mVideoSource == NULL) {
        status_t err = initVideoDecoder();

        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

    if (mAudioTrack != NULL && mAudioSource == NULL) {
        status_t err = initAudioDecoder();

        if (err != OK) {
            abortPrepare(err);
            return;
        }
    }

	mFlags |= PREPARING_CONNECTED ;
    if (mCachedSource != NULL || mRTSPController != NULL) {
        postBufferingEvent_l();
    } else {
        finishAsyncPrepare_l();
    }
}


void AwesomePlayer::finishAsyncPrepare_l() {
    if (mIsAsyncPrepare) {
        if (mVideoWidth < 0 || mVideoHeight < 0) {
            notifyListener_l(MEDIA_SET_VIDEO_SIZE, 0, 0);
        } else {
            int32_t rotationDegrees;
            if (!mVideoTrack->getFormat()->findInt32(
                        kKeyRotation, &rotationDegrees)) {
                rotationDegrees = 0;
            }

#if 1
            if (rotationDegrees == 90 || rotationDegrees == 270) {
                notifyListener_l(
                        MEDIA_SET_VIDEO_SIZE, mVideoHeight, mVideoWidth);
            } else
#endif
            {
                notifyListener_l(
                        MEDIA_SET_VIDEO_SIZE, mVideoWidth, mVideoHeight);
            }
        }

        notifyListener_l(MEDIA_PREPARED);
    }

    mPrepareResult = OK;
    mFlags &= ~(PREPARING|PREPARE_CANCELLED|PREPARING_CONNECTED);
    mFlags |= PREPARED;
	{
	    struct timeval tv;  //@hong add timecheck.
	    gettimeofday(&tv, NULL);
LOGV("prepare end time:%d s",tv.tv_sec*1000 + tv.tv_usec/1000);
		
	}
    mAsyncPrepareEvent = NULL;
    mPreparedCondition.broadcast();
}

status_t AwesomePlayer::suspend() {
    LOGE("suspend");
    Mutex::Autolock autoLock(mLock);

    if (mSuspensionState != NULL) {
        if (mLastVideoBuffer == NULL) {
            //go into here if video is suspended again
            //after resuming without being played between
            //them
            SuspensionState *state = mSuspensionState;
            mSuspensionState = NULL;
            reset_l();
            mSuspensionState = state;
            return OK;
        }

        delete mSuspensionState;
        mSuspensionState = NULL;
    }

    if (mFlags & PREPARING) {
        mFlags |= PREPARE_CANCELLED;
        if (mConnectingDataSource != NULL) {
            LOGI("interrupting the connection process");
            mConnectingDataSource->disconnect();
        }
    }
LOGV("suspend waiting...");
    while (mFlags & PREPARING) {
        mPreparedCondition.wait(mLock);
    }
LOGV("suspend waiting over");

    SuspensionState *state = new SuspensionState;
    state->mUri = mUri;
    state->mUriHeaders = mUriHeaders;
    state->mFileSource = mFileSource;

    state->mFlags = mFlags & (PLAYING | AUTO_LOOPING | LOOPING | AT_EOS);
    getPosition(&state->mPositionUs);

    if (mLastVideoBuffer) {
        size_t size = mLastVideoBuffer->range_length();

        if (size) {
            int32_t unreadable;
            if (!mLastVideoBuffer->meta_data()->findInt32(
                        kKeyIsUnreadable, &unreadable)
                    || unreadable == 0) {
                state->mLastVideoFrameSize = size;
                state->mLastVideoFrame = malloc(size);
                memcpy(state->mLastVideoFrame,
                       (const uint8_t *)mLastVideoBuffer->data()
                            + mLastVideoBuffer->range_offset(),
                       size);

                state->mVideoWidth = mVideoWidth;
                state->mVideoHeight = mVideoHeight;

                sp<MetaData> meta = mVideoSource->getFormat();
                CHECK(meta->findInt32(kKeyColorFormat, &state->mColorFormat));
                CHECK(meta->findInt32(kKeyWidth, &state->mDecodedWidth));
                CHECK(meta->findInt32(kKeyHeight, &state->mDecodedHeight));
            } else {
                LOGV("Unable to save last video frame, we have no access to "
                     "the decoded video data.");
            }
        }
    }

    reset_l();

    mSuspensionState = state;

    return OK;
}

status_t AwesomePlayer::resume() {
    LOGE("resume");
    Mutex::Autolock autoLock(mLock);

    if (mSuspensionState == NULL) {
        return INVALID_OPERATION;
    }

    SuspensionState *state = mSuspensionState;
    mSuspensionState = NULL;

    status_t err;
    if (state->mFileSource != NULL) {
        err = setDataSource_l(state->mFileSource);

        if (err == OK) {
            mFileSource = state->mFileSource;
        }
    } else {
        err = setDataSource_l(state->mUri, &state->mUriHeaders);
    }

    if (err != OK) {
        delete state;
        state = NULL;

        return err;
    }

    seekTo_l(state->mPositionUs);

    mFlags = state->mFlags & (AUTO_LOOPING | LOOPING | AT_EOS);

    if (state->mLastVideoFrame && mISurface != NULL) {
        mVideoRenderer =
            new AwesomeLocalRenderer(
                    true,  // previewOnly
                    "",
                    (OMX_COLOR_FORMATTYPE)state->mColorFormat,
                    mISurface,
                    state->mVideoWidth,
                    state->mVideoHeight,
                    state->mDecodedWidth,
                    state->mDecodedHeight,
                    0);

        mVideoRendererIsPreview = true;

        ((AwesomeLocalRenderer *)mVideoRenderer.get())->render(
                state->mLastVideoFrame, state->mLastVideoFrameSize);
    }

    if (state->mFlags & PLAYING) {
        play_l();
    }

    mSuspensionState = state;
    state = NULL;

    return OK;
}

uint32_t AwesomePlayer::flags() const {
    return mExtractorFlags;
}

void AwesomePlayer::postAudioEOS() {
    postCheckAudioStatusEvent_l();
}

void AwesomePlayer::postAudioSeekComplete() {
    postCheckAudioStatusEvent_l();
}

}  // namespace android

