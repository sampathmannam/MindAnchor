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
# v0.70.x (16 KB page-size fix): Android 15+ requires every
# PT_LOAD segment to be 16 KB aligned. The default linker
# page size on aarch64 is 4 KB; without the flags below, the
# ggml-static .o's PT_LOAD p_align is 0x1000 (4 KB) and the
# device's loader fails the .so on a 16 KB-page kernel
# (Pixel 8+, some Samsung) with a 16 KB page-size warning
# on every launch. NDK r27's linker accepts the flag.
target_link_options(mindanchor_llama PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

# Vendored whisper.cpp (Phase 6 / T-6.1 voice
# journal). The whisper subdirectory is
# responsible for adding its own
# `add_subdirectory(ggml)`; this is the upstream
# whisper.cpp layout as of b4938. whisper.cpp
# vendors its own ggml tree that is independent of
# llama.cpp's ggml — the two ggml trees have
# diverged upstream and are not shareable, see
# third_party/whisper.cpp/VENDORED.md.
#
# v0.70.1: whisper targets disabled. The comment above is
# the intent, but not what CMake does: upstream whisper's
# CMakeLists only adds its own ggml `if (NOT TARGET ggml)`,
# and llama's add_subdirectory above has already defined
# that target — so whisper.cpp (b4938-era, expects
# GGML_BACKEND_DEVICE_TYPE_IGPU and the 6-arg
# ggml_backend_sched_new) compiles against llama's older
# b4658-era ggml headers and fails with five compile
# errors on any clean checkout. A stale .cxx cache can
# mask this locally; a fresh worktree cannot. The voice
# journal is a scaffold whose loadLibrary call is a
# guarded runCatching (WhisperEngine.kt), so shipping
# without libmindanchor_whisper.so degrades cleanly to
# "unavailable" rather than crashing. Re-enable by giving
# whisper its own ggml target name (or vendoring matching
# trees), not by reordering the add_subdirectory calls —
# llama against whisper's newer ggml breaks the same way.
#
# add_subdirectory(
#     ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp
#     ${CMAKE_CURRENT_BINARY_DIR}/whisper
# )
#
# add_library(mindanchor_whisper SHARED mindanchor_whisper.cpp)
# target_link_libraries(mindanchor_whisper PRIVATE whisper)
# target_link_options(mindanchor_whisper PRIVATE
#     "-Wl,-z,max-page-size=16384"
#     "-Wl,-z,common-page-size=16384")
