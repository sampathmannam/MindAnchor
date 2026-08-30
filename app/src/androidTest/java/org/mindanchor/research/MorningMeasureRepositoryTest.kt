package org.mindanchor.research

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.withResearchImmutability
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.continuity.ContinuityPrefs
import kotlinx.coroutines.flow.first

/**
 * Proves the core Task 5 guarantees: saving a morning measure commits the
 * row and its pending continuity change atomically, and `morning_measures`
 * never holds more than one row for the same local date — a second save
 * for the same date updates the existing row in place.
 */
@RunWith(AndroidJUnit4::class)
class MorningMeasureRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var deviceIdentity: DeviceIdentityStore

    @Before
    fun setUp() = runBlocking {
        ContinuityPrefs(context).reset()
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability()
            .build()
        deviceIdentity = DeviceIdentityStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        ContinuityPrefs(context).reset()
    }

    @Test
    fun saveCommitsMeasureAndContinuityChangeTogether() = runBlocking {
        val repository = MorningMeasureRepository(
            context,
            db,
            deviceIdentity,
            testLedgerRepository(context, db).provenance,
        )
        val localDate = LocalDate.of(2026, 8, 28)

        val measure = repository.save(
            localDate = localDate,
            now = 1_000L,
            mood = 3,
            anxiety = 3,
            angerUrge = 3,
            energyFunction = 3,
            sleepQuality = 3,
        )

        val stored = db.journal().morningMeasuresNow()
        assertEquals(1, stored.size)
        assertEquals(measure.id, stored.first().id)

        val pending = db.journal().pendingChanges()
        assertTrue(
            pending.any {
                it.entityType == "MORNING_MEASURE" && it.entityId == measure.id && it.operation == "CREATE"
            },
        )
        val highWater = requireNotNull(ContinuityPrefs(context).ledgerHighWater.first())
        assertEquals(db.research().ledgerEventCount(), highWater.eventCount)
        assertEquals(db.research().ledgerHead()?.eventHash, highWater.headHash)
    }

    @Test
    fun secondSaveForSameDateUpdatesInPlaceInsteadOfInserting() = runBlocking {
        val repository = MorningMeasureRepository(
            context,
            db,
            deviceIdentity,
            testLedgerRepository(context, db).provenance,
        )
        val localDate = LocalDate.of(2026, 8, 28)

        val first = repository.save(
            localDate = localDate,
            now = 1_000L,
            mood = 3,
            anxiety = 3,
            angerUrge = 3,
            energyFunction = 3,
            sleepQuality = 3,
        )

        val second = repository.save(
            localDate = localDate,
            now = 2_000L,
            mood = 5,
            anxiety = 1,
            angerUrge = 4,
            energyFunction = 2,
            sleepQuality = 5,
        )

        val stored = db.journal().morningMeasuresNow()
        assertEquals("second save for the same date must update in place, not insert a second row", 1, stored.size)
        assertEquals(first.id, second.id)
        assertEquals(1_000L, second.createdAt)
        assertEquals(2_000L, second.updatedAt)
        assertEquals(5, stored.first().mood)
        assertEquals(1, stored.first().anxiety)

        val pending = db.journal().pendingChanges()
        assertTrue(
            pending.any {
                it.entityType == "MORNING_MEASURE" && it.entityId == first.id && it.operation == "UPDATE"
            },
        )
    }

    @Test
    fun savesForDifferentDatesProduceTwoRows() = runBlocking {
        val repository = MorningMeasureRepository(
            context,
            db,
            deviceIdentity,
            testLedgerRepository(context, db).provenance,
        )

        repository.save(
            localDate = LocalDate.of(2026, 8, 27),
            now = 1_000L,
            mood = 3,
            anxiety = 3,
            angerUrge = 3,
            energyFunction = 3,
            sleepQuality = 3,
        )
        repository.save(
            localDate = LocalDate.of(2026, 8, 28),
            now = 2_000L,
            mood = 3,
            anxiety = 3,
            angerUrge = 3,
            energyFunction = 3,
            sleepQuality = 3,
        )

        val stored = db.journal().morningMeasuresNow()
        assertEquals(2, stored.size)
    }
}
