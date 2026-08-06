package org.mindanchor.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.HeldNotification

/**
 * The batcher's one unbreakable promise: a held notification is never lost.
 *
 * These exercise the real database and the real release path on a device,
 * which is where a journal bug would actually bite.
 */
@RunWith(AndroidJUnit4::class)
class BatcherJournalTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao = AnchorDatabase.get(context).heldNotifications()

    @Before
    fun emptyTheJournal() = runBlocking {
        dao.markAllReleased(System.currentTimeMillis())
        dao.clearReleased()
    }

    private fun held(title: String, at: Long) = HeldNotification(
        packageName = "com.example.feed",
        appLabel = "Feed",
        title = title,
        text = "body of $title",
        postedAt = at,
    )

    @Test
    fun heldNotificationsSurviveUntilTheyAreReleased() = runBlocking {
        dao.insert(held("one", 1_000))
        dao.insert(held("two", 2_000))
        dao.insert(held("three", 3_000))

        assertEquals(3, dao.pending().size)
        assertEquals(3, dao.pendingCount().first())

        BatchReleaser.releaseNow(context)

        // Nothing is deleted by a release — it moves from waiting to read.
        assertEquals("nothing should still be waiting", 0, dao.pending().size)
        val journal = dao.journal().first()
        assertEquals("all three are still in the journal", 3, journal.size)
        assertTrue("every entry carries a release time", journal.all { it.releasedAt != null })
        assertEquals(setOf("one", "two", "three"), journal.map { it.title }.toSet())
    }

    @Test
    fun releasingAnEmptyJournalIsHarmless() = runBlocking {
        BatchReleaser.releaseNow(context)
        assertEquals(0, dao.pending().size)
        assertTrue(dao.journal().first().isEmpty())
    }

    @Test
    fun clearingReleasedLeavesWaitingEntriesAlone() = runBlocking {
        dao.insert(held("old", 1_000))
        BatchReleaser.releaseNow(context)
        dao.insert(held("new", 4_000))

        dao.clearReleased()

        val journal = dao.journal().first()
        assertEquals(1, journal.size)
        assertEquals("new", journal.first().title)
        assertEquals(1, dao.pending().size)
    }

    @Test
    fun theJournalIsNewestFirst() = runBlocking {
        dao.insert(held("older", 1_000))
        dao.insert(held("newer", 9_000))
        assertEquals(listOf("newer", "older"), dao.journal().first().map { it.title })
    }
}
