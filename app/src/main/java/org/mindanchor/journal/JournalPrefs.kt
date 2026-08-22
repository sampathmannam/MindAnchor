/*
 * v0.65.0: journal persistence.
 *
 * The journal's `todayEntry` is the in-progress text the
 * user is writing on the Today / Quick note screens.
 * v0.64.0 kept it in memory only — a process kill
 * (reboot, OOM, force-stop) erased the prose. v0.65.0
 * persists it.
 *
 * Why a separate prefs file rather than NotesPrefs:
 *   - NotesPrefs is the encrypted, HMAC-sealed
 *     per-note store. Mixing the in-progress draft
 *     into it would commit the draft to the
 *     permanent record on every keystroke (a v0.20
 *     design). v0.65.0 keeps the draft and the
 *     committed record separate: a draft is a draft,
 *     a Note is something the user has chosen to keep.
 *   - The draft does not need HMAC sealing. A
 *     tampered draft is the user's problem, not a
 *     threat-model concern — the on-disk Notes list
 *     is the one that needs the seal.
 *   - The draft's "empty string" is a valid value
 *     (the empty-Today state). NotesPrefs requires
 *     a non-empty body to be a valid Note.
 *
 * Storage: a single DataStore (Preferences API) with
 * one key — `today_entry`. The default value is the
 * v0.64.0 fixture prose (so a fresh install on v0.65.0
 * shows the same onboarding state as v0.64.0). On the
 * first read after a v0.64.0 → v0.65.0 upgrade, the
 * DataStore returns the default — the user sees the
 * fixture. On the first write, the value is committed
 * and the default never applies again.
 *
 * v0.65.0+ will split this into a `drafts` table when
 * NotesPrefs gets a per-draft lifecycle. For v0.65.0
 * the single-key DataStore is the right size.
 */
package org.mindanchor.journal

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.journalDataStore by preferencesDataStore(name = "journal_draft")

/**
 * v0.65.0 draft persistence. One key, one Flow.
 *
 * Default = the v0.64.0 fixture so a fresh install and
 * a v0.64.0 → v0.65.0 upgrade land on the same screen
 * state. The default never applies after the first
 * write — once the user types anything, the DataStore
 * holds their text.
 */
class JournalPrefs(private val context: Context) {

    /**
     * The current draft text. Emits the persisted value
     * on every change. Collectors should `first()` to
     * read the snapshot at startup, then `collect` to
     * react to writes from another composable.
     */
    val todayEntry: Flow<String> =
        context.journalDataStore.data.map {
            it[todayEntryKey] ?: DEFAULT_FIXTURE
        }

    /**
     * Persist the current draft. Called on every
     * keystroke (debounced by the call site if needed;
     * the DataStore write is async, so a per-keystroke
     * call does not block the main thread). Pass "" to
     * clear — the empty string is a valid value (the
     * empty-Today state).
     */
    suspend fun setTodayEntry(value: String) {
        context.journalDataStore.edit { prefs ->
            prefs[todayEntryKey] = value
        }
    }

    companion object {
        /**
         * The v0.64.0 default fixture. Same prose the
         * v0.64.0 superdesign drafts render. v0.65.0
         * inherits this as the default so a fresh install
         * shows the same onboarding state — the user
         * sees text on the page, not a blank slate.
         */
        const val DEFAULT_FIXTURE: String =
            "The light through the window is different today. It feels quieter. I haven't said it out loud yet, but there is a strange sort of peace in just noticing the way the shadows stretch across the floor. No expectations for the next hour. Just this."

        private val todayEntryKey = stringPreferencesKey("today_entry")
    }
}
