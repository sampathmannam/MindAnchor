/*
 * v0.66.0 (DBT-grounded journal) — Task 10.
 *
 * The DataStore-backed persistence for the three v0.66.0
 * settings toggles that gate the new optional surfaces
 * (voice-first, 2D Affect-Grid mood, therapist PDF
 * export). One boolean per key, all default OFF per
 * v0.65.0 BPD-safe defaults.
 *
 * ## Why a new DataStore file (`journal_settings_v66`)
 *
 * The journal package already has `JournalPrefs` (the
 * draft-text DataStore, file name `journal_draft`) and
 * a `diary_card_v66` DataStore (file name
 * `diary_card_v66`) — see
 * `app/src/main/java/org/mindanchor/journal/diary/DiaryCardPrefs.kt:90`
 * for the rationale on the `_v66` suffix (it avoids a
 * collision with the v0.28.0 `support.DiaryCardPrefs`,
 * which uses `diary_card`). The new toggles here are
 * settings metadata, not journal drafts and not diary
 * cards; reusing either of those stores would conflate
 * "did the user write today" with "is voice-first on",
 * and the existing v0.28.0 `support.DiaryCardPrefs` is
 * scheduled to leave the binary in a follow-up release
 * (so we cannot piggyback on it either). A new file
 * keeps the boundaries clean.
 *
 * The DataStore name MUST NOT collide with any other
 * `preferencesDataStore(name = "...")` in `app/src/main`
 * — verified at commit time by the Task 10 grep step.
 *
 * ## Why not SharedPreferences
 *
 * The v0.65.0 brief for the journal settings mentioned
 * a `SettingsPrefs` SharedPreferences layer, but
 * `app/src/main/java/org/mindanchor/journal/` has no
 * such class (verified by grep on 2026-08-21) — the
 * existing v0.64.0 toggles in `JournalSettings.kt:73-74`
 * are in-memory `mutableStateOf(false)` only, and the
 * rest of the project has moved to DataStore Preferences
 * (10+ stores in `app/src/main`). Staying on DataStore
 * keeps the on-device persistence story uniform.
 *
 * ## Why `public` (no modifier)
 *
 * The class is owned by `JournalSettings` (the screen
 * composable) but the three flags will be read by
 * downstream composables: `JournalToday` (Task 11,
 * Affect-Grid) and `JournalCrisis` (Task 12, voice-
 * first + therapist export). Those surfaces are in the
 * same `org.mindanchor.journal` package, so technically
 * `internal` would work, but `public` keeps the door
 * open for later cross-package composition without an
 * `internal` → `public` migration churn. There is no
 * state on the class itself; each toggle is a fresh
 * DataStore read, so no thread-safety surface to
 * protect.
 */
package org.mindanchor.journal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The DataStore name `journal_settings_v66` is unique
// within `app/src/main` — verified by the Task 10 grep
// `grep -r 'preferencesDataStore' app/src/main`. The
// extension is private to this file; the public class
// below is the only call site. This matches the pattern
// in `JournalPrefs.kt:48` and `DiaryCardPrefs.kt:90`.
private val Context.journalSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "journal_settings_v66",
)

/**
 * The three v0.66.0 settings toggles, persisted on-device
 * in a private DataStore file. All three default OFF.
 *
 * The class is a thin pass-through: each read is a
 * `Flow<Boolean>` mapped from the DataStore, and each
 * write is a `suspend fun` that calls `edit`. No
 * caching, no in-memory mirror — `JournalSettings`
 * collects the flows with `collectAsStateWithLifecycle`
 * and renders the result, which keeps the DataStore
 * emission as the single source of truth.
 */
class JournalSettingsPrefs(private val context: Context) {

    private val store get() = context.journalSettingsStore

    /**
     * Voice-first mode for the crisis surface, the
     * check-in, and the skills screen. When OFF, all
     * surfaces use the same text-first layout as
     * v0.65.0. Default OFF.
     */
    val voiceFirstEnabled: Flow<Boolean> = store.data.map { it[KEY_VOICE] ?: false }

    /**
     * The 2D Affect-Grid mood input on the Today screen.
     * When OFF, the existing 1D mood slider is shown.
     * Default OFF.
     */
    val affectGridEnabled: Flow<Boolean> = store.data.map { it[KEY_AFFECT] ?: false }

    /**
     * The "Share with therapist PDF" action in the
     * Crisis surface and the Today footer. When OFF,
     * the action is hidden. Default OFF.
     */
    val therapistExportEnabled: Flow<Boolean> = store.data.map { it[KEY_EXPORT] ?: false }

    /**
     * v0.67.0: the user's chosen display name (e.g.
     * "Maya R" or "DC"). Used by `TherapistExport` to
     * personalise the PDF file name — the recipient sees
     * "MindAnchor-Maya-R-2026-08-09-2026-08-22.pdf" rather
     * than the generic "MindAnchor-Therapist-Export-…"
     * string. The name is also shown in the privacy
     * policy activity. Empty by default; the user sets it
     * in Settings. Not validated here — the export class
     * sanitises non-ASCII / special characters before
     * putting the tag in a file name.
     */
    val displayName: Flow<String> = store.data.map { it[KEY_DISPLAY_NAME] ?: "" }

    /**
     * v0.67.0: whether the first-run journal onboarding
     * has been dismissed. Default false (not yet seen).
     * When false, [JournalRoot] renders the 3-card
     * overlay above the Today surface on first
     * composition. Tapping "Got it" or "Don't show this
     * again" sets the flag to true. The user can re-open
     * the onboarding from Settings → About → "Show
     * journal intro" (the row sets the flag back to
     * false), so the dismissal is reversible, not a
     * one-way data loss.
     */
    val onboardingSeen: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_SEEN] ?: false }

    suspend fun setVoiceFirstEnabled(value: Boolean) {
        store.edit { it[KEY_VOICE] = value }
    }

    suspend fun setAffectGridEnabled(value: Boolean) {
        store.edit { it[KEY_AFFECT] = value }
    }

    suspend fun setTherapistExportEnabled(value: Boolean) {
        store.edit { it[KEY_EXPORT] = value }
    }

    suspend fun setDisplayName(value: String) {
        store.edit { it[KEY_DISPLAY_NAME] = value.trim() }
    }

    suspend fun setOnboardingSeen(value: Boolean) {
        store.edit { it[KEY_ONBOARDING_SEEN] = value }
    }

    private companion object {
        val KEY_VOICE = booleanPreferencesKey("voice_first_enabled")
        val KEY_AFFECT = booleanPreferencesKey("affect_grid_enabled")
        val KEY_EXPORT = booleanPreferencesKey("therapist_export_enabled")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_ONBOARDING_SEEN = booleanPreferencesKey("journal_onboarding_seen")
    }
}
