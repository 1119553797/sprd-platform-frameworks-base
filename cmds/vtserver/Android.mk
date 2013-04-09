LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES:= \
	main.cpp 

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt
	endif
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils 

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

ifeq ($(BOARD_HAVE_BLUETOOTH_BK),true)
LOCAL_CFLAGS := \
    -DBOARD_HAVE_BLUETOOTH_BK
endif

LOCAL_MODULE:= vtserver


include $(BUILD_EXECUTABLE)
