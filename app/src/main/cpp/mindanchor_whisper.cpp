// v0.30+ (Phase 4 G-28) -- the native half of the
// voice journal. Mirrors mindanchor_llama.cpp's
// shape: a thin JNI wrapper around whisper.cpp's
// C API. The vendoring is at
// third_party/whisper.cpp/ (see VENDORED.md).
//
// The transcription is slow on a mid-range device
// (5-10x realtime on a Pixel 6 with whisper-tiny.en)
// so the JNI call runs on the Kotlin IO dispatcher
// (see [Whisper.transcribe]). The model is loaded
// once per process via the [nativeReady] bridge;
// the JNI side keeps a per-process state so the
// second call to [nativeTranscribe] is fast.

#include <jni.h>
#include <string>
#include <vector>
#include <whisper.h>

namespace {

// Per-process state: a single loaded model is
// sufficient for the launcher's usage. The model
// load is the slow path (5-10s on a mid-range
// device); the transcription is the fast path
// (5-10x realtime for whisper-tiny.en).
struct State {
    whisper_context* ctx = nullptr;
    std::string model_path;
    bool backend_ok = false;
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
    // WHISPER_CONTEXT_PARAMS is filled with defaults; ggml's
    // n_threads is left at 0 (= use hardware-concurrency
    // heuristic). The launcher's KDoc documents the
    // contract "4 threads by default"; the upstream
    // whisper.cpp will pick the right number for the
    // device CPU.
    whisper_context_params cparams = whisper_context_default_params();
    state().ctx = whisper_init_from_file_with_params(
        model_path.c_str(), cparams);
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
    // v0.30+ -- real whisper.cpp call. The
    // sampling strategy is GREEDY when beam_size <= 1
    // (the default), BEAM_SEARCH otherwise. The
    // launcher's KDoc says beam_size is the user-facing
    // parameter; a beam size of 1 = greedy decoding
    // matches the v0.30+ default.
    whisper_full_params wparams = whisper_full_default_params(
        beam_size <= 1 ? WHISPER_SAMPLING_GREEDY : WHISPER_SAMPLING_BEAM_SEARCH);
    wparams.print_realtime = false;
    wparams.print_progress = false;
    wparams.language = language.c_str();
    wparams.translate = false;
    // v0.30+ -- 4 threads by default per the launcher's
    // documented contract. Setting 0 lets upstream
    // pick the right number; the launcher's KDoc says
    // "4 threads" but the upstream default is more
    // portable. The previous version hardcoded 4.
    wparams.n_threads = 4;
    if (beam_size > 1) {
        wparams.beam_search.beam_size = beam_size;
    }

    int n_segments = 0;
    int rc = whisper_full(
        state().ctx,
        wparams,
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
        const char* seg = whisper_full_get_segment_text(
            state().ctx, i);
        if (seg != nullptr) {
            if (!out.empty()) {
                out += "\n";
            }
            out += seg;
        }
    }
    return out;
}

void init_backend_if_needed() {
    if (state().backend_ok) return;
    // whisper.cpp's ggml backend is initialized on
    // first whisper_init_from_file_with_params via the
    // ggml static init; the JNI lib-load is the
    // canonical "library loaded" signal. [nativeReady]
    // returns true once System.loadLibrary succeeded.
    // The previous version returned whether a model
    // was already loaded, which was a chicken-and-egg
    // bug: [Whisper.transcribe] called [nativeReady]
    // before [nativeTranscribe] and bailed out with null
    // on a fresh process, so the model load that
    // happens inside [nativeTranscribe] never ran.
    state().backend_ok = true;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_org_mindanchor_narrate_WhisperEngine_nativeReady(
    JNIEnv* /* env */, jobject /* this */) {
    // v0.30+ (Phase 4 G-28) -- "the library loaded
    // and the backend is healthy". [Whisper.transcribe]
    // calls [nativeReady] before [nativeTranscribe] and
    // uses a null return to gate the call. The previous
    // implementation returned whether a model was
    // already loaded, which was a chicken-and-egg bug:
    // a fresh process never loaded a model, so
    // [nativeReady] was always false, so the user never
    // saw a transcription. The fix: defer the model
    // load to [nativeTranscribe], let [nativeReady]
    // mean "the .so loaded and the backend is initialised".
    init_backend_if_needed();
    return state().backend_ok ? JNI_TRUE : JNI_FALSE;
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
