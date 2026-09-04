package org.mindanchor.admin

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.osModeDataStore by preferencesDataStore(name = "osmode")

/**
 * Preferences for OS Mode (master plan T-1.1/T-1.2).
 *
 * One named store, following the one-store-per-concept convention
 * (`"sunset"`, `"friction"`, `"doomscroll_list"`).
 *
 * ## What is stored here — and what deliberately is not
 *
 * The source of truth for whether apps are suspended right now is
 * **never** stored anywhere. It is re-derived on every [org.mindanchor.admin.OsMode.sync]
 * from three inputs that already exist: the device-owner grant (read live
 * from the system), the OS Mode switch below, and the sunset window
 * (stored by [org.mindanchor.data.SunsetPrefs]). A crash, a reboot, or a
 * missed alarm therefore cannot strand apps in the wrong state — the next
 * sync recomputes reality from the window.
 *
 * What *is* stored here:
 *
 * - [enabled] — the user's opt-in switch. Default OFF, like every feature
 *   in this app (project law: imposed minimalism fails).
 * - [applied] — a best-effort **cleanup hint**: the packages the last
 *   successful sync actually suspended. It exists so a later sync can lift
 *   entries that have since been removed from the doomscroll list. It is
 *   never consulted to decide *whether* something should be suspended;
 *   losing it costs nothing.
 * - [earlyReleaseAt] — when the typed-dwell unlock fired, so the rest of
 *   *this* window respects the person's choice instead of re-closing their
 *   apps on the next sync. Expired markers are wiped on window close.
 */
class OsModePrefs(private val context: Context) {

    private val enabledKey = booleanPreferencesKey("osmode_enabled")
    private val appliedKey = stringSetPreferencesKey("osmode_applied")
    private val earlyReleaseKey = longPreferencesKey("osmode_early_release_at")

    val enabled: Flow<Boolean> = context.osModeDataStore.data.map { it[enabledKey] ?: false }

    suspend fun isEnabled(): Boolean = context.osModeDataStore.data.first()[enabledKey] ?: false

    suspend fun setEnabled(value: Boolean) {
        context.osModeDataStore.edit { it[enabledKey] = value }
    }

    val applied: Flow<Set<String>> =
        context.osModeDataStore.data.map { it[appliedKey] ?: emptySet() }

    suspend fun recordApplied(packages: Set<String>) {
        context.osModeDataStore.edit { it[appliedKey] = packages }
    }

    val earlyReleaseAt: Flow<Long?> =
        context.osModeDataStore.data.map { it[earlyReleaseKey] }

    suspend fun markEarlyRelease(at: Long = System.currentTimeMillis()) {
        context.osModeDataStore.edit { it[earlyReleaseKey] = at }
    }

    suspend fun clearEarlyRelease() {
        context.osModeDataStore.edit { it.remove(earlyReleaseKey) }
    }
}
