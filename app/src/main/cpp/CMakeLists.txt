# The native half of the narrator: llama.cpp, vendored and pinned under
# third_party/, compiled into one shared library together with the JNI
# wrapper. Every llama/ggml object is linked statically into it, so the
# APK carries a single .so per ABI and the loader has one thing to find.
#
# Build options live in app/build.gradle.kts (externalNativeBuild
# arguments) rather than being duplicated here — one source of truth for
# what is switched off, and the list is load-bearing: LLAMA_CURL=OFF
# keeps network code out of a build whose whole privacy promise is that
# no path to the network exists.
cmake_minimum_required(VERSION 3.22)
project(mindanchor_llama CXX)

# v0.25.15: 16 KB page-size support for Android 15+ devices.
# Android 15 (API 35) and onward support a 16 KB page-size kernel
# (arm64 and x86_64) — see
# https://developer.android.com/guide/practices/page-sizes. Apps
# shipped for those devices after Nov 1, 2025 must have their
# native libraries built with the linker flag
# `-Wl,-z,max-page-size=16384`, otherwise the loader refuses the
# library and the app crashes on launch with a "dlopen failed"
# error. The fix is a single `add_link_options` at the top of
# the CMakeLists — it propagates to every shared library this
# file produces (the vendored llama.cpp static lib is linked
# into `mindanchor_llama`, so it picks up the same flag through
# the `target_link_libraries` below; the androidx/surface/
# datastore shared counters are loaded by AndroidX at runtime
# and need the flag passed to *their* link line, which is
# handled by the Android Gradle Plugin when `linkerOptions` is
# set in the externalNativeBuild config — see the
# `-Wl,-z,max-page-size=16384` entry in
# `app/build.gradle.kts` `defaultConfig.externalNativeBuild.cmake.arguments`).
# This file's `add_link_options` covers `mindanchor_llama.so`
# (the only native shared library this CMakeLists produces).
add_link_options("-Wl,-z,max-page-size=16384")

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/llama
)

add_library(mindanchor_llama SHARED mindanchor_llama.cpp)

# The llama target exports its own and ggml's include directories, so
# linking it is also what makes <llama.h> resolvable.
target_link_libraries(mindanchor_llama PRIVATE llama)
