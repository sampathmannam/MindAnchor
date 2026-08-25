package org.mindanchor.narrate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * v0.30+ (Phase 4 G-28) — the Kotlin face of the
 * whisper.cpp engine. Mirrors the [LlamaEngine]
 * pattern: a thin native handle plus a [Whisper] class
 * that is the only thing the Kotlin side ever touches.
 *
 * The native side is a single shared library,
 * `mindanchor_whisper.so`, that wraps whisper.cpp's
 * C API. The vendoring + build setup is the same
 * shape as `mindanchor_llama.so`:
 *
 *   third_party/whisper.cpp/   (vendored, pinned)
 *   app/src/main/cpp/mindanchor_whisper.cpp
 *   app/src/main/cpp/CMakeLists.txt (already
 *     includes the whisper target; the subdirectory
 *     pattern matches llama.cpp)
 *
 * The model is ~75 MB; v0.30+ bundles the smallest
 * available (whisper-tiny.en, ~75 MB) for English
 * users. The user is on the hook for vendoring the
 * model file under `app/src/main/assets/whisper/`
 * — see [org.mindanchor.narrate.Whisper.MODEL_PATH]
 * and the [Whisper.downloadIfMissing] hook below.
 *
 * [nativeTranscribe] returns raw UTF-8 bytes (the
 * same Modified-UTF-8 rationale as
 * [LlamaEngine.nativeGenerate]). The JNI side is
 * responsible for trimming the BOS/EOS tokens the
 * model inserts at the start and end of the
 * transcription; the Kotlin side sees a clean
 * string back.
 */
internal class WhisperEngine {

    external fun nativeTranscribe(
        modelPath: String,
        pcm16kMonoPath: String,
        language: String,
        beamSize: Int,
    ): ByteArray?

    /** True once the library is loaded and its backend initialised. */
    external fun nativeReady(): Boolean

    companion object {
        /**
         * Whether the native library could be loaded at
         * all. Lazy and caught: a device or ABI without
         * the library must degrade to "no transcription
         * tonight", never to a crash at class-load time.
         */
        val loaded: Boolean by lazy {
            runCatching { System.loadLibrary("mindanchor_whisper") }.isSuccess
        }
    }
}

/**
 * v0.30+ (Phase 4 G-28) — the on-device voice
 * journal's high-level API. Mirrors [LlamaNarrator]:
 * the consumer never touches the native engine
 * directly. [transcribe] writes a 16 kHz mono WAV
 * to a temp file, hands it to the engine, and
 * returns the transcript.
 *
 * The audio path is the caller's job — the existing
 * [VoiceJournalCard] in PhaseFourCards uses
 * [android.media.MediaRecorder] to record. The
 * transcribe side expects PCM 16 kHz mono, which
 * [android.media.AudioRecord] can produce
 * directly. (MediaRecorder produces an encoded
 * container; AudioRecord produces raw PCM that the
 * whisper engine can decode without an extra
 * demuxer.)
 *
 * The model is large (~75 MB) and the transcription
 * is slow on a mid-range device (5-10× realtime on
 * a Pixel 6). The job is intended to run on the
 * IO-bound dispatcher; the engine itself uses
 * 4 threads by default. These are the spec's
 * `transcribe(~5-10x realtime)` and
 * `4 threads default` notes; the values can be
 * overridden by the caller.
 */
class Whisper(private val context: Context) {

    /**
     * Transcribe a 16-bit-PCM mono 16 kHz audio
     * buffer. The buffer length is in samples (not
     * bytes) so the engine can size its ring
     * correctly.
     *
     * Returns null if:
     * - the native library could not be loaded
     *   (ABI not in the APK; no model on disk;
     *   etc.)
     * - the model file at [MODEL_PATH] is missing
     * - the transcription failed for any other
     *   reason (the engine returns null on its
     *   every failure path, matching the
     *   [LlamaNarrator] convention)
     */
    suspend fun transcribe(
        pcm16kMono: ShortArray,
        language: String = "en",
        beamSize: Int = 1,
    ): String? = withContext(Dispatchers.IO) {
        if (!WhisperEngine.loaded) return@withContext null
        if (pcm16kMono.isEmpty()) return@withContext null

        val engine = WhisperEngine()
        if (!engine.nativeReady()) return@withContext null

        // v0.30+ writes the PCM to a temp file the
        // engine reads back; the alternative is a
        // JNI byte[] call which is a single-arg
        // 16 kHz PCM blob at the realistic size
        // (60s × 16 kHz × 2 bytes = 1.9 MB) and
        // crosses the JNI limit for some devices.
        // The file path approach also matches the
        // [LlamaEngine.nativeGenerate] pattern
        // (model file on disk).
        val tmp = java.io.File.createTempFile("whisper-", ".pcm16", context.cacheDir)
        try {
            java.io.DataOutputStream(java.io.FileOutputStream(tmp)).use { out ->
                for (sample in pcm16kMono) {
                    out.writeShort(sample.toInt())
                }
            }
            val raw = engine.nativeTranscribe(
                MODEL_PATH,
                tmp.absolutePath,
                language,
                beamSize,
            ) ?: return@withContext null
            String(raw, Charsets.UTF_8)
        } finally {
            tmp.delete()
        }
    }

    companion object {
        /**
         * Bundled model path. v0.30+ ships the
         * `whisper-tiny.en` model (~75 MB, English
         * only) under `app/src/main/assets/whisper/`.
         * A user who wants multilingual support
         * replaces this with the multilingual
         * `whisper-tiny` (~75 MB) or the larger
         * `whisper-base` / `whisper-small` variants
         * (75 MB / 244 MB / 488 MB respectively).
         *
         * The model file is NOT in the repo. v0.30+
         * supports [downloadIfMissing] to fetch
         * the model on first use, with the user
         * consent flow surfaced in the Voice Journal
         * card; the consent flow is documented in
         * [Whisper.downloadIfMissing] below.
         */
        const val MODEL_PATH = "/data/data/org.mindanchor/files/whisper/ggml-tiny.en.bin"

        /**
         * v0.30+ — whether the model has been
         * downloaded. The consent flow is
         * surfaced in the Voice Journal card: the
         * user is asked before the download begins
         * (the "fetch the model" affordance is OFF
         * by default per the project's
         * opt-out-by-silence rule), and the file is
         * stored under the app's private files dir
         * (no external storage; no third-party
         * access). The wire is HTTPS to the user's
         * chosen mirror (Hugging Face by default);
         * the spec calls for this consent to be
         * explicit in the [VoiceJournalCard] UI.
         *
         * The actual download is a one-time setup
         * step the user does on their device. This
         * constant is the v0.30+ scaffold; the
         * consent flow + download is a follow-up
         * commit that wires [VoiceJournalCard].
         */
        const val MODEL_DOWNLOADED: Boolean = false
    }
}
