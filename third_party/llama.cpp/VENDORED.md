# Vendored: llama.cpp

Snapshot of <https://github.com/ggml-org/llama.cpp> at tag **b4792**,
commit `c43a3e7` (2025-04-13), MIT licensed — see `LICENSE` in this
directory.

## Why a snapshot, and why this version

A snapshot rather than a submodule so that a checkout of this repository
is the whole build: no second network fetch on every CI run, no workflow
changes, and no dependence on upstream keeping a tag alive. The privacy
review surface also stays flat — everything that compiles into the APK
is in this tree, reviewable at this commit.

**b4792** rather than the newest tag because the JNI wrapper in
`app/src/main/cpp/mindanchor_llama.cpp` is written against this
version's C API, and the wrapper was verified against exactly this tree
— compiled and linked on the build host — before either was committed.
Newer tags rename API entry points; an upgrade is a deliberate act, not
a drift.

**Why this upgrade from b4658**: b4658 (Feb 2025) predates PR #12108
(2025-04-13, "llama: add Phi-4-mini support (supersede #12099)"). Phi-4-
mini's GGUF declares its pre-tokenizer as `gpt-4o`, and the O200K_BASE
splitter that goes with it. b4658's vocabulary loader only knows
`gpt2` / `llama3` / `starcoder` and rejects the model with
`error loading model vocabulary: unknown pre-tokenizer type: 'gpt-4o'`.
b4792 is the first tag that recognises the Phi-4-mini tokenizer.

The C API surface used by `mindanchor_llama.cpp`
(`llama_model_load_from_file`, `llama_model_default_params`,
`llama_init_from_model`, `llama_context_default_params`,
`llama_chat_apply_template`, `llama_tokenize`,
`llama_model_meta_val_str`, `llama_model_get_vocab`,
`llama_decode`, `llama_batch`, `llama_vocab`, `llama_chat_message`,
`llama_log_set`) is byte-identical between b4658 and b4792 — the
upgrade is a vendoring-only change, no JNI wrapper edits were needed.

## What was pruned

Only what the library build needs is kept: `CMakeLists.txt`, `LICENSE`,
`cmake/`, `include/`, `src/`, `ggml/`. Removed relative to upstream:
`common/` (we build with `-DLLAMA_BUILD_COMMON=OFF` and our JNI does
not include any `common/` headers), `tests/`, `docs/`,
`examples/`, `models/`, `scripts/`, `gguf-py/`, and every other tool,
binding and script. The build passes
`-DLLAMA_BUILD_COMMON=OFF -DLLAMA_BUILD_TESTS=OFF
-DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF` (see
`app/build.gradle.kts`), so nothing references the removed directories.

`LLAMA_CURL=OFF` is non-negotiable: this app's privacy promise is that
no path to the network exists anywhere in it, native code included.

## Upgrading

1. Clone the new tag; check out its commit.
2. Copy the same six entries over this directory (`CMakeLists.txt`,
   `LICENSE`, `cmake/`, `include/`, `src/`, `ggml/`).
3. Update this file's tag, commit and date.
4. Re-verify the JNI wrapper compiles and links against the new tree on
   the build host before pushing — the API renames between versions are
   real, and CI is slower at finding them than a local compiler is.
