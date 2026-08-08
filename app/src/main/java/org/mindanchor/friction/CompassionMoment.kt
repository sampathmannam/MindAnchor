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
