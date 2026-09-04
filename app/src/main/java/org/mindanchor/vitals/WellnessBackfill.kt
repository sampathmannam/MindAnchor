package org.mindanchor.vitals

import java.time.LocalDate
import org.mindanchor.report.Sourcing
import org.mindanchor.vitals.coros.CorosDaily
import org.mindanchor.vitals.coros.CorosHrv

/**
 * The pure rules for backfilling [WellnessHistoryStore] from a
 * wearable bridge's historical series.
 *
 * ## Why a backfill exists
 *
 * The wellness ledger normally grows by one entry per day, from
 * the live read path in [WellnessRepository.readingsFor]. A
 * freshly connected COROS bridge, though, arrives with history —
 * 28 days of RHR and 7 nights of HRV already sit on the Training
 * Hub — and without a backfill the baseline says "still building
 * a picture" for 14 more days *about data the account already
 * has*. Importing that history on sync is what makes the RHR
 * baseline reportable on the connect day.
 *
 * ## The provenance rule
 *
 * [Sourcing.pick]'s precedence — measured-here > wearable >
 * phone-inferred — projected onto a ledger that does not store
 * provenance:
 *
 *  - An **existing row always wins**. It was written by the live
 *    read path, which already picked measured-here over wearable
 *    for its day; the bridge's wearable value must not rewrite
 *    it. This is also the ledger's append-only house pattern:
 *    history is filled, never revised.
 *  - For a day the ledger lacks, a **measured-here value beats
 *    the bridge value** — the pick is delegated to
 *    [Sourcing.pick] itself so the precedence has exactly one
 *    author.
 *  - The bridge fills what remains.
 *
 * Kept apart from the store so the rules are testable without an
 * Android context — the same pattern as [WellnessLedger] and
 * [WellnessStats], and what lets the persona simulation harness
 * drive a synthetic connect end-to-end.
 */
object WellnessBackfill {

    /**
     * The bridge's series as ledger entries: nightly HRV (RMSSD
     * ms) and daily RHR (bpm), the two wellness signals the
     * Training Hub carries. A row with a missing value, a date
     * this build cannot parse, or a physiologically impossible
     * zero is dropped — one bad row costs itself, never the
     * sync, and a 0 bpm "reading" must not poison the personal
     * median for months (same guard as
     * [WellnessRepository.recentSleepHours]).
     */
    fun fromBridge(hrv: List<CorosHrv>, daily: List<CorosDaily>): List<WellnessLedger.Entry> =
        hrv.mapNotNull { night ->
            entryOrNull(WellnessSignal.HRV, night.date, night.rmssd)
        } + daily.mapNotNull { day ->
            entryOrNull(WellnessSignal.RESTING_HEART_RATE, day.date, day.rhr)
        }

    private fun entryOrNull(
        signal: WellnessSignal,
        date: String,
        value: Double?,
    ): WellnessLedger.Entry? {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        val v = value?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        return WellnessLedger.Entry(signal = signal, day = day, value = v)
    }

    /**
     * The ledger after a bridge sync: [existing] rows untouched,
     * plus one row per (signal, day) the [wearable] series covers
     * and the ledger lacked — each of those picked through
     * [Sourcing.pick] against any [measuredHere] value for the
     * same (signal, day).
     *
     * Only days the bridge covers are considered: a measured-here
     * day outside the series was already recorded by the live
     * path on the day it was taken.
     */
    fun merged(
        existing: List<WellnessLedger.Entry>,
        wearable: List<WellnessLedger.Entry>,
        measuredHere: List<WellnessLedger.Entry> = emptyList(),
    ): List<WellnessLedger.Entry> {
        val taken = existing.map { it.signal to it.day }.toMutableSet()
        val deliberate = measuredHere.associate { (it.signal to it.day) to it.value }
        val filled = wearable.mapNotNull { candidate ->
            val key = candidate.signal to candidate.day
            if (!taken.add(key)) return@mapNotNull null
            val picked = Sourcing.pick(
                measuredHere = deliberate[key],
                wearable = candidate.value,
                phoneInferred = null,
            ) ?: return@mapNotNull null // unreachable: the wearable value is non-null
            WellnessLedger.Entry(signal = candidate.signal, day = candidate.day, value = picked.value)
        }
        return existing + filled
    }
}
