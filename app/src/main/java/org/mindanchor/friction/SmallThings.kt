package org.mindanchor.friction

/**
 * Offers back one of the small things a person said helps them, at the
 * moment they are reaching for something else.
 *
 * ## Why here
 *
 * Behavioural activation is the simplest well-evidenced treatment for
 * depression: do the small thing, and mood follows action rather than
 * waiting to precede it. Its hard part has always been *noticing the
 * moment of avoidance*, which in therapy is reconstructed days later from
 * memory and a diary.
 *
 * A launcher does not have to reconstruct it. Reaching for a distraction
 * app is that moment, observed live, at the second it happens. Nothing
 * else in the person's life has that timing.
 *
 * ## Why it is so carefully fenced
 *
 * The failure mode is severe and specific: a tool that suggests a walk to
 * someone who cannot get out of bed has handed them one more thing to
 * have failed at today. That is worse than doing nothing, and the people
 * it lands hardest on are the ones this app is for.
 *
 * So four rules, all enforced here rather than trusted to call sites:
 *
 *  - **Only their own words.** Nothing is ever suggested that the person
 *    did not write down themselves, while calm. This is not a launcher
 *    having opinions about how somebody should feel better.
 *  - **Never on [FrictionTone.FEATHER].** A fourth reach in ten minutes is
 *    somebody having a hard time. That is the exact moment not to be
 *    asked whether they have tried going for a walk.
 *  - **Never in the quiet hours.** At three in the morning there is no
 *    small thing, and pretending otherwise is a tool that has not noticed
 *    what time it is.
 *  - **Never instead of the door.** The offer sits beside the way in, and
 *    the way in stays exactly where it was.
 */
object SmallThings {

    /** Nobody needs a list of thirty things at the moment of reaching. */
    const val MAX = 6

    /**
     * Which small thing to offer, or null for none.
     *
     * [nthReach] rotates the choice so the same line does not become
     * wallpaper — the same habituation that [FrictionTone] exists to
     * handle. It is a plain rotation rather than anything random, so the
     * behaviour is reproducible and testable.
     */
    fun offer(
        things: List<String>,
        nthReach: Int,
        tone: FrictionTone,
        quietHours: Boolean,
    ): String? {
        if (tone == FrictionTone.FEATHER) return null
        if (quietHours) return null
        val usable = things.map { it.trim() }.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return null
        // Guard the modulo against a negative count rather than throwing
        // an ArithmeticException on somebody's home screen.
        val index = if (nthReach < 0) 0 else nthReach % usable.size
        return usable[index]
    }

    /** Adds [thing], trimmed, deduplicated, and capped at [MAX]. */
    fun add(things: List<String>, thing: String): List<String> {
        val trimmed = oneLine(thing).trim()
        if (trimmed.isEmpty()) return things
        if (things.any { it.equals(trimmed, ignoreCase = true) }) return things
        return (things + trimmed).takeLast(MAX)
    }

    fun remove(things: List<String>, thing: String): List<String> =
        things.filterNot { it == thing }

    /**
     * One per line. Blank lines are dropped on the way in, so a stored
     * file that picks up stray newlines cannot produce an empty offer.
     */
    fun encode(things: List<String>): String = things.joinToString("\n") { oneLine(it) }

    /**
     * One item per line is the whole format, so a line break inside an item
     * would split it into two on the next read. Items are the person's own
     * words, pasted as often as typed, so both writers normalise rather than
     * trusting the field to be single-line. A lone carriage return counts:
     * [lineSequence] treats it as a terminator too.
     */
    private fun oneLine(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ')


    fun decode(raw: String): List<String> =
        raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX).toList()
}
