package org.mindanchor.model

import java.time.LocalTime

/**
 * A moment, as reported by the person living it.
 *
 * Two axes rather than one, following the circumplex model that most
 * momentary-assessment work is built on: how pleasant it feels, and how
 * activated. They come apart in exactly the ways that matter — flat and
 * low is not the same state as tense and low, and a single "how are you"
 * scale collapses them into one number that cannot tell those apart.
 *
 * Both are small integer scales because this must be answerable in one
 * tap while walking. A slider invites deliberation, and deliberation is
 * how momentary assessment turns into a diary nobody keeps up.
 */
data class Moment(
    /** Unpleasant … pleasant. */
    val valence: Int,
    /** Sluggish … wired. */
    val arousal: Int,
    val atMinuteOfDay: Int,
    val day: String,
) {
    companion object {
        const val MIN = 1
        const val MAX = 5

        fun isValid(value: Int) = value in MIN..MAX
    }
}

/**
 * When to ask, how often, and — more importantly — when to stop.
 *
 * ## Why the times are scattered
 *
 * Prompting at 09:00, 13:00 and 18:00 every day teaches somebody the
 * schedule, and a person who knows a question is coming starts composing
 * the answer before it arrives. That is anticipation bias, and it is why
 * momentary-assessment protocols use signal-contingent sampling with the
 * signals scattered inside blocks rather than fixed.
 *
 * The scatter here is deterministic given a seed, so the behaviour is
 * reproducible and testable — pseudo-random to the person, fixed to the
 * test suite.
 *
 * ## Why it tapers
 *
 * The model needs labels to learn a signature, and then it mostly does
 * not. Asking four times a day forever would make this the very thing it
 * is meant to replace: an app that demands attention on its own schedule.
 * So the rate falls as the number of labels grows, to a floor that is
 * enough to notice drift without being a chore.
 */
object EmaSchedule {

    /** While the model has nothing, ask often. */
    const val LEARNING_PROMPTS = 4

    /** Once it has a signature, ask rarely. */
    const val SETTLED_PROMPTS = 1

    /**
     * Labels needed before tapering begins.
     *
     * Roughly a month at the learning rate. Fewer than this and the
     * associations are being fitted to a handful of days, most of which
     * will have been unusually similar to each other.
     */
    const val LABELS_BEFORE_TAPER = 120

    /** Never two prompts closer together than this. */
    const val MIN_GAP_MINUTES = 75

    fun promptsPerDay(labelCount: Int): Int =
        if (labelCount >= LABELS_BEFORE_TAPER) SETTLED_PROMPTS else LEARNING_PROMPTS

    /**
     * Prompt times for one day, scattered inside equal blocks between
     * [wakeMinute] and [sleepMinute].
     *
     * A window too short to hold [count] prompts at [MIN_GAP_MINUTES]
     * apart gets **fewer prompts, not none**. The first version of this
     * refused outright, which meant somebody on a short shift-work waking
     * window received nothing at all — and no labels means no model,
     * which is the one outcome that makes the whole system pointless for
     * exactly the people whose hours are already awkward.
     *
     * [seed] makes the scatter deterministic. Vary it by date in the
     * caller so consecutive days differ.
     */
    fun promptTimes(
        wakeMinute: Int,
        sleepMinute: Int,
        count: Int,
        seed: Int,
    ): List<LocalTime> {
        if (count <= 0) return emptyList()
        val window = sleepMinute - wakeMinute
        if (window <= 0) return emptyList()

        // Reduce to what the window can actually hold, rather than
        // refusing or crowding.
        val fitted = minOf(count, window / MIN_GAP_MINUTES + 1)
        if (fitted <= 0) return emptyList()

        val block = window / fitted
        var previous = Int.MIN_VALUE
        val times = mutableListOf<LocalTime>()
        for (i in 0 until fitted) {
            val blockStart = wakeMinute + i * block
            // A simple integer hash rather than Random, so the same seed
            // and day always produce the same times. Reproducibility
            // matters more here than statistical quality: this only needs
            // to be unguessable by a person, not by a cryptographer.
            val scatter = if (block <= 1) 0 else scatterFor(seed, i) % block
            var minute = blockStart + scatter
            if (previous != Int.MIN_VALUE && minute - previous < MIN_GAP_MINUTES) {
                minute = previous + MIN_GAP_MINUTES
            }
            if (minute >= sleepMinute) break
            times += LocalTime.of((minute / 60) % 24, minute % 60)
            previous = minute
        }
        return times
    }

    private fun scatterFor(seed: Int, index: Int): Int {
        var h = seed * 31 + index * 2_654_435_761L.toInt()
        h = h xor (h ushr 16)
        h *= 0x7feb352d
        h = h xor (h ushr 15)
        return h and 0x7fffffff
    }
}
