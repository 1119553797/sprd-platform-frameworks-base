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

#define LOG_NDEBUG 0
#define LOG_TAG "MediaPhoneService"
#include <utils/Log.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <dirent.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <cutils/atomic.h>
#include <cutils/properties.h> // for property_get
#include <android_runtime/ActivityManager.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/MemoryHeapBase.h>
#include <binder/MemoryBase.h>
#include <utils/String16.h>

#include <camera/ICamera.h>
#include <camera/Camera.h>
#include <camera/CameraParameters.h>

#include <media/AudioTrack.h>
#include <media/mediaphone.h>

#include "../libstagefright/include/VideoPhoneExtractor.h"//sprd vt must

#include "MediaPhoneClient.h"
#include "MediaPlayerService.h"

#include "StagefrightPlayer.h"
#include "StagefrightRecorder.h"

namespace android {

static const char* cameraPermission = "android.permission.CAMERA";
static const char* recordAudioPermission = "android.permission.RECORD_AUDIO";

#define CHECK_RT(val) \
    do { \
        status_t r = val; \
        if (r != OK) return r; \
    } while(0)
	
static bool checkPermission(const char* permissionString) {
#ifndef HAVE_ANDROID_OS
    return true;
#endif
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16(permissionString));
    if (!ok) LOGE("Request requires %s", permissionString);
    return ok;
}

status_t MediaPhoneClient::setComm(const char *urlIn, const char *urlOut)
{
    LOGV("setComm %s %s", urlIn, urlOut);
    if (strlen(urlIn) >= MAX_URL_LEN || strlen(urlOut) >= MAX_URL_LEN) {
        LOGE("mediaphone: url length exceeds MAX_URL_LEN");
        return UNKNOWN_ERROR;
    }
    strcpy(mUrlIn, urlIn);
    strcpy(mUrlOut, urlOut);
	// read out all the residual data of last call
	{
		int fd = open(mUrlIn + strlen("videophone://"), O_RDONLY|O_NONBLOCK);
		char data[10000] = {0};
		if (fd > 0) {
			LOGV("setComm(), read residual data of last call: %d", read(fd, data, 10000));
			close(fd);
		}
	}
    return OK;
}

status_t MediaPhoneClient::setCamera(const sp<ICamera>& camera)
{
    LOGV("setCamera");
    Mutex::Autolock lock(mLock);
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
    mCamera = camera;
    return OK;
    //return mRecorder->setCamera(camera);
}

status_t MediaPhoneClient::setRemoteSurface(const sp<ISurface>& surface)
{
    LOGV("setRemoteSurface");
    Mutex::Autolock lock(mLock);
    if (mPlayer == NULL) {
        LOGE("mediaphone: player is not initialized");
        return NO_INIT;
    }
	mRemoteSurface = surface;
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
    mPreviewSurface = surface;
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
    return mRecorder->setParameters(params);
}

status_t MediaPhoneClient::prepareRecorder()
{
    LOGV("prepareRecorder");
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }
	
    char urlOut[MAX_URL_LEN];
    if (!strncasecmp(mUrlOut, "videophone://", 13))
        strcpy(urlOut, mUrlOut + 13);
    else
        strcpy(urlOut, mUrlOut);
    int fd = open(urlOut, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (fd == -1) {
        LOGE("mediaphone: open %s failed errno=%d", urlOut, errno);
    } else {
        LOGI("mediaphone: open %s successed with %d", urlOut, fd);
    }
    if (mCamera == NULL) {
        LOGV("prepareRecorder(), set fake camera");
        CHECK_RT(mRecorder->setVideoSource(VIDEO_SOURCE_FAKECAMERA));
    } else {
        LOGV("prepareRecorder(), set vp video es");
        CHECK_RT(mRecorder->setCamera(mCamera));
        CHECK_RT(mRecorder->setVideoSource(VIDEO_SOURCE_CAMERA));
    }
    //CHECK_RT(mRecorder->setAudioSource(AUDIO_SOURCE_MIC));
    //CHECK_RT(mRecorder->setOutputFormat(OUTPUT_FORMAT_THREE_GPP));
    CHECK_RT(mRecorder->setOutputFormat(OUTPUT_FORMAT_VIDEOPHONE));
    CHECK_RT(mRecorder->setVideoFrameRate(10));
    CHECK_RT(mRecorder->setVideoSize(176, 144));
    //setVideoEncodingBitRate(48*1024);
    CHECK_RT(mRecorder->setParameters(String8("video-param-encoding-bitrate=48000")));
    //setAudioEncodingBitRate();
    //CHECK_RT(mRecorder->setParameters(String8("audio-param-encoding-bitrate=98304")));
    //setAudioChannels(profile.audioChannels);
    //CHECK_RT(mRecorder->setParameters(String8("audio-param-number-of-channels=1")));
    //setAudioSamplingRate(profile.audioSampleRate);
    //CHECK_RT(mRecorder->setParameters(String8("audio-param-sampling-rate=8000")));
    if (mEncodeType == 2) { // mpeg4    
	    CHECK_RT(mRecorder->setVideoEncoder(VIDEO_ENCODER_MPEG_4_SP));
    } else {
	    CHECK_RT(mRecorder->setVideoEncoder(VIDEO_ENCODER_H263));
    }
    //CHECK_RT(mRecorder->setAudioEncoder(AUDIO_ENCODER_AMR_NB));
    CHECK_RT(mRecorder->setOutputFile(fd, 0, 0));
    CHECK_RT(mRecorder->setPreviewSurface(mPreviewSurface));
    CHECK_RT(mRecorder->prepare());
    LOGV("prepareRecorder OK");
    return OK;
}

char* getUrlIn(char* dest, const char* url, int decodeType)
{
	const char* strH263 = ";3gpp";
	const char* strMpeg = ";mpeg4";
	
	if (dest == NULL)
		return NULL;

	strcat(dest, url);
	if (decodeType == 2){
		strcat(dest, strMpeg);
	} else {
		strcat(dest, strH263);
	}
	return dest;
}

status_t MediaPhoneClient::preparePlayer()
{
    LOGV("preparePlayer");
    /*if (!mPlayer->hardwareOutput()) {
        mAudioOutput = new AudioOutput();
        static_cast<MediaPlayerInterface*>(mPlayer.get())->setAudioSink(mAudioOutput);
    }*/
	static char dest[MAX_URL_LEN] = {0};
	memset(dest, 0, sizeof(dest)/sizeof(dest[0]));
    CHECK_RT(mPlayer->setDataSource(getUrlIn(dest, mUrlIn, mDecodeType), NULL));
    CHECK_RT(mPlayer->prepareAsync());
    return OK;
}

status_t MediaPhoneClient::startPlayer()
{
    LOGV("start");
    //todo: fix me
    if (mPlayer == NULL) {
        LOGE("mediaphone: player is not initialized");
        return NO_INIT;
    }

    CHECK_RT(mPlayer->start());
    return OK;
}

status_t MediaPhoneClient::startRecorder()
{
    LOGV("start");
    //todo: fix me
    if (mRecorder == NULL) {
        LOGE("mediaphone: recorder is not initialized");
        return NO_INIT;
    }

    CHECK_RT(mRecorder->start());
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
    mRecorder->stop();
	VideoPhoneDataDevice::getInstance().stop();
    mPlayer->stop();
    mCamera.clear();
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
        mPlayer->setNotifyCallback(0, 0);
        mPlayer.clear();
    }
    wp<MediaPhoneClient> client(this);
    mMediaPlayerService->removeMediaPhoneClient(client);
    return NO_ERROR;
}

MediaPhoneClient::MediaPhoneClient(const sp<MediaPlayerService>& service, pid_t pid)
{
    LOGV("Client constructor");
    mPid = pid;
	mDecodeType = 1;
    mRecorder = new StagefrightRecorder();
    mPlayer = new StagefrightPlayer();
	mRecordRecorder = NULL;
    mMediaPlayerService = service;
    mPlayer->setNotifyCallback(this, notify);
	VideoPhoneDataDevice::getInstance().clearClients();
}

MediaPhoneClient::~MediaPhoneClient()
{
    LOGV("Client destructor");
    //mAudioOutput.clear();
    release();
}

void MediaPhoneClient::notify(void* cookie, int msg, int ext1, int ext2)
{
    LOGV("notify %d %d %d", msg, ext1, ext2);
    MediaPhoneClient* client = static_cast<MediaPhoneClient*>(cookie);
    //todo: handle prepared/media_error msgs
    //refer to mediaplayer.cpp notify
    switch (msg) {
    case MEDIA_PREPARED:
        LOGV("prepared");
        msg = MEDIA_PHONE_EVENT_PREPARED;
        break;
    case MEDIA_SET_VIDEO_SIZE:
        msg = MEDIA_PHONE_EVENT_SET_VIDEO_SIZE;
        break;
    default:
	LOGE("notify error: %d, description: %s", msg, strerror(msg));
	break;
    }
    if (client->mListener != NULL) {
        client->mListener->notify(msg, ext1, ext2);
    }
    LOGV("notify end");
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
    return 0;
    //return mRecorder->setListener(listener);
}

status_t MediaPhoneClient::setDecodeType(int type)
{
    LOGV("setDecodeType(%d)", type);
    Mutex::Autolock l(mLock);
	mDecodeType = type;
    return NO_ERROR;
}

status_t MediaPhoneClient::setEncodeType(int type)
{
    LOGV("setEncodeType(%d)", type);
    Mutex::Autolock l(mLock);
	mEncodeType = type;
    return NO_ERROR;
}

status_t MediaPhoneClient::setAudioStreamType(int type)
{
    LOGV("setAudioStreamType(%d)", type);
    Mutex::Autolock l(mLock);
    if (mAudioOutput != 0) mAudioOutput->setAudioStreamType(type);
    return NO_ERROR;
}

status_t MediaPhoneClient::setVolume(float leftVolume, float rightVolume)
{
    LOGV("setVolume(%f, %f)", leftVolume, rightVolume);
    Mutex::Autolock l(mLock);
    if (mAudioOutput != 0) mAudioOutput->setVolume(leftVolume, rightVolume);
    return NO_ERROR;
}

status_t MediaPhoneClient::enableRecord(bool isEnable, int type, int fd)
{
    LOGV("enableRecord(), isEnable: %d, type: %d, fd: %d", isEnable, type, fd);
    //todo: set correct parameters
    if (isEnable) {
		if ((type < 0) || (type > 2)){
        	LOGE("type is incorrect, type: %d", type);
            return NO_INIT;
		}
        mRecordRecorder = new StagefrightRecorder();
		if ((type == 0) || (type == 2)){
	        CHECK_RT(mRecordRecorder->setVideoSource(VIDEO_SOURCE_VIDEOPHONE_VIDEO_ES));
	        CHECK_RT(mRecordRecorder->setVideoFrameRate(15));
	        CHECK_RT(mRecordRecorder->setVideoSize(176, 144));
	        CHECK_RT(mRecordRecorder->setParameters(String8("video-param-encoding-bitrate=48000")));
	        CHECK_RT(mRecordRecorder->setVideoEncoder((mDecodeType == 1)?VIDEO_ENCODER_H263:VIDEO_ENCODER_MPEG_4_SP));
		}
		if ((type == 0) || (type == 1)){
        	CHECK_RT(mRecordRecorder->setAudioSource(AUDIO_SOURCE_VOICE_CALL));
	        CHECK_RT(mRecordRecorder->setAudioEncoder(AUDIO_ENCODER_AMR_NB));
		}
        CHECK_RT(mRecordRecorder->setOutputFormat(OUTPUT_FORMAT_THREE_GPP));
        CHECK_RT(mRecordRecorder->setOutputFile(fd, 0, 0)); 
        CHECK_RT(mRecordRecorder->prepare());
        CHECK_RT(mRecordRecorder->start());
		LOGV("enableRecord(), enable ok");
        return OK;
    } else {
        if (mRecordRecorder != NULL) {
            VideoPhoneDataDevice::getInstance().stopClient(VideoPhoneDataDevice::RECORD_CLIENT);
            CHECK_RT(mRecordRecorder->stop());
            delete mRecordRecorder;
            mRecordRecorder = NULL;
	    	LOGV("enableRecord(), disable ok");
            return OK;
        } else {
            LOGE("mediaphone is not initialized");
            return NO_INIT;
        }
    }
}

status_t MediaPhoneClient::startUpLink()
{
    LOGV("startUpLink");
	CHECK_RT(prepareRecorder());
    CHECK_RT(mRecorder->start());
    return OK;
}

status_t MediaPhoneClient::stopUpLink()
{
    LOGV("stopUpLink");
    CHECK_RT(mRecorder->stop());
    /*LOGV("stopUpLink, set camera surface");
    //CHECK_RT(mRecorder->stop());
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
	mCamera->lock();
	mCamera->setPreviewDisplay(mPreviewSurface);
	mCamera->unlock();
    IPCThreadState::self()->restoreCallingIdentity(token);*/
    return OK;
}

status_t MediaPhoneClient::startDownLink()
{
    LOGV("startDownLink");
    if (mPreviewSurface != NULL) {
    	int64_t token = IPCThreadState::self()->clearCallingIdentity();
    	if (mCamera != NULL) {
            LOGV("startDownLink mCamera is not NULL");
	        mCamera->setPreviewDisplay(mPreviewSurface);
        }
    	IPCThreadState::self()->restoreCallingIdentity(token);
    }
    CHECK_RT(mPlayer->start());
    return OK;
}

status_t MediaPhoneClient::stopDownLink()
{
    LOGV("stopDownLink");
    CHECK_RT(mPlayer->pause());
	((StagefrightPlayer*)mPlayer.get())->clearRender();
	
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
    if (mCamera != NULL) {
        LOGV("stopDownLink mCamera is not NULL");
    	mCamera->stopPreview();
    	mCamera->setPreviewDisplay(NULL);
        mCamera->startPreview();
    }
    LOGV("stopDownLink, mRemoteSurface(%p)", mRemoteSurface.get());
	//mRecorder->setPreviewSurface(mRemoteSurface);
    IPCThreadState::self()->restoreCallingIdentity(token);
    return OK;
}

status_t MediaPhoneClient::setCameraParam(const char *key, int value)
{
    LOGV("setCameraParam, key: %s, value: %d", key, value);
	
	if (mCamera == NULL){
		LOGE("camera is NULL");
        return NO_INIT;
	}
	
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
	
    // Set the actual video recording frame size
    CameraParameters params(mCamera->getParameters());
	LOGV("before set, key: %s, value: %s", key, params.get(key));
    params.set(key, value);
    String8 s = params.flatten();
    if (OK != mCamera->setParameters(s)) {
        LOGE("Could not change settings."
             " Someone else is using camera");
    }
    IPCThreadState::self()->restoreCallingIdentity(token);
    return OK;
}

status_t MediaPhoneClient::getCameraParam(const char *key, int* value)
{
    LOGV("getCameraParam, key: %s", key);
	
	if (mCamera == NULL){
		LOGE("camera is NULL");
        return NO_INIT;
	}
	
    int64_t token = IPCThreadState::self()->clearCallingIdentity();
	
    // Set the actual video recording frame size
    CameraParameters params(mCamera->getParameters());
    *value = atoi(params.get(key));
	LOGV("getCameraParam, key: %s, value: %d", key, *value);
    IPCThreadState::self()->restoreCallingIdentity(token);
    return OK;
}

// TODO: Find real cause of Audio/Video delay in PV framework and remove this workaround
/* static */ int MediaPhoneClient::AudioOutput::mMinBufferCount = 4;
/* static */ bool MediaPhoneClient::AudioOutput::mIsOnEmulator = false;

#undef LOG_TAG
#define LOG_TAG "AudioSink"
MediaPhoneClient::AudioOutput::AudioOutput()
    : mCallback(NULL),
      mCallbackCookie(NULL) {
    LOGV("AudioOutput");
    mTrack = 0;
    mStreamType = AudioSystem::MUSIC;
    mLeftVolume = 1.0;
    mRightVolume = 1.0;
    mLatency = 0;
    mMsecsPerFrame = 0;
    setMinBufferCount();
}

MediaPhoneClient::AudioOutput::~AudioOutput()
{
    LOGV("~AudioOutput");
    close();
}

void MediaPhoneClient::AudioOutput::setMinBufferCount()
{
    char value[PROPERTY_VALUE_MAX];
    if (property_get("ro.kernel.qemu", value, 0)) {
        mIsOnEmulator = true;
        mMinBufferCount = 12;  // to prevent systematic buffer underrun for emulator
    }
}

bool MediaPhoneClient::AudioOutput::isOnEmulator()
{
    setMinBufferCount();
    return mIsOnEmulator;
}

int MediaPhoneClient::AudioOutput::getMinBufferCount()
{
    setMinBufferCount();
    return mMinBufferCount;
}

ssize_t MediaPhoneClient::AudioOutput::bufferSize() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->frameCount() * frameSize();
}

ssize_t MediaPhoneClient::AudioOutput::frameCount() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->frameCount();
}

ssize_t MediaPhoneClient::AudioOutput::channelCount() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->channelCount();
}

ssize_t MediaPhoneClient::AudioOutput::frameSize() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->frameSize();
}

uint32_t MediaPhoneClient::AudioOutput::latency () const
{
    return mLatency;
}

float MediaPhoneClient::AudioOutput::msecsPerFrame() const
{
    return mMsecsPerFrame;
}

status_t MediaPhoneClient::AudioOutput::getPosition(uint32_t *position)
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->getPosition(position);
}

status_t MediaPhoneClient::AudioOutput::open(
        uint32_t sampleRate, int channelCount, int format, int bufferCount,
        AudioCallback cb, void *cookie)
{
    mCallback = cb;
    mCallbackCookie = cookie;

    // Check argument "bufferCount" against the mininum buffer count
    if (bufferCount < mMinBufferCount) {
        LOGD("bufferCount (%d) is too small and increased to %d", bufferCount, mMinBufferCount);
        bufferCount = mMinBufferCount;

    }
    LOGV("open(%u, %d, %d, %d)", sampleRate, channelCount, format, bufferCount);
    if (mTrack) close();
    int afSampleRate;
    int afFrameCount;
    int frameCount;

    if (AudioSystem::getOutputFrameCount(&afFrameCount, mStreamType) != NO_ERROR) {
        return NO_INIT;
    }
    if (AudioSystem::getOutputSamplingRate(&afSampleRate, mStreamType) != NO_ERROR) {
        return NO_INIT;
    }

    frameCount = (sampleRate*afFrameCount*bufferCount)/afSampleRate;

    AudioTrack *t;
    if (mCallback != NULL) {
        t = new AudioTrack(
                mStreamType,
                sampleRate,
                format,
                (channelCount == 2) ? AudioSystem::CHANNEL_OUT_STEREO : AudioSystem::CHANNEL_OUT_MONO,
                frameCount,
                0 /* flags */,
                CallbackWrapper,
                this);
    } else {
        t = new AudioTrack(
                mStreamType,
                sampleRate,
                format,
                (channelCount == 2) ? AudioSystem::CHANNEL_OUT_STEREO : AudioSystem::CHANNEL_OUT_MONO,
                frameCount);
    }

    if ((t == 0) || (t->initCheck() != NO_ERROR)) {
        LOGE("Unable to create audio track");
        delete t;
        return NO_INIT;
    }

    LOGV("setVolume");
    t->setVolume(mLeftVolume, mRightVolume);

    mMsecsPerFrame = 1.e3 / (float) sampleRate;
    mLatency = t->latency();
    mTrack = t;
    return NO_ERROR;
}

void MediaPhoneClient::AudioOutput::start()
{
    LOGV("start");
    if (mTrack) {
        mTrack->setVolume(mLeftVolume, mRightVolume);
        mTrack->start();
    }
}



ssize_t MediaPhoneClient::AudioOutput::write(const void* buffer, size_t size)
{
    LOG_FATAL_IF(mCallback != NULL, "Don't call write if supplying a callback.");

    LOGV("write(%p, %u)", buffer, size);
    if (mTrack) {
        ssize_t ret = mTrack->write(buffer, size);
        return ret;
    }
    return NO_INIT;
}

void MediaPhoneClient::AudioOutput::stop()
{
    LOGV("stop");
    if (mTrack) mTrack->stop();
}

void MediaPhoneClient::AudioOutput::flush()
{
    LOGV("flush");
    if (mTrack) mTrack->flush();
}

void MediaPhoneClient::AudioOutput::pause()
{
    LOGV("pause");
    if (mTrack) mTrack->pause();
}

void MediaPhoneClient::AudioOutput::close()
{
    LOGV("close");
    delete mTrack;
    mTrack = 0;
}

void MediaPhoneClient::AudioOutput::setVolume(float left, float right)
{
    LOGV("setVolume(%f, %f)", left, right);
    mLeftVolume = left;
    mRightVolume = right;
    if (mTrack) {
        mTrack->setVolume(left, right);
    }
}

// static
void MediaPhoneClient::AudioOutput::CallbackWrapper(
        int event, void *cookie, void *info) {
    //LOGV("callbackwrapper");
    if (event != AudioTrack::EVENT_MORE_DATA) {
        return;
    }

    AudioOutput *me = (AudioOutput *)cookie;
    AudioTrack::Buffer *buffer = (AudioTrack::Buffer *)info;

    size_t actualSize = (*me->mCallback)(
            me, buffer->raw, buffer->size, me->mCallbackCookie);

    buffer->size = actualSize;

}

status_t MediaPhoneClient::AudioOutput::dump(int fd, const Vector<String16>& args) const
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    result.append(" AudioOutput\n");
    snprintf(buffer, 255, "  stream type(%d), left - right volume(%f, %f)\n",
            mStreamType, mLeftVolume, mRightVolume);
    result.append(buffer);
    snprintf(buffer, 255, "  msec per frame(%f), latency (%d)\n",
            mMsecsPerFrame, mLatency);
    result.append(buffer);

    ::write(fd, result.string(), result.size());
    if (mTrack != 0) {
        mTrack->dump(fd, args);
    }
    return NO_ERROR;
}


}; // namespace android

