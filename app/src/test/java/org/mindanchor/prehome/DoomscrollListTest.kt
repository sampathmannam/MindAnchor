package org.mindanchor.prehome

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.26+ (spec Phase 1) — the [DoomscrollList]
 * pins the default set and the add/remove contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DoomscrollListTest {

    private fun newList(): DoomscrollList =
        DoomscrollList(ApplicationProvider.getApplicationContext())

    @Test
    fun `default set contains the spec recommended six plus Facebook`() = runBlocking {
        val list = newList()
        // Robolectric's DataStore persists across
        // tests in the same session; clear() so the
        // [packages] read falls back to the default
        // set rather than a value written by a
        // previous test in this file.
        list.clear()
        val packages = list.packages.first()
        // The spec's recommended list is Instagram,
        // YouTube, Twitter, Reddit, TikTok, Snapchat,
        // Facebook. The launcher is opinionated; the
        // user's edit surface (a follow-up) is for
        // "I want to add / remove".
        assertTrue("com.instagram.android" in packages)
        assertTrue("com.google.android.youtube" in packages)
        assertTrue("com.twitter.android" in packages)
        assertTrue("com.reddit.frontpage" in packages)
        assertTrue("com.zhiliaoapp.musically" in packages)
        assertTrue("com.snapchat.android" in packages)
        assertTrue("com.facebook.katana" in packages)
    }

    @Test
    fun `add then remove round-trips`() = runBlocking {
        val list = newList()
        val newPkg = "com.example.doomscroll"
        list.add(newPkg)
        assertTrue(newPkg in list.packages.first())
        list.remove(newPkg)
        assertEquals(false, newPkg in list.packages.first())
    }

    @Test
    fun `setAll replaces the entire set`() = runBlocking {
        val list = newList()
        list.setAll(setOf("com.a", "com.b"))
        val packages = list.packages.first()
        assertEquals(setOf("com.a", "com.b"), packages)
    }
}
