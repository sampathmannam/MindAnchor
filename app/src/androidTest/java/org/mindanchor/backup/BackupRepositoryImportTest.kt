package org.mindanchor.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.first
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan
import org.mindanchor.data.replaceFlagged
import org.mindanchor.support.RoomSafetyPlanStore
import org.mindanchor.support.SafetyPlanRoomHarness

@RunWith(AndroidJUnit4::class)
class BackupRepositoryImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var room: SafetyPlanRoomHarness

    @Before
    fun setUp() {
        room = SafetyPlanRoomHarness(context)
    }

    @After
    fun tearDown() = room.close()

    @Test
    fun importUsesStoreMonotonicOrderingWhenItsNowIsOlder() = runBlocking {
        room.dao.savePlan(SafetyPlan(warningSigns = "local", updatedAt = 500L))
        val store = RoomSafetyPlanStore(room.dao) { 100L }
        val repository = BackupRepository(context, room.database, store)
        val backup = BackupCodec.encode(
            BackupCodec.Backup(plan = BackupCodec.Plan(warningSigns = "restored")),
        )

        assertTrue(repository.import(backup, now = 100L))
        val stored = checkNotNull(room.dao.planNow())
        assertEquals("restored", stored.warningSigns)
        assertEquals(501L, stored.updatedAt)
    }

    @Test
    fun failedPlanCommandStopsImportBeforeContacts() = runBlocking {
        room.installAbortInsertTrigger()
        val repository = BackupRepository(
            context,
            room.database,
            RoomSafetyPlanStore(room.dao) { 100L },
        )
        val backup = BackupCodec.encode(
            BackupCodec.Backup(
                plan = BackupCodec.Plan(warningSigns = "restored"),
                contacts = listOf(BackupCodec.Contact("Priya", "5551234567")),
            ),
        )

        val thrown = runCatching { repository.import(backup, now = 100L) }.exceptionOrNull()
        assertNotNull(thrown)
        room.drainTransactions()
        assertNull(room.dao.planNow())
        assertEquals(emptyList<CrisisContact>(), room.dao.contactsNow())
    }

    // --- "Add a pause before opening" survives a backup ------------------
    //
    // The pauses are the reason the launcher exists, and they are a
    // preference, not history: a phone restoring a copy of itself should
    // come back with the same apps gated. The field was in the file format
    // from the start but the export hardcoded an empty list, so a restore
    // silently returned a phone with every pause gone.

    @Test
    fun exportCarriesTheFlaggedApps() = runBlocking {
        val friction = FrictionPrefs(context)
        friction.replaceFlagged(setOf("com.example.social", "com.example.news"))
        val repository = BackupRepository(context, room.database, RoomSafetyPlanStore(room.dao))

        val decoded = checkNotNull(BackupCodec.decode(repository.export(now = 1L)))

        assertEquals(listOf("com.example.news", "com.example.social"), decoded.frictioned)
    }

    @Test
    fun importRestoresTheFlaggedApps() = runBlocking {
        val friction = FrictionPrefs(context)
        friction.replaceFlagged(emptySet())
        val repository = BackupRepository(context, room.database, RoomSafetyPlanStore(room.dao))
        val backup = BackupCodec.encode(
            BackupCodec.Backup(frictioned = listOf("com.example.social")),
        )

        assertTrue(repository.import(backup, now = 100L))

        assertEquals(setOf("com.example.social"), friction.flaggedApps.first())
    }

    @Test
    fun importOfAFileWithNoFlaggedAppsLeavesThePhoneAlone() = runBlocking {
        // Every copy saved by a build that hardcoded the empty list says
        // "frictioned": []. Treating that as "flag nothing" would delete the
        // pauses of anyone restoring one of those older files.
        val friction = FrictionPrefs(context)
        friction.replaceFlagged(setOf("com.example.kept"))
        val repository = BackupRepository(context, room.database, RoomSafetyPlanStore(room.dao))
        val backup = BackupCodec.encode(BackupCodec.Backup(frictioned = emptyList()))

        assertTrue(repository.import(backup, now = 100L))

        assertEquals(setOf("com.example.kept"), friction.flaggedApps.first())
    }
}
