package org.mindanchor.vitals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ppgSessionDataStore by preferencesDataStore(name = "ppg_sessions")

/**
 * The on-device log of PPG sessions — start, end, mean hr, duration.
 *
 * v0.25.5 WP-D: local-only telemetry for the act of measuring. The
 * reading itself is what the report reads; the session log is what
 * makes the *act* of measuring visible to the user. Nothing here
 * leaves the device. The store is a thin DataStore wrapper over
 * [PpgSessionLog]'s plain-text wire format.
 */
class PpgSessionStore(private val context: Context) {

    private val sessionsKey = stringPreferencesKey("sessions")

    /** All sessions on file, oldest first. Empty on any failure. */
    suspend fun all(): List<PpgSession> = runCatching {
        PpgSessionLog.decode(context.ppgSessionDataStore.data.first()[sessionsKey].orEmpty())
    }.getOrDefault(emptyList())

    /**
     * The most recent [limit] sessions, newest first. Used by the
     * history line on the PPG screen.
     */
    fun recent(limit: Int) =
        context.ppgSessionDataStore.data.map { prefs ->
            PpgSessionLog.decode(prefs[sessionsKey].orEmpty())
                .sortedByDescending { it.start }
                .take(limit.coerceAtLeast(0))
        }

    /**
     * Append a session and prune anything older than [KEEP_DAYS].
     *
     * Never throws: this is called from the moment a reading
     * succeeds, and a storage hiccup must not turn a completed
     * measurement into a crash in the person's hands. The same
     * "fail-soft to nothing" rule as [MeasuredStore.record].
     */
    suspend fun record(session: PpgSession) {
        runCatching {
            context.ppgSessionDataStore.edit { prefs ->
                val current = PpgSessionLog.decode(prefs[sessionsKey].orEmpty())
                val cutoff = session.start.minusSeconds(KEEP_DAYS.toLong() * 86400L)
                val pruned = current.filter { it.start.isAfter(cutoff) }
                prefs[sessionsKey] = PpgSessionLog.encode(pruned + session)
            }
        }
    }

    /** Test-only: drop everything. */
    internal suspend fun reset() {
        context.ppgSessionDataStore.edit { it.remove(sessionsKey) }
    }

    companion object {
        /** Two months of sessions is plenty for the "last 3" history line. */
        const val KEEP_DAYS = 60
    }
}
