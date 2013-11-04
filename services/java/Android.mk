LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	    com/android/server/EventLogTags.logtags \
	    com/android/server/am/EventLogTags.logtags
ifeq ($(USE_PROJECT_SEC),true)
LOCAL_SRC_FILES += $(call all-java-files-under,com)
else
LOCAL_SRC_FILES += $(call all-subdir-java-files)
endif
LOCAL_MODULE:= services
LOCAL_STATIC_JAVA_LIBRARIES := security
LOCAL_JAVA_LIBRARIES := android.policy

LOCAL_NO_EMMA_INSTRUMENT := true
LOCAL_NO_EMMA_COMPILE := true

include $(BUILD_JAVA_LIBRARY)

include $(BUILD_DROIDDOC)
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := security:classes-jarjar.jar
include $(BUILD_MULTI_PREBUILT)
