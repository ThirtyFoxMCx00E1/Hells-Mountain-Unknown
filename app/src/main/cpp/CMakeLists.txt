cmake_minimum_required(VERSION 3.22.1)

# Set before project() so the NDK's Android toolchain file picks it up.
# Kept here rather than in app/build.gradle since AGP 9's build-file DSL
# for this has been a moving target across point releases.
#
# STATIC (not shared): when STL is set here rather than through Gradle's
# own cmake.arguments, AGP doesn't know shared STL is in use and won't
# auto-bundle libc++_shared.so into the APK - the CI check caught this as
# a missing runtime dependency that would have crashed on load. Static
# linking bakes the STL directly into the library instead, so there's no
# separate runtime dependency to bundle. This project has only one native
# library, so the usual "don't use static STL with multiple .so files"
# caveat doesn't apply here.
set(ANDROID_STL "c++_static" CACHE STRING "" FORCE)

project(HellsMT LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(HellsMT SHARED
    main.cpp
    renderer.cpp
    audio_engine.cpp
    world.cpp
    player.cpp
    savegame.cpp
    spawn.cpp
    settings.cpp
)

target_compile_options(HellsMT PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
)

target_link_libraries(HellsMT PRIVATE
    EGL
    GLESv3
    android
    log
)
