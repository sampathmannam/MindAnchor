package org.mindanchor.vitals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private val Context.wellnessDataStore by preferencesDataStore(name = "wellness")

/**
 * The on-disk codec for the per-signal rolling history.
 *
 * Same discipline as the rest of the launcher (see
 * [MeasuredLedger] and [org.mindanchor.friction.GateLedger]):
 * tab-separated lines, read and written whole, one corrupt line
 * costing one entry and never the file.
 *
 * One line per (signal, day) pair, in the form
 * `<SIGNAL>\t<ISO_DATE>\t<VALUE>`. The signal name is the
 * [WellnessSignal] enum constant's `name`, so a signal removed
 * from a future build drops its rows on decode (rather than
 * silently re-appearing after a downgrade) — see
 * [WellnessLedger.decode] for the dropping rule.
 */
object WellnessLedger {

    /**
     * One row of the on-disk history. Mirrors the shape of
     * [org.mindanchor.vitals.Measurement] for [MeasuredStore], with
     * the signal as a typed enum rather than a string key, so
     * downstream code does not have to remember the
     * string-to-enum mapping.
     */
    data class Entry(
        val signal: WellnessSignal,
        val day: LocalDate,
        val value: Double,
    )

    fun encode(entries: List<Entry>): String =
        entries.joinToString("\n") { "${it.signal.name}\t${it.day}\t${it.value}" }

    /**
     * Decodes the whole on-disk payload.
     *
     * A line that fails to parse — a bad date, a non-numeric
     * value, an unknown signal name from a future build — is
     * dropped, never the file. The signal name resolves through
     * [WellnessSignal.valueOf] in a [runCatching], so a removed
     * or renamed signal discards its rows silently and the rest
     * of the file is still readable.
     */
    fun decode(raw: String): List<Entry> =
        raw.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val signal = runCatching { WellnessSignal.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val day = runCatching { LocalDate.parse(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            val value = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            Entry(signal = signal, day = day, value = value)
        }.toList()

    /**
     * Replaces any existing entry for the same day and signal.
     *
     * Two readings in one day are not two data points, they are
     * a retake: the day contributes one value to the baseline,
     * and the later measurement is the one the person trusted
     * enough to keep.
     */
    fun upsert(entries: List<Entry>, entry: Entry): List<Entry> =
        entries.filterNot { it.signal == entry.signal && it.day == entry.day } + entry

    /**
     * Drops entries older than [keepFrom].
     *
     * [LocalDate] comparisons through ISO strings: `2026-08-01`
     * compares correctly as a string, which is the whole reason
     * days are stored in that form. Nothing downstream reads
     * further back than the wellness surface's 90-day baseline
     * window, so holding more would only be a slowly growing
     * file with no reader.
     */
    fun prune(entries: List<Entry>, keepFrom: LocalDate): List<Entry> =
        entries.filter { it.day >= keepFrom }
}

/**
 * The on-device rolling history of per-signal values, one entry
 * per (signal, day) pair.
 *
 * The wellness surface ([WellnessRepository]) reads the history
 * for a signal, computes a personal baseline (median + MAD) over
 * the days strictly before "today", and surfaces today's value
 * as a robust z-score against that baseline.
 *
 * ## Why a separate file
 *
 * The history is *per-signal* and is read as a whole, not queried
 * by day. A separate DataStore is the only way [WellnessLedger]
 * stays line-oriented (a `WELLNESS` row, a `MEASURED` row, a
 * `CHECKINS` row all sit in different files), and line-oriented
 * is what makes a single corrupt byte cost one entry rather than
 * the whole file.
 *
 * ## Why the daily cadence
 *
 * The wellness surface updates after every Health Connect read,
 * which happens (a) when the launcher is resumed, (b) when the
 * nightly report runs, and (c) when the user taps the "What is
 * arriving" probe. The history grows by *one entry per day*,
 * because the unit of the baseline is a day, not a session. The
 * upsert-by-day rule in [WellnessLedger.upsert] makes a second
 * read on the same day a retake, not a duplicate.
 */
class WellnessHistoryStore(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    /**
     * Everything on file, or nothing on any failure — never a
     * crash. The wellness surface is read on the home card and
     * in settings; either reading the file must be safe.
     */
    suspend fun all(): List<WellnessLedger.Entry> = runCatching {
        WellnessLedger.decode(context.wellnessDataStore.data.first()[entriesKey].orEmpty())
    }.getOrDefault(emptyList())

    /**
     * The history for [signal], oldest first, with the most
     * recent value last.
     *
     * The signal is filtered here, not in [WellnessRepository],
     * so the repository is free to read multiple signals'
     * histories with a single [all] call. The result is sorted
     * by day ascending so the caller can read it
     * chronologically.
     */
    suspend fun historyFor(signal: WellnessSignal): List<WellnessDayValue> =
        all()
            .filter { it.signal == signal }
            .map { WellnessDayValue(day = it.day, value = it.value) }
            .sortedBy { it.day }

    /**
     * Records one day's value for one signal, replacing any
     * earlier entry for the same day, and prunes anything older
     * than [KEEP_DAYS].
     *
     * Never throws. A storage hiccup must not turn a successful
     * Health Connect read into a crash on the home card.
     */
    suspend fun record(day: LocalDate, signal: WellnessSignal, value: Double) {
        runCatching {
            context.wellnessDataStore.edit { prefs ->
                val current = WellnessLedger.decode(prefs[entriesKey].orEmpty())
                val upserted = WellnessLedger.upsert(
                    current,
                    WellnessLedger.Entry(signal = signal, day = day, value = value),
                )
                val pruned = WellnessLedger.prune(upserted, day.minusDays(KEEP_DAYS.toLong()))
                prefs[entriesKey] = WellnessLedger.encode(pruned)
            }
        }
    }

    companion object {
        /**
         * Comfortably past the wellness surface's baseline window
         * (90 days for the patterns; 30 for the simple per-feature
         * median). The 180-day floor leaves room for the
         * "still building a picture" period to extend naturally
         * while keeping the file bounded.
         */
        const val KEEP_DAYS = 180
    }
}
