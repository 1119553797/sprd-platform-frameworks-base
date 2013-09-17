LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

include frameworks/base/media/libstagefright/codecs/common/Config.mk

LOCAL_SRC_FILES:=                         \
        AMRExtractor.cpp                  \
        AMRWriter.cpp                     \
        AVIExtractor.cpp                  \
        AudioPlayer.cpp                   \
        AudioSource.cpp                   \
        AwesomePlayer.cpp                 \
        CameraSource.cpp                  \
	FakeCameraSource.cpp		  \
        DataSource.cpp                    \
        ESDS.cpp                          \
        FileSource.cpp                    \
        CmmbUriSource.cpp            \
        HTTPStream.cpp                    \
        JPEGSource.cpp                    \
        MP3Extractor.cpp                  \
        MPEG2TSWriter.cpp                 \
        MPEG4Extractor.cpp                \
        MPEG4Writer.cpp                   \
        MediaBuffer.cpp                   \
        MediaBufferGroup.cpp              \
        MediaDefs.cpp                     \
        MediaExtractor.cpp                \
        MediaSource.cpp                   \
        MetaData.cpp                      \
        NuCachedSource2.cpp               \
        NuHTTPDataSource.cpp              \
        OMXClient.cpp                     \
        OMXCodec.cpp                      \
        OggExtractor.cpp                  \
        SampleIterator.cpp                \
        SampleTable.cpp                   \
        ShoutcastSource.cpp               \
        StagefrightMediaScanner.cpp       \
        StagefrightMetadataRetriever.cpp  \
        ThreadedSource.cpp                \
        ThrottledSource.cpp               \
        TimeSource.cpp                    \
        TimedEventQueue.cpp               \
        Utils.cpp                         \
        VBRISeeker.cpp                    \
        WAVExtractor.cpp                  \
        XINGSeeker.cpp                    \
        avc_utils.cpp                     \
        string.cpp                        \
        CharDeviceSource.cpp              \
	AACExtractor.cpp                  \
	FLVExtractor.cpp		  \


ifneq ($(BOARD_SUPPORT_FEATURE_VT),false)
LOCAL_SRC_FILES += 			  \
	VideoPhoneExtractor.cpp		  \
	VideoPhoneWriter.cpp		  
endif

ifeq ($(Trout_FM),true)
LOCAL_SRC_FILES	+= FMExtractor.cpp
endif

LOCAL_C_INCLUDES:= \
	$(JNI_H_INCLUDE) \
        $(TOP)/frameworks/base/include/media/stagefright/openmax \
        $(TOP)/external/tremolo \
	$(TOP)/external/skia/include/images \
	$(TOP)/external/skia/include/core \
        $(TOP)/frameworks/base/media/libstagefright/rtsp\
	$(TOP)/frameworks/base/media/libstagefright/cmmbmxd

ifeq ($(Trout_FM),true)
LOCAL_C_INCLUDES += $(TOP)/external/sprd/fmhal \
	$(TOP)/external/sprd/tinyalsa/include
endif

LOCAL_SHARED_LIBRARIES := \
        libbinder         \
        libmedia          \
        libutils          \
        libcutils         \
        libui             \
        libsonivox        \
        libvorbisidec     \
        libsurfaceflinger_client \
        libcamera_client

LOCAL_STATIC_LIBRARIES := \
        libstagefright_aacdec \
        libstagefright_aacenc \
        libstagefright_amrnbdec \
        libstagefright_amrnbenc \
        libstagefright_amrwbdec \
        libstagefright_amrwbenc \
        libstagefright_avcdec \
        libstagefright_avcenc \
        libstagefright_m4vh263dec \
        libstagefright_m4vh263enc \
        libstagefright_vorbisdec \
        libstagefright_matroska \
        libstagefright_vpxdec \
        libvpx \
        libstagefright_mpeg2ts \
        libstagefright_httplive \
        libstagefright_rtsp \
        libstagefright_id3 \
        libstagefright_imaadpcm \
        libstagefright_g711dec \
	libstagefright_cmmb \
	libstagefright_cmmb_mxd

ifneq ($(BUILD_SPRD_STAGEFRIGHT),false)
LOCAL_STATIC_LIBRARIES += \
	libstagefright_aacdec_sprd \
        libstagefright_mp3dec_sprd \
	libstagefright_m4vh263dec_sprd	\
	libstagefright_avcdec_sprd	
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libaacdec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libmp3dec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libm4vh263dec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libavcdec_sprd.a
else
LOCAL_LDFLAGS += $(TOP)/customize/customer_cfg/${ANDROID_3RDPARTY_IMAGE_TAG}/proprietary/stagefright/libstagefright_aacdec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libaacdec_sprd.a

LOCAL_LDFLAGS += $(TOP)/customize/customer_cfg/${ANDROID_3RDPARTY_IMAGE_TAG}/proprietary/stagefright/libstagefright_mp3dec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libmp3dec_sprd.a

LOCAL_LDFLAGS += $(TOP)/customize/customer_cfg/${ANDROID_3RDPARTY_IMAGE_TAG}/proprietary/stagefright/libstagefright_m4vh263dec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libm4vh263dec_sprd.a

LOCAL_LDFLAGS += $(TOP)/customize/customer_cfg/${ANDROID_3RDPARTY_IMAGE_TAG}/proprietary/stagefright/libstagefright_avcdec_sprd.a
LOCAL_LDFLAGS += $(TOP)/frameworks/base/media/libstagefright/codecs/libavcdec_sprd.a
endif

LOCAL_SHARED_LIBRARIES += \
        libstagefright_amrnb_common \
        libstagefright_enc_common \
        libstagefright_avc_common \
        libstagefright_foundation \
        libstagefright_color_conversion \
	libskia

ifeq ($(Trout_FM),true)
LOCAL_SHARED_LIBRARIES += libFMHalSource
endif

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread -ldl
        LOCAL_SHARED_LIBRARIES += libdvm
        LOCAL_CPPFLAGS += -DANDROID_SIMULATOR
endif

ifneq ($(TARGET_SIMULATOR),true)
LOCAL_SHARED_LIBRARIES += libdl
endif

ifeq ($(TARGET_OS)-$(TARGET_SIMULATOR),linux-true)
        LOCAL_LDLIBS += -lpthread
endif

LOCAL_CFLAGS += -Wno-multichar

#ifeq ($(BUILD_SPRD_STAGEFRIGHT),true)
LOCAL_CFLAGS += -DBUILD_SPRD_AAC
LOCAL_CFLAGS += -DBUILD_SPRD_M4VH263
LOCAL_CFLAGS += -DBUILD_SPRD_AVC
#endif

ifneq ($(BOARD_SUPPORT_FEATURE_VT),false)
LOCAL_CFLAGS += -DBOARD_SUPPORT_FEATURE_VT
endif
ifeq ($(Trout_FM),true)
LOCAL_CFLAGS += -DTrout_FM
endif

LOCAL_MODULE:= libstagefright

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
