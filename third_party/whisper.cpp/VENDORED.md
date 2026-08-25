# Vendored: whisper.cpp

Snapshot of <https://github.com/ggml-org/whisper.cpp> at tag **b4938**,
commit `371b5a7561823ab2bb32142d2751e35e7534727b` (2026-08-20), MIT-licensed — see
`LICENSE` in this directory.

## Why a snapshot, and why this version

A snapshot rather than a submodule so that a checkout of this repository
is the whole build: no second network fetch on every CI run, no workflow
changes, and no dependence on upstream keeping a tag alive. The privacy
review surface also stays flat — everything that compiles into the APK
is in this tree, reviewable at this commit.

b4938 rather than the newest tag because the JNI wrapper in
`app/src/main/cpp/mindanchor_whisper.cpp` is written against this
version's C API, and the wrapper was verified against exactly this tree
— compiled and linked on the build host — before either was committed.
Newer tags rename API entry points; an upgrade is a deliberate act, not
a drift.

## What was pruned

Only what the library build needs is kept: `CMakeLists.txt`, `LICENSE`,
`include/`, `src/`, `ggml/`. Removed relative to upstream: `examples/`,
`tests/`, `docs/`, `bindings/`, `bindings/javascript`, `models/`, `samples/`,
`scripts/`, `grammars/`, `media/`, `ci/`, `cmake/`, `pi/`, `gguf-py/`,
`build-xcframework.sh`, `Makefile`, `AGENTS.md`, `AUTHORS`, `CMakePresets.json`,
`CONTRIBUTING.md`, `README.md`, `README_sycl.md`, `.devops/`, `.github/`,
`.dockerignore`, `.gitignore`, and every other tool, binding and script.
The build passes `-DWHISPER_BUILD_TESTS=OFF
-DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF` (see
`app/build.gradle.kts`), so nothing references the removed directories.

`WHISPER_CURL=OFF` is non-negotiable: this app's privacy promise is
that no path to the network exists anywhere in it, native code included.

## Vendoring two ggml trees

whisper.cpp vendors its own `ggml/` (this directory's `ggml/`).
llama.cpp vendors its own `ggml/` (`third_party/llama.cpp/ggml/`). The
two ggml trees have **diverged upstream** — different APIs, different
headers, different allocator behavior. This project keeps them as two
independent trees: `add_subdirectory(third_party/whisper.cpp)` and
`add_subdirectory(third_party/llama.cpp)` are called separately, and the
JNI shared libraries (`mindanchor_whisper.so`, `mindanchor_llama.so`) each
statically link their own ggml.

The duplication is intentional and the safe choice. Trying to de-duplicate
ggml between llama.cpp and whisper.cpp would be a version-mismatch bug
waiting to happen: whisper.cpp uses ggml's streaming API for the
audio encoder while llama.cpp uses ggml's tensor-graph API for the
transformer; the two ggml trees do not share an ABI. See
[third_party/llama.cpp/VENDORED.md](../llama.cpp/VENDORED.md) for the
parallel llama.cpp note.

## Upgrading

1. Clone the new tag; check out its commit.
2. Copy the same five entries over this directory
   (`CMakeLists.txt`, `LICENSE`, `include/`, `src/`, `ggml/`).
3. Update this file's tag, commit and date.
4. Re-verify the JNI wrapper compiles and links against the new tree on the
   build host before pushing — the API renames between versions are
   real, and CI is slower at finding them than a local compiler is.
5. If the new whisper.cpp has a different `whisper_full_default_params`
   signature, update `mindanchor_whisper.cpp`'s call site; the wrapper
   has only a handful of call sites.

## What depends on the API shape here

- `app/src/main/cpp/mindanchor_whisper.cpp` calls:
  - `whisper_init_from_file_with_params`
  - `whisper_context_default_params`
  - `whisper_full`
  - `whisper_full_get_segment_text`
  - `whisper_full_default_params`
  - `whisper_free`
  - `whisper_context_params`
  - `whisper_sampling_strategy` (enum)

  The JNI file's `init_backend_if_needed()` function tracks the
  `backend_ok` flag; on a fresh process it is false, and
  `nativeReady()` returns false. The first successful `nativeTranscribe`
  call sets the flag to true (the ggml backend is initialised by
  `whisper_init_from_file_with_params`).
- `app/src/main/java/org/mindanchor/narrate/WhisperEngine.kt`
  declares the JNI signatures and reads / writes the model file at
  `Whisper.MODEL_PATH`.
- `app/src/main/java/org/mindanchor/narrate/Whisper.kt` (the
  public API) reads the model from disk, writes the PCM to a temp file,
  and calls `engine.nativeTranscribe(...)`. The model file is not in
  the repo (75 MB); the user is on the hook for `Whisper.MODEL_PATH`.

## CI guard

`app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest`
scans every `.kt` file for outbound-network primitives
(`java.net.*`, `okhttp3.*`, `java.net.http.*`, etc.). The new files in
this directory — `app/src/main/cpp/mindanchor_whisper.cpp` (C++) and
`app/src/main/java/org/mindanchor/narrate/WhisperEngine.kt` (Kotlin) — are
not scanned by that test, but the test does scan every `.kt` file under
`app/src/main/java/org/mindanchor/narrate/`. There are zero outbound-network
references there; the whisper.cpp JNI is purely a local inference path.
The two related `okhttp3.*` references in this directory's adjacent
files (in `llm/`) are exempted by the `llmBridgeFiles` set in the
`NetworkCallsForbiddenTest` allowlist.
