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
#include <camera/ICamera.h>

namespace android {

class ICamera;
class MediaPlayerBase;
class MediaPlayerService;
class MediaRecorderBase;
class ISurface;
class MediaPlayerService;

#define MAX_URL_LEN 256

class MediaPhoneClient : public BnMediaPhone
{
    class AudioOutput : public MediaPlayerBase::AudioSink
    {
    public:
                                AudioOutput();
        virtual                 ~AudioOutput();

        virtual bool            ready() const { return mTrack != NULL; }
        virtual bool            realtime() const { return true; }
        virtual ssize_t         bufferSize() const;
        virtual ssize_t         frameCount() const;
        virtual ssize_t         channelCount() const;
        virtual ssize_t         frameSize() const;
        virtual uint32_t        latency() const;
        virtual float           msecsPerFrame() const;
        virtual status_t        getPosition(uint32_t *position);

        virtual status_t        open(
                uint32_t sampleRate, int channelCount,
                int format, int bufferCount,
                AudioCallback cb, void *cookie);

        virtual void            start();
        virtual ssize_t         write(const void* buffer, size_t size);
        virtual void            stop();
        virtual void            flush();
        virtual void            pause();
        virtual void            close();
                void            setAudioStreamType(int streamType) { mStreamType = streamType; }
                void            setVolume(float left, float right);
        virtual status_t        dump(int fd, const Vector<String16>& args) const;

        static bool             isOnEmulator();
        static int              getMinBufferCount();
    private:
        static void             setMinBufferCount();
        static void             CallbackWrapper(
                int event, void *me, void *info);

        AudioTrack*             mTrack;
        AudioCallback           mCallback;
        void *                  mCallbackCookie;
        int                     mStreamType;
        float                   mLeftVolume;
        float                   mRightVolume;
        float                   mMsecsPerFrame;
        uint32_t                mLatency;

        static bool             mIsOnEmulator;
        static int              mMinBufferCount;  // 12 for emulator; otherwise 4

    };

public:
    virtual     status_t    setComm(const char *urlIn, const char *urlOut);
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
    sp<ICamera>                  mCamera;
    sp<ISurface>                 mPreviewSurface;
    MediaRecorderBase            *mRecorder;
    sp<MediaPlayerBase>          mPlayer;
    sp<MediaPlayerService>       mMediaPlayerService;
    sp<AudioOutput>              mAudioOutput;

    sp<IMediaPlayerClient>       mListener;
    char                         mUrlIn[MAX_URL_LEN];
    char                         mUrlOut[MAX_URL_LEN];
};

}; // namespace android

#endif // ANDROID_MEDIAPHONECLIENT_H

