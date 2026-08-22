package org.mindanchor.letters

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindanchor.narrate.LlamaEngine
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.narrate.ModelStore
import org.mindanchor.narrate.NarrationGuard

/**
 * Writes the daily ASH-style letter on-device, using the same
 * Phi-4 mini GGUF as the night report.
 *
 * The class deliberately has the same shape as
 * [org.mindanchor.narrate.GuardedNarrator]: the system prompt
 * arrives already built by [LetterPrompting], the output leaves
 * through [NarrationGuard], and [write] cannot reorder either.
 * The two surfaces (night report and morning letter) share the
 * model and the safety filter, but not the prompt: the night
 * report writes about a single day; the letter writes about
 * seven.
 *
 * ## Why a separate class rather than widening [Narrator]
 *
 * [org.mindanchor.narrate.Narrator] is shaped for [org.mindanchor.report.Report]
 * — a single day's data. Adding a `narrate(week: WeekData)` would
 * either overload the interface (different input types) or
 * require a second interface on the same engine, both of which
 * invite a future caller to feed the wrong input shape. A new
 * class for the letter keeps the contracts honest: the engine
 * is shared, the safety filter is shared, the prompt and the
 * result type are letter-shaped.
 *
 * ## When this returns null
 *
 * Every way a letter can fail to be worth showing the user
 * comes back as null: the prompt was too sparse to write from
 * (see [LetterPrompting.build]), the model could not run on
 * this phone, generation failed outright, or the output was
 * rejected by [NarrationGuard]. None of those are errors to the
 * caller — the scheduler treats "no letter today" as a normal
 * day, the same way [org.mindanchor.report.ReportScheduler]
 * treats "no paragraph tonight" as a normal night.
 *
 * ## The seed
 *
 * Same as the night report: derived from the prompt, so the
 * same week of data produces the same letter across attempts
 * (a worker that re-tries after a crash reads the same letter).
 * Different weeks still read differently.
 */
class LetterWriter(private val context: Context) {

    /**
     * Writes a letter about [week], or returns null.
     *
     * @return the letter body, or null for the reasons in the
     * class kdoc
     */
    suspend fun write(week: WeekData): String? = withContext(Dispatchers.IO) {
        if (!LlamaEngine.loaded) return@withContext null
        val fit = ModelStore.fit(context)
        val contextTokens = ModelSlot.contextTokens(fit)
        if (contextTokens <= 0) return@withContext null
        val prompt = LetterPrompting.build(week) ?: return@withContext null
        val raw = runCatching {
            LlamaEngine().nativeGenerate(
                modelPath = ModelStore.modelFile(context).absolutePath,
                system = LetterPrompting.SYSTEM,
                prompt = prompt,
                contextTokens = contextTokens,
                maxNewTokens = MAX_NEW_TOKENS,
                seed = seedFor(prompt),
                threads = threads(),
            )
        }.getOrNull() ?: return@withContext null
        val text = String(raw, Charsets.UTF_8).trim()
        when (val verdict = NarrationGuard.judge(text)) {
            is NarrationGuard.Verdict.Accepted -> verdict.text
            is NarrationGuard.Verdict.Rejected -> null
        }
    }

    private fun seedFor(prompt: String): Long {
        // Same shape as the night report: a stable, non-secret
        // 64-bit hash of the prompt. A different week produces a
        // different seed; the same week, the same seed.
        var h = HASH_SEED
        for (c in prompt) {
            h = HASH_MULTIPLIER * h + c.code
        }
        return h
    }

    private fun threads(): Int = Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS)

    private companion object {
        /**
         * The max-new-tokens cap for the letter. The night
         * report paragraph is ~150 words; the letter is 2-3
         * paragraphs of similar length. 600 tokens is a
         * comfortable ceiling that lets the model finish a
         * sentence without running away.
         */
        const val MAX_NEW_TOKENS = 600
        // 64-bit FNV-style seed. The exact value is a non-secret
        // constant — it just needs to be a large odd prime with
        // good distribution across the 64-bit space.
        const val HASH_SEED: Long = 1125899906842597L
        const val HASH_MULTIPLIER: Long = 31L
        const val MIN_THREADS = 2
        const val MAX_THREADS = 8
    }
}
