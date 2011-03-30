/*
 **
 ** Copyright (c) 2008 The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "MediaPhoneC"
#include <utils/Log.h>
#include <surfaceflinger/Surface.h>
#include <media/mediaphone.h>
#include <binder/IServiceManager.h>
#include <utils/String8.h>
#include <media/IMediaPlayerService.h>
#include <media/IMediaPhone.h>

namespace android {

status_t MediaPhone::setComm(const char* urlIn, const char* urlOut)
{
    LOGV("setComm(%p, %p)", urlIn, urlOut);
    if(mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_IDLE)) {
        LOGE("setComm called in an invalid state(%d)", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->setComm(urlIn, urlOut);
    if (OK != ret) {
        LOGV("setComm failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }
    //mCurrentState = MEDIA_PHONE_INITIALIZED;
    return ret;
}

status_t MediaPhone::setCamera(const sp<ICamera>& camera)
{
    LOGV("setCamera(%p)", camera.get());
    if(mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_IDLE)) {
        LOGE("setCamera called in an invalid state(%d)", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->setCamera(camera);
    if (OK != ret) {
        LOGV("setCamera failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }
    return ret;
}

status_t MediaPhone::setRemoteSurface(const sp<Surface>& surface)
{
    LOGV("setRemoteSurface(%p)", surface.get());
    if(mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_IDLE)) {
        LOGE("setRemoteSurface called in an invalid state(%d)", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->setRemoteSurface(surface->getISurface());
    if (OK != ret) {
        LOGV("setRemoteSurface failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }
    return ret;
}

status_t MediaPhone::setLocalSurface(const sp<Surface>& surface)
{
    LOGV("setLocalSurface(%p)", surface.get());
    if(mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_IDLE)) {
        LOGE("setLocalSurface called in an invalid state(%d)", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->setLocalSurface(surface->getISurface());
    if (OK != ret) {
        LOGV("setLocalSurface failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }
    return ret;
}

status_t MediaPhone::setParameters(const String8& params) {
    LOGV("setParameters(%s)", params.string());
    if(mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }

    bool isInvalidState = (mCurrentState &
                           (MEDIA_PHONE_STARTED |
                            MEDIA_PHONE_ERROR));
    if (isInvalidState) {
        LOGE("setParameters is called in an invalid state: %d", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->setParameters(params);
    if (OK != ret) {
        LOGE("setParameters(%s) failed: %d", params.string(), ret);
        // Do not change our current state to MEDIA_RECORDER_ERROR, failures
        // of the only currently supported parameters, "max-duration" and
        // "max-filesize" are _not_ fatal.
    }

    return ret;
}

// must call with lock held
status_t MediaPhone::prepareAsync_l()
{
    if ( (mMediaPhone != 0) && ( mCurrentState & MEDIA_PHONE_IDLE ) ) {
        //mMediaPhone->setAudioStreamType(mStreamType);
        mCurrentState = MEDIA_PHONE_PREPARING;
        return mMediaPhone->prepareAsync();
    }
    LOGE("prepareAsync called in state %d", mCurrentState);
    return INVALID_OPERATION;
}

status_t MediaPhone::prepareAsync()
{
    LOGV("prepareAsync");
    Mutex::Autolock _l(mLock);
    return prepareAsync_l();
}

status_t MediaPhone::start()
{
    LOGV("start");
    if (mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_PREPARED)) {
        LOGE("start called in an invalid state: %d", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->start();
    if (OK != ret) {
        LOGE("connect failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }
    mCurrentState = MEDIA_PHONE_STARTED;
    return ret;
}

status_t MediaPhone::stop()
{
    LOGV("stop");
    if (mMediaPhone == NULL) {
        LOGE("media phone is not initialized yet");
        return INVALID_OPERATION;
    }
    if (!(mCurrentState & MEDIA_PHONE_STARTED)) {
        LOGE("stop called in an invalid state: %d", mCurrentState);
        return INVALID_OPERATION;
    }

    status_t ret = mMediaPhone->stop();
    if (OK != ret) {
        LOGE("disconnect failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }

    mCurrentState = MEDIA_PHONE_IDLE;
    return ret;
}

status_t MediaPhone::release()
{
    LOGV("release");
    if (mMediaPhone != NULL) {
        return mMediaPhone->release();
    }
    return INVALID_OPERATION;
}

MediaPhone::MediaPhone()
{
    LOGV("constructor");
    sp<IServiceManager> sm = defaultServiceManager();
    sp<IBinder> binder;

    do {
        binder = sm->getService(String16("media.player"));
        if (binder != NULL) {
            break;
        }
        LOGW("MediaPlayerService not published, waiting...");
        usleep(500000); // 0.5 s
    } while(true);

    sp<IMediaPlayerService> service = interface_cast<IMediaPlayerService>(binder);
    if (service != NULL) {
        mMediaPhone = service->createMediaPhone(getpid());
    }
    if (mMediaPhone != NULL) {
        mCurrentState = MEDIA_PHONE_IDLE;
    }
}

/*
status_t MediaPhone::initCheck()
{
    return mMediaPhone != 0 ? NO_ERROR : NO_INIT;
}
*/

MediaPhone::~MediaPhone()
{
    LOGV("destructor");
    if (mMediaPhone != NULL) {
        mMediaPhone.clear();
    }
}

status_t MediaPhone::setListener(const sp<MediaPhoneListener>& listener)
{
    LOGV("setListener");
    Mutex::Autolock _l(mLock);
    mListener = listener;
    
    status_t ret = mMediaPhone->setListener(this);
    if (OK != ret) {
        LOGV("setListener failed: %d", ret);
        mCurrentState = MEDIA_PHONE_ERROR;
        return ret;
    }

    return NO_ERROR;
}

status_t MediaPhone::setAudioStreamType(int type)
{
    LOGV("setAudioStreamType");
    Mutex::Autolock _l(mLock);
    //if (mStreamType == type) return NO_ERROR;
    if (mCurrentState & MEDIA_PHONE_PREPARED) {
        // Can't change the stream type after prepare
        LOGE("setAudioStream called in state %d", mCurrentState);
        return INVALID_OPERATION;
    }
    // cache
    //mStreamType = type;
    return OK;
}

status_t MediaPhone::setVolume(float leftVolume, float rightVolume)
{
    LOGV("setVolume(%f, %f)", leftVolume, rightVolume);
    Mutex::Autolock _l(mLock);
    //mLeftVolume = leftVolume;
    //mRightVolume = rightVolume;
    if (mMediaPhone != NULL) {
        return mMediaPhone->setVolume(leftVolume, rightVolume);
    }
    return OK;
}

void MediaPhone::notify(int msg, int ext1, int ext2)
{
    LOGV("message received msg=%d, ext1=%d, ext2=%d", msg, ext1, ext2);
    
    switch (msg) {
    case MEDIA_PHONE_EVENT_PREPARED:
        LOGV("prepared");
        mCurrentState = MEDIA_PHONE_PREPARED;
        break;
    }

    sp<MediaPhoneListener> listener;
    mLock.lock();
    listener = mListener;
    mLock.unlock();

    if (listener != NULL) {
        Mutex::Autolock _l(mNotifyLock);
        LOGV("callback application");
        listener->notify(msg, ext1, ext2);
        LOGV("back from callback");
    }
}

}; // namespace android

