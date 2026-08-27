package org.mindanchor.osmode

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.osModeDataStore by preferencesDataStore(name = "os_mode")

/**
 * OS Mode's stored state — deliberately almost nothing.
 *
 * The window is the source of truth for whether packages *should* be
 * suspended, and the device-owner grant is the source of truth for what
 * this app is allowed to do. What is stored here is only:
 *
 * - the person's opt-in (the per-feature toggle, default OFF);
 * - an escape-hatch record ("released through the slow dwell at 23:41"),
 *   compared against the current window instance so it expires on its own;
 * - the last set actually applied, a *hint* used only to lift
 *   suspensions precisely. It is never read to decide whether to
 *   suspend — that decision always comes from re-deriving the window.
 */
class OsModePrefs(private val context: Context) {

    private val optedInKey = booleanPreferencesKey("os_mode_opted_in")
    private val releasedAtKey = longPreferencesKey("os_mode_released_at_epoch_ms")
    private val lastSuspendedKey = stringSetPreferencesKey("os_mode_last_suspended")

    /** The person's choice. False until they switch it on; nothing infers it. */
    val optedIn: Flow<Boolean> = context.osModeDataStore.data.map { it[optedInKey] ?: false }

    suspend fun isOptedIn(): Boolean = context.osModeDataStore.data.first()[optedInKey] ?: false

    suspend fun setOptedIn(value: Boolean) {
        context.osModeDataStore.edit { it[optedInKey] = value }
        // Turning OS Mode off must take effect immediately, not at the
        // next alarm: someone changing their mind at 22:30 should not
        // spend the rest of the night locked out while waiting for 07:00.
        if (!value) OsModeController.rederiveSuspend(context)
    }

    /**
     * When the escape hatch was last used, in epoch millis, or 0 for
     * never. Compared against the start of the currently-running window:
     * a release belongs to one night only.
     */
    val releasedAtEpochMs: Flow<Long> =
        context.osModeDataStore.data.map { it[releasedAtKey] ?: 0L }

    suspend fun recordReleasedNow(now: Long) {
        context.osModeDataStore.edit { it[releasedAtKey] = now }
    }

    /** The set [OsModeController] last applied — a lifting hint, nothing more. */
    val lastSuspended: Flow<Set<String>> =
        context.osModeDataStore.data.map { it[lastSuspendedKey] ?: emptySet() }

    suspend fun setLastSuspended(packages: Set<String>) {
        context.osModeDataStore.edit { it[lastSuspendedKey] = packages }
    }

    /**
     * Clears every stored trace. Used when ownership itself is handed
     * back: the hint would otherwise outlive the grant that made it
     * meaningful, and a stale hint is exactly the kind of remembered
     * state this package exists to avoid.
     */
    suspend fun clear() {
        context.osModeDataStore.edit { prefs ->
            prefs.remove(optedInKey)
            prefs.remove(releasedAtKey)
            prefs.remove(lastSuspendedKey)
        }
    }
}
