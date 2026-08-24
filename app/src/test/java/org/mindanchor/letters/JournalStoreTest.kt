package org.mindanchor.letters

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.28+ (Phase 3 G-22 / G-23 / G-29 / G-8) —
 * the [JournalStore] preserves same-day entries
 * across kinds (BA / DEAR MAN / gratitude /
 * expressive writing) without overwriting the
 * daily letter. CodeRabbit review 2026-08-24 of
 * PR #38 surfaced the cross-kind collision in the
 * previous [LetterStore] wiring; this test pins
 * the new contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class JournalStoreTest {

    private fun newStore(): JournalStore =
        JournalStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `same date across kinds does not overwrite`() = runBlocking {
        val store = newStore()
        val today = LocalDate.now()
        store.save(JournalStore.Kind.BA, today, "BA:gardening|reading")
        store.save(
            JournalStore.Kind.GRATITUDE,
            today,
            "morning coffee with my daughter",
        )
        store.save(
            JournalStore.Kind.EXPRESSIVE_WRITING,
            today,
            "I am feeling overwhelmed because of the launch.",
        )
        store.save(
            JournalStore.Kind.DEAR_MAN,
            today,
            "DEAR MAN: I would like to ask for the deadline extension.",
        )

        assertEquals(
            "BA:gardening|reading",
            store.readOne(JournalStore.Kind.BA, today),
        )
        assertEquals(
            "morning coffee with my daughter",
            store.readOne(JournalStore.Kind.GRATITUDE, today),
        )
        assertEquals(
            "I am feeling overwhelmed because of the launch.",
            store.readOne(JournalStore.Kind.EXPRESSIVE_WRITING, today),
        )
        assertEquals(
            "DEAR MAN: I would like to ask for the deadline extension.",
            store.readOne(JournalStore.Kind.DEAR_MAN, today),
        )
    }

    @Test
    fun `empty body is rejected, not stored`() = runBlocking {
        val store = newStore()
        // Use a unique date so the test is
        // independent of the other tests'
        // same-day writes.
        val uniqueDate = LocalDate.of(2099, 1, 1)
        store.save(JournalStore.Kind.BA, uniqueDate, "   ")
        assertNull(store.readOne(JournalStore.Kind.BA, uniqueDate))
    }

    @Test
    fun `same kind on different dates does not overwrite`() = runBlocking {
        val store = newStore()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        store.save(JournalStore.Kind.GRATITUDE, today, "today's moment")
        store.save(JournalStore.Kind.GRATITUDE, yesterday, "yesterday's moment")
        assertEquals("today's moment", store.readOne(JournalStore.Kind.GRATITUDE, today))
        assertEquals(
            "yesterday's moment",
            store.readOne(JournalStore.Kind.GRATITUDE, yesterday),
        )
    }

    @Test
    fun `entries flow lists all stored kinds`() = runBlocking {
        val store = newStore()
        val today = LocalDate.now()
        store.save(JournalStore.Kind.BA, today, "BA:a|b")
        store.save(JournalStore.Kind.GRATITUDE, today, "moment")
        val list = store.entries.first()
        assertNotNull(list.find { it.kind == JournalStore.Kind.BA && it.body == "BA:a|b" })
        assertNotNull(list.find { it.kind == JournalStore.Kind.GRATITUDE && it.body == "moment" })
    }
}
