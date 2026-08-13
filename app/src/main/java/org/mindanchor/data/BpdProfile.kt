package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bpdProfileStore by preferencesDataStore(name = "bpd_profile")

data class BpdProfile(
    val longMessagesIRegret: Boolean = false,
    val lateNightImpulses: Boolean = false,
    val sometimesISplit: Boolean = false,
    val namedPersonToCall: Boolean = false,
    val okAtNight: Boolean = false,
)

class BpdProfilePrefs(private val context: Context) {
    private val longMessagesKey = booleanPreferencesKey("bpd_long_messages")
    private val lateNightKey = booleanPreferencesKey("bpd_late_night")
    private val splitKey = booleanPreferencesKey("bpd_split")
    private val namedPersonKey = booleanPreferencesKey("bpd_named_person")
    private val okAtNightKey = booleanPreferencesKey("bpd_ok_at_night")
    val profile: Flow<BpdProfile> = context.bpdProfileStore.data.map { prefs ->
        BpdProfile(
            longMessagesIRegret = prefs[longMessagesKey] ?: false,
            lateNightImpulses = prefs[lateNightKey] ?: false,
            sometimesISplit = prefs[splitKey] ?: false,
            namedPersonToCall = prefs[namedPersonKey] ?: false,
            okAtNight = prefs[okAtNightKey] ?: false,
        )
    }
    suspend fun update(profile: BpdProfile) {
        context.bpdProfileStore.edit { prefs ->
            prefs[longMessagesKey] = profile.longMessagesIRegret
            prefs[lateNightKey] = profile.lateNightImpulses
            prefs[splitKey] = profile.sometimesISplit
            prefs[namedPersonKey] = profile.namedPersonToCall
            prefs[okAtNightKey] = profile.okAtNight
        }
    }
}
