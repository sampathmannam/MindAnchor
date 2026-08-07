package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.friction.ExtensionLedger

private val Context.dataStore by preferencesDataStore(name = "friction")

/**
 * Friction configuration: which apps get the breathing gate, and the
 * per-day session-extension ledger that keeps "+5 minutes" honest.
 */
class FrictionPrefs(private val context: Context) {

    private val flaggedKey = stringSetPreferencesKey("flagged_packages")
    private val ledgerKey = stringPreferencesKey("extension_ledger")

    val flaggedApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[flaggedKey] ?: emptySet()
    }

    suspend fun setFlagged(packageName: String, flagged: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[flaggedKey] ?: emptySet()
            prefs[flaggedKey] = if (flagged) current + packageName else current - packageName
        }
    }

    /** Increments and returns today's extension count for [packageName]. */
    private val reachKey = stringPreferencesKey("recent_reaches")

    /**
     * Records a reach for [packageName] at [now], and returns how many
     * times it had *already* been reached for inside [windowMillis].
     *
     * This is the whole memory behind context-gated friction, and it is
     * deliberately tiny: one timestamp per app, nothing historical, nothing
     * about what was done inside the app. Entries older than the window are
     * dropped on every write, so the file cannot grow into a usage log by
     * accident.
     */
    suspend fun recordReach(packageName: String, now: Long, windowMillis: Long): Int {
        var priorReaches = 0
        context.dataStore.edit { prefs ->
            val entries = (prefs[reachKey] ?: "")
                .lineSequence()
                .mapNotNull { line ->
                    val idx = line.lastIndexOf('\t')
                    val stamp = if (idx <= 0) null else line.substring(idx + 1).toLongOrNull()
                    if (stamp == null) null else line.substring(0, idx) to stamp
                }
                .filter { now - it.second < windowMillis }
                .toList()
            priorReaches = entries.count { it.first == packageName }
            prefs[reachKey] = (entries + (packageName to now))
                .joinToString("\n") { "${it.first}\t${it.second}" }
        }
        return priorReaches
    }

    suspend fun recordExtension(packageName: String, today: String): Int {
        var count = 0
        context.dataStore.edit { prefs ->
            val updated = ExtensionLedger.increment(prefs[ledgerKey].orEmpty(), packageName, today)
            prefs[ledgerKey] = updated
            count = ExtensionLedger.count(updated, packageName, today)
        }
        return count
    }

    suspend fun extensionsToday(packageName: String, today: String): Int =
        ExtensionLedger.count(context.dataStore.data.first()[ledgerKey].orEmpty(), packageName, today)
}
