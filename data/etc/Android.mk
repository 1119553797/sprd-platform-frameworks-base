#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(my-dir)

########################
include $(CLEAR_VARS)

LOCAL_MODULE := platform.xml

LOCAL_MODULE_TAGS := user

LOCAL_MODULE_CLASS := ETC

# This will install the file in /system/etc/permissions
#
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

LOCAL_SRC_FILES := $(LOCAL_MODULE)

include $(BUILD_PREBUILT)

########################
########################
########################

#include $(CLEAR_VARS)

#LOCAL_MODULE := required_hardware.xml

#LOCAL_MODULE_TAGS := user

#LOCAL_MODULE_CLASS := ETC

# This will install the file in /system/etc/permissions
#
#LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

#LOCAL_SRC_FILES := $(LOCAL_MODULE)

#include $(BUILD_PREBUILT)
PRODUCT_COPY_FILES += \
frameworks/base/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
frameworks/base/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
frameworks/base/data/etc/android.hardware.touchscreen.xml:system/etc/permissions/android.hardware.touchscreen.xml \
frameworks/base/data/etc/android.hardware.touchscreen.multitouch.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.xml \
frameworks/base/data/etc/platform.xml:system/etc/permissions/platform.xml \
packages/wallpapers/LivePicker/android.software.live_wallpaper.xml:system/etc/permissions/android.software.live_wallpaper.xml

