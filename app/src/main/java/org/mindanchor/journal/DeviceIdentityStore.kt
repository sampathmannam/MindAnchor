package org.mindanchor.journal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.program0DeviceDataStore by preferencesDataStore(name = "program_zero_device")

/**
 * A stable, per-install device id used to attribute Journal entries to the
 * device that authored them. This is deliberately generated, not derived
 * from any hardware identifier, and is not meant to be restorable from a
 * backup (Task 11 excludes it) — a restored device gets its own identity.
 */
class DeviceIdentityStore(private val context: Context) {

    private val idKey = stringPreferencesKey("device_id")

    suspend fun id(): String {
        context.program0DeviceDataStore.data.first()[idKey]?.let { return it }
        val generated = UUID.randomUUID().toString()
        var resolved = generated
        context.program0DeviceDataStore.edit { prefs ->
            val existing = prefs[idKey]
            if (existing != null) {
                resolved = existing
            } else {
                prefs[idKey] = generated
            }
        }
        return resolved
    }
}
