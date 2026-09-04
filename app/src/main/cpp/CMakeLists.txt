# The native half of the voice journal: whisper.cpp, vendored under
# third_party/, compiled into a shared library together with its JNI
# wrapper.
#
# v0.70.5: the narrator's llama.cpp half (on-device report/letter/note
# writing) has been removed along with the Settings feature that let
# anyone put a model on the phone. This file used to build llama.cpp
# too; llama's `add_subdirectory` was also what made whisper.cpp fail
# to compile (see the comment below) by defining the `ggml` target
# first, with an older, incompatible version. Removing it did not just
# free the slot — it removed the reason whisper was disabled. Whisper
# is left commented out regardless, because re-enabling it is a
# separate decision nobody has made yet, not a side effect of this one.
#
# Build options live in app/build.gradle.kts (externalNativeBuild
# arguments) rather than being duplicated here — one source of truth for
# what is switched off, and the list is load-bearing: WHISPER_CURL=OFF
# keeps network code out of a build whose whole privacy promise is that
# no path to the network exists.
cmake_minimum_required(VERSION 3.22)
project(mindanchor_native CXX)

# Vendored whisper.cpp (Phase 6 / T-6.1 voice
# journal). The whisper subdirectory is
# responsible for adding its own
# `add_subdirectory(ggml)`; this is the upstream
# whisper.cpp layout as of b4938.
#
# v0.70.1: whisper targets disabled. Upstream whisper's CMakeLists
# only adds its own ggml `if (NOT TARGET ggml)` — while this file also
# built llama.cpp, llama's add_subdirectory ran first and defined that
# target with its older b4658-era ggml, so whisper (expecting
# GGML_BACKEND_DEVICE_TYPE_IGPU and the 6-arg ggml_backend_sched_new)
# failed with five compile errors on any clean checkout. A stale
# .cxx cache could mask this locally; a fresh worktree could not. The
# voice journal is a scaffold whose loadLibrary call is a guarded
# runCatching (WhisperEngine.kt), so shipping without
# libmindanchor_whisper.so degrades cleanly to "unavailable" rather
# than crashing.
#
# v0.70.5: llama.cpp — the thing that was defining ggml first — is
# gone. Whisper's own add_subdirectory(ggml) would now run uncontested
# and should configure cleanly. Left commented out anyway: confirming
# that and turning voice journaling on is a decision for whoever wants
# that feature, not a default this removal should flip.
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
