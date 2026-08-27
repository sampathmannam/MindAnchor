package org.mindanchor.anchorcore

import java.time.LocalDate
import java.util.Locale
import org.mindanchor.sleep.Deviation
import org.mindanchor.vitals.WellnessReading
import org.mindanchor.vitals.WellnessSignal

/**
 * The aggregator's arithmetic. Pure functions over data already collected
 * elsewhere; the Context-carrying wrapper is AnchorCoreSource (Task 5) so
 * this stays JVM-testable — the SleepMath/SleepRepository split.
 */
object AnchorCore {

    /**
     * |z| >= 2.0 flags a vital. Jacobson 2019 (J Nerv Ment Dis 207:893-6)
     * uses 2.0-2.5 per-person anomaly cut-offs; 2.0 matches the launcher's
     * MUCH_ABOVE band edge (WellnessDirection), documented for traceability.
     */
    const val FLAG_Z = 2.0

    /** An SRI drop of this many points vs the prior week counts. Design choice. */
    const val SRI_DROP_POINTS = 15

    /**
     * A day is observed when it has a screen-rhythm value (non-null map
     * entry) or any vital-ledger entry. Absent days are absent, never
     * zero-filled. The union: rhythm-observed days, plus vital-only days.
     */
    fun observedDays(
        unlockMinutesByDay: Map<LocalDate, Int?>,
        vitalDays: Set<LocalDate>,
    ): Int =
        unlockMinutesByDay.values.count { it != null } +
            vitalDays.count { unlockMinutesByDay[it] == null }

    /**
     * LATE_NIGHT_CLUSTER when Deviation has enough nights and at least one
     * ran >= 90 min past the person's own median onset. Onsets arrive in
     * the minutes-after-18:00 frame (Deviation.minutesAfterSixPm) so a
     * midnight-crossing bedtime reads as later, never as earlier.
     * Detail payload: "nights|medianOnsetAfterSixPm".
     */
    fun lateNightCluster(onsets: List<Int>, today: LocalDate): DayFact? {
        if (!Deviation.worthShowing(onsets)) return null
        val n = Deviation.laterThanUsual(onsets)
        val usual = Deviation.usual(onsets) ?: return null
        return DayFact(FactKind.LATE_NIGHT_CLUSTER, "$n|$usual", today)
    }

    /** Detail payload: "dropPoints". Silent unless the score actually fell. */
    fun sleepIrregular(thisWeekSri: Int?, lastWeekSri: Int?, today: LocalDate): DayFact? {
        if (thisWeekSri == null || lastWeekSri == null) return null
        val drop = lastWeekSri - thisWeekSri
        if (drop < SRI_DROP_POINTS) return null
        return DayFact(FactKind.SLEEP_IRREGULAR, "$drop", today)
    }

    /**
     * Vital facts for the directional signals: steps and HRV low, resting
     * heart rate high. The baseline must be reportable (the 14-day floor)
     * — robustZ alone does not enforce it, so the check is here.
     */
    fun vitalFacts(readings: List<WellnessReading>, today: LocalDate): List<DayFact> =
        readings.mapNotNull { r ->
            if (!r.baseline.isReportable) return@mapNotNull null
            val z = r.zScore ?: return@mapNotNull null
            val kind = when (r.signal) {
                WellnessSignal.STEPS -> if (z <= -FLAG_Z) FactKind.MOVEMENT_LOW else null
                WellnessSignal.HRV -> if (z <= -FLAG_Z) FactKind.HRV_LOW else null
                WellnessSignal.RESTING_HEART_RATE -> if (z >= FLAG_Z) FactKind.RHR_HIGH else null
                else -> null
            } ?: return@mapNotNull null
            DayFact(kind, String.format(Locale.ROOT, "%.1f", z), today)
        }
}
