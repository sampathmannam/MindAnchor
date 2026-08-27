package org.mindanchor.anchorcore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnchorPrefsTest {

    private val prefs
        get() = AnchorPrefs(ApplicationProvider.getApplicationContext())

    @Before
    fun resetStore() = runBlocking {
        prefs.reset()
    }

    @Test
    fun `master defaults off and streak defaults to unflagged`() = runBlocking {
        assertFalse(prefs.isEnabled())
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, prefs.cleanStreak())
        assertFalse(prefs.weekFlagged())
    }

    @Test
    fun `first enable flips hook defaults on exactly once`() = runBlocking {
        assertFalse(prefs.letterFactsEnabled.first())
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
        assertTrue(prefs.letterFactsEnabled.first())
        assertTrue(prefs.frictionHoldEnabled.first())
        assertTrue(prefs.sunsetProposalEnabled.first())
        // A hook switched off stays off across master off->on.
        prefs.setLetterFactsEnabled(false)
        prefs.setEnabled(false)
        prefs.setEnabled(true)
        assertFalse(prefs.letterFactsEnabled.first())
    }

    @Test
    fun `dismissing the proposal suppresses it for fourteen days`() = runBlocking {
        assertNull(prefs.proposalSuppressedUntil())
        val now = Instant.parse("2026-08-26T10:00:00Z")
        prefs.recordProposalDismissed(now)
        val until = prefs.proposalSuppressedUntil()
        assertNotNull(until)
        assertEquals(now.plusSeconds(14L * 24 * 3600), until)
    }

    @Test
    fun `clean streak clamps to its band`() = runBlocking {
        prefs.setCleanStreak(99)
        assertEquals(WeekPicture.CLEAN_DAYS_TO_UNFLAG, prefs.cleanStreak())
        prefs.setCleanStreak(-3)
        assertEquals(0, prefs.cleanStreak())
    }
}
