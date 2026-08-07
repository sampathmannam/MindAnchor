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
 * The only [Narrator] this build has. It always returns null.
 *
 * The inference engine this would need — llama.cpp, run over JNI against
 * whatever [ModelStore] has on file — is not built yet. This object is
 * the honest placeholder for it, and every other part of the path a real
 * engine would sit inside is already built and already tested without
 * one: [Prompting] builds the exact prompt a model would be given,
 * [NarrationGuard] judges the exact output a model would produce,
 * [ModelSlot] and [ModelStore.fit] decide whether a model on file could
 * even run here, and [org.mindanchor.report.ReportStore] and
 * [org.mindanchor.report.ReportScreen] already store and display a
 * narration end to end. The one piece missing is a model actually
 * running, and this must never paper over that: it does not fabricate a
 * paragraph, does not simulate one from a template, and does not claim to
 * have tried and failed. It returns null, honestly, every single time.
 */
object NoEngineNarrator : Narrator {
    override suspend fun narrate(report: Report): Narration? = null
}

/** Picks the [Narrator] this device should use. */
object Narrators {

    /**
     * [NoEngineNarrator] when there is no model on file, when
     * [ModelSlot.runnable] says the model on file should not be run on
     * this phone — and, for now, unconditionally otherwise too, because
     * no inference engine exists in this build. See [NoEngineNarrator]'s
     * KDoc for why that must stay honest rather than pretended around.
     *
     * When a real engine is written, swapping it in is one line: the
     * final `return` below becomes `return LlamaNarrator(context)` or
     * whatever that engine is called, and nothing above it — the model
     * lookup, the fit check, or any caller of this function — needs to
     * change for that to happen.
     */
    fun forDevice(context: Context): Narrator {
        if (!ModelStore.hasModel(context)) return NoEngineNarrator
        val fit = ModelStore.fit(context)
        if (!ModelSlot.runnable(fit)) return NoEngineNarrator
        // No inference engine exists in this build yet — see
        // NoEngineNarrator's KDoc for why this stays NoEngineNarrator
        // even when a model that would otherwise run is on file.
        return NoEngineNarrator
    }
}
