package org.mindanchor.usage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.mindanchor.vitals.MeasuredLedger
import org.mindanchor.vitals.Measurement

private val Context.dataStore by preferencesDataStore(name = "inferred")

/**
 * What the phone worked out about its own days — screen rhythm values the
 * nightly build computes and writes down before the system's short-lived
 * event log forgets them. Android keeps detailed usage events for only a
 * few days; a baseline needs months. This ledger is the difference.
 *
 * Same codec and same day-key-value shape as
 * [org.mindanchor.vitals.MeasuredStore], but deliberately a separate
 * file: that store feeds the report's measured-here slot and this one its
 * phone-inferred slot, and keeping a file per provenance makes the
 * classification structural rather than something remembered at every
 * call site. Recomputing a day already on file is a retake, not a
 * duplicate — the upsert replaces by day and key.
 */
class InferredStore(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    /** Everything on file, or nothing on any failure — never a crash. */
    suspend fun all(): List<Measurement> = runCatching {
        MeasuredLedger.decode(context.dataStore.data.first()[entriesKey].orEmpty())
    }.getOrDefault(emptyList())

    /** Records one inferred value, replacing any earlier one for the day. */
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
