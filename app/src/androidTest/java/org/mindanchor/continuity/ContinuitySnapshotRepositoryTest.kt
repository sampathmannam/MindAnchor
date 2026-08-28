package org.mindanchor.continuity

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.journal.JournalRepository
import org.mindanchor.journal.StructuralContextExtractor
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Note
import org.mindanchor.research.toEntity

/**
 * Proves the Task 7 capture guarantee: [ContinuitySnapshotRepository.capture]
 * produces a snapshot whose [ContinuitySnapshot.contentSha256] matches an
 * independently-recomputed hash of the same seeded data, and that capturing
 * twice against identical underlying content produces the same
 * `contentSha256` both times even though `snapshotId` and `createdAt`
 * necessarily differ.
 */
@RunWith(AndroidJUnit4::class)
class ContinuitySnapshotRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var deviceIdentity: DeviceIdentityStore
    private lateinit var notesPrefs: NotesPrefs
    private lateinit var letterStore: LetterStore
    private lateinit var frictionPrefs: FrictionPrefs
    private lateinit var repository: ContinuitySnapshotRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        deviceIdentity = DeviceIdentityStore(context)
        notesPrefs = NotesPrefs(context)
        letterStore = LetterStore(context)
        frictionPrefs = FrictionPrefs(context)
        repository = ContinuitySnapshotRepository(
            context = context,
            database = db,
            notesPrefs = notesPrefs,
            letterStore = letterStore,
            frictionPrefs = frictionPrefs,
            deviceIdentity = deviceIdentity,
            backupRepository = BackupRepository(context),
        )

        // These DataStores are real, on-device, process-wide singletons —
        // clear them so a previous instrumentation run on this emulator
        // cannot leak state into this test.
        letterStore.reset()
        clearNotes()
        clearFriction()
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        letterStore.reset()
        clearNotes()
        clearFriction()
    }

    private suspend fun clearNotes() {
        notesPrefs.replaceAll(emptyList())
    }

    private suspend fun clearFriction() {
        frictionPrefs.replaceFlaggedApps(emptySet())
        frictionPrefs.replaceAlwaysOpenApps(emptySet())
    }

    private suspend fun seed() {
        val journalRepository = JournalRepository(context, db, deviceIdentity, StructuralContextExtractor())
        journalRepository.create(
            title = "A day",
            body = "Something happened today.",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 27),
        )
        db.journal().upsertMorningMeasure(
            org.mindanchor.research.MorningMeasure.create(
                localDate = LocalDate.of(2026, 8, 27),
                now = 1_000L,
                mood = 3,
                anxiety = 2,
                angerUrge = 1,
                energyFunction = 4,
                sleepQuality = 3,
                sourceDeviceId = "device-a",
            ).toEntity(),
        )
        notesPrefs.add(Note(id = 1L, body = "A quick note", createdAt = 500L, updatedAt = 500L))
        letterStore.save(Letter(date = LocalDate.of(2026, 8, 26), body = "A letter"))
        letterStore.setRead(LocalDate.of(2026, 8, 26), true)
        frictionPrefs.setFlagged("com.example.social", true)
        frictionPrefs.setAlwaysOpen("com.example.work", true)
    }

    @Test
    fun captureProducesAHashMatchingAnIndependentRecomputation() = runBlocking {
        seed()

        val snapshot = repository.capture(now = 5_000L)

        val recomputed = ContinuityContentHasher.hash(snapshot.payload)
        assertEquals(recomputed, snapshot.contentSha256)

        // Sanity: the payload actually carries the seeded rows, not an
        // accidentally-empty capture that would make the hash comparison
        // vacuous.
        assertTrue(snapshot.payload.journalEntries.isNotEmpty())
        assertTrue(snapshot.payload.morningMeasures.isNotEmpty())
        assertTrue(snapshot.payload.notes.isNotEmpty())
        assertTrue(snapshot.payload.letters.isNotEmpty())
        assertTrue(snapshot.payload.readLetterDates.isNotEmpty())
        assertTrue(snapshot.payload.frictionedApps.isNotEmpty())
        assertTrue(snapshot.payload.alwaysOpenApps.isNotEmpty())
        assertFalse(snapshot.payload.legacyBackupJson.isBlank())
    }

    @Test
    fun capturingTwiceWithIdenticalContentProducesTheSameContentHash() = runBlocking {
        seed()

        val first = repository.capture(now = 5_000L)
        val second = repository.capture(now = 6_000L)

        assertEquals(first.contentSha256, second.contentSha256)
        // The two captures are still genuinely distinct snapshots.
        assertNotEquals(first.snapshotId, second.snapshotId)
        assertNotEquals(first.createdAt, second.createdAt)
    }
}
