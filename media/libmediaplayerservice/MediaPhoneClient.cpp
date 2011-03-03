/*
 ** Copyright 2008, HTC Inc.
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

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaPhoneService"
#include <utils/Log.h>

#include <fcntl.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>
#include <string.h>
#include <cutils/atomic.h>
#include <android_runtime/ActivityManager.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryBase.h>
#include <utils/String16.h>

#include <media/AudioTrack.h>

#include "MediaPhoneClient.h"
#include "MediaPlayerService.h"

#include "StagefrightPlayer.h"
#include "StagefrightRecorder.h"

//notes:
//create   new mediaplayer, new mediarecorder,init,setListener
//setsurface
//setcamera                 setCamera
//setcomm  setDataSource    setOutputFile
//prepare  prepareAsync     setVideoSource/setOutputFormat/setVideoEncoder/prepare
//setparams                 setParameters/setVideoEncoder
//star     start            start
//stop     stop             stop
//
//(idle)-setcomm->(inited)-prepare->(prepared)-start->(started)-stop->(stopped)

namespace android {

static const char* cameraPermission = "android.permission.CAMERA";
static const char* recordAudioPermission = "android.permission.RECORD_AUDIO";

static bool checkPermission(const char* permissionString) {
#ifndef HAVE_ANDROID_OS
    return true;
#endif
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16(permissionString));
    if (!ok) LOGE("Request requires %s", permissionString);
    return ok;
}

status_t MediaPhoneClient::setComm(const char *urlIn, const char *urlout)
{
    LOGV("setComm");
    //todo: fix me
    if (mPlayer == NULL) {
        LOGE("mediaphone: player is not initialized");
        return NO_INIT;
    }
    mPlayer->setDataSource(urlIn, NULL);
    int fd = open(urlout, O_WRONLY);
    return mRecorder->setOutputFile(fd, 0, 0);
}

status_t MediaPhoneClient::setCamera(const sp<ICamera>& camera)
{
    LOGV("setCamera");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setCamera(camera);
}

status_t MediaPhoneClient::setRemoteSurface(const sp<ISurface>& surface)
{
    LOGV("setRemoteSurface");
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        LOGE("mediaphone: player is not initialized");
        return NO_INIT;
    }
    return mPlayer->setVideoSurface(surface);
}

status_t MediaPhoneClient::setLocalSurface(const sp<ISurface>& surface)
{
    LOGV("setLocalSurface");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
    return mRecorder->setPreviewSurface(surface);
}

status_t MediaPhoneClient::setParameters(const String8 &params)
{
    LOGV("setParameters(%s)", params.string());
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
    //todo: video encoder setting
    return mRecorder->setParameters(params);
}

status_t MediaPhoneClient::prepareAsync()
{
    LOGV("[%d] prepareAsync", mConnId);
    if (mPlayer == NULL || mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
    mRecorder->setVideoSource(VIDEO_SOURCE_CAMERA);
    mRecorder->setOutputFormat(OUTPUT_FORMAT_MPEG_4);
    mRecorder->setVideoEncoder(VIDEO_ENCODER_H263);
    mRecorder->prepare();
    return mPlayer->prepareAsync();
}

status_t MediaPhoneClient::start()
{
    LOGV("start");
    //todo: fix me
    if (mPlayer == NULL || mRecorder == NULL) {
        LOGE("mediaphone: player & recorder is not initialized");
        return NO_INIT;
    }
    mPlayer->start();
    mRecorder->start();
    return OK;
}

status_t MediaPhoneClient::stop()
{
    LOGV("stop");
    //todo: fix me
    if (mPlayer == NULL || mRecorder == NULL) {
        LOGE("mediaphone: player & recorder is not initialized");
        return NO_INIT;
    }
    mPlayer->stop();
    mRecorder->stop();
    return OK;
}

status_t MediaPhoneClient::release()
{
    LOGV("release");
    Mutex::Autolock lock(mLock);
    if (mRecorder != NULL) {
        delete mRecorder;
        mRecorder = NULL;
    }
    if (mPlayer != NULL) {
        delete mPlayer;
        mPlayer = NULL;
    }
    wp<MediaPhoneClient> client(this);
    mMediaPlayerService->removeMediaPhoneClient(client);
    return NO_ERROR;
}

MediaPhoneClient::MediaPhoneClient(const sp<MediaPlayerService>& service, pid_t pid)
{
    LOGV("Client constructor");
    mPid = pid;
    mRecorder = new StagefrightRecorder();
    mPlayer = new StagefrightPlayer();
    mMediaPlayerService = service;
    mPlayer->setNotifyCallback(this, notify);
}

MediaPhoneClient::~MediaPhoneClient()
{
    LOGV("Client destructor");
    release();
}

void MediaPhoneClient::notify(void* cookie, int msg, int ext1, int ext2)
{
    LOGV("notify");
    MediaPhoneClient* client = static_cast<MediaPhoneClient*>(cookie);
    //todo: handle prepared/media_error msgs
    //refer to mediaplayer.cpp notify
    client->mListener->notify(msg, ext1, ext2);
}

status_t MediaPhoneClient::setListener(const sp<IMediaPlayerClient>& listener)
{
    LOGV("setListener");
    mListener = listener;
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        LOGE("mediaphone is not initialized");
        return NO_INIT;
    }
    return mRecorder->setListener(listener);
}

status_t MediaPhoneClient::setAudioStreamType(int type)
{
    LOGV("setAudioStreamType(%d)", type);
    Mutex::Autolock l(mLock);
    //if (mAudioOutput != 0) mAudioOutput->setAudioStreamType(type);
    return NO_ERROR;
}

status_t MediaPhoneClient::setVolume(float leftVolume, float rightVolume)
{
    LOGV("[%d] setVolume(%f, %f)", mConnId, leftVolume, rightVolume);
    Mutex::Autolock l(mLock);
    //if (mAudioOutput != 0) mAudioOutput->setVolume(leftVolume, rightVolume);
    return NO_ERROR;
}


}; // namespace android

