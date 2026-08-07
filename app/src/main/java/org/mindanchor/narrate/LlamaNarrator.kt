package org.mindanchor.narrate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The thin Kotlin face of the native engine — see mindanchor_llama.cpp,
 * which is the only code in this app that touches llama.cpp.
 *
 * [nativeGenerate] returns raw UTF-8 bytes rather than a String because
 * JNI's NewStringUTF wants Modified UTF-8 and aborts on the standard
 * four-byte sequences a model can emit; decoding on this side with the
 * real charset turns anything malformed into replacement characters
 * instead of a crash.
 */
internal class LlamaEngine {

    external fun nativeGenerate(
        modelPath: String,
        system: String,
        prompt: String,
        contextTokens: Int,
        maxNewTokens: Int,
        seed: Long,
        threads: Int,
    ): ByteArray?

    /** True once the library is loaded and its backend initialised. */
    external fun nativeReady(): Boolean

    companion object {
        /**
         * Whether the native library could be loaded at all. Lazy and
         * caught: a device or ABI without the library must degrade to
         * "no paragraph tonight", never to a crash at class-load time.
         */
        val loaded: Boolean by lazy {
            runCatching { System.loadLibrary("mindanchor_llama") }.isSuccess
        }
    }
}

/**
 * The narrator with a real engine behind it.
 *
 * It stays inside [GuardedNarrator]'s frame: the prompt arrives already
 * built by [Prompting], the output leaves through [NarrationGuard], and
 * this class cannot reorder either. Everything it adds is plumbing — the
 * model file's path, the context budget [ModelSlot] granted, and a seed.
 *
 * ## Why the seed is derived from the prompt
 *
 * The same report must produce the same paragraph. An app that says
 * something different each time it is asked the same question looks like
 * it knows something new, and it does not — the same discipline as
 * LinkFinder's derived permutation seeds. Sampling still runs at a
 * modest temperature, so different nights read differently; identical
 * nights read identically.
 */
class LlamaNarrator(private val context: Context) : GuardedNarrator() {

    override suspend fun generate(system: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            if (!LlamaEngine.loaded) return@withContext null
            val fit = ModelStore.fit(context)
            val contextTokens = ModelSlot.contextTokens(fit)
            if (contextTokens <= 0) return@withContext null
            val bytes = runCatching {
                LlamaEngine().nativeGenerate(
                    modelPath = ModelStore.modelFile(context).absolutePath,
                    system = system,
                    prompt = prompt,
                    contextTokens = contextTokens,
                    maxNewTokens = MAX_NEW_TOKENS,
                    seed = seedFor(prompt),
                    threads = threads(),
                )
            }.getOrNull() ?: return@withContext null
            String(bytes, Charsets.UTF_8)
        }

    companion object {
        /**
         * Backstop, not a target. NarrationGuard's 1,200-character
         * ceiling is the real limit; a model that ignores "three to five
         * sentences" hits this and its overrun is rejected there. The
         * cap just stops a looping model from burning the night.
         */
        const val MAX_NEW_TOKENS = 320

        /** Stable across runs and devices: String.hashCode is specified. */
        internal fun seedFor(prompt: String): Long =
            prompt.hashCode().toLong() and 0xffffffffL

        /**
         * Leaves two cores for the rest of the phone. This runs at three
         * in the morning on a charging device, but "overnight" is a
         * schedule, not a licence — the run-now button uses this exact
         * path in the foreground.
         */
        private fun threads(): Int =
            (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
    }
}
