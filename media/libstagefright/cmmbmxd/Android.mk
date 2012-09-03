LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	MxdCmmbClient.cpp \
 	MxdCMMBExtractor.cpp \

LOCAL_MODULE := libstagefright_cmmb_mxd

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include \
	$(TOP)/frameworks/base/media/libstagefright/include \
	$(TOP)/frameworks/base/include/media/stagefright/openmax \


include $(BUILD_STATIC_LIBRARY)
