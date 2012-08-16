LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

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

LOCAL_MODULE:= vtserver

LOCAL_MODULE_TAGS := optional

include $(BUILD_EXECUTABLE)
