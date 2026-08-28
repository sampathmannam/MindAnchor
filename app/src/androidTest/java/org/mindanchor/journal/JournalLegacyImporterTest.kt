package org.mindanchor.journal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.JournalContextEntity
import org.mindanchor.data.db.JournalDao
import org.mindanchor.data.db.JournalEntryEntity
import org.mindanchor.data.db.MorningMeasureEntity
import org.mindanchor.letters.JournalStore

/**
 * Proves the Task 4 legacy-import contract: every old protective-writing
 * [JournalStore] entry becomes exactly one Room `journal_entries` row
 * (mapped to the same-named [JournalKind]), the row id is deterministic so
 * a second import is a true no-op, structural context is derived for the
 * imported entry, and the [JournalMigrationPrefs] completion flag is only
 * ever set once the entry-insert transaction has durably succeeded.
 *
 * [JournalStore] and [JournalMigrationPrefs] are both real, on-device
 * DataStores (not swappable for a fake in this instrumented test), and
 * their backing `preferencesDataStore` delegate is a process-wide
 * singleton — so this class relies on two things to stay independent of
 * whatever ran before it: (1) [resetLegacyOnDiskState] wipes both
 * DataStore files once per process, before either delegate is ever
 * touched, so a previous instrumentation run on this emulator can't leak
 * a stale completion flag in; and (2) [FixMethodOrder] pins
 * [a_failedEntryInsertLeavesCompletionFlagUnset] to run first, so it is
 * the one test that gets to observe "flag never set" before any other
 * method in this class performs a real, successful import.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class JournalLegacyImporterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var journalStore: JournalStore
    private lateinit var migrationPrefs: JournalMigrationPrefs

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        journalStore = JournalStore(context)
        migrationPrefs = JournalMigrationPrefs(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun a_failedEntryInsertLeavesCompletionFlagUnset() = runBlocking {
        assertFalse(
            "completion flag must start unset in a fresh process",
            migrationPrefs.isLegacyImportComplete(),
        )
        journalStore.save(JournalStore.Kind.BA, LocalDate.of(2020, 1, 1), "a legacy entry")

        // A DAO whose insert always fails, wired into a real (working)
        // database so JournalLegacyImporter's `database.withTransaction { }`
        // genuinely runs a transaction around it. This proves the ordering
        // guarantee directly, without corrupting a real database connection
        // to manufacture the failure.
        val failingDao = FailingInsertJournalDao(db.journal())
        val importer = JournalLegacyImporter(journalStore, db, migrationPrefs, StructuralContextExtractor(), failingDao)

        var threw = false
        try {
            importer.importIfNeeded()
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue("importIfNeeded() must propagate the entry-insert failure", threw)
        assertFalse(
            "the completion flag must not be set when the entry-insert transaction failed",
            migrationPrefs.isLegacyImportComplete(),
        )
    }

    @Test
    fun b_importMapsEntriesDerivesContextAndIsIdempotent() = runBlocking {
        val baDate = LocalDate.of(2021, 3, 4)
        val dearManDate = LocalDate.of(2021, 3, 5)
        val gratitudeDate = LocalDate.of(2021, 3, 6)
        val expressiveDate = LocalDate.of(2021, 3, 7)
        journalStore.save(JournalStore.Kind.BA, baDate, "BA body")
        journalStore.save(JournalStore.Kind.DEAR_MAN, dearManDate, "DEAR MAN body")
        journalStore.save(JournalStore.Kind.GRATITUDE, gratitudeDate, "gratitude body")
        journalStore.save(JournalStore.Kind.EXPRESSIVE_WRITING, expressiveDate, "expressive body")

        val importer = JournalLegacyImporter(journalStore, db, migrationPrefs, StructuralContextExtractor())
        importer.importIfNeeded()

        assertTrue(migrationPrefs.isLegacyImportComplete())

        val dao = db.journal()
        val expected = listOf(
            Triple(JournalStore.Kind.BA, JournalKind.BA, baDate) to "BA body",
            Triple(JournalStore.Kind.DEAR_MAN, JournalKind.DEAR_MAN, dearManDate) to "DEAR MAN body",
            Triple(JournalStore.Kind.GRATITUDE, JournalKind.GRATITUDE, gratitudeDate) to "gratitude body",
            Triple(JournalStore.Kind.EXPRESSIVE_WRITING, JournalKind.EXPRESSIVE_WRITING, expressiveDate) to "expressive body",
        )

        for ((triple, body) in expected) {
            val (legacyKind, mappedKind, date) = triple
            val expectedId = UUID.nameUUIDFromBytes("legacy:${legacyKind.tag}:$date".encodeToByteArray()).toString()
            val expectedEpochMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val row = dao.entry(expectedId)
            assertEquals("row must exist at the deterministic legacy id for $legacyKind", body, row?.body)
            assertEquals(mappedKind.name, row?.kind)
            assertEquals("", row?.title)
            assertEquals("legacy-datastore", row?.sourceDeviceId)
            assertEquals(expectedEpochMillis, row?.createdAt)
            assertEquals(expectedEpochMillis, row?.updatedAt)
            assertEquals(null, row?.deletedAt)

            val contextRows = dao.allContext().filter { it.entryId == expectedId }
            assertTrue("context rows must exist for imported entry $legacyKind", contextRows.isNotEmpty())
            assertTrue(contextRows.any { it.key == "entry_kind" && it.value == mappedKind.name })
        }

        val countAfterFirstImport = dao.entriesNow().size

        // Re-run: must be a true no-op, not just "doesn't crash".
        importer.importIfNeeded()
        assertEquals(countAfterFirstImport, dao.entriesNow().size)

        // A second importer instance sharing the same completion flag also
        // must not re-import (the flag alone gates it).
        val secondImporter = JournalLegacyImporter(journalStore, db, migrationPrefs, StructuralContextExtractor())
        secondImporter.importIfNeeded()
        assertEquals(countAfterFirstImport, dao.entriesNow().size)
    }

    /**
     * Delegates every [JournalDao] method to [delegate] except
     * [insertEntriesIgnoreDuplicates], which always fails — used to prove
     * [JournalLegacyImporter] never sets the completion flag when the
     * entry-insert phase does not durably succeed.
     */
    private class FailingInsertJournalDao(private val delegate: JournalDao) : JournalDao {
        override suspend fun insertEntry(entry: JournalEntryEntity) = delegate.insertEntry(entry)
        override suspend fun upsertEntries(entries: List<JournalEntryEntity>) = delegate.upsertEntries(entries)
        override suspend fun insertEntriesIgnoreDuplicates(entries: List<JournalEntryEntity>): Unit =
            throw IllegalStateException("forced entry-insert failure for test")
        override fun entries() = delegate.entries()
        override suspend fun entry(id: String) = delegate.entry(id)
        override suspend fun tombstone(id: String, deletedAt: Long, updatedAt: Long) =
            delegate.tombstone(id, deletedAt, updatedAt)
        override suspend fun upsertContext(rows: List<JournalContextEntity>) = delegate.upsertContext(rows)
        override suspend fun allContext() = delegate.allContext()
        override suspend fun upsertMorningMeasure(measure: MorningMeasureEntity) =
            delegate.upsertMorningMeasure(measure)
        override suspend fun upsertMorningMeasures(measures: List<MorningMeasureEntity>) =
            delegate.upsertMorningMeasures(measures)
        override fun morningMeasures() = delegate.morningMeasures()
        override suspend fun morningMeasureByDate(localDate: String) = delegate.morningMeasureByDate(localDate)
        override suspend fun insertChange(change: ContinuityChangeEntity) = delegate.insertChange(change)
        override suspend fun pendingChanges() = delegate.pendingChanges()
        override suspend fun acknowledgePending(snapshotId: String) = delegate.acknowledgePending(snapshotId)
        override suspend fun entriesNow() = delegate.entriesNow()
        override suspend fun morningMeasuresNow() = delegate.morningMeasuresNow()
        override suspend fun allChangesNow() = delegate.allChangesNow()
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun resetLegacyOnDiskState() {
            // These are real, on-device preferencesDataStore files, not
            // swapped for a fake in this instrumented test. Wiping them
            // before either delegate is first touched in this process
            // means a previous instrumentation run on this emulator can't
            // leak a stale completion flag (or stale legacy entries) into
            // this run. Safe to run unconditionally: the files simply may
            // not exist yet on a clean emulator.
            val context: Context = ApplicationProvider.getApplicationContext()
            listOf("journal", "journal_migration").forEach { name ->
                java.io.File(context.filesDir, "datastore/$name.preferences_pb").delete()
            }
        }
    }
}
