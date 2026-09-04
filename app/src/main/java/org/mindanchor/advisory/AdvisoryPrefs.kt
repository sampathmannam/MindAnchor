package org.mindanchor.advisory

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.advisoryDataStore by preferencesDataStore(name = "program_three_advisory")

/**
 * Program 3 Task 4 — the person's own two switches, plus the one
 * recovery key, and nothing else.
 *
 * Missing keys decode to false/false/null: a person who has never
 * touched this store gets the fully closed state, the same as a build
 * that never shipped it. This store never holds an attestation fact, a
 * source value, protocol progress, or an outcome — those live only in
 * the append-only event stream, because a preference is the one kind of
 * storage this design lets a person or a bug silently overwrite.
 */
class AdvisoryPrefs(private val context: Context) {

    private val masterAdvisoryEnabledKey = booleanPreferencesKey("master_advisory_enabled")
    private val deliveryAllowedKey = booleanPreferencesKey("delivery_allowed")
    private val currentEpisodeIdKey = stringPreferencesKey("current_episode_id")

    val settings: Flow<AdvisorySettings> = context.advisoryDataStore.data.map { prefs ->
        AdvisorySettings(
            masterAdvisoryEnabled = prefs[masterAdvisoryEnabledKey] ?: false,
            deliveryAllowed = prefs[deliveryAllowedKey] ?: false,
            currentEpisodeId = prefs[currentEpisodeIdKey],
        )
    }

    suspend fun setMasterAdvisoryEnabled(enabled: Boolean) {
        context.advisoryDataStore.edit { it[masterAdvisoryEnabledKey] = enabled }
    }

    suspend fun setDeliveryAllowed(enabled: Boolean) {
        context.advisoryDataStore.edit { it[deliveryAllowedKey] = enabled }
    }

    suspend fun setCurrentEpisodeId(episodeId: String?) {
        context.advisoryDataStore.edit { prefs ->
            if (episodeId == null) prefs.remove(currentEpisodeIdKey) else prefs[currentEpisodeIdKey] = episodeId
        }
    }

    /**
     * A restored backup never reopens the master switch, the delivery
     * switch, or a recovery key that names an episode this install never
     * ran — a restored device starts exactly as closed as a fresh one.
     */
    suspend fun disableAfterRestore() {
        context.advisoryDataStore.edit { prefs ->
            prefs[masterAdvisoryEnabledKey] = false
            prefs[deliveryAllowedKey] = false
            prefs.remove(currentEpisodeIdKey)
        }
    }
}
