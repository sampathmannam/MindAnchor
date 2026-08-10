package org.mindanchor.sim

import org.mindanchor.friction.LoopPhase
import org.mindanchor.friction.OpenLoop
import org.mindanchor.sim.personas.Persona
import org.mindanchor.sleep.BedtimeList
import org.mindanchor.sleep.BedtimePhase
import org.mindanchor.vitals.DailyVitals
import org.mindanchor.vitals.WellnessReading
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.vitals.WellnessStats
import java.time.LocalDate
import java.time.LocalTime

/**
 * One day's simulated outcome: the wellness reading per signal, the
 * open-loop phase, and the bedtime-list phase.
 *
 * This is the *one* output shape the simulation runner emits. Every
 * persona's 14-day schedule becomes a `List<SimulationDay>`, and the
 * issues (WP-5) are derived from those lists.
 */
data class SimulationDay(
    val date: LocalDate,
    val personaId: String,
    val readings: Map<WellnessSignal, WellnessReading>,
    val openLoopPhase: LoopPhase,
    val bedtimeListPhase: BedtimePhase,
)

/**
 * Pure-Kotlin simulation runner that drives the launcher's logic
 * against a [Persona]'s 14-day schedule.
 *
 * The runner is the *only* place where the launcher's pure functions
 * are exercised end-to-end on synthetic data. It exists to:
 *  1. Confirm the launcher's math produces the right bands on the
 *     right days for the right persona.
 *  2. Surface "issues" (WP-5) — days where the launcher's output
 *     does not match the citation's prediction.
 *  3. Give WP-6 (research-backed fixes) a target.
 *
 * ## What this runner does *not* do
 *
 *  - It does not run the full Compose UI; that lives in
 *    androidTest and on physical devices. The runner tests the
 *    pure-Kotlin math the UI is built on, not the UI.
 *  - It does not introduce new logic. Every output is a direct
 *    application of [WellnessStats], [OpenLoop.phase], and
 *    [BedtimeList.phase] — the same code paths the live launcher
 *    hits on a real day. If the runner passes, the launcher should
 *    pass for the same data.
 *  - It does not decide the *default* sunset window. The
 *    22:00 → 07:00 default is the launcher's choice, and the
 *    runner takes the [sunsetStart] / [sunsetEnd] as inputs so
 *    the simulation can test alternative windows.
 */
object WellnessSimulationRunner {

    /**
     * The five signals the launcher surfaces as a wellness card.
     * Matches [WellnessSignal.ORDERED].
     */
    private val SIGNALS: List<WellnessSignal> = WellnessSignal.ORDERED

    /**
     * Run the simulation for [persona] starting at [start], with a
     * given [seed] and [sunsetStart] / [sunsetEnd] (the wind-down
     * window the persona has configured; the launcher's default is
     * 22:00 → 07:00, the night-owl and shift-worker personas
     * exercise non-default windows).
     *
     * The persona's [Persona.schedule] produces a 14-day window of
     * "test" data. To get a 14-day baseline underneath it, the
     * runner also generates a 14-day "warmup" schedule using a
     * different seed offset — the warmup days are not part of the
     * returned list, but they are the prior history on day 1 of
     * the test. This is how a real install behaves: the launcher
     * needs 14 days of data before it can show a baseline, and the
     * persona library is doing the same work in simulation.
     */
    fun run(
        persona: Persona,
        start: LocalDate,
        seed: Long,
        sunsetStart: LocalTime = LocalTime.of(22, 0),
        sunsetEnd: LocalTime = LocalTime.of(7, 0),
    ): List<SimulationDay> {
        // Warmup: 14 days strictly before [start], with a different
        // seed so the warmup noise is not the same as the test noise.
        val warmupStart = start.minusDays(14)
        val warmup = persona.schedule(warmupStart, seed xor WARMUP_SEED_SALT)
        val schedule = persona.schedule(start, seed)
        // The "current" quiet-hour check is at 23:00 — the middle
        // of the default wind-down window. The runner treats the
        // same instant for every day, since the launcher's "quiet
        // hours" is a window, not a moment.
        val now = LocalTime.of(23, 0)

        val out = mutableListOf<SimulationDay>()
        for (i in schedule.indices) {
            val day = schedule[i]
            val prior = warmup + schedule.subList(0, i)
            val readings = readingsFor(day, prior)
            val openLoop = openLoopFor(day, prior, now, sunsetStart, sunsetEnd)
            val bedtime = bedtimeFor(day, prior, now, sunsetStart, sunsetEnd)
            out.add(
                SimulationDay(
                    date = day.date ?: start.plusDays(i.toLong()),
                    personaId = persona.id,
                    readings = readings,
                    openLoopPhase = openLoop,
                    bedtimeListPhase = bedtime,
                ),
            )
        }
        return out
    }

    /** XOR salt to make the warmup noise different from the test noise. */
    private const val WARMUP_SEED_SALT: Long = 0x6D696E6463687220L  // "mindchr"

    /**
     * Build the 5-signal reading map for [today] given [prior] history.
     *
     * Today is excluded from the prior — the z-score is computed
     * from the days strictly before today, so a particularly high
     * HRV day does not call itself a "much above" day by
     * definition. See [WellnessStats.reading].
     *
     * Each reading is clamped to NO_DATA when the baseline is not
     * reportable (sampleCount < 14). This matches the home card's
     * `isReportable` gate in `HomeScreen.kt`; the runner's contract
     * is "what would the home card show", not "what does the math
     * say in isolation".
     */
    private fun readingsFor(
        today: DailyVitals,
        prior: List<DailyVitals>,
    ): Map<WellnessSignal, WellnessReading> {
        val out = LinkedHashMap<WellnessSignal, WellnessReading>()
        for (signal in SIGNALS) {
            val history = prior.mapNotNull { valueOf(it, signal) }
            val baseline = WellnessStats.baseline(signal, history)
            val todayValue = valueOf(today, signal)
            val reading = WellnessStats.reading(signal, todayValue, baseline)
            out[signal] = clampToReportable(reading)
        }
        return out
    }

    /**
     * If the baseline is not reportable, the home card does not
     * surface this reading — return a copy with [WellnessReading.zScore]
     * forced to null, which makes [WellnessReading.direction] fall
     * through to NO_DATA via [WellnessDirection.bandFor].
     */
    private fun clampToReportable(reading: WellnessReading): WellnessReading {
        if (reading.baseline.isReportable) return reading
        return reading.copy(zScore = null)
    }

    /** Extract the [signal] value from a [DailyVitals], or null. */
    private fun valueOf(v: DailyVitals, signal: WellnessSignal): Double? = when (signal) {
        WellnessSignal.HRV -> v.hrvRmssd
        WellnessSignal.RESTING_HEART_RATE -> v.restingHeartRate
        WellnessSignal.STEPS -> v.steps?.toDouble()
        WellnessSignal.SLEEP_MINUTES -> v.sleepMinutes?.toDouble()
        WellnessSignal.MINDFULNESS_MINUTES -> v.mindfulnessMinutes?.toDouble()
    }

    /**
     * The open-loop phase the launcher would show for this day at
     * [now]. No stored note state in the runner — the persona
     * library doesn't track it yet, so the runner assumes nothing
     * was captured (the worst-case open-loop behaviour, which is
     * "show the prompt").
     *
     * When the persona library gains a "captured last night" flag
     * (WP-3+), the runner will pass it here and the RETURN phase
     * will start firing.
     */
    private fun openLoopFor(
        today: DailyVitals,
        @Suppress("UNUSED_PARAMETER") prior: List<DailyVitals>,
        now: LocalTime,
        sunsetStart: LocalTime,
        sunsetEnd: LocalTime,
    ): LoopPhase {
        val quietHours = isQuiet(now, sunsetStart, sunsetEnd)
        // No captured note in the runner today — the launcher's
        // "show the prompt" path. When a note was captured last
        // night, the launcher hands it back in the morning.
        return OpenLoop.phase(
            quietHours = quietHours,
            note = null,
            notedDay = null,
            today = today.date ?: LocalDate.now(),
        )
    }

    /**
     * The bedtime-list phase the launcher would show for this day
     * at [now]. No stored list in the runner — same caveat as
     * [openLoopFor].
     */
    private fun bedtimeFor(
        today: DailyVitals,
        @Suppress("UNUSED_PARAMETER") prior: List<DailyVitals>,
        now: LocalTime,
        sunsetStart: LocalTime,
        sunsetEnd: LocalTime,
    ): BedtimePhase {
        val quietHours = isQuiet(now, sunsetStart, sunsetEnd)
        return BedtimeList.phase(
            quietHours = quietHours,
            items = emptyList(),
            writtenDay = null,
            today = today.date ?: LocalDate.now(),
        )
    }

    /** True when [now] is inside the wind-down window. */
    private fun isQuiet(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        // Same overnight-aware rule as SunsetPrefs.isInWindow.
        return if (start <= end) {
            now >= start && now < end
        } else {
            now >= start || now < end
        }
    }
}
