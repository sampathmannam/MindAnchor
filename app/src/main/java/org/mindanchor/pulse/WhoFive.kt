package org.mindanchor.pulse

/**
 * WHO-5 Well-Being Index scoring (public-domain instrument, WHO 1998;
 * validated — Topp et al. 2015). Five items, each answered 0–5; raw sum
 * 0–25 is multiplied by 4 for the standard 0–100 score.
 */
object WhoFive {

    const val ITEM_COUNT = 5
    const val MAX_ANSWER = 5

    /** Returns the 0–100 score, or null if any item is unanswered (-1). */
    fun score(answers: List<Int>): Int? {
        if (answers.size != ITEM_COUNT) return null
        if (answers.any { it < 0 || it > MAX_ANSWER }) return null
        return answers.sum() * 4
    }
}
