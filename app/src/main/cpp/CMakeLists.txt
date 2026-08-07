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

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp
    ${CMAKE_CURRENT_BINARY_DIR}/llama
)

add_library(mindanchor_llama SHARED mindanchor_llama.cpp)

# The llama target exports its own and ggml's include directories, so
# linking it is also what makes <llama.h> resolvable.
target_link_libraries(mindanchor_llama PRIVATE llama)
