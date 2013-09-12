LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
# SPRD: Modify 20130912 Spreadst of Bug 215339 support support multi-card carrier info display
LOCAL_JAVA_LIBRARIES += telephony-common mms-common

LOCAL_MODULE := android.policy

include $(BUILD_JAVA_LIBRARY)

# additionally, build unit tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))
