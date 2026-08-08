package org.mindanchor.model

/**
 * A check-in response. One record per accepted
 * check-in; rejections are *not* recorded (see
 * docs/research/26 §B3, B6).
 *
 * The data class is the *response*, not the
 * mood state. The check-in engine never infers
 * anything from the response; it captures the
 * response and stores it.
 *
 * ## Why this is *not* a mood log
 *
 * The check-in has no valence / arousal / mood
 * field. It has a single 1-5 rating on the
 * question "How did today sit?" and an optional
 * 1-3 sentence free-text reflection. The rating
 * is a N-of-1 within-person signal the user can
 * chart over time; the launcher does *not*
 * interpret the rating, has no cut-off, and has
 * no screen-positive interpretation. The
 * project's no-mood-inference rule is enforced
 * by the absence of a mood field.
 *
 * Evidence: docs/research/26 §B5, citing Hays
 * 2009 (PROMIS Global Health, 1-5 single-item
 * global rating) and Robins 2001 (single-item
 * self-esteem measure). The literature does not
 * validate this exact shape; the brief is honest
 * about the gap.
 */
data class CheckIn(
    /**
     * The single-item rating. 1 = "rough,"
     * 2 = "low," 3 = "ok," 4 = "good,"
     * 5 = "bright." The anchors are user-language
     * (no clinical anchor). The launcher does
     * not surface a cut-off or screen-positive
     * interpretation.
     */
    val rating: Int,
    /**
     * Optional 1-3 sentence free-text reflection.
     * Empty string for "no reflection." The
     * launcher does not interpret or summarise;
     * the user owns the words.
     */
    val reflection: String = "",
    /**
     * The moment the check-in was accepted, in
     * epoch milliseconds. The launcher uses this
     * for the "rate limit between check-ins"
     * check (Wrzus & Neubauer 2023 median 120
     * min inter-prompt interval).
     */
    val atMillis: Long,
) {
    init {
        require(rating in MIN_RATING..MAX_RATING) {
            "rating must be in $MIN_RATING..$MAX_RATING, was $rating"
        }
        require(reflection.length <= MAX_REFLECTION) {
            "reflection must be ≤ $MAX_REFLECTION chars, was ${reflection.length}"
        }
        require(atMillis > 0L) {
            "atMillis must be > 0, was $atMillis"
        }
    }

    companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5
        const val MAX_REFLECTION = 1_000
    }
}

/**
 * Storage codec for [CheckIn]s. One record per
 * line, tab-separated. The body of the reflection
 * is *base64-encoded* so it can contain any
 * character (tabs, newlines, unicode) without
 * breaking the line-delimited format. Same
 * base64 pattern as [NoteStore].
 *
 * The codec is *dumb* — validation is the caller's
 * job. Malformed lines are silently skipped.
 */
object CheckInStore {

    /**
     * Encode a list of check-ins. The list is
     * written in the order given (the caller is
     * expected to pass them in append-order).
     */
    fun encode(checkIns: List<CheckIn>): String =
        checkIns.joinToString("\n") { it.encode() }

    /**
     * Decode a text form back into a list of
     * check-ins. Malformed lines are silently
     * skipped; an out-of-range rating or an
     * over-long reflection is rejected.
     */
    fun decode(raw: String): List<CheckIn> =
        raw.lineSequence().mapNotNull(::decodeLine).toList()

    /**
     * Encode a single check-in to a line. The
     * format is `atMillis\trating\tbase64(reflection)`.
     */
    fun CheckIn.encode(): String {
        val reflectionB64 = java.util.Base64.getEncoder()
            .encodeToString(reflection.toByteArray(Charsets.UTF_8))
        return "$atMillis\t$rating\t$reflectionB64"
    }

    /**
     * Decode a single line. Returns null if the
     * line is malformed (wrong number of fields,
     * non-numeric atMillis / rating, body not
     * valid base64, body does not decode to valid
     * UTF-8, rating out of range, reflection
     * over MAX_REFLECTION).
     */
    fun decodeLine(line: String): CheckIn? {
        if (line.isEmpty()) return null
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return null
        val atMillis = parts[0].toLongOrNull() ?: return null
        val rating = parts[1].toIntOrNull() ?: return null
        val reflectionB64 = parts[2]
        if (atMillis <= 0L) return null
        if (rating !in CheckIn.MIN_RATING..CheckIn.MAX_RATING) return null
        val reflectionBytes = try {
            java.util.Base64.getDecoder().decode(reflectionB64)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val reflection = try {
            String(reflectionBytes, Charsets.UTF_8)
        } catch (e: java.nio.charset.MalformedInputException) {
            return null
        }
        if (reflection.length > CheckIn.MAX_REFLECTION) return null
        return CheckIn(rating = rating, reflection = reflection, atMillis = atMillis)
    }
}

/**
 * The on-device check-in state. v0.20.1 round 5
 * (docs/research/26-notes-and-check-in.md).
 *
 * The state holds *only* accepted check-ins. The
 * count of consecutive rejections in the current
 * day is *not* in this state — it is in the
 * [CheckInEngine] rate-limit state, which is
 * in-memory only and reset on app restart.
 */
data class CheckInState(
    val checkIns: List<CheckIn> = emptyList(),
) {
    /**
     * Add a new check-in. Pure function.
     */
    fun add(checkIn: CheckIn): CheckInState =
        copy(checkIns = checkIns + checkIn)

    /**
     * The check-ins accepted in the given day
     * (milliseconds-since-epoch day). Used to
     * count the daily cap. Pure function.
     */
    fun acceptedInDay(dayStartMillis: Long, dayEndMillis: Long): List<CheckIn> =
        checkIns.filter { it.atMillis in dayStartMillis until dayEndMillis }
}

/**
 * The check-in engine's rate-limit state. v0.20.1
 * round 5.
 *
 * The engine gates check-in prompts by:
 *  - **2-hour minimum** between accepted check-ins
 *    (Wrzus & Neubauer 2023, 477-study meta-analysis)
 *  - **4 prompts/day soft cap** (Williams 2021,
 *    m-EMA compliance sweet spot)
 *  - **Auto-pause after 3 consecutive rejections in
 *    a day** (project-side defensive default; not
 *    a literature-derived number, see brief §B6)
 *
 * The state is *transient* — it is reset on app
 * restart and never written to disk. The launcher
 * prefers a missed check-in over a permanent record
 * of "user said no 47 times" (the no-mood-inference
 * rule).
 */
data class CheckInRateLimit(
    /**
     * The moment of the most recently *accepted*
     * check-in. The engine does not re-prompt
     * until [MIN_INTERVAL_MILLIS] has passed since
     * this moment. Zero is a valid value
     * (midnight 1970-01-01), so the engine
     * treats it as "never accepted" and only
     * triggers when > 0.
     */
    val lastAcceptedMillis: Long = 0L,
    /**
     * The number of check-ins accepted in the
     * current day (00:00 local-time to now). The
     * engine does not re-prompt when this
     * reaches [DAILY_CAP].
     */
    val acceptedToday: Int = 0,
    /**
     * The millis-since-epoch at which the
     * "current day" started (00:00 local time).
     * Used to detect a day-rollover. The sentinel
     * value [UNINITIALISED_DAY] marks a fresh
     * rate-limit state that has not yet been
     * initialised; we cannot use 0L because 0L is
     * a valid day-start (midnight 1970-01-01).
     */
    val dayStartMillis: Long = UNINITIALISED_DAY,
    /**
     * The number of consecutive rejections in the
     * current day. When this reaches
     * [AUTO_PAUSE_REJECTIONS], the engine stops
     * queuing for the rest of the day.
     */
    val consecutiveRejections: Int = 0,
    /**
     * Whether the engine is in the auto-paused
     * state. When true, the engine does not
     * re-queue any check-ins for the rest of the
     * day, regardless of trigger events.
     */
    val autoPaused: Boolean = false,
) {
    companion object {
        /**
         * The minimum interval between accepted
         * check-ins. Wrzus & Neubauer 2023 median
         * is 120 minutes; we round to 90 minutes
         * to leave a *little* room for the user to
         * feel some signal (brief §B2).
         */
        const val MIN_INTERVAL_MILLIS: Long = 90L * 60L * 1000L

        /**
         * The soft cap on check-ins per day.
         * Williams 2021 sweet spot is 1-3 prompts
         * at 87% compliance; 4+ drops to 77%. We
         * cap at 4, with the explicit caveat that
         * this is the *upper* end of the sweet
         * spot — most users will see 2-3.
         */
        const val DAILY_CAP: Int = 4

        /**
         * The number of consecutive rejections in
         * a day that triggers the auto-pause.
         * Project-side defensive default; not a
         * literature-derived number (brief §B6).
         * 3 balances "give the user room to say
         * no sometimes" against "stop asking when
         * the user has stopped saying yes."
         */
        const val AUTO_PAUSE_REJECTIONS: Int = 3

        /**
         * The sentinel for an uninitialised day
         * start. We cannot use 0L because 0L is
         * a valid day-start (midnight 1970-01-01).
         * [Long.MIN_VALUE] is the natural "no
         * value" sentinel for a non-nullable Long.
         */
        const val UNINITIALISED_DAY: Long = Long.MIN_VALUE
    }
}

/**
 * The check-in engine. Pure functions; the engine
 * state is in [CheckInRateLimit] and the accepted
 * check-ins are in [CheckInState]. The engine does
 * *not* talk to Android (no Context, no AlarmManager);
 * the trigger is a separate concern (see
 * `CheckInTrigger` in the UI round).
 */
object CheckInEngine {

    /**
     * Decide whether a check-in should fire, given
     * the current rate-limit state, the on-disk
     * check-in state, and the current time.
     *
     * Returns true if:
     *  - The engine is not auto-paused
     *  - Less than [DAILY_CAP] check-ins have been
     *    accepted today
     *  - The most recent accepted check-in was more
     *    than [MIN_INTERVAL_MILLIS] ago
     *
     * False otherwise. Pure function.
     */
    fun shouldFire(
        rateLimit: CheckInRateLimit,
        state: CheckInState,
        nowMillis: Long,
    ): Boolean {
        // Day rollover: a fresh day resets the
        // auto-paused and daily counts. We must
        // check the *post-rollover* state, because
        // the user might have been auto-paused
        // yesterday but it's now a new day.
        val (effectiveToday, _) = rolloverIfNeeded(rateLimit, nowMillis)
        if (effectiveToday.autoPaused) return false
        if (effectiveToday.acceptedToday >= CheckInRateLimit.DAILY_CAP) return false
        if (effectiveToday.lastAcceptedMillis > 0L &&
            nowMillis - effectiveToday.lastAcceptedMillis < CheckInRateLimit.MIN_INTERVAL_MILLIS
        ) {
            return false
        }
        return true
    }

    /**
     * Record an *accepted* check-in. Returns the
     * new rate-limit state and the new check-in
     * state. Pure function. The reflection is
     * not parsed; the user owns the words.
     */
    fun recordAcceptance(
        rateLimit: CheckInRateLimit,
        state: CheckInState,
        checkIn: CheckIn,
        nowMillis: Long,
    ): Pair<CheckInRateLimit, CheckInState> {
        val (effectiveToday, _) = rolloverIfNeeded(rateLimit, nowMillis)
        return Pair(
            effectiveToday.copy(
                lastAcceptedMillis = nowMillis,
                acceptedToday = effectiveToday.acceptedToday + 1,
                consecutiveRejections = 0,
            ),
            state.add(checkIn),
        )
    }

    /**
     * Record a *rejection*. Returns the new
     * rate-limit state. Pure function. The
     * rejection itself is *not* stored; the
     * project rule is "the user's behaviour
     * trace is not a product surface" (brief
     * §B3, B6).
     */
    fun recordRejection(
        rateLimit: CheckInRateLimit,
        nowMillis: Long,
    ): CheckInRateLimit {
        val (effectiveToday, _) = rolloverIfNeeded(rateLimit, nowMillis)
        val newConsecutive = effectiveToday.consecutiveRejections + 1
        return effectiveToday.copy(
            consecutiveRejections = newConsecutive,
            autoPaused = newConsecutive >= CheckInRateLimit.AUTO_PAUSE_REJECTIONS,
        )
    }

    /**
     * Reset the rate-limit state (e.g. for a
     * user-triggered "re-enable" after an
     * auto-pause). Pure function.
     */
    fun reset(rateLimit: CheckInRateLimit, nowMillis: Long): CheckInRateLimit {
        return rateLimit.copy(
            lastAcceptedMillis = 0L,
            acceptedToday = 0,
            consecutiveRejections = 0,
            autoPaused = false,
            dayStartMillis = computeDayStart(nowMillis),
        )
    }

    /**
     * Detect a day rollover. If the current time
     * is past the day boundary, the daily counts
     * are reset. Pure function.
     *
     * The "current day" boundary is local-time
     * 00:00. The [dayStartMillis] is the millis-
     * since-epoch at 00:00 of the current day
     * in the device's local timezone. The engine
     * detects a rollover by comparing the
     * current [nowMillis]'s day boundary to the
     * stored [dayStartMillis]: if they differ, a
     * new day has begun.
     *
     * The first call (when [dayStartMillis] is 0)
     * initialises the day boundary and clears the
     * daily counters. The last-accepted timestamp
     * is preserved across both initialisation and
     * rollover, because the 2h minimum interval
     * is "since last accepted", not "today only."
     */
    internal fun rolloverIfNeeded(
        rateLimit: CheckInRateLimit,
        nowMillis: Long,
    ): Pair<CheckInRateLimit, Boolean> {
        val nowDay = computeDayStart(nowMillis)
        if (rateLimit.dayStartMillis == CheckInRateLimit.UNINITIALISED_DAY) {
            // First call; initialise the day
            // boundary and clear the daily
            // counters. The last-accepted
            // timestamp is preserved (the 2h
            // minimum is "since last accepted",
            // not "today only").
            return Pair(
                rateLimit.copy(
                    dayStartMillis = nowDay,
                    acceptedToday = 0,
                    consecutiveRejections = 0,
                    autoPaused = false,
                ),
                true,
            )
        }
        if (nowDay > rateLimit.dayStartMillis) {
            // Day rollover: reset the daily counts,
            // clear the auto-pause, and keep the
            // last-accepted timestamp.
            return Pair(
                rateLimit.copy(
                    dayStartMillis = nowDay,
                    acceptedToday = 0,
                    consecutiveRejections = 0,
                    autoPaused = false,
                ),
                true,
            )
        }
        return Pair(rateLimit, false)
    }

    /**
     * Compute the start-of-day millis for a given
     * moment. The default implementation uses
     * java.time.LocalDate; tests can override.
     * Pure function.
     */
    internal fun computeDayStart(millis: Long): Long {
        // Use the system default time zone. The
        // caller (the trigger layer) supplies the
        // current time; the engine does not
        // depend on the system clock.
        val zone = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(millis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}
