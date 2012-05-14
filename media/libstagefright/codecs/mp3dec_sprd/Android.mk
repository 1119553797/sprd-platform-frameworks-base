LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        MP3SPRDDecoder.cpp

LOCAL_MODULE := libstagefright_mp3dec_sprd

LOCAL_C_INCLUDES := \
        $(TOP)/frameworks/base/media/libstagefright/include \
        $(TOP)/frameworks/base/media/libstagefright/codecs/mp3dec_sprd

include $(BUILD_STATIC_LIBRARY)
