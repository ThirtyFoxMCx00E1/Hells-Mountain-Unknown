LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := unsolvedcase
LOCAL_SRC_FILES := ../cpp/main.cpp ../cpp/renderer.cpp
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra
LOCAL_LDLIBS := -landroid -llog -lEGL -lGLESv3
include $(BUILD_SHARED_LIBRARY)
