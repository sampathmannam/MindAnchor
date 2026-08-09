package org.mindanchor.vitals

import java.time.LocalDate

/**
 * One day's vitals, reduced from whatever Health Connect happened to hold.
 *
 * ## Why every field is nullable
 *
 * This app is built to work with any wearable and to survive somebody
 * changing watches, which is the whole reason it talks to Health Connect
 * rather than to one vendor's SDK. But Health Connect is a shared store,
 * not a promise: a COROS Pacer 3 writes heart rate and exercise and
 * nothing else, a sleep-tracking ring would write the opposite, and any
 * watch can simply spend a day on the charger. There is no wearable that
 * makes all nine of these fields true at once, so none of them may be
 * assumed present. A field being null is not an error condition to work
 * around later — it is the normal, expected shape of a lot of days.
 *
 * ## Why completeness is a field, not an afterthought
 *
 * A day with one resting-heart-rate reading and nothing else is not "some
 * data" in the sense that matters to a person reading it back — it is
 * mostly silence with one number attached. Anything downstream that turns
 * vitals into a claim about somebody's night needs to be able to refuse to
 * speak on days like that, the same way [org.mindanchor.sleep.Deviation]
 * refuses to speak without enough nights. [fieldsPresent] and
 * [completeness] exist so that refusal is a comparison against a number
 * instead of a judgement call scattered across call sites.
 */
data class DailyVitals(
    val date: LocalDate?,
    /** Resting bpm, the mean of the day's resting-heart-rate readings. */
    val restingHeartRate: Double?,
    /** Mean bpm across the day's instantaneous heart-rate samples. */
    val meanHeartRate: Double?,
    /** Lowest bpm across the day's instantaneous heart-rate samples. */
    val minHeartRate: Double?,
    /** RMSSD in milliseconds, the mean of the day's HRV readings. */
    val hrvRmssd: Double?,
    /** Total minutes asleep, summed across the day's sleep sessions. */
    val sleepMinutes: Int?,
    /**
     * Minutes after 18:00 that the night's sleep began, on the same
     * convention as [org.mindanchor.sleep.Deviation.minutesAfterSixPm]: an
     * evening running past midnight must read as later, not wrap around to
     * near zero and look like the earliest night of the week.
     */
    val sleepOnset: Int?,
    /** Steps, summed across the day's records. */
    val steps: Long?,
    /** Minutes covered by the day's exercise sessions. */
    val activeMinutes: Int?,
    /**
     * Total minutes of meditation / breathing / guided mindfulness,
     * summed across the day's [MindfulnessSessionRecord] sessions. The
     * mental-health signal the wearable story was missing before — a
     * 34-RCT meta-analysis (Gál et al., J Affect Disord 2020) reports
     * app-based mindfulness reduces stress at g = 0.46, anxiety at
     * g = 0.16–0.40, depression at g = 0.24–0.43. The signal here is
     * not "this fixes X" — it is "this is a thing worth showing the
     * person, alongside steps and sleep, in the same units."
     */
    val mindfulnessMinutes: Int? = null,
) {

    /** How many of [FIELD_COUNT] measured fields are present. */
    val fieldsPresent: Int
        get() = listOf(
            restingHeartRate, meanHeartRate, minHeartRate, hrvRmssd,
            sleepMinutes, sleepOnset, steps, activeMinutes, mindfulnessMinutes,
        ).count { it != null }

    /** [fieldsPresent] as a fraction of [FIELD_COUNT]; 0.0 when the watch had nothing to say. */
    val completeness: Double
        get() = fieldsPresent.toDouble() / FIELD_COUNT

    companion object {

        /** The nine measured fields; [date] is a key, not a measurement, so it is not counted. */
        const val FIELD_COUNT = 9

        /** A day where nothing was read — the honest shape when Health Connect is unavailable or empty. */
        fun empty(date: LocalDate? = null): DailyVitals = DailyVitals(
            date = date,
            restingHeartRate = null,
            meanHeartRate = null,
            minHeartRate = null,
            hrvRmssd = null,
            sleepMinutes = null,
            sleepOnset = null,
            steps = null,
            activeMinutes = null,
            mindfulnessMinutes = null,
        )
    }
}

/**
 * The reductions behind [DailyVitals], kept apart from any Android or
 * Health Connect type so that a wrong assumption here is caught by a fast
 * JVM test rather than by a person's data looking wrong on screen.
 *
 * Every function takes the plainest primitives that carry the meaning —
 * timestamp-in-epoch-millis paired with a value, or a start/end pair for a
 * session — and every one treats an empty list as "nothing was recorded",
 * returning null rather than a misleading zero. Zero steps and no step
 * data are different facts; only [DailyVitals.completeness] is allowed to
 * collapse that distinction, and only on purpose.
 */
object DailyVitalsReducer {

    private const val MINUTES_PER_DAY = 1440
    private const val SIX_PM_MINUTE = 18 * 60
    private const val MILLIS_PER_MINUTE = 60_000L

    /** Mean bpm of instantaneous heart-rate [samples] (timestamp millis to bpm). */
    fun meanHeartRate(samples: List<Pair<Long, Double>>): Double? =
        if (samples.isEmpty()) null else samples.sumOf { it.second } / samples.size

    /** Lowest bpm among instantaneous heart-rate [samples]. */
    fun minHeartRate(samples: List<Pair<Long, Double>>): Double? =
        samples.minOfOrNull { it.second }

    /** Mean bpm of the day's resting-heart-rate [readings] (timestamp millis to bpm). */
    fun restingHeartRate(readings: List<Pair<Long, Double>>): Double? =
        if (readings.isEmpty()) null else readings.sumOf { it.second } / readings.size

    /** Mean RMSSD in milliseconds of the day's HRV [readings] (timestamp millis to ms). */
    fun hrvRmssd(readings: List<Pair<Long, Double>>): Double? =
        if (readings.isEmpty()) null else readings.sumOf { it.second } / readings.size

    /**
     * Total minutes asleep, summing every session's (start millis, end
     * millis) duration in [sessions]. Overlapping sessions are not merged
     * — Health Connect sessions from a single, honest source do not
     * overlap, and silently discarding an overlap would hide a genuine
     * data problem rather than fix one.
     */
    fun sleepMinutes(sessions: List<Pair<Long, Long>>): Int? {
        if (sessions.isEmpty()) return null
        val totalMillis = sessions.sumOf { (start, end) -> (end - start).coerceAtLeast(0L) }
        return (totalMillis / MILLIS_PER_MINUTE).toInt()
    }

    /**
     * Onset of the night's main sleep session, as minutes after 18:00 —
     * see [org.mindanchor.sleep.Deviation.minutesAfterSixPm], whose
     * formula this mirrors exactly, for why that origin rather than
     * midnight.
     *
     * "Main" session is the earliest start among [sessions]: a nap taken
     * later in the day is not what "went to bed" means, and picking the
     * earliest start is the simplest rule that gets that right without
     * trying to classify sessions by length or time of day.
     *
     * [zoneOffsetMinutes] is the caller's local offset for that instant;
     * Health Connect timestamps are absolute (UTC-anchored) and carry no
     * zone of their own to fall back on, so the caller must supply one.
     */
    fun sleepOnset(sessions: List<Pair<Long, Long>>, zoneOffsetMinutes: Int): Int? {
        val earliestStart = sessions.minOfOrNull { it.first } ?: return null
        val localEpochMinute = Math.floorDiv(earliestStart, MILLIS_PER_MINUTE) + zoneOffsetMinutes
        val minuteOfDay = localEpochMinute.mod(MINUTES_PER_DAY.toLong()).toInt()
        return ((minuteOfDay - SIX_PM_MINUTE) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    /** Total steps across the day's [counts]. */
    fun steps(counts: List<Long>): Long? =
        if (counts.isEmpty()) null else counts.sum()

    /**
     * Minutes covered by the day's exercise [sessions] (start millis, end
     * millis). Overlapping sessions are summed rather than merged, for the
     * same reason as [sleepMinutes].
     */
    fun activeMinutes(sessions: List<Pair<Long, Long>>): Int? {
        if (sessions.isEmpty()) return null
        val totalMillis = sessions.sumOf { (start, end) -> (end - start).coerceAtLeast(0L) }
        return (totalMillis / MILLIS_PER_MINUTE).toInt()
    }

    /**
     * Total minutes of meditation / breathing / guided mindfulness across
     * the day's [MindfulnessSessionRecord] sessions. Same overlap rule
     * as [activeMinutes] — a session that is still running at midnight
     * would be split into two and we accept the resulting double-count
     * over a wrong answer. A person meditating past midnight is not a
     * dataset worth a more careful implementation.
     */
    fun mindfulnessMinutes(sessions: List<Pair<Long, Long>>): Int? {
        if (sessions.isEmpty()) return null
        val totalMillis = sessions.sumOf { (start, end) -> (end - start).coerceAtLeast(0L) }
        return (totalMillis / MILLIS_PER_MINUTE).toInt()
    }
}
