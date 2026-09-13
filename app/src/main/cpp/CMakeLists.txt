cmake_minimum_required(VERSION 3.22.1)

# Set before project() so the NDK's Android toolchain file picks it up.
# Kept here rather than in app/build.gradle since AGP 9's build-file DSL
# for this has been a moving target across point releases.
set(ANDROID_STL "c++_shared" CACHE STRING "" FORCE)

project(unsolvedcase LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(unsolvedcase SHARED
    main.cpp
    renderer.cpp
    audio_engine.cpp
    world.cpp
    player.cpp
    savegame.cpp
)

target_compile_options(unsolvedcase PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
)

target_link_libraries(unsolvedcase PRIVATE
    EGL
    GLESv3
    android
    log
    c++_shared
)
