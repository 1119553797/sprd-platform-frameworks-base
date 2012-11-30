LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	AVCSPRDDecoder.cpp	

LOCAL_MODULE := libstagefright_avcdec_sprd
LOCAL_MODULE_TAGS := optional

LOCAL_CFLAGS :=  -fno-strict-aliasing -D_VSP_LINUX_  -D_VSP_  -DCHIP_ENDIAN_LITTLE -DITRANS_ASSEMBLY -DCHIP_8810 
#LOCAL_CFLAGS += -DYUV_THREE_PLANE
LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES := \
	$(TOP)/frameworks/base/media/libstagefright/include \
        $(TOP)/frameworks/base/include/media/stagefright/openmax

include $(BUILD_STATIC_LIBRARY)
