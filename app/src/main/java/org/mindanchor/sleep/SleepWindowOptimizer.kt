package org.mindanchor.sleep

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Suggests a wind-down window from the user's own recent sleep onsets.
 *
 * ## The finding this is built on
 *
 * Sleep *regularity* predicts mental-health and metabolic outcomes more
 * strongly than sleep *duration* (Windred et al. 2024, *SLEEP* 47(1):zsad285,
 * DOI 10.1093/sleep/zsad285 — UK Biobank cohort, the Sleep Regularity
 * Index). The launcher already computes a 0–100 regularity score in
 * [SleepMath.regularityScore]; what was missing was a concrete suggestion
 * for *which* minute of the day to anchor that regularity around.
 *
 * ## What the optimizer does
 *
 * Looks at the last [MIN_NIGHTS] or more sleep onsets, takes the median
 * (one all-nighter should not move the suggestion), and proposes a
 * wind-down window that:
 *
 * - starts 90 minutes before the median sleep onset — the wind-down has
 *   to land *before* sleep, not during it;
 * - ends 8 hours after the median sleep onset — a sensible default
 *   sleep length. Anyone whose actual sleep is shorter or longer than
 *   8h can nudge either end of the window by hand, the same way they
 *   can nudge any other quiet-hours time.
 *
 * The suggestion is opt-in: the optimizer returns it, the settings panel
 * shows it, and a button applies it. Nothing is set automatically. The
 * [org.mindanchor.data.SunsetPrefs.setWindow] call that consumes the
 * suggestion flips the same "user customised" flag as a manual nudge, so
 * a later chronotype change will not stomp on the optimized window.
 *
 * ## What the optimizer does NOT do
 *
 * - It does not interpret the data. "Your median sleep onset is 23:45"
 *   is a fact about a phone screen, checkable by the user, and wrong
 *   only if the arithmetic is wrong. What the median means in the
 *   context of the user's life is for them to say.
 * - It does not score the user's behaviour. A regularity of 40 is not
 *   a judgement; it is a measurement, on the same scale as 90, and
 *   the same wording ("Your nights cluster around X") applies at both
 *   ends.
 * - It does not write anything. [suggest] is pure. The caller decides
 *   whether to apply, ignore, or show.
 */
object SleepWindowOptimizer {

    /**
     * The same floor [Deviation.MIN_NIGHTS] uses: with fewer than
     * this many nights, "usual" is not a thing that exists yet, and
     * a suggestion built on less is a guess dressed as data.
     */
    const val MIN_NIGHTS = 5

    /**
     * How long before the median sleep onset the wind-down should
     * start. One hour is too short to actually wind down; two hours
     * is too long for a quiet-hours window the user has to live with
     * every evening. 90 minutes is the middle.
     */
    const val WIND_DOWN_LEAD_MINUTES = 90L

    /**
     * The default sleep length. The launcher's wind-down window
     * covers the *whole* quiet-hours period, not just the wind-down;
     * the part past sleep onset is the "phone stays quiet through
     * the night" half, and 8 hours is a sensible default.
     */
    const val DEFAULT_SLEEP_HOURS = 8L

    /**
     * A suggested wind-down window, or null when there is not
     * enough data to suggest anything.
     *
     * The returned window is the optimiser's *recommendation*, not
     * a verdict on the user's life. The settings panel renders it
     * as a one-line suggestion with a single button to apply it.
     */
    fun suggest(windows: List<SleepWindow>, zone: ZoneId): Suggestion? {
        if (windows.size < MIN_NIGHTS) return null
        // The Deviation class already has a "minutes after 18:00" framing
        // that handles the midnight wraparound correctly: a 00:30 onset
        // becomes 390, not 30. Using it here means the median of a week
        // that spans midnight lands in the right place; using raw
        // minute-of-day would put it at the wrong end of the clock.
        val onsets = windows.map { minuteOfDay(it.startMillis, zone) }
            .map { Deviation.minutesAfterSixPm(it) }
        val medianAfterSix = Deviation.usual(onsets) ?: return null
        // medianAfterSix is a count of minutes past 18:00, in [0, 1440).
        // Convert back to minute-of-day for LocalTime.of, but with the
        // correct wrap (e.g. 390 = 18:00 + 390 = 00:30, not 18:30).
        val median = (medianAfterSix + 18 * 60) % 1440
        val startMinute = ((median - WIND_DOWN_LEAD_MINUTES).toInt() + 1440) % 1440
        val endMinute = (median + DEFAULT_SLEEP_HOURS.toInt() * 60) % 1440
        return Suggestion(
            medianOnset = LocalTime.of(median / 60, median % 60),
            startTime = LocalTime.of(startMinute / 60, startMinute % 60),
            endTime = LocalTime.of(endMinute / 60, endMinute % 60),
            nightsUsed = windows.size,
        )
    }

    private fun minuteOfDay(millis: Long, zone: ZoneId): Int {
        val dateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        return dateTime.hour * 60 + dateTime.minute
    }

    /**
     * What [suggest] returns.
     *
     * [nightsUsed] is the number of windows the suggestion was
     * built on. The settings panel surfaces it as "from your last
     * N nights" so the user can read the suggestion's confidence
     * at a glance, without doing the arithmetic themselves.
     */
    data class Suggestion(
        val medianOnset: LocalTime,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val nightsUsed: Int,
    )
}
