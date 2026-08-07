# Vendored: llama.cpp

Snapshot of <https://github.com/ggml-org/llama.cpp> at tag **b4658**,
commit `855cd0734aca26c86cc23d94aefd34f934464ac9` (2025-02-06), MIT
licensed — see `LICENSE` in this directory.

## Why a snapshot, and why this version

A snapshot rather than a submodule so that a checkout of this repository
is the whole build: no second network fetch on every CI run, no workflow
changes, and no dependence on upstream keeping a tag alive. The privacy
review surface also stays flat — everything that compiles into the APK
is in this tree, reviewable at this commit.

b4658 rather than the newest tag because the JNI wrapper in
`app/src/main/cpp/mindanchor_llama.cpp` is written against this
version's C API, and the wrapper was verified against exactly this tree
— compiled and linked on the build host — before either was committed.
Newer tags rename API entry points; an upgrade is a deliberate act, not
a drift.

## What was pruned

Only what the library build needs is kept: `CMakeLists.txt`, `LICENSE`,
`cmake/`, `include/`, `src/`, `ggml/`. Removed relative to upstream:
`examples/`, `tests/`, `docs/`, `common/`, `models/`, `scripts/`,
`gguf-py/`, and every other tool, binding and script. The build passes
`-DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_TESTS=OFF
-DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF` (see
`app/build.gradle.kts`), so nothing references the removed directories.

`LLAMA_CURL=OFF` is non-negotiable: this app's privacy promise is that
no path to the network exists anywhere in it, native code included.

## Upgrading

1. Clone the new tag; check out its commit.
2. Copy the same six entries over this directory.
3. Update this file's tag, commit and date.
4. Re-verify the JNI wrapper compiles and links against the new tree on
   the build host before pushing — the API renames between versions are
   real, and CI is slower at finding them than a local compiler is.
