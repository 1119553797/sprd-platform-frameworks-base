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

#define LOG_NDEBUG 0
#define LOG_TAG "IMediaPhone"
#include <utils/Log.h>
#include <binder/Parcel.h>
#include <surfaceflinger/ISurface.h>
#include <camera/ICamera.h>
#include <media/IMediaPlayerClient.h>
#include <media/IMediaPhone.h>

namespace android {

enum {
    RELEASE = IBinder::FIRST_CALL_TRANSACTION,
    SET_COMM,
    SET_CAMERA,
    SET_REMOTE_SURFACE,
    SET_LOCAL_SURFACE,
    SET_LISTENER,
    SET_PARAMETERS,
    PREPARE_ASYNC,
    START,
    STOP,
    SET_AUDIO_STREAM_TYPE,
    SET_VOLUME
};

class BpMediaPhone: public BpInterface<IMediaPhone>
{
public:
    BpMediaPhone(const sp<IBinder>& impl)
    : BpInterface<IMediaPhone>(impl)
    {
    }

    status_t setComm(const char* urlIn, const char *urlOut)
    {
        LOGV("setComm(%s, %s)", urlIn, urlOut);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeCString(urlIn);
        data.writeCString(urlOut);
        remote()->transact(SET_COMM, data, &reply);
        return reply.readInt32();
    }

    status_t setCamera(const sp<ICamera>& camera)
    {
        LOGV("setCamera(%p)", camera.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeStrongBinder(camera->asBinder());
        remote()->transact(SET_CAMERA, data, &reply);
        return reply.readInt32();
    }

    status_t setRemoteSurface(const sp<ISurface>& surface)
    {
        LOGV("setRemoteSurface(%p)", surface.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeStrongBinder(surface->asBinder());
        remote()->transact(SET_REMOTE_SURFACE, data, &reply);
        return reply.readInt32();
    }

    status_t setLocalSurface(const sp<ISurface>& surface)
    {
        LOGV("setLocalSurface(%p)", surface.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeStrongBinder(surface->asBinder());
        remote()->transact(SET_LOCAL_SURFACE, data, &reply);
        return reply.readInt32();
    }

    status_t setListener(const sp<IMediaPlayerClient>& listener)
    {
        LOGV("setListener(%p)", listener.get());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeStrongBinder(listener->asBinder());
        remote()->transact(SET_LISTENER, data, &reply);
        return reply.readInt32();
    }

    status_t setParameters(const String8& params)
    {
        LOGV("setParameter(%s)", params.string());
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeString8(params);
        remote()->transact(SET_PARAMETERS, data, &reply);
        return reply.readInt32();
    }

    status_t prepareAsync()
    {
        LOGV("prepareAsync");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        remote()->transact(PREPARE_ASYNC, data, &reply);
        return reply.readInt32();
    }

    status_t start()
    {
        LOGV("start");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        remote()->transact(START, data, &reply);
        return reply.readInt32();
    }

    status_t stop()
    {
        LOGV("stop");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        remote()->transact(STOP, data, &reply);
        return reply.readInt32();
    }

    status_t release()
    {
        LOGV("release");
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        remote()->transact(RELEASE, data, &reply);
        return reply.readInt32();
    }

    status_t setAudioStreamType(int type)
    {
        LOGV("setAudioStreamType(%d)", type);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeInt32(type);
        remote()->transact(SET_AUDIO_STREAM_TYPE, data, &reply);
        return reply.readInt32();
    }

    status_t setVolume(float leftVolume, float rightVolume)
    {
        LOGV("setVolume(%f, %f)", leftVolume, rightVolume);
        Parcel data, reply;
        data.writeInterfaceToken(IMediaPhone::getInterfaceDescriptor());
        data.writeFloat(leftVolume);
        data.writeFloat(rightVolume);
        remote()->transact(SET_VOLUME, data, &reply);
        return reply.readInt32();
    }
};

IMPLEMENT_META_INTERFACE(MediaPhone, "android.media.IMediaPhone");

// ----------------------------------------------------------------------

status_t BnMediaPhone::onTransact(
                                     uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case RELEASE: {
            LOGV("RELEASE");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(release());
            return NO_ERROR;
        } break;
        case SET_COMM: {
            LOGV("SET_COMM");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            const char* pathIn = data.readCString();
            const char* pathOut = data.readCString();
            reply->writeInt32(setComm(pathIn, pathOut));
            return NO_ERROR;
        } break;
        case SET_CAMERA: {
            LOGV("SET_CAMERA");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            sp<ICamera> camera = interface_cast<ICamera>(data.readStrongBinder());
            reply->writeInt32(setCamera(camera));
            return NO_ERROR;
        } break;
        case SET_REMOTE_SURFACE: {
            LOGV("SET_REMOTE_SURFACE");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            sp<ISurface> surface = interface_cast<ISurface>(data.readStrongBinder());
            reply->writeInt32(setRemoteSurface(surface));
            return NO_ERROR;
        } break;
        case SET_LOCAL_SURFACE: {
            LOGV("SET_LOCAL_SURFACE");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            sp<ISurface> surface = interface_cast<ISurface>(data.readStrongBinder());
            reply->writeInt32(setLocalSurface(surface));
            return NO_ERROR;
        } break;
        case SET_LISTENER: {
            LOGV("SET_LISTENER");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            sp<IMediaPlayerClient> listener =
                interface_cast<IMediaPlayerClient>(data.readStrongBinder());
            reply->writeInt32(setListener(listener));
            return NO_ERROR;
        } break;
       case SET_PARAMETERS: {
            LOGV("SET_PARAMETERS");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(setParameters(data.readString8()));
            return NO_ERROR;
        } break;
        case PREPARE_ASYNC: {
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(prepareAsync());
            return NO_ERROR;
        } break;
        case START: {
            LOGV("START");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(start());
            return NO_ERROR;
        } break;
        case STOP: {
            LOGV("STOP");
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(stop());
            return NO_ERROR;
        } break;
        case SET_AUDIO_STREAM_TYPE: {
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(setAudioStreamType(data.readInt32()));
            return NO_ERROR;
        } break;
        case SET_VOLUME: {
            CHECK_INTERFACE(IMediaPhone, data, reply);
            reply->writeInt32(setVolume(data.readFloat(), data.readFloat()));
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

}; // namespace android
