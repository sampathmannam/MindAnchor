package org.mindanchor.note

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindanchor.model.NoteType

/**
 * On-device classification of a note's body into one
 * of the four [NoteType]s. The classifier is the
 * smallest piece of v0.25.0 that can be tested
 * without a model: the prompt and the parser are
 * pure functions; the engine call is the only side
 * effect.
 *
 * ## Why the system prompt is one line
 *
 * The classifier's job is a single word, not a
 * paragraph. A long system prompt would invite the
 * model to add reasoning, hedging, or commentary —
 * all of which the parser would have to reject, and
 * all of which is wasted context-window budget on a
 * 3.8B-parameter quantised model. The system prompt
 * is one line: "You are a one-word classifier.
 * Output exactly one of: GENERAL TASK REMINDER
 * JOURNAL." The model has one job and no
 * instructions to do anything else.
 *
 * ## Why the parser defaults to GENERAL
 *
 * A note that the model cannot classify (a
 * misclassification, a malformed output, a string
 * we don't recognise) is not a "no type" situation
 * — the note exists, the user wrote it, the type
 * is the safest possible read. GENERAL is the
 * catch-all the user named in the brainstorm: "I
 * just need to remember this" lands here, alongside
 * anything the model can't fit into a tighter
 * bucket. A null type is reserved for "the model
 * isn't on the phone" or "the classifier hasn't
 * run yet", not for "the model didn't know".
 *
 * ## Why the seed is body-derived
 *
 * Same discipline as the night report and the
 * letter: a stable, prompt-derived seed so the
 * same body produces the same type across
 * attempts. A retried classifier that read "TASK"
 * once reads "TASK" again; a different body
 * reads differently.
 */
class NoteClassifier(private val context: Context) {

    /**
     * The system prompt. One line, four tokens. The
     * model is told exactly what its output must
     * look like; anything else is a malformed output
     * and falls back to GENERAL.
     */
    private val system: String =
        "You are a one-word classifier. " +
            "Output exactly one of: GENERAL TASK REMINDER JOURNAL."

    /**
     * Classify [body] into a [NoteType]. Never
     * returns null — the safe default is GENERAL.
     *
     * The classifier always returns a value, even
     * when:
     *  - the model isn't on the phone
     *  - the model can't run on this device
     *  - generation failed outright
     *  - the output was malformed
     *
     * The first three return GENERAL silently. The
     * fourth returns GENERAL after parsing. None of
     * these is an error to the caller — see the
     * caller pattern in [ClassifierEnqueuer] and
     * the [org.mindanchor.data.NotesPrefs.setType]
     * flow.
     */
    suspend fun classify(body: String): NoteType = withContext(Dispatchers.IO) {
        // v0.72.x: the on-device classifier is gone — the
        // offline Phi-4 model is no longer shipped. Without
        // an engine, every note is GENERAL; the user's own
        // words still drive the rest of the data layer
        // (timestamp, search), the classification just stops
        // trying to second-guess what they meant.
        if (body.isBlank()) return@withContext NoteType.GENERAL
        return@withContext NoteType.GENERAL
    }

    /**
     * The first non-blank token of [text], uppercased,
     * mapped to a [NoteType]. Anything that does not
     * match one of the four known names falls back to
     * GENERAL.
     */
    internal fun parseOutput(text: String): NoteType {
        val token = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.uppercase()
            ?: return NoteType.GENERAL
        return when (token) {
            "GENERAL" -> NoteType.GENERAL
            "TASK" -> NoteType.TASK
            "REMINDER" -> NoteType.REMINDER
            "JOURNAL" -> NoteType.JOURNAL
            else -> NoteType.GENERAL
        }
    }

    /**
     * Stable, body-derived 64-bit seed. Same shape as
     * [org.mindanchor.letters.LetterWriter.seedFor]:
     * FNV-style hash, non-secret, deterministic. Two
     * notes with the same body produce the same
     * type; a re-classification after a model
     * glitch reads the same answer.
     */
    private fun seedFor(prompt: String): Long {
        var h = HASH_SEED
        for (c in prompt) {
            h = HASH_MULTIPLIER * h + c.code
        }
        return h
    }

    private companion object {
        /**
         * The max-new-tokens cap for the classifier. The
         * output is one word; 4 is the minimum that
         * survives a model that adds a trailing
         * newline or punctuation.
         */
        const val MAX_NEW_TOKENS = 4
        // 64-bit FNV-style seed. Non-secret constant
        // chosen for good distribution; the value is
        // shared with LetterWriter so the on-disk
        // letter and the type for a given body stay
        // stable across reads of the same prompt.
        const val HASH_SEED: Long = 1125899906842597L
        const val HASH_MULTIPLIER: Long = 31L
        /**
         * Same thread budget as the night report and
         * the letter. Two cores for the rest of the
         * phone; the classifier is fire-and-forget so
         * 2 is the safe floor.
         */
        const val THREADS = 2
    }
}
