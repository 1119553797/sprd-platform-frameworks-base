LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        SPRDAACDecoder.cpp

LOCAL_C_INCLUDES := \
        frameworks/base/media/libstagefright/include \
        frameworks/base/include/media/stagefright/openmax \
        frameworks/base/media/libstagefright/codecs/aacdec_sprd   \
        frameworks/base/include/media/stagefright

LOCAL_CFLAGS := -DOSCL_EXPORT_REF= -DOSCL_IMPORT_REF= -D_AACARM_  -D_ARMNINEPLATFORM_  -DAAC_DEC_LITTLE_ENDIAN

LOCAL_SHARED_LIBRARIES := \
          libstagefright libstagefright_omx libstagefright_foundation libutils libui libbinder libdl

LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/aacdec_sprd/libaacdec_sprd.a

LOCAL_MODULE := libstagefright_sprd_aacdec
LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
