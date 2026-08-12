package org.mindanchor.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The DataStore round-trip for [BackupPrefs].
 * v0.25.4 (WP-C).
 *
 * [BackupPrefs] is the v0.25.4 per-type
 * auto-sync toggle store. Two boolean keys,
 * each defaulting to `false` (opt-in). The
 * Settings sub-section reads / writes the
 * flow; the WP-D scheduler reads the same
 * store to decide whether to fire on a new
 * note / letter.
 *
 * Five tests:
 *  1. Default state: both toggles are `false`.
 *  2. [setAutoSyncNotes] round-trips.
 *  3. [setAutoSyncLetters] round-trips.
 *  4. A fresh [BackupPrefs] instance on the
 *     same context reads the last write
 *     (DataStore is process-wide).
 *  5. [reset] clears the store.
 *
 * Robolectric 4.13 with `@Config(sdk = [34])`
 * is the project's pinned test configuration
 * (see the v0.25.2 Task 13 reader-prefs test
 * for the rationale).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPrefsRoundTripFindingTest {

    @Before fun resetStore() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        BackupPrefs(ctx).reset()
    }

    @Test fun `default state has both toggles off`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        val notes = prefs.autoSyncNotes.first()
        val letters = prefs.autoSyncLetters.first()
        // The defaults are explicit (false),
        // not "the first emission" — a brand
        // new install has never written to the
        // store, so the defaults take effect.
        assertFalse("default autoSyncNotes must be false", notes)
        assertFalse("default autoSyncLetters must be false", letters)
    }

    @Test fun `setAutoSyncNotes true round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setAutoSyncNotes(true)
        // The first() call subscribes to the flow
        // and awaits the first emission, which
        // includes the new write. If the write
        // did not land, first() awaits a default
        // (the timeout in DataStore).
        val collected = prefs.autoSyncNotes.first()
        assertTrue("setAutoSyncNotes(true) must round-trip", collected)
    }

    @Test fun `setAutoSyncLetters false round-trips after a previous true`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setAutoSyncLetters(true)
        prefs.setAutoSyncLetters(false)
        val collected = prefs.autoSyncLetters.first()
        assertFalse("setAutoSyncLetters(false) must round-trip", collected)
    }

    @Test fun `a fresh BackupPrefs instance reads the last write (DataStore persists)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        BackupPrefs(ctx).setAutoSyncNotes(true)
        val fresh = BackupPrefs(ctx)
        val collected = fresh.autoSyncNotes.first()
        assertEquals("DataStore is process-wide", true, collected)
    }

    @Test fun `reset clears both toggles back to false`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setAutoSyncNotes(true)
        prefs.setAutoSyncLetters(true)
        prefs.reset()
        val notes = prefs.autoSyncNotes.first()
        val letters = prefs.autoSyncLetters.first()
        assertFalse("reset must clear autoSyncNotes", notes)
        assertFalse("reset must clear autoSyncLetters", letters)
    }
}
