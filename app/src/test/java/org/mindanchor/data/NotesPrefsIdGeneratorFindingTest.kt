package org.mindanchor.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.9: pin the process-singleton shape of the
 * NotesPrefs id generator. v0.25.8 claimed to be a
 * process-singleton but was a class-level `by lazy`,
 * which resolves one lazy per class instance — two
 * `NotesPrefs` instances in the same process had two
 * counters seeded to the same `System.currentTimeMillis()`.
 *
 * The v0.25.9 fix moves the generator to the
 * [org.mindanchor.data.NotesPrefs.Companion] (a true
 * per-class-loader singleton), and replaces the
 * `runBlocking` first-call seed with an async
 * `seedFromDiskIfNeeded` called from
 * `HomeActivity.onCreate`. Three file-shape pins:
 *  1. The `idGenerator` is declared on the
 *     `companion object`, not on the class.
 *  2. The class has no `private val idGenerator`
 *     (the v0.25.8 shape).
 *  3. `HomeActivity.onCreate` calls
 *     `NotesPrefs.seedFromDiskIfNeeded(...)` from
 *     the `lifecycleScope.launch` block.
 *  4. The companion exposes `seedFromDiskIfNeeded`
 *     (suspend, not blocking).
 *
 * A regression that returns the generator to a
 * class-level `by lazy` (the v0.25.8 bug) flips
 * pins 1 and 2 red.
 */
class NotesPrefsIdGeneratorFindingTest {

    @Test
    fun `idGenerator lives on the companion object (process-singleton)`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // Pin #1: the AtomicLong is declared on the
        // companion object, not on the class.
        val onCompanion = source!!.contains("companion object") &&
            source.contains("private val idGenerator = AtomicLong")
        // The companion shape is "AtomicLong(System.currentTimeMillis())"
        // because the seed is now async — the initial
        // value is the wall clock, and the seed call
        // from HomeActivity raises it to maxExisting.
        val initialValue = source.contains("AtomicLong(System.currentTimeMillis())")
        assertTrue(
            "NotesPrefs must declare idGenerator on the companion object so " +
                "two NotesPrefs instances share the same AtomicLong. onCompanion=" +
                "$onCompanion initialValue=$initialValue.",
            onCompanion && initialValue,
        )
    }

    @Test
    fun `class no longer declares a class-level idGenerator (v0_25_8 shape is gone)`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // Pin #2: the v0.25.8 shape was a class-level
        // `private val idGenerator: ... by lazy { ... }`.
        // The new shape declares it on the companion
        // only. A `by lazy` block on the class would
        // be per-instance and silently re-introduce
        // the duplicate-id bug.
        val classLevelByLazy = Regex(
            "private val idGenerator[^=]*by lazy \\{",
            RegexOption.MULTILINE,
        ).containsMatchIn(source!!)
        assertFalse(
            "NotesPrefs must not declare idGenerator as a class-level `by lazy` — " +
                "that resolves one lazy per class instance and re-introduces the " +
                "v0.25.8 duplicate-id bug. classLevelByLazy=$classLevelByLazy.",
            classLevelByLazy,
        )
    }

    @Test
    fun `companion exposes seedFromDiskIfNeeded (suspend, not blocking)`() {
        val source = readSource("data/NotesPrefs.kt")
        assertNotNull(source)
        // Pin #4: the companion has a suspend
        // seedFromDiskIfNeeded(context) function. The
        // old shape was a `runBlocking` inside the
        // lazy init — that blocked the calling
        // thread (main thread on the home card).
        val hasSeed = source!!.contains("suspend fun seedFromDiskIfNeeded")
        // The body must not call runBlocking — the
        // whole point of the fix is to remove the
        // runBlocking from the hot path.
        val noRunBlockingInSeed = !Regex(
            "suspend fun seedFromDiskIfNeeded[^{]*\\{[^{]*runBlocking",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(source)
        assertTrue(
            "NotesPrefs.Companion must expose a suspend `seedFromDiskIfNeeded` and " +
                "must not call runBlocking inside it. hasSeed=$hasSeed " +
                "noRunBlockingInSeed=$noRunBlockingInSeed.",
            hasSeed && noRunBlockingInSeed,
        )
    }

    @Test
    fun `HomeActivity onCreate calls NotesPrefs seedFromDiskIfNeeded`() {
        val source = readSource("HomeActivity.kt")
        assertNotNull(source)
        // Pin #3: HomeActivity.onCreate must invoke
        // the seed in a lifecycleScope coroutine.
        // Without this call, the generator stays at
        // its initial System.currentTimeMillis()
        // value and the next process start does not
        // read the max existing id from disk.
        val callsSeed = source!!.contains("NotesPrefs.seedFromDiskIfNeeded")
        val inLifecycleScope = source.contains("lifecycleScope.launch") &&
            // The seed call should be in the same
            // block as the BackupPrefs rehydration.
            source.indexOf("NotesPrefs.seedFromDiskIfNeeded") >
                source.indexOf("lifecycleScope.launch")
        assertTrue(
            "HomeActivity.onCreate must call NotesPrefs.seedFromDiskIfNeeded from " +
                "the lifecycleScope coroutine. callsSeed=$callsSeed " +
                "inLifecycleScope=$inLifecycleScope.",
            callsSeed && inLifecycleScope,
        )
    }

    private fun assertFalse(message: String, condition: Boolean) {
        org.junit.Assert.assertFalse(message, condition)
    }

    private fun readSource(relative: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/$relative",
            "../app/src/main/java/org/mindanchor/$relative",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
