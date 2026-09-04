package org.mindanchor.admin

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DataStore round-trips for [OsModePrefs] (Robolectric, mirroring
 * [org.mindanchor.prehome.DoomscrollListTest]).
 *
 * Note what is deliberately absent: no test reads or writes "currently
 * suspended" as state, because OS Mode does not store it. The applied-set
 * here is only a cleanup hint for lifting packages that left the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class OsModePrefsTest {

    private fun newPrefs(): OsModePrefs =
        OsModePrefs(ApplicationProvider.getApplicationContext())

    @Test
    fun `defaults are off, empty, and unreleased`() = runBlocking {
        val prefs = newPrefs()
        assertEquals(false, prefs.isEnabled())
        assertEquals(emptySet<String>(), prefs.applied.first())
        assertNull(prefs.earlyReleaseAt.first())
    }

    @Test
    fun `enabled round-trips`() = runBlocking {
        val prefs = newPrefs()
        prefs.setEnabled(true)
        assertTrue(prefs.isEnabled())
        prefs.setEnabled(false)
        assertEquals(false, prefs.isEnabled())
    }

    @Test
    fun `applied hint round-trips`() = runBlocking {
        val prefs = newPrefs()
        prefs.recordApplied(setOf("com.instagram.android", "com.reddit.frontpage"))
        assertEquals(
            setOf("com.instagram.android", "com.reddit.frontpage"),
            prefs.applied.first(),
        )
        prefs.recordApplied(emptySet())
        assertEquals(emptySet<String>(), prefs.applied.first())
    }

    @Test
    fun `early release marker round-trips and clears`() = runBlocking {
        val prefs = newPrefs()
        prefs.markEarlyRelease(at = 1_775_000_000_000)
        assertEquals(1_775_000_000_000L, prefs.earlyReleaseAt.first())
        prefs.clearEarlyRelease()
        assertNull(prefs.earlyReleaseAt.first())
    }
}
