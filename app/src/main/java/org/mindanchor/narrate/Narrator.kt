package org.mindanchor.narrate

import android.content.Context
import org.mindanchor.report.Report

/**
 * What a [Narrator] produced: the text, and nothing else.
 *
 * There is deliberately no sources field here. [org.mindanchor.report.ReportSection.sources]
 * is what the app shows next to a section, drawn from the actual passages
 * [org.mindanchor.report.ReportComposer] retrieved for it — never from
 * anything a model said about what it drew on. [Prompting] withholds
 * sources from the model in the first place and [NarrationGuard] rejects
 * output that names one anyway, and carrying a "sources" field on this
 * type would invite some future caller to trust the model's word for it
 * instead of the app's own record.
 */
data class Narration(val text: String)

/**
 * Something that can turn a night's [Report] into a short paragraph about
 * it, or decline to.
 */
interface Narrator {

    /**
     * Writes about [report], or returns null.
     *
     * Null covers every way this can fail to produce something worth
     * showing: nothing to write about, no model able to run, generation
     * that failed outright, or output [NarrationGuard] judged unsafe. None
     * of those are errors to the caller — see
     * [org.mindanchor.report.ReportScheduler], which folds "no narration"
     * into the same success outcome as "no report at all", for the same
     * reason [org.mindanchor.report.ReportComposer]'s own KDoc gives: this
     * runs unattended at night, and a paragraph is a nice-to-have on top
     * of a report that stands on its own without one.
     */
    suspend fun narrate(report: Report): Narration?
}

/**
 * Everything around a language model except the model.
 *
 * ## Why this class exists rather than a convention
 *
 * [Prompting] and [NarrationGuard] were written before any engine, and
 * for a while nothing called them. That is a worse state than it looks:
 * a guard nobody invokes is not a safeguard, it is a file. Whoever
 * eventually drops in llama.cpp would have had to remember, unprompted,
 * to build the prompt through [Prompting] and to put the output through
 * [NarrationGuard] — and the failure mode of forgetting is a phone
 * telling somebody they sound depressed.
 *
 * So the order is fixed here and [narrate] is final. An engine supplies
 * [generate] and nothing else. It cannot choose its own prompt, cannot
 * skip the check, and cannot return text that has not been judged. The
 * guard is load-bearing rather than advisory, and
 * [org.mindanchor.narrate.GuardedNarratorTest] proves it by running a
 * fake engine that tries to say exactly the wrong thing.
 */
abstract class GuardedNarrator : Narrator {

    /**
     * Runs the model.
     *
     * [system] is the instruction and [prompt] the day's counts and
     * passages — see [Prompting], which built both. Return the model's
     * raw text, or null if it could not run at all. Returning unchecked
     * text is safe precisely because the caller checks it.
     */
    protected abstract suspend fun generate(system: String, prompt: String): String?

    final override suspend fun narrate(report: Report): Narration? {
        // Null here means a quiet night, or nothing in the corpus worth
        // writing from. Neither is worth waking a model for.
        val prompt = Prompting.build(report) ?: return null
        val raw = runCatching { generate(Prompting.SYSTEM, prompt) }.getOrNull() ?: return null
        return when (val verdict = NarrationGuard.judge(raw)) {
            is NarrationGuard.Verdict.Accepted -> Narration(verdict.text)
            // Rejected output is dropped whole and silently. The report
            // still shows the counts and the passages, exactly as it did
            // before any model existed, which is why this costs a
            // paragraph rather than a night.
            is NarrationGuard.Verdict.Rejected -> null
        }
    }
}

/**
 * The narrator for a phone that cannot run a model tonight: no model on
 * file, or one [ModelSlot] refuses to run here. It generates nothing,
 * honestly, rather than fabricating or templating a paragraph.
 *
 * It goes through [GuardedNarrator] rather than short-circuiting
 * [Narrator] directly, so this path and [LlamaNarrator]'s are the same
 * path with one method swapped.
 */
object NoEngineNarrator : GuardedNarrator() {
    override suspend fun generate(system: String, prompt: String): String? = null
}

/** Picks the [Narrator] this device should use. */
object Narrators {

    /**
     * [NoEngineNarrator] when there is no model on file or when
     * [ModelSlot.runnable] says the one on file should not be run on
     * this phone; [LlamaNarrator] otherwise. The narrator still checks
     * nothing itself — every refusal that matters happens here or inside
     * the guard, and [LlamaEngine.loaded] covers the one failure only
     * the device can reveal, a library that will not load.
     */
    fun forDevice(context: Context): Narrator {
        if (!ModelStore.hasModel(context)) return NoEngineNarrator
        val fit = ModelStore.fit(context)
        if (!ModelSlot.runnable(fit)) return NoEngineNarrator
        return LlamaNarrator(context)
    }
}
