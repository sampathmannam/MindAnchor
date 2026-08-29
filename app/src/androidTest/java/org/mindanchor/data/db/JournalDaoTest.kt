package org.mindanchor.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [JournalDao] against a real (in-memory) Room database, covering
 * the contract the DAO signatures encode: one entry per id, one morning
 * measure per local date, context rows kept separate from their entry, and
 * pending continuity changes acknowledged by snapshot id.
 */
@RunWith(AndroidJUnit4::class)
class JournalDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var dao: JournalDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        dao = db.journal()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertEntryAbortsOnDuplicateId() = runBlocking {
        val entry = JournalEntryEntity(
            id = "entry-1",
            createdAt = 1_000L,
            updatedAt = 1_000L,
            localDate = "2026-08-28",
            title = "A day",
            body = "Original words",
            kind = "DAILY",
            sourceDeviceId = "device-a",
            deletedAt = null,
        )
        dao.insertEntry(entry)

        try {
            dao.insertEntry(entry.copy(body = "Overwritten words"))
            fail("expected insertEntry to abort on a duplicate id")
        } catch (expected: SQLiteConstraintException) {
            // ABORT is the DAO's declared conflict strategy for insertEntry.
        }

        assertEquals("Original words", dao.entry("entry-1")?.body)
        assertEquals(1, dao.entriesNow().size)
    }

    @Test
    fun upsertMorningMeasureKeepsExactlyOneRowPerLocalDate() = runBlocking {
        dao.upsertMorningMeasure(
            MorningMeasureEntity(
                id = "measure-1",
                localDate = "2026-08-28",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                mood = 3,
                anxiety = 2,
                angerUrge = 1,
                energyFunction = 4,
                sleepQuality = 3,
                instrumentVersion = "v1",
                sourceDeviceId = "device-a",
            ),
        )
        // A different primary key but the same localDate: REPLACE resolves the
        // UNIQUE index on localDate (not just the primary key), so SQLite
        // deletes the first row before inserting this one.
        dao.upsertMorningMeasure(
            MorningMeasureEntity(
                id = "measure-2",
                localDate = "2026-08-28",
                createdAt = 2_000L,
                updatedAt = 2_000L,
                mood = 5,
                anxiety = 1,
                angerUrge = 0,
                energyFunction = 5,
                sleepQuality = 4,
                instrumentVersion = "v1",
                sourceDeviceId = "device-a",
            ),
        )

        val rows = dao.morningMeasuresNow()
        assertEquals(1, rows.size)
        assertEquals("measure-2", rows.first().id)
        assertEquals(5, rows.first().mood)
    }

    @Test
    fun contextRowsAreSeparateFromTheEntryTheyDescribe() = runBlocking {
        dao.insertEntry(
            JournalEntryEntity(
                id = "entry-1",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                localDate = "2026-08-28",
                title = "A day",
                body = "Original words",
                kind = "DAILY",
                sourceDeviceId = "device-a",
                deletedAt = null,
            ),
        )
        dao.upsertContext(
            listOf(
                JournalContextEntity(
                    id = "context-1",
                    entryId = "entry-1",
                    recordType = "MOOD",
                    key = "mood",
                    value = "calm",
                    sourceStart = 0,
                    sourceEnd = 4,
                    confidence = 0.9,
                    extractorVersion = "v1",
                    createdAt = 2_000L,
                ),
            ),
        )

        val context = dao.allContext()
        assertEquals(1, context.size)
        assertEquals("entry-1", context.first().entryId)
        assertEquals(1, dao.entriesNow().size)
    }

    @Test
    fun pendingChangesAreAcknowledgedBySnapshotId() = runBlocking {
        dao.insertChange(
            ContinuityChangeEntity(
                id = "change-1",
                entityType = "JournalEntry",
                entityId = "entry-1",
                operation = "CREATE",
                occurredAt = 1_000L,
                acknowledgedSnapshotId = null,
            ),
        )

        assertEquals(1, dao.pendingChanges().size)

        dao.acknowledgePending("snapshot-1")

        assertTrue(dao.pendingChanges().isEmpty())
    }
}
