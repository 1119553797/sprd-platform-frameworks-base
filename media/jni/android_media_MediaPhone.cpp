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

#define LOG_NDEBUG 0
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

#include <cutils/properties.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <errno.h>

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
    jfieldID stopWaitRequestForAT;

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
    LOGV("JNIMediaPhoneListener::notify %d %d %d", msg, ext1, ext2);

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
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    // we should not pass a null camera to get_native_camera() call.
    if (camera == NULL) {
        process_media_phone_call(env, thiz, mp->setCamera(NULL),
            "java/lang/RuntimeException", "setCamera failed.");
    } else {
        sp<Camera> c = get_native_camera(env, camera, NULL);
        process_media_phone_call(env, thiz, mp->setCamera(c->remote()),
            "java/lang/RuntimeException", "setCamera failed.");
    }
}

static void setRemoteSurface(const sp<MediaPhone>& mp, JNIEnv *env, jobject thiz)
{
    jobject surface = env->GetObjectField(thiz, fields.remote_surface);
    if (surface != NULL) {
        const sp<Surface> native_surface = get_surface(env, surface);
        if (NULL == native_surface.get()) {
            LOGE("setRemoteSurface(), failed to get surface");
            return;
        }
        LOGV("setRemoteSurface: surface=%p (id=%d)",
             native_surface.get(), native_surface->getIdentity());
        mp->setRemoteSurface(native_surface);
    } else {
	    LOGV("setRemoteSurface: surface=NULL");
		mp->setRemoteSurface(NULL);
    }
}

static void setLocalSurface(const sp<MediaPhone>& mp, JNIEnv *env, jobject thiz)
{
    jobject surface = env->GetObjectField(thiz, fields.local_surface);
    if (surface != NULL) {
        const sp<Surface> native_surface = get_surface(env, surface);
        if (NULL == native_surface.get()) {
            LOGE("setLocalSurface(), failed to get surface");
            return;
        }
        LOGV("setLocalSurface: surface=%p (id=%d)",
             native_surface.get(), native_surface->getIdentity());
        mp->setLocalSurface(native_surface);
    } else {
    	LOGV("setLocalSurface: surface=NULL");
		mp->setLocalSurface(NULL);
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
android_media_MediaPhone_setLocalSurface(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    setLocalSurface(mp, env, thiz);
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
android_media_MediaPhone_prepare(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    
    setRemoteSurface(mp, env, thiz);
	setLocalSurface(mp, env, thiz);
    
    process_media_phone_call( env, thiz, mp->prepare(), "java/io/IOException", "Prepare Async failed.");
}

static void
android_media_MediaPhone_prepareAsync(JNIEnv *env, jobject thiz)
{
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call( env, thiz, mp->prepareAsync(), "java/io/IOException", "Prepare Async failed." );
}

static void
android_media_MediaPhone_startPlayer(JNIEnv *env, jobject thiz)
{
    LOGV("startPlayer");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    process_media_phone_call(env, thiz, mp->startPlayer(), "java/lang/RuntimeException", "startPlayer failed.");
}

static void
android_media_MediaPhone_startRecorder(JNIEnv *env, jobject thiz)
{
    LOGV("startRecorder");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    process_media_phone_call(env, thiz, mp->startRecorder(), "java/lang/RuntimeException", "startRecorder failed.");
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
android_media_MediaPhone_setDecodeType(JNIEnv *env, jobject thiz, jint type)
{
    LOGV("setDecodeType: %d", type);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->setDecodeType(type) , NULL, NULL);
}

static void
android_media_MediaPhone_setEncodeType(JNIEnv *env, jobject thiz, jint type)
{
    LOGV("setEncodeType: %d", type);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->setEncodeType(type) , NULL, NULL);
}

static void
android_media_MediaPhone_setAudioStreamType(JNIEnv *env, jobject thiz, jint streamtype)
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
android_media_MediaPhone_setVolume(JNIEnv *env, jobject thiz, jfloat leftVolume, jfloat rightVolume)
{
    LOGV("setVolume: left %f  right %f", leftVolume, rightVolume);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->setVolume(leftVolume, rightVolume), NULL, NULL);
}

static void
android_media_MediaPhone_enableRecord(JNIEnv *env, jobject thiz, jboolean isEnable, int type, jobject fileDescriptor)
{		
    LOGV("enableRecord: isEnable %d, type: %d", isEnable, type);
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if ((mp == NULL) || ((isEnable && (fileDescriptor == NULL)))) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }

	if (isEnable){		
    	int fd = getParcelFileDescriptorFD(env, fileDescriptor);
    	process_media_phone_call(env, thiz, mp->enableRecord(isEnable, type, fd), NULL, NULL);
	} else {
    	process_media_phone_call(env, thiz, mp->enableRecord(isEnable, type, 0), NULL, NULL);
	}
}

static void
android_media_MediaPhone_startUpLink(JNIEnv *env, jobject thiz)
{
    LOGV("startUpLink: ");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->startUpLink(), NULL, NULL);
}

static void
android_media_MediaPhone_stopUpLink(JNIEnv *env, jobject thiz)
{
    LOGV("stopUpLink: ");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->stopUpLink(), NULL, NULL);
}

static void
android_media_MediaPhone_startDownLink(JNIEnv *env, jobject thiz)
{
    LOGV("startDownLink: ");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->startDownLink(), NULL, NULL);
}

static void
android_media_MediaPhone_stopDownLink(JNIEnv *env, jobject thiz)
{
    LOGV("stopDownLink: ");
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
    if (mp == NULL ) {
        jniThrowException(env, "java/lang/IllegalStateException", NULL);
        return;
    }
    process_media_phone_call(env, thiz, mp->stopDownLink(), NULL, NULL);
}

// FIXME: deprecated
static jobject
android_media_MediaPhone_getFrameAt(JNIEnv *env, jobject thiz, jint msec)
{
    return NULL;
}

static void
android_media_MediaPhone_setCameraParam(JNIEnv *env, jobject thiz, jstring key, jint value)
{
    LOGV("setCameraParam()");
	const char *temp_key = env->GetStringUTFChars(key, NULL);
    if (temp_key == NULL) {  // Out of memory
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return;
    }
	LOGV("setCameraParam: key %s, value %d", temp_key, value);
	
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
	process_media_phone_call(env, thiz, mp->setCameraParam(temp_key, value), NULL, NULL);
	env->ReleaseStringUTFChars(key, temp_key);
}

static int
android_media_MediaPhone_getCameraParam(JNIEnv *env, jobject thiz, jstring key)
{
	int value = -1;
    LOGV("getCameraParam()");
	const char *temp_key = env->GetStringUTFChars(key, NULL);
    if (temp_key == NULL) {  // Out of memory
        jniThrowException(env, "java/lang/RuntimeException", "Out of memory");
        return value;
    }
	
    sp<MediaPhone> mp = getMediaPhone(env, thiz);
	process_media_phone_call(env, thiz, mp->getCameraParam(temp_key, &value), NULL, NULL);
	LOGV("getCameraParam: key %s, value %d", temp_key, value);
	env->ReleaseStringUTFChars(key, temp_key);
	return value;
}

const int AT_NONE = 0;
const int AT_TIMEOUT = -1;
const int AT_SELECT_ERR = -2;
const int AT_REPORT_IFRAME = 1;
const int AT_REQUEST_IFRAME = 2;
const int AT_BOTH_IFRAME = 3;
#define MAX(a,b) ((a)>(b) ? (a):(b))

static int
android_media_MediaPhone_native_waitRequestForAT(JNIEnv *env, jobject thiz)
{
	int retval = AT_NONE;
	int vt_pipe_request_iframe = -1;
	int vt_pipe_report_iframe = -1;
	if (vt_pipe_request_iframe < 0) vt_pipe_request_iframe = open("/dev/rpipe/ril.vt.1", O_RDWR);
	if (vt_pipe_report_iframe < 0) vt_pipe_report_iframe = open("/dev/rpipe/ril.vt.2", O_RDWR);
	if ((vt_pipe_request_iframe > 0) && (vt_pipe_request_iframe > 0)){
		do {
			struct timeval tv = {0};
			tv.tv_sec = 0;
		        tv.tv_usec = 200 * 1000;
			fd_set rfds;
			FD_ZERO(&rfds);
			FD_SET(vt_pipe_report_iframe, &rfds);
			FD_SET(vt_pipe_request_iframe, &rfds);

			retval = select(MAX(vt_pipe_report_iframe, vt_pipe_request_iframe) + 1, &rfds, NULL, NULL, &tv);
			if (retval == -1) {
				LOGE("select err");
				retval = AT_SELECT_ERR;
			} else if (retval > 0) {
				ssize_t len = 0;
				char buf[128];
				//LOGV("read vt_pipe, retval: %d", retval);
				if (FD_ISSET(vt_pipe_request_iframe, &rfds)) {
					len = read(vt_pipe_request_iframe, buf, sizeof(buf) - 1);
					LOGV("read vt_pipe_request_iframe, len: %d", len);
					retval = AT_REQUEST_IFRAME;
				}
				if (FD_ISSET(vt_pipe_report_iframe, &rfds)) {
					len = read(vt_pipe_report_iframe, buf, sizeof(buf) - 1);
					LOGV("read vt_pipe_report_iframe, len: %d", len);
					if (retval == AT_REQUEST_IFRAME) {
						retval = AT_BOTH_IFRAME;
					} else {
						retval = AT_REPORT_IFRAME;
					}
				}
				break;
			} else {
				//LOGE("select timeout");
				retval = AT_TIMEOUT;
			}
			bool  bStop = (bool*)env->GetBooleanField(thiz, fields.stopWaitRequestForAT);
			if (bStop) {
				LOGE("bStop");
				break;
			}
		} while (1);
	}else {
	    LOGE("vt_pipe_report_iframe: %d, vt_pipe_request_iframe: %d", vt_pipe_report_iframe, vt_pipe_request_iframe);        
        LOGE("vt_pipe errno: %d, %s", errno, strerror(errno));
    }
	if (vt_pipe_report_iframe > 0) {
		close(vt_pipe_report_iframe);
	}
	if (vt_pipe_request_iframe > 0) {
		close(vt_pipe_request_iframe);
	}
	return retval;
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

    fields.surface_native = env->GetFieldID(surface, "mNativeSurface", "I");
    if (fields.surface_native == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find Surface.mNativeSurface");
        return;
    }
	
    fields.stopWaitRequestForAT = env->GetFieldID(clazz, "mStopWaitRequestForAT", "Z");
    if (fields.stopWaitRequestForAT == NULL) {
        jniThrowException(env, "java/lang/RuntimeException", "Can't find MediaPhone.mStopWaitRequestForAT");
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
    {"_setLocalSurface",    "()V",                              (void *)android_media_MediaPhone_setLocalSurface},
    {"setParameters",        "(Ljava/lang/String;)V",           (void *)android_media_MediaPhone_setParameters},
    {"prepare",         	 "()V",                             (void *)android_media_MediaPhone_prepare},
    {"prepareAsync",         "()V",                             (void *)android_media_MediaPhone_prepareAsync},
    {"setComm",              "(Ljava/lang/String;Ljava/lang/String;)V",           (void *)android_media_MediaPhone_setComm},
    {"_startPlayer",         "()V",                             (void *)android_media_MediaPhone_startPlayer},
    {"_startRecorder",       "()V",                             (void *)android_media_MediaPhone_startRecorder},
    {"_stop",                "()V",                             (void *)android_media_MediaPhone_stop},
    {"getVideoWidth",        "()I",                             (void *)android_media_MediaPhone_getVideoWidth},
    {"getVideoHeight",       "()I",                             (void *)android_media_MediaPhone_getVideoHeight},
    {"_release",             "()V",                             (void *)android_media_MediaPhone_release},
    {"setDecodeType",   	 "(I)V",                            (void *)android_media_MediaPhone_setDecodeType},
    {"setEncodeType",   	 "(I)V",                            (void *)android_media_MediaPhone_setEncodeType},
    {"setAudioStreamType",   "(I)V",                            (void *)android_media_MediaPhone_setAudioStreamType},
    {"setVolume",            "(FF)V",                           (void *)android_media_MediaPhone_setVolume},
    {"_enableRecord",        "(ZILjava/io/FileDescriptor;)V",   (void *)android_media_MediaPhone_enableRecord},
    {"startUpLink",          "()V",                             (void *)android_media_MediaPhone_startUpLink},
    {"stopUpLink",           "()V",                             (void *)android_media_MediaPhone_stopUpLink},
    {"startDownLink",        "()V",                           	(void *)android_media_MediaPhone_startDownLink},
    {"stopDownLink",         "()V",                           	(void *)android_media_MediaPhone_stopDownLink},
    {"getFrameAt",           "(I)Landroid/graphics/Bitmap;",    (void *)android_media_MediaPhone_getFrameAt},
    {"setCameraParam",       "(Ljava/lang/String;I)V",          (void *)android_media_MediaPhone_setCameraParam},
    {"getCameraParam",       "(Ljava/lang/String;)I",          (void *)android_media_MediaPhone_getCameraParam},
    {"native_waitRequestForAT",       "()I",          (void *)android_media_MediaPhone_native_waitRequestForAT},
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


