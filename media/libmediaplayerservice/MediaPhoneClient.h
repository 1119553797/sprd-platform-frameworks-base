/*
 **
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

#ifndef ANDROID_MEDIAPHONECLIENT_H
#define ANDROID_MEDIAPHONECLIENT_H

#include <media/IMediaPhone.h>
#include <media/MediaPlayerInterface.h>

namespace android {

class MediaPlayerBase;
class MediaPlayerService;
class MediaRecorderBase;
class ISurface;
class MediaPlayerService;

class MediaPhoneClient : public BnMediaPhone
{
public:
    virtual     status_t    setComm(const char *urlIn, const char *urlout);
    virtual	    status_t		setCamera(const sp<ICamera>& camera);
    virtual     status_t    setRemoteSurface(const sp<ISurface>& surface);
    virtual     status_t    setLocalSurface(const sp<ISurface>& surface);
    virtual     status_t    setListener(const sp<IMediaPlayerClient>& listener);
    virtual     status_t    setParameters(const String8 &params);
    virtual     status_t    prepareAsync();
    virtual     status_t    start();
    virtual     status_t    stop();
    virtual     status_t		setAudioStreamType(int type);
    virtual     status_t		setVolume(float leftVolume, float rightVolume);
    virtual     status_t		release();

    static      void			notify(void* cookie, int msg, int ext1, int ext2);

private:
    friend class                 MediaPlayerService;  // for accessing private constructor

                                 MediaPhoneClient(const sp<MediaPlayerService>& service, pid_t pid);
    virtual 		         ~MediaPhoneClient();

    pid_t			 mPid;
    Mutex			 mLock;
    MediaRecorderBase            *mRecorder;
    MediaPlayerBase              *mPlayer;
    sp<MediaPlayerService>       mMediaPlayerService;

    sp<IMediaPlayerClient>       mListener;
};

}; // namespace android

#endif // ANDROID_MEDIAPHONECLIENT_H

