/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_BOOTANIMATION_H
#define ANDROID_BOOTANIMATION_H

#include <stdint.h>
#include <sys/types.h>

#include <androidfw/AssetManager.h>
#include <utils/threads.h>

#include <EGL/egl.h>
#include <GLES/gl.h>

/*added boot and shutdown animation */
#include <fcntl.h>
#include <media/mediaplayer.h>
/*added boot and shutdown animation ---*/

class SkBitmap;

namespace android {
/*added boot and shutdown animation ,define  path here*/
#define BOOTANIMATION_BOOT_FILM_PATH_DEFAULT		"/system/media/bootanimation.zip"
#define BOOTANIMATION_SHUTDOWN_FILM_PATH_DEFAULT	"/system/media/shutdownanimation.zip"

#define BOOTANIMATION_BOOT_SOUND_PATH_DEFAULT		"/system/media/bootsound.mp3"
#define BOOTANIMATION_SHUTDOWN_SOUND_PATH_DEFAULT	"/system/media/shutdownsound.mp3"


#define BOOTANIMATION_PATHSET_MAX	100

class Surface;
class SurfaceComposerClient;
class SurfaceControl;

// ---------------------------------------------------------------------------

class BootAnimation : public Thread, public IBinder::DeathRecipient
{
public:
                BootAnimation();
    virtual     ~BootAnimation();

    sp<SurfaceComposerClient> session() const;

	bool setsoundpath(String8 path);
	bool setmoviepath(String8 path);
	bool setdescname(String8 path);

	bool setsoundpath_default(String8 path);
	bool setmoviepath_default(String8 path);
	bool setdescname_default(String8 path);

private:
    virtual bool        threadLoop();
    virtual status_t    readyToRun();
    virtual void        onFirstRef();
    virtual void        binderDied(const wp<IBinder>& who);

    struct Texture {
        GLint   w;
        GLint   h;
        GLuint  name;
    };

    struct Animation {
        struct Frame {
            String8 name;
            FileMap* map;
            mutable GLuint tid;
            bool operator < (const Frame& rhs) const {
                return name < rhs.name;
            }
        };
        struct Part {
            int count;
            int pause;
            String8 path;
            SortedVector<Frame> frames;
            bool playUntilComplete;
        };
        int fps;
        int width;
        int height;
        Vector<Part> parts;
    };

    status_t initTexture(Texture* texture, AssetManager& asset, const char* name);
    status_t initTexture(void* buffer, size_t len);
    bool android();
    bool movie();

/*added boot and shutdown animation ,next function and param is metioned*/
    bool soundplay();
    bool soundstop();
    sp<MediaPlayer> mp;
    String8		soundpath;
    String8		moviepath;
    String8		descname;
    String8 		movie_default_path;
    String8 		sound_default_path;
    String8		descname_default;

/*added boot and shutdown animation ---*/

    void checkExit();

    sp<SurfaceComposerClient>       mSession;
    AssetManager mAssets;
    Texture     mAndroid[2];
    int         mWidth;
    int         mHeight;
    EGLDisplay  mDisplay;
    EGLDisplay  mContext;
    EGLDisplay  mSurface;
    sp<SurfaceControl> mFlingerSurfaceControl;
    sp<Surface> mFlingerSurface;
    bool        mAndroidAnimation;
    ZipFileRO   mZip;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_BOOTANIMATION_H
