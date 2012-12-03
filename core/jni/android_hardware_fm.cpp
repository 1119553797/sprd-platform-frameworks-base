
#define LOG_TAG "fm"

#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "utils/Log.h"
#include "utils/misc.h"
#include "android_runtime/AndroidRuntime.h"
#include "fm/FmInterface.h"
#include "fm/KTFmImpl.h"

#define FM_JNI_UNINITIALIZED    -(1 << 10)

using namespace android;

static FmInterface* gFmInterface = NULL;

/* native interface */
static jint android_hardware_fm_FmJni_powerUp(JNIEnv* env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("powerUp() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->powerUp();
}

static jint android_hardware_fm_FmJni_powerDown(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("powerDown() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->powerDown();
}

static jint android_hardware_fm_FmJni_setFreq(JNIEnv * env, jobject thiz, jint freq) {
    if (gFmInterface == NULL) {
        ALOGE("setFreq() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setFreq(freq);
}

static jint android_hardware_fm_FmJni_getFreq(JNIEnv * env, jobject thiz){
    if (gFmInterface == NULL) {
        ALOGE("getFreq() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getFreq();
}

static jint android_hardware_fm_FmJni_startSearch(JNIEnv * env, jobject thiz, jint freq, jint direction, jint timeout) {
    if (gFmInterface == NULL) {
        ALOGE("startSearch() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->startSearch(freq, direction, timeout);
}

static jint android_hardware_fm_FmJni_cancelSearch(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("cancelSearch() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->cancelSearch();
}

static jint android_hardware_fm_FmJni_setAudioMode(JNIEnv * env, jobject thiz, jint mode) {
    if (gFmInterface == NULL) {
        ALOGE("setAudioMode() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }   

    return gFmInterface->setAudioMode(mode);
}

static jint android_hardware_fm_FmJni_getAudioMode(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getAudioMode() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getAudioMode();
}

static jint android_hardware_fm_FmJni_setBand(JNIEnv * env, jobject thiz, jint band) {
    if (gFmInterface == NULL) {
        ALOGE("setBand() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setBand(band);
}

static jint android_hardware_fm_FmJni_getBand(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getBand() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getBand();
}

static jint android_hardware_fm_FmJni_setStepType(JNIEnv * env, jobject thiz, jint type) {
    if (gFmInterface == NULL) {
        ALOGE("setStepType() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setStepType(type);
}

static jint android_hardware_fm_FmJni_getStepType(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getStepType() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getStepType();
}

static jint android_hardware_fm_FmJni_setMuteMode(JNIEnv * env, jobject thiz, jint mode) {
    if (gFmInterface == NULL) {
        ALOGE("setMuteMode() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setMuteMode(mode);
}

static jint android_hardware_fm_FmJni_getMuteMode(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getMuteMode() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getMuteMode();
}

static jint android_hardware_fm_FmJni_setVolume(JNIEnv * env, jobject thiz, jint volume) {
    if (gFmInterface == NULL) {
        ALOGE("setVolume() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setVolume(volume);
}

static jint android_hardware_fm_FmJni_getVolume(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getVolume() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getVolume();
}

static jint android_hardware_fm_FmJni_setRssi(JNIEnv * env, jobject thiz, jint rssi) {
    if (gFmInterface == NULL) {
        ALOGE("setRssi() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->setRssi(rssi);
}

static jint android_hardware_fm_FmJni_getRssi(JNIEnv * env, jobject thiz) {
    if (gFmInterface == NULL) {
        ALOGE("getRssi() fm isn't initialized");
        return FM_JNI_UNINITIALIZED;
    }

    return gFmInterface->getRssi();
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    {"powerUp", "()I", (void*)android_hardware_fm_FmJni_powerUp},
    {"powerDown", "()I", (void*)android_hardware_fm_FmJni_powerDown},
    {"setFreq", "(I)I", (void*)android_hardware_fm_FmJni_setFreq},
    {"getFreq", "()I", (void*)android_hardware_fm_FmJni_getFreq},
    {"startSearch", "(III)I", (void*)android_hardware_fm_FmJni_startSearch},
    {"cancelSearch", "()I", (void*)android_hardware_fm_FmJni_cancelSearch},
    {"setAudioMode", "(I)I", (void*)android_hardware_fm_FmJni_setAudioMode},
    {"getAudioMode", "()I", (void*)android_hardware_fm_FmJni_getAudioMode},
    {"setStepType", "(I)I", (void*)android_hardware_fm_FmJni_setStepType},
    {"getStepType", "()I", (void*)android_hardware_fm_FmJni_getStepType},
    {"setBand", "(I)I", (void*)android_hardware_fm_FmJni_setBand},
    {"getBand", "()I", (void*)android_hardware_fm_FmJni_getBand},
    {"setMuteMode", "(I)I", (void*)android_hardware_fm_FmJni_setMuteMode},
    {"getMuteMode", "()I", (void*)android_hardware_fm_FmJni_getMuteMode},
    {"setVolume", "(I)I", (void*)android_hardware_fm_FmJni_setVolume},
    {"getVolume", "()I", (void*)android_hardware_fm_FmJni_getVolume},
    {"setRssi", "(I)I", (void*)android_hardware_fm_FmJni_setRssi},
    {"getRssi", "()I", (void*)android_hardware_fm_FmJni_getRssi},
};

int register_android_hardware_fm_FmJni(JNIEnv* env) {
    gFmInterface = KTFmImpl::getInstance();
    if (gFmInterface == NULL) {
        ALOGE("failed to get fm instance");
        return -1;
    }

    return AndroidRuntime::registerNativeMethods(env, "android/hardware/fm/FmJni", gMethods, NELEM(gMethods));
}
