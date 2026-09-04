package org.mindanchor.journal

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.journalMigrationDataStore by preferencesDataStore(name = "journal_migration")

/**
 * Tracks whether the one-time legacy Journal import ([JournalLegacyImporter])
 * has completed. A dedicated, minimal DataStore holding a single boolean
 * flag — nothing else belongs here.
 */
class JournalMigrationPrefs(private val context: Context) {

    private val legacyImportCompleteKey = booleanPreferencesKey("legacy_import_complete")

    suspend fun isLegacyImportComplete(): Boolean =
        context.journalMigrationDataStore.data.first()[legacyImportCompleteKey] ?: false

    suspend fun markLegacyImportComplete() {
        context.journalMigrationDataStore.edit { prefs ->
            prefs[legacyImportCompleteKey] = true
        }
    }

    /**
     * Unsets the completion flag. Test-only: an instrumented test that
     * drives the real [JournalLegacyImporter] (by launching the real
     * [JournalActivity]) must be able to leave this on-device, process-wide
     * DataStore singleton exactly as it found it — deleting the backing
     * file from outside does not reset an already-open DataStore instance,
     * so this goes through the same singleton every reader/writer shares.
     */
    suspend fun clear() {
        context.journalMigrationDataStore.edit { prefs ->
            prefs.remove(legacyImportCompleteKey)
        }
    }
}
