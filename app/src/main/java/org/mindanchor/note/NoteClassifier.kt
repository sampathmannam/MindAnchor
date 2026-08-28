package org.mindanchor.note

import org.mindanchor.model.NoteType

/**
 * Classification of a note's body into a [NoteType].
 *
 * v0.70.5: the on-device model this classifier called (Settings →
 * Reading → Model) has been removed. GENERAL was already the answer
 * on every phone that had never imported a model — the ordinary case
 * for everyone — so nothing observable changes; this is that same
 * outcome made permanent and honest rather than reached through a
 * model-lookup path that could never succeed again anyway.
 */
class NoteClassifier {
    fun classify(body: String): NoteType = NoteType.GENERAL
}
