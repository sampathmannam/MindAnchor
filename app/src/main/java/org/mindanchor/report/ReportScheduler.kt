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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
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
import org.mindanchor.vitals.DailyVitals
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
     * The alarm fired. Decide, maybe build, and always arm the next one.
     */
    internal suspend fun onAlarm(context: Context) {
        val appContext = context.applicationContext
        var decision = ReportSchedule.Decision.WAIT_FOR_TOMORROW
        try {
            val store = ReportStore(appContext)
            if (!store.enabled.first()) return
            val now = LocalDateTime.now()
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
        val at = ReportSchedule.nextRun(LocalDateTime.now(), decision)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
        // Oldest first, so the lists handed to ReportComposer are in the
        // order its KDoc says they are — most recent last. Nothing
        // downstream is order-sensitive today (a median is not), but a
        // list that quietly disagrees with the contract it is passed
        // under is a trap for whatever reads it next.
        val historyDates = (HISTORY_DAYS downTo 1).map { reportDay.minusDays(it.toLong()) }

        val vitalsByDate = (listOf(reportDay) + historyDates).associateWith { date ->
            HealthConnectSource.readDailyVitals(context, date, zone)
        }
        // A store read that fails — corrupt DataStore file, anything — is
        // "no check-ins on record", not a reason to abandon the vitals
        // half of the report.
        val moments = runCatching { MomentStore(context).moments.first() }
            .getOrDefault(emptyList())
        val momentsByDay = moments.groupBy { it.day }

        val today = valuesFor(reportDay, vitalsByDate, momentsByDay)
        val history = Signal.entries.associateWith { signal ->
            historyDates.mapNotNull { date -> valueFor(signal, date, vitalsByDate, momentsByDay) }
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
        ReportStore(context).save(
            report = report,
            narration = narration?.text,
            generatedDay = LocalDate.now(zone).toString(),
        )
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

/** Today's values for every signal that could be read at all on [date]. */
private fun valuesFor(
    date: LocalDate,
    vitalsByDate: Map<LocalDate, DailyVitals>,
    momentsByDay: Map<String, List<Moment>>,
): Map<Signal, Double> =
    Signal.entries.mapNotNull { signal ->
        valueFor(signal, date, vitalsByDate, momentsByDay)?.let { signal to it }
    }.toMap()

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
