package org.mindanchor.friction

/**
 * A self-compassion micro-moment — Neff 2003, *Self and Identity*
 * 2(2):85–101, doi:10.1080/15298860309027.
 *
 * `docs/research/15` §3 named the S-compassion micro-moment
 * pattern as a SOTA feature gap: a small, opt-in, scripted
 * exercise the launcher surfaces at the moment the user is
 * reaching for a doomscroll app. The literature is small but
 * positive:
 *  - Linardon et al. 2020, *J Clin Psychol* meta-analysis of 27
 *    RCTs of smartphone-delivered acceptance / mindfulness /
 *    self-compassion apps: psychological distress g = −0.32
 *    (95% CI −0.48 to −0.16), self-compassion g = 0.31
 *    (95% CI 0.07–0.56). PMID 32586436.
 *  - Liu et al. 2023, *Psicologia: Reflexão e Crítica* 36:32,
 *    doi:10.1186/s41155-023-00276-w — app-guided 4-week
 *    loving-kindness meditation in college students: significant
 *    increase in self-compassion, significant *decrease* in
 *    suicidal ideation.
 *
 * The mechanism is the Neff "Self-Compassion Break": name the
 * moment ("this is a moment of suffering"), recognise the
 * common humanity ("others feel this too"), offer a phrase of
 * self-kindness ("may I be kind to myself"). The three steps
 * are deliberately short — under a minute — so they fit
 * inside the existing friction gate's breath-pace overlay.
 *
 * The micro-moments are *opt-in* by the user. The launcher
 * never auto-surfaces one; it offers a *rotation* of the
 * user's own chosen moments at the moment of friction, so
 * the prompt is the user's own words rather than the
 * launcher's.
 */
data class CompassionMoment(
    /** Free-text. What the user is willing to say to themselves. */
    val phrase: String = "",
) {
    /** A moment is "live" when the user has written something. */
    val isLive: Boolean get() = phrase.trim().isNotEmpty()
}

/**
 * Pure list operations on [CompassionMoment]s — adding,
 * removing, capping. Kept here (next to the data class) so
 * the storage layer has a single place to call for the
 * mutation rules, the same way [SmallThings.add] /
 * [SmallThings.remove] live next to their data class.
 *
 * The MAX cap is the same as [SmallThings.MAX]: a launcher
 * rotating a wall of phrases would defeat the rotation.
 * Six is the most a person would write in a calm hour
 * (most write one or two) and the most that the gate
 * would rotate through without any single phrase
 * becoming wallpaper.
 */
object CompassionList {
    const val MAX = 6
    const val MAX_PHRASE = 140

    /** Trim, blank-blank out, and add to the list. No-op when
     *  the input is blank or the list is at MAX. The
     *  resulting phrase is trimmed and capped to
     *  [MAX_PHRASE] characters. */
    fun add(moments: List<CompassionMoment>, phrase: String): List<CompassionMoment> {
        val cleaned = phrase.trim().take(MAX_PHRASE)
        if (cleaned.isEmpty()) return moments
        if (moments.any { it.phrase.trim() == cleaned }) return moments
        if (moments.size >= MAX) return moments
        return moments + CompassionMoment(cleaned)
    }

    /** Drop the first match (trim-equal). No-op when the
     *  phrase is blank. */
    fun remove(moments: List<CompassionMoment>, phrase: String): List<CompassionMoment> {
        val cleaned = phrase.trim()
        if (cleaned.isEmpty()) return moments
        val idx = moments.indexOfFirst { it.phrase.trim() == cleaned }
        if (idx < 0) return moments
        return moments.toMutableList().apply { removeAt(idx) }
    }
}

/**
 * Storage codec for [CompassionMoment]s — the user's own set
 * of phrases the launcher rotates through. One per line in a
 * plain text file, following the [OpenLoop.encode] /
 * [SmallThings.encode] pattern.
 */
object CompassionStore {

    fun encode(moments: List<CompassionMoment>): String =
        moments.joinToString("\n") { it.phrase.trim() }

    fun decode(raw: String): List<CompassionMoment> =
        raw.lineSequence()
            .map { CompassionMoment(phrase = it.trim()) }
            .filter { it.isLive }
            .toList()

    /**
     * Picks one of the user's live moments to show, or null
     * when no live moments exist. Round-robin so the same
     * phrase does not become wallpaper; the FrictionTone
     * anti-habituation rule (`docs/research/07` §1) applies
     * here too.
     */
    fun rotate(moments: List<CompassionMoment>, reach: Int): CompassionMoment? {
        val live = moments.filter { it.isLive }
        if (live.isEmpty()) return null
        val index = if (reach < 0) 0 else reach % live.size
        return live[index]
    }
}
