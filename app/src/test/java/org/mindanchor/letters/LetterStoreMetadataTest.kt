package org.mindanchor.letters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.25.7+ wire-format metadata round-trip through
 * [LetterStore].
 *
 * [LetterLedgerMetadataTest] pins the codec (encode
 * string → [LetterLedger] decode back). This test
 * pins the *DataStore* layer: a [Letter] with
 * metadata fields written via [LetterStore.save] must
 * come back through [LetterStore.letters] with every
 * field intact, and a pre-v0.25.7 letter written
 * before the metadata fields existed must come back
 * with all five metadata fields null.
 *
 * The DataStore is a process-wide singleton keyed on
 * the preferences name, so two tests in the same
 * Robolectric class share state unless explicitly
 * reset. [LetterStore.reset] (test-only, internal)
 * clears every key before each test, so each starts
 * from a fresh-install state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LetterStoreMetadataTest {

    @Before fun resetStore() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LetterStore(ctx).reset()
    }

    @Test fun `save a letter with all 5 metadata fields round-trips through the store`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val letter = Letter(
            date = LocalDate.of(2026, 8, 22),
            body = "It was a quiet Tuesday.",
            provider = "groq",
            model = "llama-3.3-70b-versatile",
            promptTokens = 1240,
            completionTokens = 380,
            durationMs = 1234L,
        )
        store.save(letter)

        val read = store.letters.first().single()
        assertEquals(letter.date, read.date)
        assertEquals(letter.body, read.body)
        assertEquals("groq", read.provider)
        assertEquals("llama-3.3-70b-versatile", read.model)
        assertEquals(1240, read.promptTokens)
        assertEquals(380, read.completionTokens)
        assertEquals(1234L, read.durationMs)
    }

    @Test fun `save a letter with no provider round-trips with all metadata null`() = runBlocking {
        // A pre-v0.25.7 letter (canned, on-device Phi-4).
        // The save() call must NOT promote null metadata
        // to empty strings; the ledger's null-provider
        // branch writes the 2-field line shape, and the
        // reader must see null in every metadata slot.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val letter = Letter(
            date = LocalDate.of(2026, 8, 15),
            body = "A canned letter from the local Phi-4 model.",
        )
        store.save(letter)

        val read = store.letters.first().single()
        assertEquals(letter.date, read.date)
        assertEquals(letter.body, read.body)
        assertNull(read.provider)
        assertNull(read.model)
        assertNull(read.promptTokens)
        assertNull(read.completionTokens)
        assertNull(read.durationMs)
    }

    @Test fun `save a letter with partial metadata round-trips the nulls as null`() = runBlocking {
        // A failed LLM call that still produced a body
        // but no usage stats: provider + model known,
        // token counts and duration unknown. The store
        // must not drop the letter or coerce nulls to
        // empty strings.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val letter = Letter(
            date = LocalDate.of(2026, 8, 22),
            body = "A letter with provider but no model.",
            provider = "groq",
            model = null,
            promptTokens = null,
            completionTokens = null,
            durationMs = null,
        )
        store.save(letter)

        val read = store.letters.first().single()
        assertEquals(letter, read)
    }

    @Test fun `save twice for the same date keeps the latest letter`() = runBlocking {
        // Regenerate behaviour: the user re-writes
        // today's letter; the older one for the same
        // date must be replaced, not duplicated. The
        // second letter carries the LLM metadata; the
        // first was a pre-v0.25.7 canned letter. After
        // the save, only the second is in the inbox.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        val date = LocalDate.of(2026, 8, 22)
        store.save(
            Letter(
                date = date,
                body = "First attempt, canned.",
            ),
        )
        store.save(
            Letter(
                date = date,
                body = "Second attempt, LLM-driven.",
                provider = "groq",
                model = "llama-3.3-70b-versatile",
                promptTokens = 900,
                completionTokens = 320,
                durationMs = 2000L,
            ),
        )

        val read = store.letters.first()
        assertEquals(1, read.size)
        val only = read.single()
        assertEquals(date, only.date)
        assertEquals("Second attempt, LLM-driven.", only.body)
        assertEquals("groq", only.provider)
        assertEquals("llama-3.3-70b-versatile", only.model)
    }

    @Test fun `save letters for two different dates keeps both`() = runBlocking {
        // Sanity check that the dedup branch in
        // [LetterStore.save] is keyed on date, not on
        // body. A LLM-driven letter for one date and a
        // canned letter for a different date both live
        // in the inbox.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store = LetterStore(ctx)
        store.save(
            Letter(
                date = LocalDate.of(2026, 8, 21),
                body = "A canned letter from Friday.",
            ),
        )
        store.save(
            Letter(
                date = LocalDate.of(2026, 8, 22),
                body = "An LLM letter for Saturday.",
                provider = "groq",
                model = "llama-3.3-70b-versatile",
                promptTokens = 1100,
                completionTokens = 350,
                durationMs = 1500L,
            ),
        )

        val read = store.letters.first()
        assertEquals(2, read.size)
        val byDate = read.associateBy { it.date }
        assertNull("Friday's letter must not have LLM metadata", byDate[LocalDate.of(2026, 8, 21)]?.provider)
        assertEquals("groq", byDate[LocalDate.of(2026, 8, 22)]?.provider)
    }

    @Test fun `a fresh LetterStore on the same context reads back the metadata`() = runBlocking {
        // DataStore is the source of truth. A new
        // [LetterStore] instance on the same context
        // must read the metadata back, not the
        // in-memory cache. This is the test that
        // catches a future contributor who "optimises"
        // [LetterStore.letters] to skip the disk
        // round-trip — the in-memory cache would
        // outlive the on-disk format change and the
        // metadata would silently disappear.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        LetterStore(ctx).save(
            Letter(
                date = LocalDate.of(2026, 8, 22),
                body = "A letter written by the LLM.",
                provider = "groq",
                model = "llama-3.3-70b-versatile",
                promptTokens = 1200,
                completionTokens = 400,
                durationMs = 1800L,
            ),
        )
        val fresh = LetterStore(ctx)
        val read = fresh.letters.first().single()
        assertEquals("groq", read.provider)
        assertEquals("llama-3.3-70b-versatile", read.model)
        assertEquals(1200, read.promptTokens)
        assertEquals(400, read.completionTokens)
        assertEquals(1800L, read.durationMs)
    }
}
