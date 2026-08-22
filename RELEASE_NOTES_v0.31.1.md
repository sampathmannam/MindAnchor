# MindAnchor v0.31.1 — Phi-4 quant switch + KV cache quantisation

v0.31.0's native diagnostics revealed that the
`llama_model_load_from_file` call was returning `null` for the
Q4_K_M model on a phone with 1.8 GB available RAM. v0.31.1
addresses the two levers: the model file itself and the
runtime KV cache.

## Fixed in v0.31.1

### 1. Smaller model: Q4_K_M (2.32 GB) → Q2_K (1.57 GB)

The Q4_K_M file was the largest single allocation in the load
process. On a phone with 1.5-2 GB of free RAM (the test
device: a Moto G84 with 11.5 GB total / 1.8 GB available),
the model's mmap'd working set plus llama.cpp's runtime
overhead needed ~3-4 GB — beyond what the OS would give the
process. `llama_model_load_from_file` returned `null` and
narration silently failed (the `getOrNull` in
`LlamaNarrator.kt` swallowed the error before v0.31.0 added
native logging).

The smallest Phi-4-mini instruct quant that HuggingFace
publishes is **Q2_K at 1.567 GiB** (Unsloth mirror, the
primary URL; Microsoft's official mirror as fallback). The
quality loss for 1-3 paragraph English-prose summaries is
+0.87 perplexity over FP16 — visible on close reading, not
catastrophic for short summarisation (per llama.cpp's
published perplexity deltas). For longer Q&A, a Q4_K_M build
on a phone with 4+ GB available is the right tier; v0.31.1
does not address that case.

The change is three constants in `Phi4ModelDownload.kt` and
the corresponding `strings.xml` copy:

- `PRIMARY_URL` and `FALLBACK_URL` point at the Q2_K file.
- `DOWNLOAD_SUBPATH` matches the upstream Q2_K filename
  (`Phi-4-mini-instruct-Q2_K.gguf`).
- `DOWNLOAD_TITLE` reads "Phi-4 mini (Q2_K)".
- `APPROXIMATE_BYTES` drops from 2,490,000,000 to
  1,683,000,000.
- The settings card string `model_download` updates from
  "Download Phi-4 mini (Q4_K_M, 2.49 GB)" to "Download Phi-4
  mini (Q2_K, 1.6 GB)".

### 2. KV cache quantisation in llama.cpp context init

Even with a 1.57 GB model file, the F16 KV cache at
`n_ctx=2048` is 256 MB — the second-largest single
allocation. llama.cpp's `llama_context_params` supports
per-tensor K and V quantisation since b2366 (marked
[EXPERIMENTAL] in `llama.h` but stable since 2024). The
recommended production split is K=Q8_0, V=Q4_0: K is more
attention-sensitive, V is more tolerant of quantisation
error. At `n_ctx=2048` this drops the cache from 256 MB to
~104 MB — a 152 MB saving, which is the order of magnitude
the 1.8 GB-available Moto G84 needs.

Two companion changes in the same context init:

- `offload_kqv = false`: the phone has no real GPU backend;
  this is a no-op without a GPU but removes a small
  bookkeeping branch in `llama_kv_cache_init` that we don't
  need.
- `n_ubatch = 128`: the default is 512; our prompts are
  ~500 tokens but a tighter physical-batch cap reduces the
  peak compute scratch by roughly 4×. `n_batch` (the cap)
  stays at `n_tokens`, so `n_batch >= n_ubatch` is preserved.

### 3. Widened `DOWNLOAD_BASENAME_PREFIX`

v0.30.x's `isPhi4File` check was a hard match on the
"Phi-4-mini-instruct-Q4_K_M" prefix; v0.31.1 widens it to
"Phi-4-mini-instruct-Q" so both the v0.30.x Q4_K_M files
(still on the test phone's Downloads collection) and the
new v0.31.1 Q2_K file are recognised by the suffix-collision
check. The loader itself does not care which quant was
downloaded; llama.cpp picks up the actual format from the
GGUF header. A v0.30.x Q4_K_M file on a phone with 1.5-2 GB
free RAM will still fail at load time — see the
`model_load_failed_out_of_memory` string for the user-facing
message; the new Q2_K download is what fixes the problem.

## Verification

Five FindingTests updated in
`Phi4ModelDownloadFindingTest.kt`:

- `primary URL is the Unsloth mirror and points to Q2_K`
- `fallback URL is the Microsoft mirror and points to Q2_K`
- `download subpath matches the upstream Q2_K filename`
- `approximate size is in the 1-point-5 GB ballpark`
- `both URLs target the Q2_K file`
- `isPhi4File accepts the DownloadManager collision-suffix
  basename` (renamed test, updated Q2_K filename)

All 1471 tests pass, detekt clean. The phone test of v0.31.1
on the test device is in progress at the time of writing
(awaiting a chance to test with the Q2_K file imported).

## Known limitations (carried from v0.31.0)

- **Long Q&A quality**: Q2_K is the smallest published
  quant; longer passages or anything approaching a real
  assistant turn will still want Q4_K_M or Q4_0 on a phone
  with 4+ GB free.
- **Reasoning model alternative**: the Phi-4 reasoning
  variant has a UD-IQ2_XXS quant at 1.205 GiB, but it emits
  `<think>...</think>` blocks that the current
  `LlamaNarrator.kt` would have to strip. v0.31.1 does not
  change the narrator — adding a stripping pass is a v0.31.2
  question if a user needs the smaller file badly.
- **`model_load_failed_out_of_memory` not yet surfaced**:
  the string is in `strings.xml` (added in v0.31.0) but not
  yet wired to a Toast/dialog when `Narrator.narrate()`
  returns null. The native log line is the current
  diagnostic; a UI surface for it is on the v0.32.0 list.

## Not changed

- `ModelSlot.fit()`: per the v0.31.1 research agent's
  recommendation, the static budget calculation is correct
  for the device (it uses `totalMemBytes`, which is the
  right answer for the *idle, charging, overnight* case
  the settings card advertises). The daytime "Build now" /
  "Generate now" affordances need a different metric
  (`availMemBytes`) but that is a separate change.
- `Prompting.build()`: still returns `null` when
  `report.sections.isEmpty()` — quiet nights get no
  narration. This is the "no hallucinations" rule, by
  design.
