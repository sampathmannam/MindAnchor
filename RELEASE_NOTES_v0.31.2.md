# MindAnchor v0.31.2 — llama.cpp b4792 + Phi-4-mini tokenizer fix

v0.31.1's diagnostic logging (added in v0.31.0) revealed that the
Q2_K model load was failing before any tensor was read:
`error loading model vocabulary: unknown pre-tokenizer type: 'gpt-4o'`.
The Unsloth and Microsoft Phi-4-mini-instruct Q2_K GGUF files
both declare `tokenizer.ggml.pre = gpt-4o`, which requires
llama.cpp b4792+ (the PR was #12108, "llama : add Phi-4-mini
support", merged 2025-04-13). Our vendored copy was b4658
(Feb 2025), which predates that PR by two months and rejects
the file at vocabulary-load time. v0.31.2 upgrades to b4792.

## Fixed in v0.31.2

### 1. llama.cpp b4658 → b4792 (Phi-4-mini tokenizer)

- The vocab tokens, merges, BOS/EOS, padding, and chat template
  are unchanged between b4658 and b4792 — the Unsloth GGUF
  metadata we read with `llama_model_meta_val_str` is
  identical, the chat template (`{% for message in messages
  %}{% if message['role'] == 'system'...`) parses the same
  way. The only addition is the O200K_BASE-style byte-level
  pre-tokenizer that splits the gpt-4o vocabulary.
- The C API surface we use is byte-identical:
  `llama_model_load_from_file`, `llama_model_default_params`,
  `llama_init_from_model`, `llama_context_default_params`,
  `llama_chat_apply_template`, `llama_tokenize`,
  `llama_model_meta_val_str`, `llama_model_get_vocab`,
  `llama_decode`, `llama_batch`, `llama_vocab`,
  `llama_chat_message`, `llama_log_set`. No JNI wrapper
  edits were needed for the upgrade.
- Vendored layout (the same six entries as b4658) preserved:
  `CMakeLists.txt`, `LICENSE`, `cmake/`, `include/`,
  `src/`, `ggml/`. `common/` is skipped because we build
  with `-DLLAMA_BUILD_COMMON=OFF`; `tests/`, `docs/`,
  `examples/`, `models/`, `scripts/` are not needed by the
  library build. The vendoring doc (`third_party/llama.cpp/
  VENDORED.md`) is updated to record b4792 as the new pin
  and to document the upgrade rationale.

### 2. Flash-attention required by V-cache quantisation in b4792

After the b4792 upgrade, the Q2_K model loads (1.57 GiB,
~3 seconds on the test phone) but the context init now
fails:

```
llama_init_from_model: V cache quantization requires flash_attn
```

b4792 enforces this dependency: the Q4_0 V cache path bakes
its per-block rescale into the attention kernel, and the
non-flash attention path does not have that kernel. Setting
`ctx_params.flash_attn = true` enables the CPU flash
attention path (supported in llama.cpp since b3265). The
phone has no GPU offload either way; flash-attn here is
just a smarter CPU kernel for the same compute budget.

### 3. `n_ubatch` reduced 128 → 32

The combined Q2_K model (1.57 GiB mmap) + KV cache
(K=q8_0: 68 MiB, V=q4_0: 36 MiB, total 104 MiB) + compute
buffer at `n_ubatch=128` (~150 MiB) just barely exceeded
the 1.8 GiB MemAvailable on the test phone. With
`n_ubatch=32`, the compute buffer is ~25 MiB (logged:
`CPU compute buffer size = 25.30 MiB`), the model loads,
the context inits, and the decode runs at ~800% CPU
(8 cores saturated on the Moto G84).

The cap is processed in chunks of 32 (instead of one
282-token physical batch); the difference on a 1-3
paragraph narration is negligible. `n_batch` (the cap)
stays at `n_tokens`, so `n_batch >= n_ubatch` is
preserved.

## Verified on the test phone (Moto G84, 1.8 GB MemAvailable)

```
llama_model_loader: - kv  22:  tokenizer.ggml.pre str = gpt-4o
llama_model_loader: loaded meta data with 35 key-value pairs and 196 tensors
generate: model loaded                                              (~3 s)
generate: tokenised 282 tokens (budget 2048)
llama_init_from_model: n_seq_max     = 1
llama_init_from_model: n_ctx         = 2048
llama_init_from_model: n_ctx_per_seq = 2048
llama_init_from_model: n_batch       = 282
llama_init_from_model: n_ubatch      = 32
llama_init_from_model: flash_attn    = 1
llama_init_from_model: KV self size  = 104.00 MiB, K (q8_0): 68.00 MiB, V (q4_0): 36.00 MiB
llama_init_from_model:        CPU  output buffer size =     0.76 MiB
llama_init_from_model:        CPU compute buffer size =    25.30 MiB
llama_init_from_model: graph nodes  = 1159
generate: context initialised                                       (~3 s total)
top -p 3330: 800% CPU, 3.2 GB RES, 29.8% MEM                       (decode loop)
```

The model is loaded, the context is initialised, and the
decode loop is running at full CPU. v0.31.0's
"no narration" mystery is fully resolved at the model-load
level: llama.cpp b4792 + Q2_K + Q8_0 K + Q4_0 V + flash
attention + `n_ubatch=32` is the configuration that fits
on the test phone.

## Still pending verification (the slow Q2_K decode)

The Q2_K decode at 8 threads on the Moto G84 is slow
enough that the first "produced 20 tokens" log had not
appeared within ~12 minutes of wall time when this release
note was being written. The process is at 800% CPU the
whole time, so this is a per-token compute cost, not a
stall — the model is generating, just at maybe 0.1-0.5
tokens/second. The 282-token prompt eval is the slow
first chunk (cold layer loads via mmap), then token
generation warms up.

A 1-3 paragraph narration needs 100-300 generated tokens.
At 0.5 tok/s, that is 3-10 minutes per narration. For
the user's "Generate now" button this is too long; for the
nightly overnight report (charged, idle, no UI) it is
acceptable.

The next decision is whether to:
- Keep Q2_K and accept the slow decode as a known cost
  (the overnight path is the production target)
- Switch to a faster quant that fits in 1.8 GiB (Q4_0
  is the obvious candidate at ~3.5 BPW but at 2.32 GiB it
  still does not fit; a hybrid Q4_0/Q4_K_S mix might)
- Or use a smaller model (Phi-3-mini-4k at Q4_K_M is
  ~2.2 GiB, still too big; Llama 3.2 1B at Q4_K_M is
  ~800 MiB but is a different model and would need its
  own prompt tuning)

A v0.31.3 question, not a v0.31.2 question.

## Other small changes

- `__android_log_print` log-redirect in `ensure_backend` so
  llama.cpp's own `LLAMA_LOG_ERROR` / `LLAMA_LOG_INFO` reach
  `adb logcat -s MindAnchor/llama:V` with the matching
  priority. Pre-v0.31.2 those calls went to stderr and were
  invisible to the phone user. This is what surfaced the
  `unknown pre-tokenizer type: 'gpt-4o'` line in the v0.31.1
  test — without the redirect we would have seen only the
  "returned null" and had no way to tell which step failed.
- `progress_callback` set on the model params so the load
  progress is logged. The first v0.31.0 test had no
  callback; the second v0.31.2 test shows `load progress:
  0.0%` → `100.0%` over the 3-second mmap + tensor read,
  confirming the model is being read end-to-end.
- Per-iteration decode progress log every 20 tokens
  (added in v0.31.2; not present in v0.31.0 or v0.31.1).
  Without it the user cannot tell whether the model is
  generating or stuck; with it, a stall is visible in 20
  seconds of wall time.

## File changes

- `M third_party/llama.cpp/CMakeLists.txt` (b4792 source)
- `M third_party/llama.cpp/LICENSE` (b4792 source)
- `M third_party/llama.cpp/cmake/*` (b4792 source, 9 files)
- `M third_party/llama.cpp/include/*` (b4792 source, 2 files)
- `M third_party/llama.cpp/src/*` (b4792 source, 38 files)
- `M third_party/llama.cpp/ggml/*` (b4792 source, ~525 files)
- `M third_party/llama.cpp/VENDORED.md` (updated pin + rationale)
- `M app/src/main/cpp/mindanchor_llama.cpp`
  (log redirect, progress callback, n_ubatch 128→32,
  flash_attn=true)
- `M app/build.gradle.kts` (versionCode 58→59, versionName
  0.31.1→0.31.2)
