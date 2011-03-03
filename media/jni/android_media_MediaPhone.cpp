/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaPhoneJNI"
#include <utils/Log.h>

#include <surfaceflinger/SurfaceComposerClient.h>
#include <camera/ICameraService.h>
#include <camera/Camera.h>
#include <media/mediaphone.h>
#include <stdio.h>
#include <assert.h>
#include <limits.h>
#include <unistd.h>
#include <fcntl.h>
#include <utils/threads.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"


// ----------------------------------------------------------------------------

using namespace android;

// ----------------------------------------------------------------------------

// helper function to extract a native Camera object from a Camera Java object
extern sp<Camera> get_native_camera(JNIEnv *env, jobject thiz, struct JNICameraContext** context);

struct fields_t {
    jfieldID    context;
    jfieldID    remote_surface;
    jfieldID    local_surface;
    /* actually in android.view.Surface XXX */
    jfieldID    surface_native;

    jmethodID   post_event;
};
static fields_t fields;

static Mutex sLock;

// ----------------------------------------------------------------------------
// ref-counted object for callbacks
class JNIMediaPhoneListener: public MediaPhoneListener
{
public:
    JNIMediaPhoneListener(JNIEnv* env, jobject thiz, jobject weak_thiz);
    ~JNIMediaPhoneListener();
    void notify(int msg, int ext1, int ext2);
private:
    JNIMediaPhoneListener();
    jclass      mClass;     // Reference to MediaPhone class
    jobject     mObject;    // Weak ref to MediaPhone Java object to call on
};

JNIMediaPhoneListener::JNIMediaPhoneListener(JNIEnv* env, jobject thiz, jobject weak_thiz)
{

    // Hold onto the MediaPhone class for use in calling the static method
    // that posts events to the application thread.
    jclass clazz = env->GetObjectClass(thiz);
    if (clazz == NULL) {
        LOGE("Can't find android/media/MediaPhone");
        jniThrowException(env, "java/lang/Exception", NULL);
        return;
    }
    mClass = (jclass)env->NewGlobalRef(clazz);

    // We use a weak reference so the MediaPhone object can be garbage collected.
    // The reference is only used as a proxy for callbacks.
    mObject  = env->NewGlobalRef(weak_thiz);
}

JNIMediaPhoneListener::~JNIMediaPhoneListener()
{
    // remove global references
    JNIEnv *env = AndroidRuntime::getJNIEnv();
    env->DeleteGlobalRef(mObject);
    env->DeleteGlobalRef(mClass);
}

void JNIMediaPhoneListener::notify(int msg, int ext1, int ext2)
{
    LOGV("JNIMediaPhoneListener::notify");

    JNIEnv *env = AndroidRuntime::getJNIEnv();
    env->CallStaticVoidMethod(mClass, fields.post_event, mObject, msg, ext1, ext2, 0);
}

// ----------------------------------------------------------------------------

static sp<Surface> get_surface(JNIEnv* env, jobject clazz)
{
    LOGV("get_surface");
    Surface* const p = (Surface*)env->GetIntField(clazz, fields.surface_native);
    return sp<Surface>(p);
}

static sp<MediaPhone> getMediaPhone(JNIEnv* env, jobject thiz)
{
    Mutex::Autolock l(sLock);
    MediaPhone* const p = (MediaPhone*)env->GetIntField(thiz, fields.context);
    return sp<MediaPhone>(p);
}

static sp<MediaPhone> setMediaPhone(JNIEnv* env, jobject thiz, const sp<MediaPhone>& recorder)
{
    Mutex::Autolock l(sLock);
    sp<MediaPhone> old = (MediaPhone*)env->GetIntField(thiz, fields.context);
    if (recorder.get()) {
        recorder->incStrong(thiz);
    }
    if (old != 0) {
        old->decStrong(thiz);
    }
    env->SetIntField(thiz, fields.context, (int)recorder.get());
    return old;
}

// Returns true if it throws an exception.
static bool process_media_phone_call(JNIEnv *env, jobject thiz, status_t opStatus, const char* exception, const char* message)
{
    LOGV("process_media_phone_call");
    if (exception == NULL) {  // Don't throw exception. Instead, send an event.
        if (opStatus != (status_t) OK) {
            sp<MediaPhone> mp = getMediaPhone(env, thiz);
            if (mp != 0) mp->notify(MEDIA_PHONE_EVENT_ERROR, opStatus, 0);
        }
    } else {  // Throw exception!
        if (opStatus == (status_t)INVALID_OPERATION) {
            jniThrowException(env, "java/lang/IllegalStateException", NULL);
            return true;
        } else if (opStatus != (status_t)OK) {
            if (strlen(message) > 230) {
               // if the message is too long, don't bother displaying the status code
               jniThrowException( env, exception, message);
            } else {
               char msg[256];
                // append the status code to the message
               sprintf(msg, "%s: status=0x%X", message, opStatus);
               jniThrowException( env, exception, msg);
            }
        }
    }
    return false;
}

static void android_media_MediaPhone_setCamera(JNIEnv* env, jobject thiz, jobject camera)
{
    // we should not pass a null camera to get_native_camera() call.
    if (camera == NULL) {
        jniThrowException(env, "java/lang/NullPointerException", "camera object is a NULL pointer");
        return;
    }
    sp<Camera> c = get_native_camera(env, camera, NULL);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    process_media_phone_call(env, thiz, mp->setCamera(c->remote()),
            "java/lang/RuntimeException", "setCamera failed.");
}

static void setRemoteSurface(const sp<MediaPhone>& mp, JNIEnv *env, jobject thiz)
{
    jobject surface = env->GetObjectField(thiz, fields.remote_surface);
    if (surface != NULL) {
        const sp<Surface> native_surface = get_surface(env, surface);
        LOGV("prepare: surface=%p (id=%d)",
             native_surface.get(), native_surface->ID());
        mp->setRemoteSurface(native_surface);
    }
}

static void
android_media_MediaPhone_setRemoteSurface(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    setRemoteSurface(mp, env, thiz);
}


static void
android_media_MediaPhone_setParameters(JNIEnv *env, jobject thiz, jstring params)
{
    LOGV("setParameter()");
    if (params == NULL)
    {
        LOGE("Invalid or empty params string.  This parameter will be ignored.");
        return;
    }

    sp<MediaPhone> mp = getMediaPhone(env, thiz);

    const char* params8 = env->GetStringUTFChars(params, NULL);
    if (params8 == NULL)
    {
        LOGE("Failed to covert jstring to String8.  This parameter will be ignored.");
        return;
    }

    process_media_phone_call(env, thiz, mp->setParameters(String8(params8)), "java/lang/RuntimeException", "setParameter failed.");
    env->ReleaseStringUTFChars(params,params8);
}

static void
android_media_MediaPhone_setComm(JNIEnv *env, jobject thiz, jstring path_in, jstring path_out)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }

    if (path_in == NULL || path_out == NULL) {
        jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        return;
    }

    const char *pathInStr = env->GetStringUTFChars(path_in, NULL);
    if (pathInStr == NULL) {  // Out of memory
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return;
    }
    const char *pathOutStr = env->GetStringUTFChars(path_out, NULL);
    if (pathOutStr == NULL) {  // Out of memory
        env->ReleaseStringUTFChars(path_in, pathInStr);
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return;
    }
    LOGV("setDataSource: path_in %s path_out %s", pathInStr, pathOutStr);
    status_t opStatus = mp->setComm(pathInStr, pathOutStr);

    // Make sure that local ref is released before a potential exception
    env->ReleaseStringUTFChars(path_in, pathInStr);
    env->ReleaseStringUTFChars(path_out, pathOutStr);
    process_media_phone_call(env, thiz, opStatus, "java/io/IOException", "setComm failed.");
}

static void
android_media_MediaPhone_prepareAsync(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    jobject surface = env->GetObjectField(thiz, fields.remote_surface);
    if (surface != NULL) {
        const sp<Surface> native_surface = get_surface(env, surface);
        LOGV("prepareAsync: surface=%p (id=%d)",
             native_surface.get(), native_surface->getIdentity());
        mp->setRemoteSurface(native_surface);
    }
    surface = env->GetObjectField(thiz, fields.local_surface);
    if (surface != NULL) {
        const sp<Surface> native_surface = get_surface(env, surface);

        // The application may misbehave and
        // the preview surface becomes unavailable
        if (native_surface.get() == 0) {
            LOGE("Application lost the surface");
            jniThrowException(env, "java/io/IOException", "invalid preview surface");
            return;
        }

        LOGI("prepare: surface=%p (identity=%d)", native_surface.get(), native_surface->getIdentity());
        if (process_media_phone_call(env, thiz, mp->setLocalSurface(native_surface), "java/lang/RuntimeException", "setPreviewSurface failed.")) {
            return;
        }
    }
    
    process_media_phone_call( env, thiz, mp->prepareAsync(), "java/io/IOException", "Prepare Async failed." );
}

static void
android_media_MediaPhone_start(JNIEnv *env, jobject thiz)
{
    LOGV("start");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    process_media_phone_call(env, thiz, mp->start(), "java/lang/RuntimeException", "start failed.");
}

static void
android_media_MediaPhone_stop(JNIEnv *env, jobject thiz)
{
    LOGV("stop");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    process_media_phone_call(env, thiz, mp->stop(), "java/lang/RuntimeException", "stop failed.");
}

static void
android_media_MediaPhone_release(JNIEnv *env, jobject thiz)
{
    LOGV("release");
    sp<MediaPhone> mp = setMediaPhone(env, thiz, 0);
    if (mp != NULL) {
        mp->setListener(NULL);
        mp->release();
    }
}

static int
android_media_MediaPhone_getVideoWidth(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }
    int w;
//    if (0 != mp->getVideoWidth(&w)) {
//        LOGE("getVideoWidth failed");
        w = 0;
//    }
    LOGV("getVideoWidth: %d", w);
    return w;
}

static int
android_media_MediaPhone_getVideoHeight(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return 0;
    }
    int h;
//    if (0 != mp->getVideoHeight(&h)) {
//        LOGE("getVideoHeight failed");
        h = 0;
//    }
    LOGV("getVideoHeight: %d", h);
    return h;
}

static void
android_media_MediaPhone_setAudioStreamType(JNIEnv *env, jobject thiz, int streamtype)
{
    LOGV("setAudioStreamType: %d", streamtype);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->setAudioStreamType(streamtype) , NULL, NULL);
}

static void
android_media_MediaPhone_setVolume(JNIEnv *env, jobject thiz, float leftVolume, float rightVolume)
{
    LOGV("setVolume: left %f  right %f", leftVolume, rightVolume);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->setVolume(leftVolume, rightVolume), NULL, NULL);
}

// FIXME: deprecated
static jobject
android_media_MediaPhone_getFrameAt(JNIEnv *env, jobject thiz, jint msec)
{
    return NULL;
}

// This function gets some field IDs, which in turn causes class initialization.
// It is called from a static block in MediaPhone, which won't run until the
// first time an instance of this class is used.
static void
android_media_MediaPhone_native_init(JNIEnv *env)
{
    jclass clazz;

    clazz = env->FindClass("android/media/MediaPhone");
    if (clazz == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/media/MediaPhone");
        return;
    }

    fields.context = env->GetFieldID(clazz, "mNativeContext", "I");
    if (fields.context == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MediaPhone.mNativeContext");
        return;
    }

    fields.remote_surface = env->GetFieldID(clazz, "mRemoteSurface", "Landroid/view/Surface;");
    if (fields.remote_surface == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MediaPhone.mRemoteSurface");
        return;
    }

    fields.local_surface = env->GetFieldID(clazz, "mLocalSurface", "Landroid/view/Surface;");
    if (fields.local_surface == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MediaPhone.mLocalSurface");
        return;
    }

    jclass surface = env->FindClass("android/view/Surface");
    if (surface == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find android/view/Surface");
        return;
    }

    fields.surface_native = env->GetFieldID(surface, "mSurface", "I");
    if (fields.surface_native == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find Surface.mSurface");
        return;
    }

    fields.post_event = env->GetStaticMethodID(clazz, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");
    if (fields.post_event == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "MediaPhone.postEventFromNative");
        return;
    }
}


static void
android_media_MediaPhone_native_setup(JNIEnv *env, jobject thiz, jobject weak_this)
{
    LOGV("setup");
    sp<MediaPhone> mr = new MediaPhone();
    if (mr == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return;
    }
//    if (mr->initCheck() != NO_ERROR) {
//        jniThrowException(env, "java/lang/IOException", "Unable to initialize camera");
//        return;
//    }

    // create new listener and give it to MediaPhone
    sp<JNIMediaPhoneListener> listener = new JNIMediaPhoneListener(env, thiz, weak_this);
    mr->setListener(listener);

    setMediaPhone(env, thiz, mr);
}

static void
android_media_MediaPhone_native_finalize(JNIEnv *env, jobject thiz)
{
    LOGV("finalize");
    android_media_MediaPhone_release(env, thiz);
}

// ----------------------------------------------------------------------------

static JNINativeMethod gMethods[] = {
    {"setCamera",            "(Landroid/hardware/Camera;)V",    (void *)android_media_MediaPhone_setCamera},
    {"_setRemoteSurface",    "()V",                             (void *)android_media_MediaPhone_setRemoteSurface},
    {"setParameters",         "(Ljava/lang/String;)V",           (void *)android_media_MediaPhone_setParameters},
    {"prepareAsync",         "()V",                              (void *)android_media_MediaPhone_prepareAsync},
    {"setComm",              "(Ljava/lang/String;Ljava/lang/String;)V",           (void *)android_media_MediaPhone_setComm},
    {"_start",               "()V",                             (void *)android_media_MediaPhone_start},
    {"_stop",                "()V",                             (void *)android_media_MediaPhone_stop},
    {"getVideoWidth",        "()I",                             (void *)android_media_MediaPhone_getVideoWidth},
    {"getVideoHeight",       "()I",                             (void *)android_media_MediaPhone_getVideoHeight},
    {"_release",             "()V",                             (void *)android_media_MediaPhone_release},
    {"setAudioStreamType",   "(I)V",                            (void *)android_media_MediaPhone_setAudioStreamType},
    {"setVolume",            "(FF)V",                           (void *)android_media_MediaPhone_setVolume},
    {"getFrameAt",           "(I)Landroid/graphics/Bitmap;",    (void *)android_media_MediaPhone_getFrameAt},
    {"native_init",          "()V",                             (void *)android_media_MediaPhone_native_init},
    {"native_setup",         "(Ljava/lang/Object;)V",           (void *)android_media_MediaPhone_native_setup},
    {"native_finalize",      "()V",                             (void *)android_media_MediaPhone_native_finalize},
};

static const char* const kClassPathName = "android/media/MediaPhone";

// This function only registers the native methods, and is called from
// JNI_OnLoad in android_media_MediaPlayer.cpp
int register_android_media_MediaPhone(JNIEnv *env)
{
    return AndroidRuntime::registerNativeMethods(env,
                "android/media/MediaPhone", gMethods, NELEM(gMethods));
}


