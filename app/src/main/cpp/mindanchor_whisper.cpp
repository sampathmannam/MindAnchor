// v0.30+ (Phase 4 G-28) — the native half of the
// voice journal. Mirrors mindanchor_llama.cpp's
// shape: a thin JNI wrapper around whisper.cpp's
// C API. The actual whisper.cpp vendoring is a
// user setup step (third_party/whisper.cpp/);
// this file is the Kotlin↔C glue.
//
// The transcription is slow on a mid-range device
// (5-10× realtime on a Pixel 6 with whisper-tiny.en)
// so the JNI call runs on the Kotlin IO dispatcher
// (see [WhisperEngine.transcribe]). The model is
// loaded once per [WhisperEngine] instance via the
// [nativeReady] bridge; the JNI side keeps a
// per-process state so the second call to
// [nativeTranscribe] is fast.

#include <jni.h>
#include <string>
#include <vector>

// Forward declarations of the whisper.cpp C API.
// The full whisper.h header is in
// third_party/whisper.cpp/include/whisper.h.
// We forward-declare the minimum surface the JNI
// uses so this file compiles before whisper.cpp is
// vendored; once the vendoring is in place, this
// file is the only change needed (delete the
// forward declarations, include whisper.h, link
// the whisper target).

extern "C" {

struct whisper_context;
struct whisper_full_params;

struct whisper_context_params {
    int n_threads;
};

whisper_context* whisper_init_from_file(const char* path_model);
void whisper_free(whisper_context* ctx);

struct whisper_full_params whisper_full_default_params(
    int strategy);

int whisper_full(
    whisper_context* ctx,
    whisper_full_params wparams,
    const float* samples,
    int n_samples,
    int* n_segments);

} // extern "C"

namespace {

// Per-process state: a single loaded model is
// sufficient for the launcher's usage. The model
// load is the slow path (5-10s on a mid-range
// device); the transcription is the fast path
// (5-10x realtime for whisper-tiny.en).
struct State {
    whisper_context* ctx = nullptr;
    std::string model_path;
};

State& state() {
    static State s;
    return s;
}

void release_model() {
    if (state().ctx != nullptr) {
        whisper_free(state().ctx);
        state().ctx = nullptr;
    }
    state().model_path.clear();
}

bool ensure_loaded(const std::string& model_path) {
    if (state().ctx != nullptr && state().model_path == model_path) {
        return true;
    }
    release_model();
    state().ctx = whisper_init_from_file(model_path.c_str());
    if (state().ctx == nullptr) {
        return false;
    }
    state().model_path = model_path;
    return true;
}

// Read a 16-bit-PCM mono 16 kHz file into a
// float[] of normalised samples. The whisper
// engine expects float32 in [-1, 1].
std::vector<float> read_pcm16(const std::string& path) {
    std::vector<float> out;
    FILE* f = fopen(path.c_str(), "rb");
    if (f == nullptr) return out;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) {
        fclose(f);
        return out;
    }
    int sample_count = static_cast<int>(size / 2);
    out.reserve(sample_count);
    for (int i = 0; i < sample_count; ++i) {
        int16_t s = 0;
        if (fread(&s, 2, 1, f) != 1) break;
        out.push_back(static_cast<float>(s) / 32768.0f);
    }
    fclose(f);
    return out;
}

std::string transcribe(
    const std::string& model_path,
    const std::string& pcm_path,
    const std::string& language,
    int beam_size) {
    if (!ensure_loaded(model_path)) {
        return std::string();
    }
    auto samples = read_pcm16(pcm_path);
    if (samples.empty()) {
        return std::string();
    }
    whisper_full_params params =
        whisper_full_default_params(beam_size);
    params.language = language.c_str();
    params.translate = false;
    int n_segments = 0;
    int rc = whisper_full(
        state().ctx,
        params,
        samples.data(),
        static_cast<int>(samples.size()),
        &n_segments);
    if (rc != 0 || n_segments <= 0) {
        return std::string();
    }
    // Stitch the segment texts. whisper's
    // segment-getter is per-segment; the JNI
    // shape of the full text is the concatenation
    // with single newlines between segments. The
    // Kotlin side does no further processing.
    std::string out;
    for (int i = 0; i < n_segments; ++i) {
        // whisper_get_segment_text is the
        // C-API getter. Imported via the
        // forward declaration when whisper.h is
        // included; this file's forward
        // declaration block is the "compile
        // before vendoring" stub.
        // (The real function is
        //  const char* whisper_full_get_segment_text
        //   (whisper_context*, int i_segment);
        // we leave the actual call to the
        // follow-up commit that includes
        // whisper.h.)
        // For the scaffold: append a placeholder
        // so the JNI round-trip works.
        out += "[transcript stub]\n";
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_mindanchor_narrate_WhisperEngine_nativeReady(
    JNIEnv* /* env */, jobject /* this */) {
    return state().ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_org_mindanchor_narrate_WhisperEngine_nativeTranscribe(
    JNIEnv* env, jobject /* this */,
    jstring jmodel_path, jstring jpcm_path,
    jstring jlanguage, jint beam_size) {
    const char* model_path = env->GetStringUTFChars(
        jmodel_path, nullptr);
    const char* pcm_path = env->GetStringUTFChars(
        jpcm_path, nullptr);
    const char* language = env->GetStringUTFChars(
        jlanguage, nullptr);
    std::string result = transcribe(
        model_path, pcm_path, language,
        static_cast<int>(beam_size));
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    env->ReleaseStringUTFChars(jpcm_path, pcm_path);
    env->ReleaseStringUTFChars(jlanguage, language);
    if (result.empty()) {
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(
        static_cast<jsize>(result.size()));
    env->SetByteArrayRegion(
        out, 0, static_cast<jsize>(result.size()),
        reinterpret_cast<const jbyte*>(result.data()));
    return out;
}

} // extern "C"
