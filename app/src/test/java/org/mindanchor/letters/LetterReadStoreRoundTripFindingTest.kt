package org.mindanchor.letters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LetterReadStoreRoundTripFindingTest {

    // DataStore is a process-wide singleton keyed on the preferences
    // name. Two tests in the same Robolectric class share state unless
    // explicitly reset. [LetterStore.reset] (test-only, internal)
    // clears every key in the underlying DataStore so each test
    // starts from a fresh-install state.
    @Before fun resetStore() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LetterStore(ctx).reset()
    }

    @Test fun `readDates is empty for a fresh install`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        assertEquals(emptySet<LocalDate>(), store.readDates.first())
    }

    @Test fun `setRead date true round-trips (date is in readDates)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val date = LocalDate.of(2026, 8, 12)
        store.setRead(date, true)
        assertTrue(
            "After setRead(date, true), the date must be in readDates",
            date in store.readDates.first(),
        )
    }

    @Test fun `setRead date false after a read round-trips (date is NOT in readDates)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val date = LocalDate.of(2026, 8, 12)
        store.setRead(date, true)
        store.setRead(date, false)
        assertFalse(
            "After setRead(date, false) following a read, the date must " +
                "NOT be in readDates",
            date in store.readDates.first(),
        )
    }

    @Test fun `multiple reads accumulate in the set`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val d1 = LocalDate.of(2026, 8, 10)
        val d2 = LocalDate.of(2026, 8, 11)
        val d3 = LocalDate.of(2026, 8, 12)
        store.setRead(d1, true)
        store.setRead(d2, true)
        store.setRead(d3, true)
        val read = store.readDates.first()
        assertEquals(
            "All three dates must be in readDates after setRead(true) each",
            setOf(d1, d2, d3),
            read,
        )
    }

    @Test fun `a fresh LetterStore instance on the same context reads the same set`() =
        runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val date = LocalDate.of(2026, 8, 12)
        LetterStore(ctx).setRead(date, true)
        val fresh = LetterStore(ctx)
        assertTrue(
            "A new LetterStore instance on the same context must read " +
                "the persisted read-set (DataStore is the source of truth).",
            date in fresh.readDates.first(),
        )
    }
}
