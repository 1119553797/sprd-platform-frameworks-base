LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    IMAADPCMDecoder.cpp

LOCAL_C_INCLUDES := \
        frameworks/base/media/libstagefright/include \

LOCAL_MODULE := libstagefright_imaadpcm

include $(BUILD_STATIC_LIBRARY)
