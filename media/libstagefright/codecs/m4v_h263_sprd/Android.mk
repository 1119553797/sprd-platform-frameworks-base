LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sc8825)
include $(LOCAL_PATH)/8825/Android.mk
endif

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sc7710)
include $(LOCAL_PATH)/7710/Android.mk
endif

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sc8810)
include $(LOCAL_PATH)/7710/Android.mk
endif

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sc8830)
include $(LOCAL_PATH)/8830/Android.mk
endif
