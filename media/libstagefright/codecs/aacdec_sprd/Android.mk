LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
        AACSPRDDecoder.cpp

LOCAL_MODULE := libstagefright_aacdec_sprd

LOCAL_CFLAGS := -D_AACARM_  -D_ARMNINEPLATFORM_  -DAAC_DEC_LITTLE_ENDIAN

LOCAL_C_INCLUDES := \
        $(TOP)/frameworks/base/media/libstagefright/include \
        $(TOP)/external/sprd/aacdec/decode_src \
        $(TOP)/external/sprd/aacdec/decode_inc \
        $(TOP)/external/sprd/aacdec/decode_inc/codebook 

include $(BUILD_STATIC_LIBRARY)
