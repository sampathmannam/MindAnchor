package org.mindanchor.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "onboarding")

/** The struggles a user can name; each maps to the features that address it. */
enum class Goal {
    INTERRUPTIONS, // -> notification batching
    COMPULSIVE_APPS, // -> friction gate
    SLEEP, // -> sunset mode + sleep rhythm
    MEASUREMENT, // -> wellbeing pulse
}

class OnboardingPrefs(private val context: Context) {

    private val doneKey = booleanPreferencesKey("done")
    private val goalsKey = stringSetPreferencesKey("goals")

    val done: Flow<Boolean> = context.dataStore.data.map { it[doneKey] ?: false }

    suspend fun complete(goals: Set<Goal>) {
        context.dataStore.edit { prefs ->
            prefs[doneKey] = true
            prefs[goalsKey] = goals.map { it.name }.toSet()
        }
    }
}
