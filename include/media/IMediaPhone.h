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

#ifndef ANDROID_IMEDIAPHONE_H
#define ANDROID_IMEDIAPHONE_H

#include <binder/IInterface.h>

namespace android {

class ISurface;
class ICamera;
class IMediaPlayerClient;

class IMediaPhone: public IInterface
{
public:
    DECLARE_META_INTERFACE(MediaPhone);

    virtual status_t    setComm(const char *urlIn, const char *urlOut) = 0;
    virtual	status_t		setCamera(const sp<ICamera>& camera) = 0;
    virtual	status_t		setRemoteSurface(const sp<ISurface>& surface) = 0;
    virtual	status_t		setLocalSurface(const sp<ISurface>& surface) = 0;
    virtual	status_t		setListener(const sp<IMediaPlayerClient>& listener) = 0;
    virtual status_t    setParameters(const String8 &params) = 0;
    virtual status_t    prepareAsync() = 0;
    virtual	status_t		start() = 0;
    virtual	status_t		stop() = 0;
    virtual status_t    release() = 0;
    virtual status_t		setAudioStreamType(int type) = 0;
    virtual status_t		setVolume(float leftVolume, float rightVolume) = 0;
};

// ----------------------------------------------------------------------------

class BnMediaPhone: public BnInterface<IMediaPhone>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

}; // namespace android

#endif // ANDROID_IMEDIAPHONE_H

