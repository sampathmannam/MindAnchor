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

    /**
     * What the person said they were struggling with.
     *
     * This was written on the first run and then never read by anything,
     * which made the whole goal-elicitation step decorative. Unknown names
     * are dropped rather than throwing, so a stored goal removed in a later
     * version degrades to "not selected" instead of crashing the launcher
     * on somebody's home screen.
     */
    val goals: Flow<Set<Goal>> = context.dataStore.data.map { prefs ->
        prefs[goalsKey].orEmpty().mapNotNullTo(mutableSetOf()) { name ->
            runCatching { Goal.valueOf(name) }.getOrNull()
        }
    }

    suspend fun complete(goals: Set<Goal>) {
        context.dataStore.edit { prefs ->
            prefs[doneKey] = true
            prefs[goalsKey] = goals.map { it.name }.toSet()
        }
    }

    /** Changes the answer later, without replaying onboarding. */
    suspend fun setGoals(goals: Set<Goal>) {
        context.dataStore.edit { prefs ->
            prefs[goalsKey] = goals.map { it.name }.toSet()
        }
    }
}
