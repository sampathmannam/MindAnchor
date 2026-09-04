package org.mindanchor.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The DataStore round-trip for [BackupPrefs].
 * v0.25.4 (WP-C); consolidated to one toggle plus
 * the nightly-sync bookkeeping in v0.70.7.
 *
 * [BackupPrefs] is the single opt-in gate for the
 * Google Drive backup (default `false`), plus
 * [BackupPrefs.lastSyncDay] — the bookkeeping
 * [DriveSyncSchedule.decide] reads to avoid
 * running the backup more than once in a night.
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

    @Test fun `default state has the toggle off and no last sync day`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        // The defaults are explicit (false / null),
        // not "the first emission" — a brand new
        // install has never written to the store,
        // so the defaults take effect.
        assertFalse("default driveNightlySyncEnabled must be false", prefs.driveNightlySyncEnabled.first())
        assertNull("default lastSyncDay must be null", prefs.lastSyncDay.first())
    }

    @Test fun `setDriveNightlySyncEnabled true round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setDriveNightlySyncEnabled(true)
        // The first() call subscribes to the flow
        // and awaits the first emission, which
        // includes the new write. If the write
        // did not land, first() awaits a default
        // (the timeout in DataStore).
        val collected = prefs.driveNightlySyncEnabled.first()
        assertTrue("setDriveNightlySyncEnabled(true) must round-trip", collected)
    }

    @Test fun `setDriveNightlySyncEnabled false round-trips after a previous true`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setDriveNightlySyncEnabled(true)
        prefs.setDriveNightlySyncEnabled(false)
        val collected = prefs.driveNightlySyncEnabled.first()
        assertFalse("setDriveNightlySyncEnabled(false) must round-trip", collected)
    }

    @Test fun `setLastSyncDay round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setLastSyncDay("2026-08-28")
        assertEquals("2026-08-28", prefs.lastSyncDay.first())
    }

    @Test fun `a fresh BackupPrefs instance reads the last write (DataStore persists)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        BackupPrefs(ctx).setDriveNightlySyncEnabled(true)
        val fresh = BackupPrefs(ctx)
        val collected = fresh.driveNightlySyncEnabled.first()
        assertEquals("DataStore is process-wide", true, collected)
    }

    @Test fun `reset clears the toggle and the last sync day back to defaults`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = BackupPrefs(ctx)
        prefs.setDriveNightlySyncEnabled(true)
        prefs.setLastSyncDay("2026-08-28")
        prefs.reset()
        assertFalse("reset must clear driveNightlySyncEnabled", prefs.driveNightlySyncEnabled.first())
        assertNull("reset must clear lastSyncDay", prefs.lastSyncDay.first())
    }
}
