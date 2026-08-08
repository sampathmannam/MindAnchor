package org.mindanchor.pulse

/**
 * WHO-5 Well-Being Index scoring (public-domain instrument, WHO 1998;
 * validated — Topp et al. 2015). Five items, each answered 0–5; raw sum
 * 0–25 is multiplied by 4 for the standard 0–100 score.
 *
 * ## Banding
 *
 * Two cut-offs, both primary-source-cited in `docs/research/13`:
 *  - **score ≤ 50** (raw < 13) is the **WHO 1998 DepCare** and
 *    **Topp 2015** depression-screen cut-off. Below this, the WHO
 *    DepCare document explicitly recommends administering the Major
 *    Depression (ICD-10) Inventory as a *second step*. Mean sensitivity
 *    0.87, mean specificity 0.76 across eight studies (Topp 2015).
 *  - **score ≤ 28** (raw ≤ 7) is the **more restrictive** cut-off that
 *    "more restrictively equals the level of well-being among patients
 *    with DSM-IV major depression" (Topp 2015, citing Löwe). Sensitivity
 *    0.93, specificity 0.83.
 *
 * The screen-positive definition used here is **broader than the score
 * alone**: WHO 1998 says any single item answered 0 or 1 also flags
 * the result for further assessment. Both criteria are checked
 * because forgetting the per-item check would miss a real signal that
 * the originating document calls out by name.
 *
 * ## Clinically meaningful change
 *
 * **10 points** on the 0–100 scale is the published threshold for
 * clinically meaningful change (WHO 1998 citing John Ware 1995;
 * Topp 2015). It is a *band* (8–12) — the code uses 10 as the
 * defensible centre. Below 10 points, a change is shown as arithmetic
 * only and not framed as a trend.
 *
 * ## Why this is in pure functions, not a UI
 *
 * `docs/CLINICAL_REVIEW.md` R3 makes "never interpreting a WHO-5
 * score" an invariant. The pure-function split is the one way to
 * honour that: the band and the screen-positive flag are *facts* the
 * presentation code can read, and the *wording* — which is what
 * carries interpretation — lives in [PulseScreen] and is the
 * clinician-reviewed part. The data side and the language side
 * can be reviewed separately by people with different training.
 */
object WhoFive {

    const val ITEM_COUNT = 5
    const val MAX_ANSWER = 5

    /** Topp 2015 / WHO 1998 — below this, the WHO recommends a second-step assessment. */
    const val SCREEN_POSITIVE_CUTOFF = 50

    /** Topp 2015 (Löwe) — the more restrictive cut-off, level of DSM-IV major depression. */
    const val VERY_LOW_CUTOFF = 28

    /** WHO 1998 (John Ware 1995) — 10% difference is a meaningful change. */
    const val MEANINGFUL_CHANGE = 10

    /** Returns the 0–100 score, or null if any item is unanswered (-1). */
    fun score(answers: List<Int>): Int? {
        if (answers.size != ITEM_COUNT) return null
        if (answers.any { it < 0 || it > MAX_ANSWER }) return null
        return answers.sum() * 4
    }

    /**
     * Which band the score falls into.
     *
     * A null score (incomplete answers) is its own band, distinct from
     * every other value, so the presentation code can refuse to render
     * a verdict on partial data.
     */
    fun band(score: Int?): Band = when {
        score == null -> Band.INCOMPLETE
        score <= VERY_LOW_CUTOFF -> Band.VERY_LOW
        score <= SCREEN_POSITIVE_CUTOFF -> Band.LOW
        else -> Band.OKAY
    }

    /**
     * Whether the WHO 1998 second-step assessment is recommended.
     *
     * True when the score is at or below the screen-positive cut-off,
     * **or** when any single item was answered 0 or 1 — the second
     * criterion the WHO document calls out by name and that the field
     * often forgets.
     */
    fun screenPositive(answers: List<Int>, score: Int?): Boolean {
        if (score != null && score <= SCREEN_POSITIVE_CUTOFF) return true
        return answers.any { it == 0 || it == 1 }
    }

    /**
     * Whether the change between two readings crosses the meaningful-change
     * threshold, and in which direction. A null previous is the first
     * reading and has no change to evaluate.
     *
     * The threshold is **deliberately coarse** (10 points): sub-threshold
     * shifts are common in day-to-day mood variance, and the brief
     * (`docs/research/13`) is clear that framing sub-threshold noise as
     * "improvement" is a documented harm (DISCOVER RCT, Lancet Digital
     * Health 2024; Parker 2020, JMIR mHealth).
     */
    fun change(current: Int?, previous: Int?): Change? {
        if (current == null || previous == null) return null
        val delta = current - previous
        if (kotlin.math.abs(delta) < MEANINGFUL_CHANGE) return null
        return if (delta > 0) Change.MEANINGFUL_UP else Change.MEANINGFUL_DOWN
    }

    enum class Band {
        /** Incomplete answers — the questionnaire was not finished. */
        INCOMPLETE,

        /** score > 50. */
        OKAY,

        /** 29–50. Reduced well-being; further reflection appropriate. */
        LOW,

        /** score ≤ 28. Level of well-being seen in DSM-IV major depression. */
        VERY_LOW,
    }

    enum class Change {
        /** Current is ≥ 10 points above the previous reading. */
        MEANINGFUL_UP,

        /** Current is ≥ 10 points below the previous reading. */
        MEANINGFUL_DOWN,
    }
}
