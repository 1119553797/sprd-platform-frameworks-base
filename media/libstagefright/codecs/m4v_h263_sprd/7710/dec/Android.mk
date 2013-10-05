LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        SPRDMPEG4Decoder.cpp

LOCAL_C_INCLUDES := \
        frameworks/base/media/libstagefright/include \
        frameworks/base/include/media/stagefright/openmax \
	frameworks/base/include/media/stagefright \
	$(TOP)/device/sprd/common/libs/gralloc \
	$(TOP)/device/sprd/common/libs/mali/src/ump/include \
	$(TARGET_OUT_INTERMEDIATES)/KERNEL/usr/include/video 
	

LOCAL_CFLAGS := -DOSCL_EXPORT_REF= -DOSCL_IMPORT_REF=

LOCAL_ARM_MODE := arm

LOCAL_SHARED_LIBRARIES := \
        libstagefright libstagefright_omx libstagefright_foundation libutils  libui libbinder libdl 

LOCAL_MODULE := libstagefright_sprd_mpeg4dec
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))

