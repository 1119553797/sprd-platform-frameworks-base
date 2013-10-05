LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        SPRDMP3Decoder.cpp

LOCAL_C_INCLUDES := \
        frameworks/base/media/libstagefright/include \
        frameworks/base/include/media/stagefright/openmax \
        frameworks/base/media/libstagefright/codecs/mp3dec_sprd   \
        frameworks/base/include/media/stagefright

LOCAL_CFLAGS := -DOSCL_EXPORT_REF= -DOSCL_IMPORT_REF=

LOCAL_SHARED_LIBRARIES := \
          libstagefright libstagefright_omx libstagefright_foundation libutils libui libbinder libdl

LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/mp3dec_sprd/libmp3dec_sprd.a

LOCAL_MODULE := libstagefright_sprd_mp3dec
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
