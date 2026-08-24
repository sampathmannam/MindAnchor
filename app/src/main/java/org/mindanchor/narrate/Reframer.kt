package org.mindanchor.narrate

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The v0.27+ (Phase 2 G-3) compassionate reframe of a Letter.
 *
 * Long-press any saved Letter → "Reframe with wise mind" →
 * this class produces a 1-3 line DBT-wise-mind rewrite
 * of the body. The user can save the reframe as an
 * alternate version of the Letter.
 *
 * ## Why a separate class (not a new Narrator method)
 *
 * The existing [Narrator] interface is for [org.mindanchor.report.Report]s —
 * multi-paragraph, evidence-anchored, the report's data drives the
 * prompt. A Letter reframe is a different shape: 1-3 lines, no
 * evidence anchor (the reframe is the user's own words, gently
 * reframed), and the system prompt is the DBT wise-mind voice.
 * Two different shapes deserve two different classes, not a
 * flag-driven divergence in one.
 *
 * ## Why the LLM-first / template-fallback split
 *
 * The LLM is the primary path: it produces a richer reframe
 * than a template can. The template is the fallback for
 * devices without a model loaded, or when the safety
 * classifier rejects the input.
 *
 * ## Safety
 *
 * The reframe is BPD-safe by design: the system prompt
 * instructs "validating emotion + pointing to facts + suggesting
 * a skill" — the validate-then-suggest family. The reframe
 * is not directive, never suggests a diagnosis, and is
 * always editable.
 *
 * @see Linehan 1993 DBT Skills Training Manual, 2nd ed.,
 *   Guilford Press, "wise mind" skill. The reframe voice is
 *   the Linehan wise-mind voice: "emotion + reason + the
 *   third thing the person is missing."
 * @see Neff 2003 Self-Compassion, the project's
 *   BPD-safe-by-default copy.
 */
class Reframer(private val context: Context) {

    /**
     * Reframe the given [letterBody] using the on-device
     * LLM. Falls back to a curated template when the
     * model is not loaded or the LLM call returns null
     * (the LlamaNarrator returns null on every failure
     * path: no model, no model slot, native null, etc.).
     *
     * The fallback is a *deliberate* template, not a
     * silent crash. A user with no model can still
     * benefit from the reframe affordance; the LLM
     * just gives them more.
     */
    suspend fun reframe(letterBody: String): String = withContext(Dispatchers.IO) {
        val llm = LlamaNarrator(context)
        val out = llm.reframeLetterBody(letterBody)
        if (!out.isNullOrBlank()) return@withContext out.trim()
        templateReframe(letterBody)
    }

    /**
     * The curated template fallback. The first line
     * validates the emotion by restating the user's
     * own words; the second line offers a fact-check
     * by pointing at the Linehan wise-mind frame; the
     * third line offers a single concrete skill.
     *
     * The skill list is the same one the on-device LLM
     * is asked to draw from, and the three skills are
     * the ones every DBT client knows: TIPP (crisis
     * survival), DEAR MAN (interpersonal), and the
     * bedtime-procrastination "write it down" hook
     * (Scullin 2018).
     */
    private fun templateReframe(letterBody: String): String {
        val firstSentence = letterBody.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
            ?: "what you wrote"
        return """
            |$firstSentence
            |
            |That is a real feeling. Two facts on the table:
            |you wrote it down, and the day continued.
            |
            |One skill for tonight: TIPP if it is sharp, DEAR
            |MAN if it is with a person, or the bedtime list
            |(write tomorrow in three lines) if it is wide.
        """.trimMargin()
    }
}
