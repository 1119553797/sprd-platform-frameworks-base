/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include <media/stagefright/CharDeviceSource.h>
#include <media/stagefright/MediaDebug.h>

#define LOG_NDEBUG 0
#define LOG_TAG "VideoPhoneExtractor"
#include <utils/Log.h>

#include <stdio.h>
#include <fcntl.h>

namespace android {

CharDeviceSource::CharDeviceSource(const char *filename)
    : mFd(open(filename, O_RDONLY)),
    	mNeedClose(1) {
}

CharDeviceSource::CharDeviceSource(int fd)
    : mFd(fd){
}

CharDeviceSource::~CharDeviceSource() {
    if (mNeedClose) {
        close(mFd);
        mFd = -1;
    }
}

status_t CharDeviceSource::initCheck() const {
    return mFd > 0 ? OK : NO_INIT;
}

ssize_t CharDeviceSource::readAt(off_t offset, void *data, size_t size) {
    if (mFd == -1) {
        return NO_INIT;
    }

    Mutex::Autolock autoLock(mLock);

    return read(mFd, data, size);
}

status_t CharDeviceSource::getSize(off_t *size) {
    *size = 0xFFFFFFFF;

    return OK;
}

}  // namespace android
