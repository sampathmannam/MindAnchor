package org.mindanchor.vitals

import android.content.Context
import java.time.LocalDate

/**
 * The bridge between [HealthConnectSource] and
 * [WellnessHistoryStore].
 *
 * For each [WellnessSignal]:
 *  1. Read the day's value from Health Connect (or from the app's
 *     own measured store when the camera PPG path applies — HRV
 *     is the one signal the launcher is built to measure itself,
 *     see [org.mindanchor.pulse]).
 *  2. Persist today's value to the rolling history (upsert by
 *     day, prune to [WellnessHistoryStore.KEEP_DAYS]).
 *  3. Compute a personal baseline (median + MAD) over the days
 *     *strictly before today*.
 *  4. Reduce the result to a [WellnessReading] — today's value,
 *     the baseline, and a robust z-score.
 *
 * The history and the baseline are deliberately kept apart: the
 * history is a file, the baseline is a function of the history.
 * The repository is the only place they meet.
 *
 * ## Why nothing here throws
 *
 * Same rule as the rest of the launcher: this runs in a process
 * that must not crash because a wearable was not paired today.
 * A missing provider, a permission denial, a corrupt ledger,
 * a single out-of-range value — any of them is "this signal has
 * no reading today", not a fatal error.
 */
class WellnessRepository(private val context: Context) {

    private val source = HealthConnectSource
    private val history = WellnessHistoryStore(context)

    /**
     * The five readings for [day], one per [WellnessSignal], in
     * the order [WellnessSignal.ORDERED] renders them.
     *
     * A signal with no value today reads as [WellnessReading] with
     * `today = null`; the baseline is still computed from the
     * prior days' history, so the home card can show "vs your
     * last 30 days" even on a day the watch had nothing to say.
     *
     * ## Why measured-here beats wearable for HRV
     *
     * [org.mindanchor.vitals.PpgScreen] produces a deliberate
     * morning HRV measurement when the user holds a finger on
     * the camera for ninety seconds. The [Sourcing.pick] rule
     * used in the nightly report — measured-here > wearable >
     * inferred — is the same rule applied here: a deliberate act
     * is the highest-fidelity source of the day, and a system
     * that quietly preferred a watch's value over it would teach
     * the person the act was pointless. For the other four
     * signals, only the wearable (or Health Connect) has an
     * answer; measured-here is null by design.
     */
    suspend fun readingsFor(day: LocalDate): List<WellnessReading> {
        val vitals = runCatching { source.readDailyVitals(context, day) }.getOrNull()
        val measured = runCatching { MeasuredStore(context).all() }
            .getOrDefault(emptyList())
            .filter { it.day == day.toString() }
            .associate { it.key to it.value }
        return WellnessSignal.ORDERED.map { signal ->
            val wearable = valueFor(signal, vitals)
            val measuredHere = measuredFor(signal, measured)
            val today = measuredHere ?: wearable
            // Persist today's value to history *before* computing
            // the baseline. The baseline is over days strictly
            // before today, so persisting today does not contaminate
            // it — but doing the persist first also means a crash
            // between read and persist does not lose the day's
            // value the next time around (the next refresh would
            // see it, compute on the right history, and arrive at
            // the same answer).
            today?.let { runCatching { history.record(day, signal, it) } }
            val historyValues = runCatching { history.historyFor(signal) }
                .getOrDefault(emptyList())
                .filter { it.day < day }
                .map { it.value }
            val baseline = WellnessStats.baseline(signal, historyValues)
            WellnessStats.reading(signal, today, baseline)
        }
    }

    /**
     * Extracts [signal]'s value from a day's [DailyVitals], or
     * null when the watch had nothing to record for that signal.
     *
     * The mapping is a single place on purpose: any future signal
     * added to the wellness surface lives or dies by this
     * one-liner, and a wrong mapping is the only failure mode
     * this function can produce. A test on each branch is
     * cheap and worth it.
     */
    private fun valueFor(signal: WellnessSignal, vitals: DailyVitals?): Double? = when (signal) {
        WellnessSignal.HRV -> vitals?.hrvRmssd
        WellnessSignal.RESTING_HEART_RATE -> vitals?.restingHeartRate
        WellnessSignal.STEPS -> vitals?.steps?.toDouble()
        WellnessSignal.SLEEP_MINUTES -> vitals?.sleepMinutes?.toDouble()
        WellnessSignal.MINDFULNESS_MINUTES -> vitals?.mindfulnessMinutes?.toDouble()
    }

    /**
     * Extracts [signal]'s value from the day's [MeasuredStore]
     * readings — what the launcher measured itself (the camera
     * PPG's HRV is the only entry today, but the store is
     * general and this lookup does not know that).
     *
     * Only [WellnessSignal.HRV] has a measured-here source for
     * now: the PPG screen records under [Signal.HRV] (the
     * report's signal name) and the wellness signal is the
     * same RMSSD in milliseconds. The other four signals are
     * always null here by design — a deliberate measurement
     * the launcher does not yet make.
     */
    private fun measuredFor(
        signal: WellnessSignal,
        measured: Map<String, Double>,
    ): Double? = when (signal) {
        WellnessSignal.HRV -> measured[org.mindanchor.report.Signal.HRV.name]
        WellnessSignal.RESTING_HEART_RATE -> null
        WellnessSignal.STEPS -> null
        WellnessSignal.SLEEP_MINUTES -> null
        WellnessSignal.MINDFULNESS_MINUTES -> null
    }
}
