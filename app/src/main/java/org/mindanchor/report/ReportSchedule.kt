package org.mindanchor.report

import java.time.LocalDateTime

/**
 * When the nightly report may run, and what to do when it may not.
 *
 * ## Why this is not WorkManager
 *
 * It was, for exactly one commit. `setRequiresCharging(true)` and
 * `setRequiresDeviceIdle(true)` express this in two lines and the library
 * handles the rest, which is why it was the obvious choice.
 *
 * It also silently merges `android.permission.ACCESS_NETWORK_STATE` into
 * the manifest, because WorkManager supports network constraints whether
 * or not anything asks for one. This app declares no network permission
 * at all — that is the whole basis of the promise on the About screen
 * that nothing leaves the phone, and `PrivacyTest` asserts it against the
 * app as actually installed, which is how the merge was caught.
 *
 * The permission could have been stripped back out with
 * `tools:node="remove"`. That was rejected: WorkManager's constraint
 * trackers read connectivity through `ConnectivityManager`, and whether
 * any given code path does so when no work declares a network constraint
 * is a question that can only be settled on a device. There is no device
 * here. Trading a structural, tested privacy guarantee for an untestable
 * assumption about a library's internals is a bad trade at any odds, and
 * the guarantee is the more important half by a wide margin.
 *
 * So the two constraints are checked here instead, explicitly. It is more
 * code, it uses only APIs the rest of this app already uses, it adds no
 * permission of any kind, and — the part that makes it worth writing —
 * the decision becomes a pure function that can be tested rather than a
 * property of a scheduler that cannot.
 *
 * ## Why charging and not-interactive, both
 *
 * The report reads roughly a month of Health Connect history plus every
 * stored check-in, which is real work nobody asked to watch happen.
 * Charging means it costs no battery anyone notices. Not interactive
 * means the screen is off and nobody is mid-task. Either alone would let
 * this run while somebody is using the phone.
 */
object ReportSchedule {

    /**
     * The hour the night's report is attempted.
     *
     * Three in the morning: the day being reported on has closed
     * completely, so the "yesterday" [ReportScheduler] builds from is a
     * whole day rather than one still in progress, and almost nobody is
     * holding the phone.
     */
    const val RUN_HOUR = 3

    /**
     * How many hours past [RUN_HOUR] it will keep trying.
     *
     * A phone that was not charging at three may well be by four. Past
     * the end of this window the person is plausibly awake and using it,
     * and a report that arrives at lunchtime describing last night is
     * worth less than the battery it would cost — better to wait for the
     * next night. Kept small enough that [RUN_HOUR] + [RETRY_HOURS] never
     * crosses midnight, which this deliberately does not handle.
     */
    const val RETRY_HOURS = 5

    enum class Decision {
        /** Conditions are met and today's report has not been written. */
        RUN,

        /** Not yet, but still inside tonight's window — try again in an hour. */
        RETRY,

        /** Nothing more tonight. Arm the next one. */
        WAIT_FOR_TOMORROW,
    }

    /**
     * What to do at this moment.
     *
     * [lastRunDay] is the calendar day the report was last actually
     * written — [ReportStore.generatedDay] — and it is what stops a retry
     * from rebuilding a report that already exists. It is compared
     * against [today] as a plain string because that is how it is stored;
     * an unparseable or absent value simply means "not today", which is
     * the safe reading.
     */
    fun decide(
        charging: Boolean,
        interactive: Boolean,
        hourOfDay: Int,
        lastRunDay: String?,
        today: String,
    ): Decision = when {
        lastRunDay == today -> Decision.WAIT_FOR_TOMORROW
        charging && !interactive -> Decision.RUN
        hourOfDay in RUN_HOUR until (RUN_HOUR + RETRY_HOURS) -> Decision.RETRY
        else -> Decision.WAIT_FOR_TOMORROW
    }

    /**
     * When to set the next alarm, given what was just decided.
     *
     * [Decision.RUN] gets tomorrow too: the run happens now, and the
     * alarm being armed is the one after it. Nothing here ever leaves
     * AlarmManager holding no alarm at all, which is the failure mode
     * that ends the feature silently rather than loudly.
     */
    fun nextRun(now: LocalDateTime, decision: Decision): LocalDateTime = when (decision) {
        Decision.RETRY -> now.plusHours(1)
        Decision.RUN, Decision.WAIT_FOR_TOMORROW -> nextNight(now)
    }

    /** The next occurrence of [RUN_HOUR], which may be later today. */
    private fun nextNight(now: LocalDateTime): LocalDateTime {
        val tonight = now.toLocalDate().atTime(RUN_HOUR, 0)
        return if (tonight.isAfter(now)) tonight else tonight.plusDays(1)
    }
}
