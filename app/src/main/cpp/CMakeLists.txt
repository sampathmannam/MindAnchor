# The native half of the narrator and the voice journal: llama.cpp +
# whisper.cpp, vendored and pinned under third_party/, compiled into
# one shared library each together with the JNI wrapper. Every
# llama/ggml / whisper/ggml object is linked statically into its
# respective .so, so the APK carries a single .so per ABI per native
# library and the loader has one thing to find per native surface.
#
# Build options live in app/build.gradle.kts (externalNativeBuild
# arguments) rather than being duplicated here — one source of truth for
# what is switched off, and the list is load-bearing: LLAMA_CURL=OFF
# and WHISPER_CURL=OFF keep network code out of a build whose whole
# privacy promise is that no path to the network exists.
cmake_minimum_required(VERSION 3.22)
project(mindanchor_native CXX)

# Vendored llama.cpp (Phase 1 narrator). The llama
# subdirectory is responsible for adding its own
# `add_subdirectory(ggml)`; this is the upstream
# llama.cpp layout as of b4658.
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/llama
)

add_library(mindanchor_llama SHARED mindanchor_llama.cpp)
target_link_libraries(mindanchor_llama PRIVATE llama)

# Vendored whisper.cpp (Phase 6 / T-6.1 voice
# journal). The whisper subdirectory is
# responsible for adding its own
# `add_subdirectory(ggml)`; this is the upstream
# whisper.cpp layout as of b4938. whisper.cpp
# vendors its own ggml tree that is independent of
# llama.cpp's ggml — the two ggml trees have
# diverged upstream and are not shareable, see
# third_party/whisper.cpp/VENDORED.md.
add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/whisper
)

add_library(mindanchor_whisper SHARED mindanchor_whisper.cpp)
target_link_libraries(mindanchor_whisper PRIVATE whisper)
