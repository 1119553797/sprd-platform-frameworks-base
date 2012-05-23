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
#define LOG_TAG "CameraSource"
#include <utils/Log.h>

#include <OMX_Component.h>
#include <binder/IPCThreadState.h>
#include <media/stagefright/CameraSource.h>
#include <media/stagefright/MediaDebug.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <camera/Camera.h>
#include <camera/CameraParameters.h>
#include <utils/String8.h>
#include <cutils/properties.h>

#include <sys/ioctl.h>
#include <linux/android_pmem.h>
#include <cutils/properties.h>

namespace android {

static bool s_bDebug = false;
#define DEBUG_LOGD if(s_bDebug)LOGV

struct CameraSourceListener : public CameraListener {
    CameraSourceListener(const sp<CameraSource> &source);

    virtual void notify(int32_t msgType, int32_t ext1, int32_t ext2);
    virtual void postData(int32_t msgType, const sp<IMemory> &dataPtr);

    virtual void postDataTimestamp(
            nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr);

protected:
    virtual ~CameraSourceListener();

private:
    wp<CameraSource> mSource;

    CameraSourceListener(const CameraSourceListener &);
    CameraSourceListener &operator=(const CameraSourceListener &);
};

CameraSourceListener::CameraSourceListener(const sp<CameraSource> &source)
    : mSource(source) {
}

CameraSourceListener::~CameraSourceListener() {
}

void CameraSourceListener::notify(int32_t msgType, int32_t ext1, int32_t ext2) {
    LOGI("notify(%d, %d, %d)", msgType, ext1, ext2);
}

void CameraSourceListener::postData(int32_t msgType, const sp<IMemory> &dataPtr) {
    LOGI("postData(%d, ptr:%p, size:%d)",
         msgType, dataPtr->pointer(), dataPtr->size());
}

void CameraSourceListener::postDataTimestamp(
        nsecs_t timestamp, int32_t msgType, const sp<IMemory>& dataPtr) {

    sp<CameraSource> source = mSource.promote();
    if (source.get() != NULL) {
        source->dataCallbackTimestamp(timestamp/1000, msgType, dataPtr);
    }
}

static int32_t getColorFormat(const char* colorFormat) {
    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV422SP)) {
       return OMX_COLOR_FormatYUV422SemiPlanar;
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV420SP)) {
        return OMX_COLOR_FormatYUV420SemiPlanar;
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_YUV422I)) {
        return OMX_COLOR_FormatYCbYCr;
    }

    if (!strcmp(colorFormat, CameraParameters::PIXEL_FORMAT_RGB565)) {
       return OMX_COLOR_Format16bitRGB565;
    }

    LOGE("Uknown color format (%s), please add it to "
         "CameraSource::getColorFormat", colorFormat);

    CHECK_EQ(0, "Unknown color format");
}

// static
CameraSource *CameraSource::Create() {
    sp<Camera> camera = Camera::connect(0);

    if (camera.get() == NULL) {
        return NULL;
    }

    return new CameraSource(camera);
}

// static
CameraSource *CameraSource::CreateFromCamera(const sp<Camera> &camera) {
    if (camera.get() == NULL) {
        return NULL;
    }

    return new CameraSource(camera);
}

CameraSource::CameraSource(const sp<Camera> &camera)
    : mCamera(camera),
      mFirstFrameTimeUs(0),
      mLastFrameTimestampUs(0),
      mNumFramesReceived(0),
      mNumFramesEncoded(0),
      mNumFramesDropped(0),
      mNumGlitches(0),
      mGlitchDurationThresholdUs(200000),
      mCollectStats(false),
      mStarted(false) {

	char propBuf[PROPERTY_VALUE_MAX];  
        property_get("debug.videophone", propBuf, "");	
	LOGI("property_get: %s.", propBuf);
	if (!strcmp(propBuf, "1")) {
		s_bDebug = true;
	} else {
		s_bDebug = false;
	}

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    String8 s = mCamera->getParameters();
    IPCThreadState::self()->restoreCallingIdentity(token);

    printf("params: \"%s\"\n", s.string());

    int32_t width, height, stride, sliceHeight;
    CameraParameters params(s);
    params.getPreviewSize(&width, &height);

    // Calculate glitch duraton threshold based on frame rate
    int32_t frameRate = params.getPreviewFrameRate();
    int64_t glitchDurationUs = (1000000LL / frameRate);
    if (glitchDurationUs > mGlitchDurationThresholdUs) {
        mGlitchDurationThresholdUs = glitchDurationUs;
    }

    //@zha
    //const char *colorFormatStr = params.get(CameraParameters::KEY_VIDEO_FRAME_FORMAT);
    //CHECK(colorFormatStr != NULL);
    //int32_t colorFormat = getColorFormat(colorFormatStr);
    int32_t colorFormat = OMX_COLOR_FormatYUV420SemiPlanar;

    // XXX: query camera for the stride and slice height
    // when the capability becomes available.
    stride = width;
    sliceHeight = height;

    mMeta = new MetaData;
    mMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_RAW);
    mMeta->setInt32(kKeyColorFormat, colorFormat);
    mMeta->setInt32(kKeyWidth, width);
    mMeta->setInt32(kKeyHeight, height);
    mMeta->setInt32(kKeyStride, stride);
    mMeta->setInt32(kKeySliceHeight, sliceHeight);

}

CameraSource::~CameraSource() {
    if (mStarted) {
        stop();
    }
}

status_t CameraSource::start(MetaData *meta) {
    LOGI("start");	
    CHECK(!mStarted);

    char value[PROPERTY_VALUE_MAX];
    if (property_get("media.stagefright.record-stats", value, NULL)
        && (!strcmp(value, "1") || !strcasecmp(value, "true"))) {
        mCollectStats = true;
    }

    mStartTimeUs = 0;
    int64_t startTimeUs;
    if (meta && meta->findInt64(kKeyTime, &startTimeUs)) {
        mStartTimeUs = startTimeUs;
    }

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->setListener(new CameraSourceListener(this));
    CHECK_EQ(OK, mCamera->startRecording());
    IPCThreadState::self()->restoreCallingIdentity(token);

    mStarted = true;
    LOGI("start X");		
    return OK;
}

status_t CameraSource::stop() {
    LOGI("stop");
    Mutex::Autolock autoLock(mLock);
    mStarted = false;
    mFrameAvailableCondition.signal();

    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->setListener(NULL);
    mCamera->stopRecording();
    releaseQueuedFrames();
    while (!mFramesBeingEncoded.empty()) {
        LOGI("Waiting for outstanding frames being encoded: %d",
                mFramesBeingEncoded.size());
        mFrameCompleteCondition.wait(mLock);
    }
    mCamera.clear();
    IPCThreadState::self()->restoreCallingIdentity(token);

    if (mCollectStats) {
        LOGI("Frames received/encoded/dropped: %d/%d/%d in %lld us",
                mNumFramesReceived, mNumFramesEncoded, mNumFramesDropped,
                mLastFrameTimestampUs - mFirstFrameTimeUs);
    }

    CHECK_EQ(mNumFramesReceived, mNumFramesEncoded + mNumFramesDropped);
    LOGI("stop X");			
    return OK;
}

void CameraSource::releaseQueuedFrames() {
    List<sp<IMemory> >::iterator it;
    while (!mFramesReceived.empty()) {
        it = mFramesReceived.begin();
        mCamera->releaseRecordingFrame(*it);
        mFramesReceived.erase(it);
        ++mNumFramesDropped;
    }
}

sp<MetaData> CameraSource::getFormat() {
    return mMeta;
}

void CameraSource::releaseOneRecordingFrame(const sp<IMemory>& frame) {
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    mCamera->releaseRecordingFrame(frame);
    IPCThreadState::self()->restoreCallingIdentity(token);
}

void CameraSource::signalBufferReturned(MediaBuffer *buffer) {
    DEBUG_LOGD("signalBufferReturned: %p", buffer->data());
    Mutex::Autolock autoLock(mLock);
    for (List<sp<IMemory> >::iterator it = mFramesBeingEncoded.begin();
         it != mFramesBeingEncoded.end(); ++it) {
        if ((*it)->pointer() ==  buffer->data()) {

            releaseOneRecordingFrame((*it));
            mFramesBeingEncoded.erase(it);
            ++mNumFramesEncoded;
            buffer->setObserver(0);
            buffer->release();
            mFrameCompleteCondition.signal();
            return;
        }
    }
    CHECK_EQ(0, "signalBufferReturned: bogus buffer");
}

status_t CameraSource::read(
        MediaBuffer **buffer, const ReadOptions *options) {
    DEBUG_LOGD("read");

    *buffer = NULL;

    int64_t seekTimeUs;
    ReadOptions::SeekMode mode;
    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
        return ERROR_UNSUPPORTED;
    }

    sp<IMemory> frame;
    int64_t frameTime;

    {
        Mutex::Autolock autoLock(mLock);
        while (mStarted) {
            while(mFramesReceived.empty()) {
                mFrameAvailableCondition.wait(mLock);
            }

            if (!mStarted) {
                return OK;
            }

            frame = *mFramesReceived.begin();
            mFramesReceived.erase(mFramesReceived.begin());

            frameTime = *mFrameTimes.begin();
            mFrameTimes.erase(mFrameTimes.begin());
            int64_t skipTimeUs;
            if (!options || !options->getSkipFrame(&skipTimeUs)) {
                skipTimeUs = frameTime;
            }
            if (skipTimeUs > frameTime) {
                DEBUG_LOGD("skipTimeUs: %lld us > frameTime: %lld us",
                    skipTimeUs, frameTime);
                releaseOneRecordingFrame(frame);
                ++mNumFramesDropped;
                // Safeguard against the abuse of the kSkipFrame_Option.
                if (skipTimeUs - frameTime >= 1E6) {
                    LOGE("Frame skipping requested is way too long: %lld us",
                        skipTimeUs - frameTime);
                    return UNKNOWN_ERROR;
                }
            } else {
                mFramesBeingEncoded.push_back(frame);
                *buffer = new MediaBuffer(frame->pointer(), frame->size());//@zha temporary modify for sprd camera output size error
                DEBUG_LOGD("read: buffer.size() %d", (*buffer)->size());
                (*buffer)->setObserver(this);
                (*buffer)->add_ref();
                (*buffer)->meta_data()->setInt64(kKeyTime, frameTime);

		//added by jgdu to get physical address 
		int32_t phy_addr;
		ssize_t offset = 0;
    		size_t size = 0;
    		sp<IMemoryHeap> heap = frame->getMemory(&offset, &size);
		int fd = heap->getHeapID();	
	        struct pmem_region region;
	        ::ioctl(fd,PMEM_GET_PHYS,&region);
		phy_addr = region.offset+offset;								
    		//LOGI("CameraSource::read: ID = %d, base = %p, offset = %p, size = %d pointer %p,phy addr %p", heap->getHeapID(), heap->base(), offset, size, frame->pointer(),phy_addr);
		(*buffer)->meta_data()->setInt32(kKeyPlatformPrivate, phy_addr);
                return OK;
            }
        }
    }
    return OK;
}

void CameraSource::dataCallbackTimestamp(int64_t timestampUs,
        int32_t msgType, const sp<IMemory> &data) {
    DEBUG_LOGD("dataCallbackTimestamp: timestamp %lld us", timestampUs);
    Mutex::Autolock autoLock(mLock);
    if (!mStarted) {
        releaseOneRecordingFrame(data);
        ++mNumFramesReceived;
        ++mNumFramesDropped;
        return;
    }

    if (mNumFramesReceived > 0 &&
        timestampUs - mLastFrameTimestampUs > mGlitchDurationThresholdUs) {
        if (mNumGlitches % 10 == 0) {  // Don't spam the log
            LOGW("Long delay detected in video recording");
        }
        ++mNumGlitches;
    }

    mLastFrameTimestampUs = timestampUs;
    if (mNumFramesReceived == 0) {
        mFirstFrameTimeUs = timestampUs;
        // Initial delay
        if (mStartTimeUs > 0) {
            if (timestampUs < mStartTimeUs) {
                // Frame was captured before recording was started
                // Drop it without updating the statistical data.
                releaseOneRecordingFrame(data);
                return;
            }
            mStartTimeUs = timestampUs - mStartTimeUs;
        }
    }
    ++mNumFramesReceived;

    mFramesReceived.push_back(data);
    int64_t timeUs = mStartTimeUs + (timestampUs - mFirstFrameTimeUs);
    mFrameTimes.push_back(timeUs);
    DEBUG_LOGD("initial delay: %lld, current time stamp: %lld",
        mStartTimeUs, timeUs);
    mFrameAvailableCondition.signal();
}

}  // namespace android
