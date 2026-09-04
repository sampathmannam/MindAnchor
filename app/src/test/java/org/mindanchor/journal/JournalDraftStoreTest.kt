package org.mindanchor.journal

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 4 — [JournalDraftStore] is the crash/kill recovery net for an
 * in-progress Journal entry: whatever the person has typed survives a
 * process death before they hit save. These tests pin the blank state, the
 * save/read round trip, clear-after-commit, and exact round-tripping of
 * tabs/newlines/Unicode (a draft is free text, unlike the structural
 * context facts derived from a committed entry).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class JournalDraftStoreTest {

    private fun newStore(): JournalDraftStore =
        JournalDraftStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `blank initial state has no draft`() = runBlocking {
        val store = newStore()
        assertNull(store.read())
    }

    @Test
    fun `save then read round trips exactly`() = runBlocking {
        val store = newStore()
        store.save(title = "A title", body = "The body of the draft.", now = 12_345L)

        val draft = store.read()
        assertEquals("A title", draft?.title)
        assertEquals("The body of the draft.", draft?.body)
        assertEquals(12_345L, draft?.updatedAt)
    }

    @Test
    fun `clear after commit removes the draft`() = runBlocking {
        val store = newStore()
        store.save(title = "A title", body = "The body of the draft.", now = 12_345L)
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun `tabs newlines and unicode round trip exactly`() = runBlocking {
        val store = newStore()
        val body = "line1\tline2\nline3 — 日本語 🎉"
        store.save(title = "", body = body, now = 1L)

        val draft = store.read()
        assertEquals(body, draft?.body)
    }
}
