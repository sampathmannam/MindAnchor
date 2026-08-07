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

namespace {

// One backend init for the process. llama_backend_init is cheap and
// idempotent-by-design here: this flag just keeps the intent obvious.
bool backend_ready = false;

void ensure_backend() {
    if (!backend_ready) {
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
        return nullptr;
    }

    // Load the model for this one generation and free it before
    // returning. One paragraph a night does not justify holding
    // gigabytes mapped between runs, and ModelSlot budgeted the phone on
    // the assumption that this process lets go.
    llama_model_params model_params = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (model == nullptr) return nullptr;

    const llama_vocab *vocab = llama_model_get_vocab(model);
    if (vocab == nullptr) {
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
        llama_model_free(model);
        return nullptr;
    }
    tokens.resize(static_cast<size_t>(n_tokens));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(context_tokens);
    ctx_params.n_batch = static_cast<uint32_t>(n_tokens);
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return nullptr;
    }

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
            failed = true;
            break;
        }
        llama_token next = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;

        char piece[256];
        int32_t piece_len = llama_token_to_piece(
            vocab, next, piece, sizeof(piece), /*lstrip=*/0, /*special=*/false);
        if (piece_len < 0) {
            failed = true;
            break;
        }
        output.append(piece, static_cast<size_t>(piece_len));

        batch = llama_batch_get_one(&next, 1);
    }

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);

    if (failed || output.empty()) return nullptr;
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
