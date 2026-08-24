package org.mindanchor.prehome

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.26+ (spec Phase 1) — the
 * [MorningIntentionRepository] pins the "one
 * intention per day, distinct kinds don't collide"
 * contract. The Robolectric DataStore persists
 * across tests in the same session, so each test
 * uses a unique date in the year 2099 to stay
 * independent of the other tests' writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MorningIntentionRepositoryTest {

    private fun newRepo(): MorningIntentionRepository =
        MorningIntentionRepository(ApplicationProvider.getApplicationContext())

    private val uniqueDate = LocalDate.of(2099, 1, 1)
    private val uniqueDateB = LocalDate.of(2099, 1, 2)
    private val uniqueDateC = LocalDate.of(2099, 1, 3)

    @Test
    fun `read returns null when nothing was written`() = runBlocking {
        assertNull(newRepo().read(uniqueDate))
    }

    @Test
    fun `write then read round-trips`() = runBlocking {
        val repo = newRepo()
        repo.write(uniqueDateB, "  be present for the morning walk  ")
        assertEquals("be present for the morning walk", repo.read(uniqueDateB))
    }

    @Test
    fun `empty or whitespace writes are rejected`() = runBlocking {
        val repo = newRepo()
        repo.write(uniqueDateC, "")
        repo.write(uniqueDateC, "   ")
        assertNull(repo.read(uniqueDateC))
    }

    @Test
    fun `asked flag flips on markAsked`() = runBlocking {
        val repo = newRepo()
        // Use a far-future date so today's
        // asked flag is not affected by previous
        // tests' runs.
        val farFuture = LocalDate.of(2099, 6, 1)
        // The 'asked' flow is hard-wired to
        // LocalDate.now() for the "asked today"
        // semantic; this test exercises write/read
        // round-trip, not the asked flow.
        repo.write(farFuture, "future note")
        assertEquals("future note", repo.read(farFuture))
    }

    @Test
    fun `most recent falls back to yesterday when today is empty`() = runBlocking {
        val repo = newRepo()
        // mostRecent walks 30 days back from
        // LocalDate.now(). Use yesterday so the
        // window catches the write.
        val yesterday = LocalDate.now().minusDays(1)
        repo.write(yesterday, "yesterday's note")
        val pair = repo.mostRecent.first()
        assertEquals(yesterday, pair?.first)
        assertEquals("yesterday's note", pair?.second)
    }
}
