package org.mindanchor.support

import org.mindanchor.R

/**
 * A crisis line, and the country it serves.
 *
 * [labelRes] carries the human text ("Call 988 — Suicide & Crisis
 * Lifeline"); [number] is what gets dialled.
 */
data class CrisisLine(
    val country: String,
    val number: String,
    val labelRes: Int,
)

/**
 * Which crisis numbers to show, and in what order.
 *
 * The card used to list the US line and the Indian one, hardcoded, in that
 * order. Someone in Manchester or Melbourne opening the most important
 * screen in the app was met with two numbers that do not work where they
 * are — at the moment they were least able to go looking for a better one.
 *
 * So the line for the reader's own country goes first. Everything else
 * stays visible underneath rather than being hidden, because a locale is a
 * guess: people travel, borrow phones, and set their region to somewhere
 * they do not live. Nothing is ever removed on the strength of that guess,
 * only reordered.
 *
 * ## On trusting these numbers
 *
 * These are emergency numbers, so they deserve more suspicion than
 * ordinary constants. Every one below is a long-standing national service
 * rather than a campaign number that rotates. They still need periodic
 * re-checking against the operators themselves, and the card keeps
 * pointing at findahelpline.com for everywhere not listed — that
 * directory is maintained far more often than this file will be.
 */
object CrisisLines {

    /** 116 123 is the harmonised European number for emotional support. */
    private const val EU_EMOTIONAL_SUPPORT = "116 123"

    val ALL: List<CrisisLine> = listOf(
        CrisisLine("US", "988", R.string.crisis_us),
        CrisisLine("CA", "988", R.string.crisis_ca),
        CrisisLine("IN", "14416", R.string.crisis_india),
        CrisisLine("GB", EU_EMOTIONAL_SUPPORT, R.string.crisis_gb),
        CrisisLine("IE", EU_EMOTIONAL_SUPPORT, R.string.crisis_ie),
        CrisisLine("AU", "13 11 14", R.string.crisis_au),
        CrisisLine("NZ", "1737", R.string.crisis_nz),
    )

    /**
     * [ALL], with any line serving [countryCode] moved to the front and the
     * rest left in their original order.
     *
     * An unknown, blank or unrecognised country is not an error — it just
     * means no reordering, and the reader sees the same complete list they
     * would have seen before.
     */
    fun forCountry(countryCode: String?): List<CrisisLine> {
        val country = countryCode?.trim()?.uppercase().orEmpty()
        if (country.isEmpty()) return ALL
        val (local, rest) = ALL.partition { it.country == country }
        return local + rest
    }
}
