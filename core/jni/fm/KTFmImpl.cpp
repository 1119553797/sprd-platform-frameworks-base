
#define LOG_TAG "KTFmImpl"

#define LOG_NDEBUG 0

#include "KTFmImpl.h"
#include <linux/videodev2.h>
#include <utils/Log.h>

#define V4L2_CID_PRIVATE_STATE  (V4L2_CID_PRIVATE_BASE + 4)
#define FREQUENCY_TRANSFER_FACTOR  100

namespace android {

hw_module_t* KTFmImpl::mModule = NULL;
Mutex KTFmImpl::mModuleLock;

FmInterface* KTFmImpl::getInstance() {
    FmInterface* value = NULL;

    Mutex::Autolock _l(mModuleLock);

    if (mModule == NULL) {
        int result = hw_get_module(FM_HARDWARE_MODULE_ID, (hw_module_t const**)&mModule);
        ALOGV("KTFmImpl() hw_get_module:result=%d", result);
        if (result != 0) {
            mModule = NULL;
        }
    }

    if (mModule != NULL) {
        value = new KTFmImpl();
    }

    return value;
}

KTFmImpl::KTFmImpl():mDevice(NULL), mIsSearching(false),
                     mMuteState(-1), mVolume(-1),
                     mRssi(-1), mStepType(-1) {
}

KTFmImpl::~KTFmImpl() {
    if (mDevice != NULL) {
        powerDown();
    }
}

int KTFmImpl::powerUp() {
    Mutex::Autolock _l(mDeviceLock);

    hw_device_t* device;
    int result = mModule->methods->open(mModule, FM_HARDWARE_MODULE_ID, &device);
    ALOGV("KTFmImpl() hw_get_device:result=%d", result);
    if (result != 0) {
        device = NULL;
    }
    mDevice = (fm_device_t *)device;

    if (mDevice != NULL) {
        result = mDevice->setControl(mDevice, V4L2_CID_PRIVATE_STATE, 1);
        ALOGV("KTFmImpl() enable fm:result=%d", result);
        if (result != 0) {
            hw_device_t* device = (hw_device_t*)mDevice;
            device->close(device);
            mDevice = NULL;
        }
    }

    return result;
}

int KTFmImpl::powerDown() {
    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        int result = mDevice->setControl(mDevice, V4L2_CID_PRIVATE_STATE, 0);
        ALOGV("KTFmImpl() disable fm:result=%d", result);
        hw_device_t* device = (hw_device_t*)mDevice;
        device->close(device);
        mDevice = NULL;
    }

    return 0;
}

int KTFmImpl::setFreq(int freq) {
    Mutex::Autolock _l(mDeviceLock);

    int result = -1;
    if (mDevice != NULL) {
        result = mDevice->setFreq(mDevice, freq / FREQUENCY_TRANSFER_FACTOR);
        ALOGV("setFreq() freq=%d result=%d", freq, result);
    }

    return result;
}

int KTFmImpl::getFreq() {
    Mutex::Autolock _l(mDeviceLock);

    int result = -1;
    int freq = -1;
    if (mDevice != NULL) {
        if ((result = mDevice->getFreq(mDevice, &freq)) < 0) {
            freq = -1;
        } else {
            freq *= FREQUENCY_TRANSFER_FACTOR;
        }
        ALOGV("getFreq() freq=%d result=%d", freq, result);
    }

    return freq;
}

int KTFmImpl::startSearch(int freq, int direction, int timeout) {
    Mutex::Autolock _l(mDeviceLock);

    int result = -1;
    if (mDevice != NULL) {
        mIsSearching = true;
        result = mDevice->startSearch(mDevice, freq / FREQUENCY_TRANSFER_FACTOR, direction, timeout, -1);
        ALOGV("startSearch() freq=%d direction=%d timeout=%d result=%d", freq, direction, timeout, result);
        mIsSearching = false;
    }

    return result;
}

int KTFmImpl::cancelSearch() {
    int result = -1;

    if (mIsSearching) {
        result = mDevice->cancelSearch(mDevice);
    } else {
        Mutex::Autolock _l(mDeviceLock);
        if (mDevice != NULL) {
            result = mDevice->cancelSearch(mDevice);
        }
    }
    ALOGV("cancelSearch() result=%d", result);

    return result;
}

int KTFmImpl::setAudioMode(int mode) {
    return -1;
}

int KTFmImpl::getAudioMode() {
    return -1;
}

int KTFmImpl::setStepType(int type) {
    return -1; 
}

int KTFmImpl::getStepType() {
    return -1; 
}

int KTFmImpl::setBand(int band) {
    return -1;
}

int KTFmImpl::getBand() {
    return -1;
}

int KTFmImpl::setMuteMode(int mode) {
    int result = -1;

    Mutex::Autolock _l(mDeviceLock);
    if (mDevice != NULL) {
        result = mDevice->setControl(mDevice, V4L2_CID_AUDIO_MUTE, mode);
        ALOGV("setMuteMode() mode=%d result=%d", mode, result);
        if (result == 0) {
            mMuteState = mode;
        }
    }

    return result;
}

int KTFmImpl::getMuteMode() {
    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        return mMuteState;
    }

    return -1;
}

int KTFmImpl::setVolume(int volume) {
    int result = -1;

    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        result = mDevice->setControl(mDevice, V4L2_CID_AUDIO_VOLUME, volume);
        ALOGV("setVolume() volume=%d result=%d", volume, result);
        if (result == 0) {
            mVolume = volume;
        }
    }

    return result;
}

int KTFmImpl::getVolume() {
    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        return mVolume;
    }

    return -1;
}

int KTFmImpl::setRssi(int rssi) {
    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        mRssi = rssi;
        return 0;
    }

    return -1;
}

int KTFmImpl::getRssi() {
    Mutex::Autolock _l(mDeviceLock);

    if (mDevice != NULL) {
        return mRssi;
    }

    return -1;
}

};
