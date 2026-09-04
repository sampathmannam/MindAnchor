package org.mindanchor.letters

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 4 — [JournalStore.entries] only probes the latest 365 days, which is
 * fine for the launcher's recent-entries view but would silently drop an
 * old protective-writing entry during a one-time import to Room. This test
 * pins the contract that [JournalStore.allEntries] enumerates every saved
 * key regardless of age.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class JournalStoreAllEntriesTest {

    private fun newStore(): JournalStore =
        JournalStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `allEntries returns entries older than 365 days that entries flow misses`() = runBlocking {
        val store = newStore()
        val today = LocalDate.now()
        val old = today.minusDays(400)

        store.save(JournalStore.Kind.BA, today, "recent BA entry")
        store.save(JournalStore.Kind.GRATITUDE, old, "old gratitude entry")

        // The existing 365-day-bounded Flow misses the old entry.
        val recentOnly = store.entries.first()
        assertNull(recentOnly.find { it.kind == JournalStore.Kind.GRATITUDE && it.date == old })

        // allEntries() does not miss it.
        val all = store.allEntries()
        assertNotNull(all.find { it.kind == JournalStore.Kind.BA && it.date == today && it.body == "recent BA entry" })
        assertNotNull(all.find { it.kind == JournalStore.Kind.GRATITUDE && it.date == old && it.body == "old gratitude entry" })
    }
}
