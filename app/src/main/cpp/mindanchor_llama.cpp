// The one place this app runs a language model.
//
// Everything above this layer is deliberately ignorant of llama.cpp:
// Kotlin hands over a system instruction, a prompt, a context size and a
// seed, and gets back raw text or nothing. Prompt construction lives in
// Prompting and judgement of the output in NarrationGuard, both on the
// Kotlin side and both already tested — this file must stay too dumb to
// undermine them.
//
// Every failure path returns null rather than throwing across JNI. The
// caller treats null as "no paragraph tonight", which is this app's
// ordinary good outcome, and a 3am background crash in native code is
// indistinguishable from a silently broken feature forever after.

#include <jni.h>

#include <string>
#include <vector>

#include "llama.h"

#include <android/log.h>

// v0.31.0: diagnostic logging. The whole native layer is
// deliberately silent on failure (every error returns
// null, never throws) — see the file KDoc. The v0.30.0
// phone E2E test surfaced a case where the report's
// narration was always null and we could not tell which
// of the eleven "return null" paths was the cause. The
// phone user does not have a developer console, and
// `adb logcat` is the only realistic diagnostic surface.
// These tags are not stripped from release builds
// because they go through __android_log_print, which
// costs nothing at runtime and is what Logcat will see
// when an end user runs `adb logcat -s MindAnchor/llama:V`
// in support.
#define LOG_TAG "MindAnchor/llama"
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// One backend init for the process. llama_backend_init is cheap and
// idempotent-by-design here: this flag just keeps the intent obvious.
bool backend_ready = false;

// v0.31.1: log redirect. llama.cpp's own LLAMA_LOG_ERROR /
// LLAMA_LOG_INFO go to stderr by default — they never reach
// logcat with the MindAnchor/llama tag, so a load failure
// that prints "error loading model architecture: …" to
// stderr is invisible to the user (or to the engineer
// reading `adb logcat -s MindAnchor/llama:V`). Plug a
// custom log callback that mirrors everything to logcat at
// the matching priority. The level mapping is ggml's:
//   LLAMA_LOG_LEVEL_ERROR / WARN / INFO / DEBUG. We map
//   DEBUG to INFO so a debug build does not flood the
//   buffer; the user-facing level on the Android side
//   does not change.
static void llama_log_redirect(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    int android_level;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: android_level = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN:  android_level = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_INFO:  android_level = ANDROID_LOG_INFO;  break;
        case GGML_LOG_LEVEL_DEBUG: android_level = ANDROID_LOG_INFO;  break;
        default:                   android_level = ANDROID_LOG_INFO;  break;
    }
    __android_log_write(android_level, "MindAnchor/llama", text);
}

void ensure_backend() {
    if (!backend_ready) {
        llama_log_set(llama_log_redirect, nullptr);
        llama_backend_init();
        backend_ready = true;
    }
}

std::string from_jstring(JNIEnv *env, jstring value) {
    if (value == nullptr) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return "";
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

// The model's own chat template, read straight from its metadata. A
// model without one gets a plain two-part text prompt instead — worse
// prose, honest fallback, and NarrationGuard judges the result either
// way.
std::string render_prompt(const llama_model *model,
                          const std::string &system,
                          const std::string &user) {
    std::string tmpl;
    int32_t needed = llama_model_meta_val_str(model, "tokenizer.chat_template", nullptr, 0);
    if (needed > 0) {
        tmpl.resize(static_cast<size_t>(needed) + 1);
        int32_t written = llama_model_meta_val_str(
            model, "tokenizer.chat_template", tmpl.data(), tmpl.size());
        if (written > 0) {
            tmpl.resize(static_cast<size_t>(written));
        } else {
            tmpl.clear();
        }
    }

    if (!tmpl.empty()) {
        const llama_chat_message messages[2] = {
            {"system", system.c_str()},
            {"user", user.c_str()},
        };
        std::vector<char> buf(system.size() + user.size() + tmpl.size() + 1024);
        int32_t n = llama_chat_apply_template(
            tmpl.c_str(), messages, 2, /*add_ass=*/true, buf.data(),
            static_cast<int32_t>(buf.size()));
        if (n > static_cast<int32_t>(buf.size())) {
            buf.resize(static_cast<size_t>(n));
            n = llama_chat_apply_template(
                tmpl.c_str(), messages, 2, true, buf.data(),
                static_cast<int32_t>(buf.size()));
        }
        if (n > 0) return std::string(buf.data(), static_cast<size_t>(n));
    }

    return system + "\n\n" + user + "\n";
}

}  // namespace

// Returns the model's output as raw UTF-8 bytes rather than a jstring.
// NewStringUTF expects Modified UTF-8 and aborts under CheckJNI on the
// standard 4-byte sequences a model can emit — any emoji would crash a
// debug build. Kotlin decodes the bytes with the real UTF-8 charset,
// which replaces anything malformed instead of crashing on it.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_mindanchor_narrate_LlamaEngine_nativeGenerate(
    JNIEnv *env, jobject /*thiz*/, jstring jmodel_path, jstring jsystem,
    jstring jprompt, jint context_tokens, jint max_new_tokens, jlong seed,
    jint threads) {
    ensure_backend();

    const std::string model_path = from_jstring(env, jmodel_path);
    const std::string system = from_jstring(env, jsystem);
    const std::string user = from_jstring(env, jprompt);
    if (model_path.empty() || context_tokens <= 0 || max_new_tokens <= 0) {
        ALOGW("generate refused: empty path or non-positive budget (path=%s ctx=%d max=%d)",
              model_path.empty() ? "<empty>" : model_path.c_str(), context_tokens, max_new_tokens);
        return nullptr;
    }
    ALOGI("generate: model=%s ctx=%d max_new=%d threads=%d prompt_chars=%zu",
          model_path.c_str(), context_tokens, max_new_tokens, threads, user.size());

    // Load the model for this one generation and free it before
    // returning. One paragraph a night does not justify holding
    // gigabytes mapped between runs, and ModelSlot budgeted the phone on
    // the assumption that this process lets go.
    llama_model_params model_params = llama_model_default_params();
    // v0.31.1 diagnostic: progress callback. llama_model_load_from_file
    // returns null silently with no way to know which step failed
    // (file open, mmap, header parse, tensor read, tensor upload). The
    // progress callback fires during tensor load with values 0.0-1.0;
    // logging each step tells us whether the load is reaching the
    // weights (and failing on a particular one) or failing before any
    // tensor is read (file format / mmap / header). The callback
    // returns true to continue loading, so it is non-invasive.
    model_params.progress_callback = [](float progress, void * /*user_data*/) -> bool {
        ALOGI("load progress: %.1f%%", progress * 100.0f);
        return true;
    };
    ALOGI("generate: about to call llama_model_load_from_file (mmap=%d mlock=%d n_gpu=%d)",
          model_params.use_mmap ? 1 : 0,
          model_params.use_mlock ? 1 : 0,
          model_params.n_gpu_layers);
    llama_model *model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model == nullptr) {
        ALOGE("generate: llama_model_load_from_file returned null for %s", model_path.c_str());
        return nullptr;
    }
    ALOGI("generate: model loaded");

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (vocab == nullptr) {
        ALOGE("generate: llama_model_get_vocab returned null");
        llama_model_free(model);
        return nullptr;
    }

    const std::string rendered = render_prompt(model, system, user);

    // Tokenise with room checked up front: a prompt that does not fit
    // the context alongside its answer is a reason to stay silent, not
    // to truncate the instructions and generate from half of them.
    std::vector<llama_token> tokens(rendered.size() + 16);
    int32_t n_tokens = llama_tokenize(
        vocab, rendered.c_str(), static_cast<int32_t>(rendered.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()),
        /*add_special=*/true, /*parse_special=*/true);
    if (n_tokens < 0) {
        tokens.resize(static_cast<size_t>(-n_tokens));
        n_tokens = llama_tokenize(
            vocab, rendered.c_str(), static_cast<int32_t>(rendered.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    if (n_tokens <= 0 || n_tokens + max_new_tokens > context_tokens) {
        ALOGW("generate: prompt did not fit (n_tokens=%d max_new=%d context=%d)",
              n_tokens, max_new_tokens, context_tokens);
        llama_model_free(model);
        return nullptr;
    }
    ALOGI("generate: tokenised %d tokens (budget %d)", n_tokens, context_tokens);
    tokens.resize(static_cast<size_t>(n_tokens));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(context_tokens);
    ctx_params.n_batch = static_cast<uint32_t>(n_tokens);
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    // v0.31.1: KV cache quantisation. The Phi-4-mini Q4_K_M
    // model is 2.32 GB on disk; on a phone with 1.5-2 GB free
    // RAM, the F16 KV cache (256 MB at n_ctx=2048) is the
    // biggest single allocation that pushes the load over
    // the line. llama.cpp supports per-tensor K and V
    // quantisation since b2366, and the recommended
    // production split is K=Q8_0, V=Q4_0: K is more
    // attention-sensitive, V is more tolerant of
    // quantisation error. At n_ctx=2048 this drops the
    // cache from 256 MB to ~104 MB — a 152 MB saving,
    // which is the order of magnitude the 1.8 GB-available
    // Moto G84 needs. Both types are unconditional in
    // ggml (no extra build flag), the fields are marked
    // [EXPERIMENTAL] in llama.h but stable since 2024,
    // and this is the same split `llama-cli` ships as
    // its default for short-context inference.
    ctx_params.type_k = GGML_TYPE_Q8_0;
    ctx_params.type_v = GGML_TYPE_Q4_0;
    // v0.31.2: V cache quantisation needs flash attention in
    // b4792+. The Q4_0 V cache path bakes its rescale into the
    // attention kernel, and llama.cpp's non-flash path does not
    // have that kernel. flash_attn=true here is a CPU flash
    // path (CPU backend supports it since b3265) — no GPU
    // offload, no Metal/Vulkan, just the same compute budget
    // with a smarter attention kernel. Marked [EXPERIMENTAL]
    // in llama.h but stable enough for llama-cli's default.
    ctx_params.flash_attn = true;
    // v0.31.1: no-GPU phone. The phone has no real GPU
    // backend; offload_kqv=true is the llama.cpp default
    // and a no-op without a GPU, but it adds a small
    // bookkeeping branch in llama_kv_cache_init that we
    // don't need. Set it false explicitly so a future
    // llama.cpp change in the offload path can't
    // accidentally cost us a frame.
    ctx_params.offload_kqv = false;
    // v0.31.1: tighter physical-batch cap. The default is
    // 512 (llama.cpp:9336); our prompts are ~500 tokens,
    // processed in one physical batch of 500 anyway.
    // Setting n_ubatch = 128 reduces the peak compute
    // scratch by roughly 4× without affecting decode
    // (which is 1 token at a time, n_ubatch-irrelevant).
    // n_batch (the cap) stays at n_tokens as before, so
    // n_batch >= n_ubatch is preserved.
    //
    // v0.31.2 (b4792 upgrade + Q2_K + phone with 1.8 GB
    // available): n_ubatch=128 still left the context
    // init failing on the test device — the compute
    // buffer at 128 is large enough that it pushed total
    // RSS over the 1.8 GB ceiling. n_ubatch=32 cuts the
    // compute scratch another 4× and the context inits
    // cleanly. The cap is processed in chunks of 32
    // (instead of one 282-token physical batch); the
    // difference on a 1-3 paragraph narration is
    // negligible. The model still generates the full
    // response — n_ubatch only bounds the *peak* scratch,
    // not the number of tokens decoded.
    ctx_params.n_ubatch = 32;
    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        ALOGE("generate: llama_init_from_model returned null");
        llama_model_free(model);
        return nullptr;
    }
    ALOGI("generate: context initialised");

    // Modest temperature under a fixed, caller-supplied seed: the same
    // report must produce the same paragraph, for the same reason
    // LinkFinder derives rather than draws its seeds — an app that says
    // something different each time it is asked the same question looks
    // like it knows something new, and it does not.
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(static_cast<uint32_t>(seed)));

    std::string output;
    bool failed = false;
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    for (int produced = 0; produced < max_new_tokens; produced++) {
        if (llama_decode(ctx, batch) != 0) {
            ALOGE("generate: llama_decode failed at token %d", produced);
            failed = true;
            break;
        }
        llama_token next = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) {
            ALOGI("generate: EOG after %d tokens", produced);
            break;
        }

        char piece[256];
        int32_t piece_len = llama_token_to_piece(
            vocab, next, piece, sizeof(piece), /*lstrip=*/0, /*special=*/false);
        if (piece_len < 0) {
            ALOGE("generate: llama_token_to_piece failed at token %d", produced);
            failed = true;
            break;
        }
        output.append(piece, static_cast<size_t>(piece_len));

        // v0.31.2: progress log every 20 tokens. The Q2_K
        // decode is ~1 tok/s on a phone, so 600 tokens
        // takes 10 minutes. The decode loop had no
        // progress indicator before, and the user cannot
        // tell whether the model is generating or stuck
        // — the difference is real when the OS background-
        // limits the process after a few minutes and the
        // decode throttles to 0.1 tok/s. A line every 20
        // tokens is one per ~20 seconds, cheap.
        if ((produced + 1) % 20 == 0) {
            ALOGI("generate: produced %d tokens, %zu chars so far",
                  produced + 1, output.size());
        }

        batch = llama_batch_get_one(&next, 1);
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);

    if (failed) {
        ALOGE("generate: failed mid-stream, output=%zu chars", output.size());
        return nullptr;
    }
    if (output.empty()) {
        ALOGW("generate: empty output (model produced zero tokens before EOG)");
        return nullptr;
    }
    ALOGI("generate: produced %zu chars", output.size());
    jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.size()),
                            reinterpret_cast<const jbyte *>(output.data()));
    return result;
}

// A build-time truth the instrumented tests can ask for: does the
// library load and do its symbols resolve on this device? Costs nothing
// at runtime and turns "the engine exists" from a claim into a check.
extern "C" JNIEXPORT jboolean JNICALL
Java_org_mindanchor_narrate_LlamaEngine_nativeReady(JNIEnv * /*env*/, jobject /*thiz*/) {
    ensure_backend();
    return JNI_TRUE;
}
