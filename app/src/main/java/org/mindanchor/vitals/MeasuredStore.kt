package org.mindanchor.vitals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "measured")

/** One thing the app measured itself, on one day. */
data class Measurement(val day: String, val key: String, val value: Double)

/**
 * The tab-separated codec for [MeasuredStore] — same discipline as
 * [org.mindanchor.friction.GateLedger] and the report's own ledger: plain
 * lines, read and written whole, one corrupt line costing one entry and
 * never the file.
 */
object MeasuredLedger {

    fun encode(entries: List<Measurement>): String =
        entries.joinToString("\n") { "${it.day}\t${it.key}\t${it.value}" }

    /** Bad lines are dropped, never thrown on. */
    fun decode(raw: String): List<Measurement> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val value = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            if (parts[0].isBlank() || parts[1].isBlank()) return@mapNotNull null
            Measurement(day = parts[0], key = parts[1], value = value)
        }.toList()

    /**
     * Replaces any existing entry for the same day and key.
     *
     * Two readings in one day are not two data points, they are a retake:
     * the day contributes one value to the baseline, and the later
     * measurement is the one the person trusted enough to keep.
     */
    fun upsert(entries: List<Measurement>, entry: Measurement): List<Measurement> =
        entries.filterNot { it.day == entry.day && it.key == entry.key } + entry

    /**
     * Drops entries older than [keepFrom].
     *
     * ISO dates compare correctly as strings, which is the whole reason
     * days are stored in that form. Nothing downstream reads further back
     * than the pattern window, so holding more would only be a slowly
     * growing file with no reader.
     */
    fun prune(entries: List<Measurement>, keepFrom: String): List<Measurement> =
        entries.filter { it.day >= keepFrom }
}

/**
 * What the app measured itself — starting with the camera PPG's RMSSD.
 *
 * ## The gap this closes
 *
 * Found by tracing, not assumed: the nightly report read HRV only from
 * Health Connect, and the camera reading — built precisely because COROS
 * never exports HRV — was rendered once on screen and discarded. The
 * person could have measured faithfully every morning and the report
 * would have said "still building a picture" forever. Every deliberate
 * measurement now lands here, and the report reads this store first.
 *
 * Keys are [org.mindanchor.report.Signal] names, so the store and the
 * report can never drift into disagreeing about what a value is called.
 */
class MeasuredStore(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    /** Everything on file, or nothing on any failure — never a crash. */
    suspend fun all(): List<Measurement> = runCatching {
        MeasuredLedger.decode(context.dataStore.data.first()[entriesKey].orEmpty())
    }.getOrDefault(emptyList())

    /**
     * Records one measurement, replacing any earlier one from the same
     * day, and prunes anything older than [KEEP_DAYS].
     *
     * Never throws: this is called from the moment a reading succeeds,
     * and a storage hiccup must not turn a completed measurement into a
     * crash in the person's hands.
     */
    suspend fun record(day: LocalDate, key: String, value: Double) {
        runCatching {
            context.dataStore.edit { prefs ->
                val current = MeasuredLedger.decode(prefs[entriesKey].orEmpty())
                val next = MeasuredLedger.prune(
                    MeasuredLedger.upsert(current, Measurement(day.toString(), key, value)),
                    keepFrom = day.minusDays(KEEP_DAYS.toLong()).toString(),
                )
                prefs[entriesKey] = MeasuredLedger.encode(next)
            }
        }
    }

    companion object {
        /** Comfortably past the report's 90-day pattern window. */
        const val KEEP_DAYS = 120
    }
}
