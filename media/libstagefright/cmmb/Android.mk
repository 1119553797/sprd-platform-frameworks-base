LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
 	CMMBExtractor.cpp \
	SockChannel.cpp

LOCAL_MODULE := libstagefright_cmmb

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include \
	$(TOP)/frameworks/base/media/libstagefright/include \
	$(TOP)/external/opencore/extern_libs_v2/khronos/openmax/include \

LOCAL_CFLAGS := -DOSCL_EXPORT_REF= -DOSCL_IMPORT_REF=


include $(BUILD_STATIC_LIBRARY)
