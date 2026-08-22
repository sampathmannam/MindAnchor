package org.mindanchor.report

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.corpus.CorpusStore
import org.mindanchor.model.Moment
import org.mindanchor.model.MomentStore
import org.mindanchor.narrate.Narrators
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.sleep.SleepWindow
import org.mindanchor.usage.InferredStore
import org.mindanchor.usage.RhythmRepository
import org.mindanchor.vitals.DailyVitals
import org.mindanchor.vitals.MeasuredStore
import org.mindanchor.vitals.HealthConnectSource

/**
 * Arms the nightly report, and builds it when the alarm fires.
 *
 * [ReportSchedule] decides *whether* to run and *when* to try next;
 * everything here is the Android glue that reads the two conditions off
 * the device, arms the alarm, and does the work. Same shape as
 * [org.mindanchor.model.EmaScheduler], and for the same reason: the
 * decision is worth testing and the glue is not testable, so they are
 * kept apart.
 *
 * ## Why one alarm re-arms the next
 *
 * Every firing arms the following one, whatever it decided — a run, a
 * retry, or a night given up on. Nothing here ever leaves AlarmManager
 * holding nothing, which is the failure that ends a feature silently
 * instead of loudly.
 *
 * ## Why nothing here throws
 *
 * This fires at three in the morning with nobody watching, and it can
 * also fire while somebody is looking at their own home screen. A missing
 * Health Connect provider, a corrupt corpus, a DataStore write that did
 * not land — none of them is worth a crash, and a quiet night with
 * nothing to report is [ReportComposer]'s ordinary, good outcome rather
 * than a failure at all. Every path is wrapped, and the next alarm is
 * armed in a `finally` so even an unexpected failure cannot end the
 * schedule.
 */
object ReportScheduler {

    private const val ACTION_REPORT = "org.mindanchor.REPORT_RUN"
    private const val REQUEST_CODE = 96

    /**
     * Days of history gathered per signal — comfortably above
     * [org.mindanchor.model.Baseline.MIN_OBSERVATIONS] so a handful of
     * missing days (a watch left on the charger, say) does not tip a
     * signal into [Report.notYetKnown].
     */
    private const val HISTORY_DAYS = 30

    /**
     * Mirrors [PatternFinder.WINDOW_DAYS]. Kept as its own constant,
     * rather than read off that object at every use below, so this file's
     * date arithmetic reads the same way [HISTORY_DAYS]'s does.
     */
    private const val PATTERN_WINDOW_DAYS = 90

    /**
     * How many days behind the report day the screen-rhythm recompute
     * reaches. Android keeps detailed usage events for only about a week,
     * so this is bounded by what the log can still answer — and a few
     * skipped nights (phone off the charger, say) heal themselves on the
     * next run instead of becoming holes in the ledger.
     */
    private const val RHYTHM_BACKFILL_DAYS = 6L

    /**
     * Arms, or re-arms, the nightly alarm.
     *
     * Safe to call every time the setting is switched on, on boot, and on
     * every app start: it always sets the one alarm under the one request
     * code, so repeated calls replace it rather than accumulate. If the
     * report is switched off, the alarm is cancelled instead.
     */
    suspend fun ensureScheduled(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
            if (!ReportStore(appContext).enabled.first()) {
                alarmManager.cancel(pendingIntent(appContext))
                return
            }
            armNext(appContext, alarmManager, ReportSchedule.Decision.WAIT_FOR_TOMORROW)
        }
    }

    /** Switched off from settings: no nightly report until asked for again. */
    fun cancel(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            appContext.getSystemService(AlarmManager::class.java)
                ?.cancel(pendingIntent(appContext))
        }
    }

    /**
     * Builds the report right now, on demand, whatever the hour.
     *
     * This exists so the whole pipeline — Health Connect reads, the
     * app-measured store, the sleep fallback, composition, patterns,
     * storage — can be proven on a real phone in its first minute of use
     * rather than trusted to a 3am alarm nobody watches. It deliberately
     * skips [ReportSchedule.decide]: a person pressing a button IS the
     * charging-and-idle question answered.
     */
    internal suspend fun runNow(context: Context): Boolean =
        runCatching { buildAndStore(context.applicationContext) }.isSuccess

    /**
     * The alarm fired. Decide, maybe build, and always arm the next one.
     */
    internal suspend fun onAlarm(context: Context) {
        val appContext = context.applicationContext
        var decision = ReportSchedule.Decision.WAIT_FOR_TOMORROW
        try {
            val store = ReportStore(appContext)
            if (!store.enabled.first()) return
            // v0.25.10 (SOTA v2 bug-hunt B4): read the firing moment in
            // the system zone as a ZonedDateTime. The pre-fix shape was
            // a bare wall-clock-now read — DST-bound for the hourOfDay
            // decision. armNext already reads ZonedDateTime via
            // ReportSchedule.nextRun(Instant, ZoneId, decision); the
            // firing site is now the same shape so a timezone change
            // between arming and firing produces one consistent answer.
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            decision = ReportSchedule.decide(
                charging = isCharging(appContext),
                interactive = isInteractive(appContext),
                hourOfDay = now.hour,
                lastRunDay = runCatching { store.generatedDay.first() }.getOrNull(),
                today = LocalDate.now().toString(),
            )
            if (decision == ReportSchedule.Decision.RUN) {
                runCatching { buildAndStore(appContext) }
            }
        } catch (_: Throwable) {
            // Deliberately swallowed. See the class KDoc: nothing this
            // does at 3am is worth a crash, and the finally below is what
            // keeps tomorrow's alarm armed regardless.
        } finally {
            runCatching {
                val alarmManager = appContext.getSystemService(AlarmManager::class.java)
                if (alarmManager != null) armNext(appContext, alarmManager, decision)
            }
        }
    }

    private fun armNext(
        context: Context,
        alarmManager: AlarmManager,
        decision: ReportSchedule.Decision,
    ) {
        // v0.25.5: nextRun now takes the wall-clock-to-instant math as
        // its own parameter, so the zone is re-read at the moment the
        // answer is computed. A DST shift or a timezone change between
        // armings is handled here, not in a cached offset.
        val triggerAt = ReportSchedule
            .nextRun(Instant.now(), ZoneId.systemDefault(), decision)
            .toEpochMilli()
        val pending = pendingIntent(context)
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        // setAndAllowWhileIdle rather than setExact for the inexact path:
        // this does not need to land on the minute, and a report is
        // exactly the kind of thing Doze should be allowed to shift by a
        // few minutes. Both variants pierce Doze, which matters because
        // three in the morning is when Doze is deepest.
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReportAlarmReceiver::class.java).setAction(ACTION_REPORT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Whether the phone is on power.
     *
     * Read from the sticky `ACTION_BATTERY_CHANGED` broadcast, which is
     * available to any app without a permission of any kind — the whole
     * reason this is done here rather than left to WorkManager. A null
     * registration result, which is legal, reads as not charging: this
     * errs towards waiting, and waiting costs a night at worst.
     */
    private fun isCharging(context: Context): Boolean = runCatching {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        plugged != 0
    }.getOrDefault(false)

    /**
     * Whether the screen is on.
     *
     * Not a perfect reading of "nobody is using this phone" — a screen
     * can be on in a pocket — but it is the closest thing available
     * without a permission, and it is the same signal WorkManager's own
     * idle constraint is built on. Unreadable reads as interactive, which
     * again errs towards waiting.
     */
    private fun isInteractive(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
    }.getOrDefault(true)

    private suspend fun buildAndStore(context: Context) {
        val zone = ZoneId.systemDefault()
        // The night just finished. This only ever runs in the small hours,
        // well after the calendar day being reported on has closed.
        // Reporting on LocalDate.now() instead would describe a day still
        // in progress, and whether that day looked "usual" would depend on
        // the exact minute the phone happened to be charging rather than
        // on anything about the day itself.
        val reportDay = LocalDate.now(zone).minusDays(1)
        // Oldest first, so the lists handed to ReportComposer — and to
        // PatternFinder, which is stricter about it — are in the order
        // their KDocs say they are. Wide enough to cover both the ordinary
        // 30-day baseline and PatternFinder's 90-day search, plus one more
        // day: a pattern's oldest labelled day needs the *signal* from the
        // day before it, so the window has to reach one day further back
        // than the last day any label is drawn from.
        val windowDays = maxOf(HISTORY_DAYS, PATTERN_WINDOW_DAYS) + 1
        val windowDates = (windowDays downTo 1).map { reportDay.minusDays(it.toLong()) }
        // The most recent HISTORY_DAYS of windowDates, which is exactly
        // the 30-day list this read used to produce on its own — the
        // baseline's behaviour does not change just because the read
        // behind it now covers more ground.
        val historyDates = windowDates.takeLast(HISTORY_DAYS)
        val patternDates = windowDates.takeLast(PATTERN_WINDOW_DAYS)

        // One read, wide enough for both purposes — see the windowDates
        // comment above for why a second, separate read was not the answer.
        val vitalsByDate = (listOf(reportDay) + windowDates).associateWith { date ->
            HealthConnectSource.readDailyVitals(context, date, zone)
        }
        // A store read that fails — corrupt DataStore file, anything — is
        // "no check-ins on record", not a reason to abandon the vitals
        // half of the report.
        val moments = runCatching { MomentStore(context).moments.first() }
            .getOrDefault(emptyList())
        val momentsByDay = moments.groupBy { it.day }

        // What the app measured itself — camera PPG readings — and what
        // the phone can infer about sleep from its own screen rhythm.
        // Both fail soft to nothing: a corrupt store or a missing
        // UsageStats grant costs those sources, never the night.
        val measured = runCatching { MeasuredStore(context).all() }
            .getOrDefault(emptyList())
            .associate { (it.day to it.key) to it.value }
        val sleepByWake = runCatching {
            SleepRepository(context).estimate()?.windows.orEmpty()
        }.getOrDefault(emptyList()).associateBy { it.wakeDate }

        // The phone's own daytime rhythm — first pickup after 03:00 and
        // total screen minutes — recomputed for the recent days the
        // system's event log still remembers and written into a ledger
        // before it forgets them. The ledger, not the log, is what the
        // baseline reads: events survive on the device for about a week,
        // a baseline needs months, and this line is the difference.
        // Recomputing a day already on file just overwrites it with the
        // same answer. No usage-access grant, or any failure, costs this
        // source and never the night.
        val inferredStore = InferredStore(context)
        runCatching {
            val recent = (RHYTHM_BACKFILL_DAYS downTo 0L).map { reportDay.minusDays(it) }
            RhythmRepository(context).rhythms(recent)?.forEach { (date, rhythm) ->
                rhythm.firstUnlockMinute?.let {
                    inferredStore.record(date, Signal.FIRST_UNLOCK.name, it.toDouble())
                }
                rhythm.screenMinutes?.let {
                    inferredStore.record(date, Signal.SCREEN_TIME.name, it.toDouble())
                }
            }
        }
        val inferred = runCatching { inferredStore.all() }
            .getOrDefault(emptyList())
            .associate { (it.day to it.key) to it.value }

        val allDates = windowDates + reportDay
        val sourcedByDate = allDates.associateWith { date ->
            Signal.entries.mapNotNull { signal ->
                sourcedFor(signal, date, vitalsByDate, momentsByDay, measured, sleepByWake, inferred, zone)
                    ?.let { signal to it }
            }.toMap()
        }
        val today = sourcedByDate.getValue(reportDay).mapValues { it.value.value }
        val history = Signal.entries.associateWith { signal ->
            historyDates.mapNotNull { date -> sourcedByDate[date]?.get(signal)?.value }
        }
        // Per-signal coverage across the whole window: the fact that
        // answers "why is this signal missing?" before anyone has to ask
        // it. Computed here because this is the one place that already
        // holds every source's answer for every day.
        val coverage = Signal.entries.map { signal ->
            var days = 0
            var lastDay: String? = null
            var lastSource: MeasureSource? = null
            allDates.forEach { date ->
                sourcedByDate[date]?.get(signal)?.let {
                    days += 1
                    lastDay = date.toString()
                    lastSource = it.source
                }
            }
            Coverage(signal, days, lastDay, lastSource)
        }

        val report = ReportComposer.compose(
            day = reportDay.toString(),
            today = today,
            history = history,
            corpus = CorpusStore.load(context),
        )
        // A model failing to write anything — no engine yet, this phone
        // cannot run one, generation itself threw — is not a reason to
        // fail the whole night's report; the report stands on its own
        // without a paragraph on top of it. See Narrator's own KDoc for
        // why null is the ordinary outcome here, not an error.
        val narration = runCatching { Narrators.forDevice(context).narrate(report) }.getOrNull()
        // A failure here — LinkFinder's permutation test throwing on some
        // unexpected shape of data, say — is no different from finding
        // nothing: the report still stands on its own without it.
        val signalsByDay = Signal.entries.associateWith { signal ->
            windowDates.mapNotNull { date ->
                sourcedByDate[date]?.get(signal)?.let { date to it.value }
            }.toMap()
        }
        val labelsByDay = labelsByDay(windowDates, momentsByDay)
        val patterns = runCatching {
            PatternFinder.find(
                signalsByDay = signalsByDay,
                labelsByDay = labelsByDay,
                days = patternDates,
                todaysSignals = today,
                seed = reportDay.toEpochDay(),
            )
        }.getOrDefault(emptyList())
        // Whether the search could run at all, which is not the same
        // question as whether it found anything — see PatternFinder's own
        // KDoc, which says so and until now had nobody asking. Defaulting
        // to false on failure keeps the screen quiet rather than claiming
        // to still be learning when the truth is unknown.
        val patternsStillLearning = patterns.isEmpty() && runCatching {
            PatternFinder.stillLearning(signalsByDay, labelsByDay, patternDates)
        }.getOrDefault(false)
        val store = ReportStore(context)
        store.save(
            report = report,
            narration = narration?.text,
            patterns = patterns,
            generatedDay = LocalDate.now(zone).toString(),
            patternsStillLearning = patternsStillLearning,
        )
        store.saveCoverage(CoverageLedger.encode(coverage))
        store.saveFacts(FactsLedger.encode(sourcedByDate.getValue(reportDay)))
    }
}

/** Fires nightly, and hourly through the retry window when conditions are not met. */
class ReportAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ReportScheduler.onAlarm(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * One signal's value on [date] with its provenance, or null when nothing
 * measured it at all.
 *
 * Check-ins are classified [MeasureSource.MEASURED_HERE] rather than
 * passing through the wearable slot: valence and arousal only ever come
 * from a person answering a prompt, and calling that "from your wearable"
 * on the coverage screen would be a small lie told daily.
 */
private fun sourcedFor(
    signal: Signal,
    date: LocalDate,
    vitalsByDate: Map<LocalDate, DailyVitals>,
    momentsByDay: Map<String, List<Moment>>,
    measured: Map<Pair<String, String>, Double>,
    sleepByWake: Map<LocalDate, SleepWindow>,
    inferred: Map<Pair<String, String>, Double>,
    zone: ZoneId,
): Sourced? {
    val existing = valueFor(signal, date, vitalsByDate, momentsByDay)
    val checkIn = signal == Signal.VALENCE || signal == Signal.AROUSAL
    return Sourcing.pick(
        measuredHere = measured[date.toString() to signal.name]
            ?: existing.takeIf { checkIn },
        wearable = existing.takeUnless { checkIn },
        phoneInferred = phoneInferred(signal, date, sleepByWake, inferred, zone),
    )
}

/**
 * Sleep, inferred from the phone's own screen rhythm, for when no
 * wearable wrote a session — which on this project's own hardware is the
 * expected case, since the watch's Health Connect exports were verified
 * to be heart rate and exercise only. Same minutes-after-18:00 frame as
 * the wearable path, so the baseline never sees two framings of one
 * signal.
 */
private fun phoneInferred(
    signal: Signal,
    date: LocalDate,
    sleepByWake: Map<LocalDate, SleepWindow>,
    inferred: Map<Pair<String, String>, Double>,
    zone: ZoneId,
): Double? = when (signal) {
    Signal.SLEEP_MINUTES -> sleepByWake[date]?.let { window ->
        ((window.endMillis - window.startMillis) / 60_000L).toDouble()
    }

    Signal.SLEEP_ONSET -> sleepByWake[date]?.let { window ->
        val start = Instant.ofEpochMilli(window.startMillis).atZone(zone)
        Deviation.minutesAfterSixPm(start.hour * 60 + start.minute).toDouble()
    }

    // The screen-rhythm pair comes from the inferred ledger rather than
    // being recomputed here: the ledger holds months where the event log
    // holds a week — see the recompute-and-record step in buildAndStore.
    Signal.FIRST_UNLOCK, Signal.SCREEN_TIME -> inferred[date.toString() to signal.name]

    else -> null
}

/**
 * One signal's value on [date], or null when it was not measured that
 * day — a watch on the charger, or no check-ins answered, are both
 * ordinary and both mean "skip this day for this signal", never zero.
 */
private fun valueFor(
    signal: Signal,
    date: LocalDate,
    vitalsByDate: Map<LocalDate, DailyVitals>,
    momentsByDay: Map<String, List<Moment>>,
): Double? {
    val vitals = vitalsByDate[date]
    return when (signal) {
        Signal.HRV -> vitals?.hrvRmssd
        Signal.RESTING_HEART_RATE -> vitals?.restingHeartRate
        Signal.SLEEP_MINUTES -> vitals?.sleepMinutes?.toDouble()
        // Re-framed to minutes after 18:00 before it goes anywhere near a
        // baseline. As a raw minute-of-day, 23:50 is 1430 and 00:10 is 10,
        // so a single night crossing midnight puts a 1420-minute gap into a
        // series whose real spread is twenty minutes — the median lands
        // between two clusters that nobody ever slept at, and the dispersion
        // is wide enough that no genuinely late night could ever clear it.
        // Anyone whose bedtime straddles midnight is most people, so this is
        // the ordinary case rather than an edge one. See Deviation for the
        // same frame and for its honest limitation with day sleepers.
        Signal.SLEEP_ONSET -> vitals?.sleepOnset?.let { Deviation.minutesAfterSixPm(it).toDouble() }
        Signal.STEPS -> vitals?.steps?.toDouble()
        // Mindfulness comes from a Health Connect write by a meditation
        // app the launcher does not see. The launcher surfaces the total
        // minutes; the report describes it as "you sat X minutes today",
        // never as a relationship to mood or stress. That relationship
        // exists in the literature (Gál et al., J Affect Disord 2020)
        // but the report's job is to show the number, not to interpret it.
        Signal.MINDFULNESS_MINUTES -> vitals?.mindfulnessMinutes?.toDouble()
        // The screen-rhythm pair has no wearable behind it and no
        // check-in either — it exists only through the phone-inferred
        // slot, so from every other source the honest answer is nothing.
        Signal.FIRST_UNLOCK, Signal.SCREEN_TIME -> null
        // Valence and arousal have no watch behind them at all — they
        // come only from EMA check-ins, averaged across however many
        // were answered that day.
        Signal.VALENCE -> momentsByDay[date.toString()]
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.valence.toDouble() }
            ?.average()
        Signal.AROUSAL -> momentsByDay[date.toString()]
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.arousal.toDouble() }
            ?.average()
    }
}

/**
 * Both labels' values across [dates], one check-in day averaged into one
 * number the same way [valueFor] already averages [Signal.VALENCE] and
 * [Signal.AROUSAL] — kept separate from that function only because
 * [Label] and [Signal] are different types naming the same two axes for
 * two different purposes; see [SignalLabel]'s own KDoc.
 */
private fun labelsByDay(
    dates: List<LocalDate>,
    momentsByDay: Map<String, List<Moment>>,
): Map<Label, Map<LocalDate, Double>> =
    Label.entries.associateWith { label ->
        dates.mapNotNull { date ->
            val dayMoments = momentsByDay[date.toString()]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val average = when (label) {
                Label.VALENCE -> dayMoments.map { it.valence.toDouble() }.average()
                Label.AROUSAL -> dayMoments.map { it.arousal.toDouble() }.average()
            }
            date to average
        }.toMap()
    }
