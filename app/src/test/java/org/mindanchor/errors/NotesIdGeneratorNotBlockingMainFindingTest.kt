package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 (SOTA v2 bug-hunt, agent #5): NotesPrefs.idGenerator uses
 * runBlocking inside a `by lazy` initializer. The first caller of
 * nextNoteId() blocks whatever thread it is on (Main, in the
 * home-card path) until the DataStore read + decode completes. If
 * the lazy initializer throws, the AtomicLong is never created and
 * a subsequent call races with another first-call to seed the same
 * counter — re-introducing the v0.25.7 B4 (idCounter dup) bug the
 * v0.25.7+ WP-3 fix was meant to eliminate.
 *
 * File-shape pin: the fix PR moves the seeding to a coroutine
 * initializer and makes nextNoteId() a suspend fun. The asserts
 * below are the regression guard.
 */
class NotesIdGeneratorNotBlockingMainFindingTest {

    @Test
    fun `NotesPrefs does not call runBlocking inside the idGenerator lazy (regression guard for B3)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/data/NotesPrefs.kt",
        ).readText()
        assertFalse(
            "NotesPrefs.idGenerator must not call runBlocking — the first " +
                "nextNoteId() caller (the home-card path) is on the main thread, " +
                "and a blocking read there is an ANR waiting to happen.",
            source.contains("runBlocking {"),
        )
    }

    @Test
    fun `NotesPrefs nextNoteId is a fast non-suspend read of the companion AtomicLong (regression guard for B3)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/data/NotesPrefs.kt",
        ).readText()
        // v0.25.9 fix shape: nextNoteId is a fast,
        // non-suspend read of the companion's
        // AtomicLong. The slow part (the DataStore
        // read) was moved to a one-time
        // [Companion.seedFromDiskIfNeeded] called
        // from HomeActivity.onCreate. The
        // alternative "make nextNoteId a suspend
        // fun" was considered and rejected: every
        // call site would need to be in a coroutine,
        // the AtomicLong increment is microseconds,
        // and an extra `suspend` keyword at the call
        // site is more invasive than the lazy-seed
        // pattern.
        val isFastNonSuspend = Regex(
            "fun nextNoteId\\(\\):\\s*Long = idGenerator\\.incrementAndGet",
        ).containsMatchIn(source)
        val exposesAsyncSeed = source.contains("suspend fun seedFromDiskIfNeeded")
        assertTrue(
            "NotesPrefs.nextNoteId() must be a fast non-suspend `idGenerator.incrementAndGet()` " +
                "delegating to the companion's AtomicLong, and the companion must expose " +
                "an async `suspend fun seedFromDiskIfNeeded` for the one-time disk read. " +
                "isFastNonSuspend=$isFastNonSuspend exposesAsyncSeed=$exposesAsyncSeed.",
            isFastNonSuspend && exposesAsyncSeed,
        )
    }
}
