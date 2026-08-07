package org.mindanchor.report

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.mindanchor.corpus.CorpusStore
import org.mindanchor.model.Moment
import org.mindanchor.model.MomentStore
import org.mindanchor.sleep.Deviation
import org.mindanchor.vitals.DailyVitals
import org.mindanchor.vitals.HealthConnectSource

/**
 * Runs [ReportComposer] once a night and stores whatever it finds.
 *
 * ## Why charging and idle, both
 *
 * This reads roughly a month of Health Connect history plus every stored
 * check-in, which is real work the person did not ask to watch happen.
 * Charging means it costs no battery anyone notices; idle means the
 * screen is off and nobody is mid-task on the phone. Either constraint
 * alone would let this run while somebody is actively using the device —
 * requiring both is what makes "overnight, unattended" an actual
 * guarantee rather than a hope.
 *
 * ## Why [ExistingPeriodicWorkPolicy.UPDATE] and a stable unique name
 *
 * [ensureScheduled] can legitimately be called more than once — every
 * time the setting is switched on, and potentially on every app upgrade.
 * `UPDATE` replaces whatever periodic request already exists under
 * [UNIQUE_NAME] in place, carrying its already-elapsed period forward,
 * so calling this repeatedly re-arms the one job rather than stacking a
 * second nightly worker running alongside the first.
 *
 * ## Why an empty report is [androidx.work.ListenableWorker.Result.success]
 *
 * A steady month with nothing unusual is [ReportComposer]'s ordinary,
 * good outcome — see its own KDoc. Reporting that as a failure would ask
 * WorkManager to retry a job that has nothing wrong with it, and would
 * eventually surface as though the feature were broken on nights when it
 * did its job perfectly. Every other failure mode — Health Connect
 * absent, a DataStore write that did not land, a corrupt corpus — is
 * folded into the same outcome for the same reason: this runs unattended
 * at 3am, and nothing here is worth waking anyone, or a retry storm, over.
 * See [org.mindanchor.grayscale.Grayscale] for the same "never throws"
 * convention used for the same reason.
 */
class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val zone = ZoneId.systemDefault()
        // The night just finished. This worker only ever fires while the
        // phone is charging *and* idle — in practice, overnight, well
        // after the calendar day being reported on has closed. Reporting
        // on LocalDate.now() instead would describe a day still in
        // progress, and whether that day looked "usual" would depend on
        // the exact minute WorkManager happened to satisfy the
        // constraints rather than on anything about the day itself.
        val reportDay = LocalDate.now(zone).minusDays(1)
        // Oldest first, so the lists handed to ReportComposer are in the
        // order its KDoc says they are — most recent last. Nothing
        // downstream is order-sensitive today (a median is not), but a
        // list that quietly disagrees with the contract it is passed
        // under is a trap for whatever reads it next.
        val historyDates = (HISTORY_DAYS downTo 1).map { reportDay.minusDays(it.toLong()) }

        val vitalsByDate = (listOf(reportDay) + historyDates).associateWith { date ->
            HealthConnectSource.readDailyVitals(applicationContext, date, zone)
        }
        // A store read that fails — corrupt DataStore file, anything —
        // is "no check-ins on record", not a reason to abandon the vitals
        // half of the report.
        val moments = runCatching { MomentStore(applicationContext).moments.first() }
            .getOrDefault(emptyList())
        val momentsByDay = moments.groupBy { it.day }

        val today = valuesFor(reportDay, vitalsByDate, momentsByDay)
        val history = Signal.entries.associateWith { signal ->
            historyDates.mapNotNull { date -> valueFor(signal, date, vitalsByDate, momentsByDay) }
        }

        val corpus = CorpusStore.load(applicationContext)
        val report = ReportComposer.compose(
            day = reportDay.toString(),
            today = today,
            history = history,
            corpus = corpus,
        )
        ReportStore(applicationContext).save(report, generatedDay = LocalDate.now(zone).toString())
        Result.success()
    }.getOrElse { Result.success() }

    companion object {

        /**
         * Stable across every call to [ensureScheduled], and across app
         * upgrades: it is the only thing standing between "re-arm the
         * nightly job" and "quietly start a second one that runs
         * alongside the first forever".
         */
        private const val UNIQUE_NAME = "nightly-report"

        /** Days of history gathered per signal — comfortably above
         * [org.mindanchor.model.Baseline.MIN_OBSERVATIONS] so a handful of
         * missing days (a watch left on the charger, say) does not tip a
         * signal into [Report.notYetKnown]. */
        private const val HISTORY_DAYS = 30

        /**
         * Arms, or re-arms, the nightly worker.
         *
         * Safe to call every time the setting is switched on and every
         * time the app starts — see the class KDoc for why `UPDATE` and
         * [UNIQUE_NAME] together make repeated calls idempotent rather
         * than cumulative.
         */
        // NOTE(ci): the WorkManager surface here — Constraints.Builder,
        // PeriodicWorkRequestBuilder, ExistingPeriodicWorkPolicy.UPDATE,
        // WorkManager.enqueueUniquePeriodicWork — is the standard shape
        // across recent work-runtime-ktx releases, but unverified against
        // the exact 2.10.0 artifact pinned by this build; check here
        // first if this file fails to compile.
        fun ensureScheduled(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()
            val request = PeriodicWorkRequestBuilder<ReportWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Switched off from settings: no nightly report until asked for again. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
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
