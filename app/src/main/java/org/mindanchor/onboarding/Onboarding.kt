package org.mindanchor.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
    private val installDayKey = stringPreferencesKey("install_day")
    private val recapSeenDayKey = stringPreferencesKey("recap_seen_day")

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
        prefs[goalsKey].orEmpty()
            .mapNotNull { name -> runCatching { Goal.valueOf(name) }.getOrNull() }
            .toSet()
    }

    /**
     * v0.25.5 WP-E: the day the user first ran the app. Set on the
     * first [complete] call (not at app launch, not implicitly by
     * any other code path — see the comment inside [complete] for
     * the rationale: a user who installs the app, uses the launcher
     * for 30 days without completing onboarding, and only then
     * completes it has their install-day stamped at completion,
     * not at install). Never overwritten after. Used by
     * [recapWindow] to decide when a 14-day recap should surface.
     */
    val installDay: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[installDayKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /**
     * v0.25.5 WP-E: the most recent day the user dismissed a 14-day
     * recap. Null = no recap has been seen yet (so the first recap
     * in the current window will show). A non-null value suppresses
     * the recap if the user is still in the same window.
     */
    val recapSeenDay: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[recapSeenDayKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /**
     * Marks the current 14-day window as seen. The recap will
     * reappear at the next window (day 28, day 42, ...) even if
     * nothing else changes.
     */
    suspend fun markRecapSeen(today: LocalDate = LocalDate.now()) {
        context.dataStore.edit { it[recapSeenDayKey] = today.toString() }
    }

    /**
     * v0.25.5 WP-E: the 14-day recap window function.
     *
     * Kanfer & Goldstein 1991: a 14-day checkpoint is the earliest the
     * user can detect a habit pattern. The window is 7 days wide
     * (days 14-20, 28-34, 42-48, ...). The recap is shown when the
     * user is in a window AND has not already seen (or dismissed)
     * the recap for that window.
     *
     * Pure function — testable without a Context, on LocalDate
     * inputs alone. Kept as a method on the class for the
     * fluent-call surface from the screen; the pure-function
     * shape is what makes the test surface possible.
     */
    fun inRecapWindow(
        installDay: LocalDate?,
        recapSeenDay: LocalDate?,
        today: LocalDate,
    ): Boolean = inRecapWindowPure(installDay, recapSeenDay, today)

    suspend fun complete(goals: Set<Goal>) {
        context.dataStore.edit { prefs ->
            prefs[doneKey] = true
            prefs[goalsKey] = goals.map { it.name }.toSet()
            // v0.25.5 WP-E: stamp the install day on the first
            // completion. The day is set on `complete()` rather than
            // at first launch so users who skipped onboarding (the
            // launcher is usable without it) still get a recap on
            // day 14 from their first interaction.
            if (prefs[installDayKey] == null) {
                prefs[installDayKey] = LocalDate.now().toString()
            }
        }
    }

    /** Changes the answer later, without replaying onboarding. */
    suspend fun setGoals(goals: Set<Goal>) {
        context.dataStore.edit { prefs ->
            prefs[goalsKey] = goals.map { it.name }.toSet()
        }
    }
}

/**
 * v0.25.5 WP-E: the pure-function form of [OnboardingPrefs.inRecapWindow].
 *
 * Lives at the top level (rather than as a method) so the test
 * surface can call it without an [android.content.Context]. The
 * method form on the class is the fluent-call surface; this
 * function is the test surface. Same body, two entry points.
 */
fun inRecapWindowPure(
    installDay: LocalDate?,
    recapSeenDay: LocalDate?,
    today: LocalDate,
): Boolean {
    if (installDay == null) return false
    val daysSince = java.time.temporal.ChronoUnit.DAYS.between(installDay, today).toInt()
    if (daysSince < 14) return false
    // Each window is 14 days apart; the window opens at day 14, 28,
    // 42, ... and is 7 days wide. So the 14-day window is days 14-20,
    // the next is days 28-34, then 42-48, etc.
    val windowIndex = (daysSince - 14) / 14
    val daysIntoWindow = daysSince - (14 + windowIndex * 14)
    if (daysIntoWindow !in 0..6) return false
    val seenWindow = recapSeenDay?.let { seen ->
        val seenSince = java.time.temporal.ChronoUnit.DAYS.between(installDay, seen).toInt()
        if (seenSince < 14) null else (seenSince - 14) / 14
    }
    return seenWindow != windowIndex
}
