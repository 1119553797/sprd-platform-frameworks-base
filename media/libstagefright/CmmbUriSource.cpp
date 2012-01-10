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

#include <media/stagefright/CmmbUriSource.h>
#include <media/stagefright/MediaDebug.h>

namespace android {

CmmbUriSource::CmmbUriSource(const char *uri)
{
      strcpy(mUri, uri);
}

CmmbUriSource::~CmmbUriSource() {
}

status_t CmmbUriSource::initCheck() const {
    return mUri[0] != 0 ? OK : NO_INIT;
}

ssize_t CmmbUriSource::readAt(off_t offset, void *data, size_t size) {
    return 0;
}

status_t CmmbUriSource::getSize(off_t *size) {
    *size = 0;

    return OK;
}

}  // namespace android
