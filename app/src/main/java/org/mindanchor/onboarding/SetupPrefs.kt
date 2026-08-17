/*
 * v0.35.1 — setup wizard preferences.
 *
 * The wizard has its own DataStore (named "setup_wizard") separate
 * from the existing "onboarding" DataStore. The two pref sets are
 * unrelated: the "onboarding" one is the goal-elicitation flow the
 * user goes through on first run, the "setup_wizard" one is the
 * data-source walkthrough that happens after.
 *
 * The wizard opens the first time the home is shown with no source
 * connected, and never again unless the user re-runs it from
 * Settings → "Run setup wizard again". The two flags that gate the
 * auto-open are:
 *
 *   * `wizardCompleted` — set when the user reaches the Done step.
 *     Means: do not auto-open.
 *   * `userDismissedWizard` — set when the user backs out of step
 *     1 (or otherwise says "not now"). Means: do not auto-open on
 *     this cold start. Re-runs from Settings are still possible
 *     regardless of this flag.
 *
 * Per-step `skipped` flags carry the user's "I will come back to
 * this" intent. Re-running the wizard from Settings lands on the
 * first step whose `skipped == false`, not from Welcome.
 */
package org.mindanchor.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.setupWizardDataStore by preferencesDataStore(name = "setup_wizard")

/** The 5 setup steps in order. Welcome (idx 0) and Done (idx 5) are not skippable. */
enum class SetupStep {
    WELCOME,
    HEALTH_CONNECT,
    PAIR_WATCH,
    COROS,
    PPG,
    DONE,
}

/**
 * The per-step skip state, in a single immutable snapshot.
 *
 * The ViewModel reads this and decides the next step. Splitting
 * the decision out of [SetupPrefs] keeps the prefs class
 * side-effect free and the ViewModel test surface simple.
 */
data class SetupProgress(
    val healthConnectSkipped: Boolean = false,
    val pairWatchSkipped: Boolean = false,
    val corosSkipped: Boolean = false,
    val ppgSkipped: Boolean = false,
) {
    /** The first non-skipped step at or after [after]. */
    fun firstPendingAfter(after: SetupStep): SetupStep = when (after) {
        SetupStep.WELCOME ->
            if (!healthConnectSkipped) SetupStep.HEALTH_CONNECT
            else if (!pairWatchSkipped) SetupStep.PAIR_WATCH
            else if (!corosSkipped) SetupStep.COROS
            else if (!ppgSkipped) SetupStep.PPG
            else SetupStep.DONE
        SetupStep.HEALTH_CONNECT ->
            if (!pairWatchSkipped) SetupStep.PAIR_WATCH
            else if (!corosSkipped) SetupStep.COROS
            else if (!ppgSkipped) SetupStep.PPG
            else SetupStep.DONE
        SetupStep.PAIR_WATCH ->
            if (!corosSkipped) SetupStep.COROS
            else if (!ppgSkipped) SetupStep.PPG
            else SetupStep.DONE
        SetupStep.COROS ->
            if (!ppgSkipped) SetupStep.PPG
            else SetupStep.DONE
        SetupStep.PPG -> SetupStep.DONE
        SetupStep.DONE -> SetupStep.DONE
    }
}

class SetupPrefs(private val context: Context) {

    private val wizardCompletedKey = booleanPreferencesKey("wizard_completed")
    private val userDismissedKey = booleanPreferencesKey("user_dismissed_wizard")
    private val welcomeSeenKey = booleanPreferencesKey("welcome_seen")
    private val healthConnectSkippedKey = booleanPreferencesKey("health_connect_skipped")
    private val pairWatchSkippedKey = booleanPreferencesKey("pair_watch_skipped")
    private val corosSkippedKey = booleanPreferencesKey("coros_skipped")
    private val ppgSkippedKey = booleanPreferencesKey("ppg_skipped")

    val wizardCompleted: Flow<Boolean> = context.setupWizardDataStore.data
        .map { it[wizardCompletedKey] ?: false }

    val userDismissedWizard: Flow<Boolean> = context.setupWizardDataStore.data
        .map { it[userDismissedKey] ?: false }

    val progress: Flow<SetupProgress> = context.setupWizardDataStore.data.map { prefs ->
        SetupProgress(
            healthConnectSkipped = prefs[healthConnectSkippedKey] ?: false,
            pairWatchSkipped = prefs[pairWatchSkippedKey] ?: false,
            corosSkipped = prefs[corosSkippedKey] ?: false,
            ppgSkipped = prefs[ppgSkippedKey] ?: false,
        )
    }

    /** True if the user finished the wizard at least once. */
    suspend fun markCompleted() {
        context.setupWizardDataStore.edit { it[wizardCompletedKey] = true }
    }

    /** True if the user backed out of step 1 (or otherwise said "not now"). */
    suspend fun markDismissed() {
        context.setupWizardDataStore.edit { it[userDismissedKey] = true }
    }

    /** Clear the dismissed flag so the next cold start re-prompts. */
    suspend fun clearDismissed() {
        context.setupWizardDataStore.edit { it[userDismissedKey] = false }
    }

    suspend fun markWelcomeSeen() {
        context.setupWizardDataStore.edit { it[welcomeSeenKey] = true }
    }

    suspend fun setSkipped(step: SetupStep, skipped: Boolean) {
        val key = when (step) {
            SetupStep.HEALTH_CONNECT -> healthConnectSkippedKey
            SetupStep.PAIR_WATCH -> pairWatchSkippedKey
            SetupStep.COROS -> corosSkippedKey
            SetupStep.PPG -> ppgSkippedKey
            else -> return // WELCOME and DONE are not skippable
        }
        context.setupWizardDataStore.edit { it[key] = skipped }
    }

    /** Clear the dismissed flag plus the per-step skipped flags. Used on re-run from Settings. */
    suspend fun reset() {
        context.setupWizardDataStore.edit { prefs ->
            prefs[userDismissedKey] = false
            prefs[healthConnectSkippedKey] = false
            prefs[pairWatchSkippedKey] = false
            prefs[corosSkippedKey] = false
            prefs[ppgSkippedKey] = false
        }
    }
}
