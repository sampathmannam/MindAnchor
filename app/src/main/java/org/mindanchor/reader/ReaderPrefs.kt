package org.mindanchor.reader

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The text size used by every long-form reading surface. v0.25.2-B
 * ships this for the letter; the report will reuse the same
 * [ReaderPrefs] in v0.25.3.
 *
 * Three sizes, pinned by [org.mindanchor.reader.ReadingSizeDefaultsFindingTest]:
 *   SMALL  = 14sp  (88% of 16sp baseline)
 *   MEDIUM = 18sp  (default; 113% of 16sp baseline)
 *   LARGE  = 32sp  (exactly 200% of 16sp baseline; the WCAG 2.2 SC
 *                    1.4.4 maximum compliant size)
 */
data class ReadingSize(val sp: Int) {
    companion object {
        val SMALL = ReadingSize(14)
        val MEDIUM = ReadingSize(18)
        val LARGE = ReadingSize(32)
    }
}

/**
 * Persists the user's chosen [ReadingSize] across app restarts.
 *
 * v0.25.2-B (this file). Replaces the v0.25.2-A stub that held the
 * value in [android.content.SharedPreferences]. The DataStore-backed
 * version is a [Flow] (not a [kotlinx.coroutines.flow.StateFlow])
 * because DataStore's read API is fundamentally a flow, and the
 * Settings view model already wraps it in
 * `.stateIn(viewModelScope, SharingStarted.Eagerly, MEDIUM)` —
 * Task 9 made that conversion forward-compatible with this widening.
 *
 * Distinct from [org.mindanchor.letters.LetterStore] so a fresh
 * install (no letters yet) still has a size set, and so future
 * reading surfaces (the report, planned for v0.25.3) can share the
 * same DataStore.
 */
class ReaderPrefs(private val context: Context) {

    private val sizeKey = intPreferencesKey("reading_size_sp")

    val size: Flow<ReadingSize> = context.readerDataStore.data
        .map { prefs -> prefs[sizeKey]?.let(::ReadingSize) ?: ReadingSize.MEDIUM }

    suspend fun setSize(size: ReadingSize) {
        context.readerDataStore.edit { it[sizeKey] = size.sp }
    }

    /**
     * Clears every key in the underlying DataStore. Test-only — used
     * by [org.mindanchor.reader.ReaderPrefsRoundTripFindingTest]'s
     * `@Before` to isolate tests in the same class (DataStore is a
     * process-wide singleton keyed on the preferences name, so two
     * tests in the same class share state without an explicit reset).
     *
     * `internal` so the test (same module) can call it, but the rest
     * of the app and any third-party callers cannot. Production code
     * does not need to clear the store; the "default" is the
     * in-memory fallback in the `size` flow.
     */
    internal suspend fun reset() {
        context.readerDataStore.edit { it.clear() }
    }

    private companion object {
        private val Context.readerDataStore by preferencesDataStore(name = "reader_prefs")
    }
}
