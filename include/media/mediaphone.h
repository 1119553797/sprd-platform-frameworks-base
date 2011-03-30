/*
 ** Copyright (C) 2008 The Android Open Source Project
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
 **
 ** limitations under the License.
 */

#ifndef ANDROID_MEDIAPHONE_H
#define ANDROID_MEDIAPHONE_H

#include <utils/Log.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <utils/Errors.h>
#include <media/IMediaPlayerClient.h>

namespace android {

class Surface;
class IMediaPhone;
class ICamera;

typedef void (*media_completion_f)(status_t status, void *cookie);


/*
 * The state machine of the media_phone uses a set of different state names.
 * The mapping between the media_phone and the pvauthorengine is shown below:
 *
 *    mediaphone                        mediaplayer     mediarecorder
 * ----------------------------------------------------------------
 *    MEDIA_PHONE_ERROR                 ERROR           ERROR
 *    MEDIA_PHONE_IDLE                  IDLE            IDLE
 *    MEDIA_PHONE_INITIALIZED           INITIALIZED     DATASOURCE_CONFIGURED
 *    MEDIA_PHONE_PREPARING             PREPARING
 *    MEDIA_PHONE_PREPARED              PREPARED        PREPARED
 *    MEDIA_PHONE_STARTED               STARTED         RECORDING
 */
enum media_phone_states {
    MEDIA_PHONE_ERROR                 =      0,
    MEDIA_PHONE_IDLE                  = 1 << 0,
    MEDIA_PHONE_INITIALIZED           = 1 << 1,
    MEDIA_PHONE_PREPARING             = 1 << 2,
    MEDIA_PHONE_PREPARED              = 1 << 3,
    MEDIA_PHONE_STARTED               = 1 << 4
};

// The "msg" code passed to the listener in notify.
enum media_phone_event_type {
    MEDIA_PHONE_EVENT_PREPARED                 = 1,
    MEDIA_PHONE_EVENT_SET_VIDEO_SIZE           = 2,
    MEDIA_PHONE_EVENT_ERROR                    = 100,
    MEDIA_PHONE_EVENT_INFO                     = 200
};

enum media_phone_error_type {
    MEDIA_PHONE_ERROR_UNKNOWN                  = 1
};

// The codes are distributed as follow:
//   0xx: Reserved
//   8xx: General info/warning
//
enum media_phone_info_type {
    MEDIA_PHONE_INFO_UNKNOWN                   = 1,
};

// ----------------------------------------------------------------------------
// ref-counted object for callbacks
class MediaPhoneListener: virtual public RefBase
{
public:
    virtual void notify(int msg, int ext1, int ext2) = 0;
};

class MediaPhone : public BnMediaPlayerClient
{
public:
    MediaPhone();
    ~MediaPhone();

    //status_t    initCheck();
    status_t    setComm(const char *urlIn, const char *urlOut);
    status_t    setCamera(const sp<ICamera>& camera);
    status_t    setRemoteSurface(const sp<Surface>& surface);
    status_t    setLocalSurface(const sp<Surface>& surface);
    status_t    setListener(const sp<MediaPhoneListener>& listener);
    status_t    setParameters(const String8 &params);
    status_t    prepareAsync();
    status_t    start();
    status_t    stop();
    status_t    release();
    status_t    setAudioStreamType(int type);
    status_t    setVolume(float leftVolume, float rightVolume);
    void        notify(int msg, int ext1, int ext2);

private:
    status_t    prepareAsync_l();
    void                    doCleanUp();

    sp<IMediaPhone>          mMediaPhone;
    sp<MediaPhoneListener>   mListener;
    media_phone_states       mCurrentState;
    bool                        mIsCommSet;
    bool                        mIsCameraSet;
    bool                        mIsLocalSurfaceSet;
    bool                        mIsRemoteSurfaceSet;
    Mutex                       mLock;
    Mutex                       mNotifyLock;
};

};  // namespace android

#endif // ANDROID_MEDIAPHONE_H
