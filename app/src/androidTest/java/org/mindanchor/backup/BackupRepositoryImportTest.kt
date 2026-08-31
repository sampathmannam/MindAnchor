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
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan
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
}
