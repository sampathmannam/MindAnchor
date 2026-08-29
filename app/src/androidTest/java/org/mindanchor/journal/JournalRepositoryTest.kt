package org.mindanchor.journal

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
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.data.db.AnchorDatabase

/**
 * Proves the core Task 3 guarantee: a Journal entry's authorship is durable
 * even when deriving structural context from it fails. Context derivation
 * is a nice-to-have; the user's own words are never at risk.
 */
@RunWith(AndroidJUnit4::class)
class JournalRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AnchorDatabase
    private lateinit var deviceIdentity: DeviceIdentityStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java).build()
        deviceIdentity = DeviceIdentityStore(context)
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        // ContinuityPrefs is a real, on-device DataStore singleton
        // (not injectable per-test), so a test that flips
        // contextExtractionEnabled must reset it — same pattern
        // established in Tasks 4/6/11 for other singleton DataStores.
        ContinuityPrefs(context).reset()
    }

    @Test
    fun createCommitsEntryAndContinuityChangeTogether() = runBlocking {
        val repository = JournalRepository(context, db, deviceIdentity, StructuralContextExtractor())

        val entry = repository.create(
            title = "A day",
            body = "Something happened.",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 28),
        )

        val storedEntry = db.journal().entry(entry.id)
        assertEquals("Something happened.", storedEntry?.body)

        val pending = db.journal().pendingChanges()
        assertTrue(pending.any { it.entityType == "JOURNAL_ENTRY" && it.entityId == entry.id && it.operation == "CREATE" })
    }

    @Test
    fun entryAndChangeSurviveAFailingExtractor() = runBlocking {
        val failingExtractor = object : JournalContextExtractor {
            override fun extract(entry: JournalEntry, now: Long): List<JournalContext> {
                throw IllegalStateException("boom")
            }
        }
        val repository = JournalRepository(context, db, deviceIdentity, failingExtractor)

        val entry = repository.create(
            title = "A day",
            body = "Something happened.",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 28),
        )

        val storedEntry = db.journal().entry(entry.id)
        assertEquals("Something happened.", storedEntry?.body)

        val pending = db.journal().pendingChanges()
        assertTrue(pending.any { it.entityType == "JOURNAL_ENTRY" && it.entityId == entry.id && it.operation == "CREATE" })

        val context = db.journal().allContext().filter { it.entryId == entry.id }
        assertTrue(context.isEmpty())
    }

    @Test
    fun disablingContextExtractionSkipsItWithoutAffectingEntryCreation() = runBlocking {
        ContinuityPrefs(context).setContextExtractionEnabled(false)
        val repository = JournalRepository(context, db, deviceIdentity, StructuralContextExtractor())

        val entry = repository.create(
            title = "A day",
            body = "Something happened.",
            now = 1_000L,
            localDate = LocalDate.of(2026, 8, 28),
        )

        val storedEntry = db.journal().entry(entry.id)
        assertEquals("Something happened.", storedEntry?.body)
        assertTrue(db.journal().allContext().filter { it.entryId == entry.id }.isEmpty())

        ContinuityPrefs(context).setContextExtractionEnabled(true)
        repository.retryContext(entry.id)
        assertTrue(
            "re-enabling and retrying must produce the facts the disabled save skipped",
            db.journal().allContext().filter { it.entryId == entry.id }.isNotEmpty(),
        )
    }
}
