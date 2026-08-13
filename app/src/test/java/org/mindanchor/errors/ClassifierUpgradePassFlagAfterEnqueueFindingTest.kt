package org.mindanchor.errors

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B12 (SOTA v2 bug-hunt, agent #5): ClassifierEnqueuer.runUpgradePassIfNeeded
 * sets the upgrade flag in the same `launch` body that calls
 * `enqueueAll(untyped)`. The enqueue is fire-and-forget; the actual
 * classification work is in flight on the `scope` but has not completed
 * when the flag is set. The KDoc on the function explicitly says "The
 * flag is set only after the read succeeds, so a crash mid-pass causes
 * the pass to retry on the next launch" — the code does not implement
 * this. A process kill between the flag-set and the classifications
 * completing leaves notes un-typed and the flag `true`, so the pass
 * never re-runs. This is pre-existing (v0.25.0) but still in
 * production.
 *
 * File-shape pin: the fix PR `joinAll()`s the enqueued classification
 * jobs before the flag-set.
 */
class ClassifierUpgradePassFlagAfterEnqueueFindingTest {

    @Test
    fun `ClassifierEnqueuer runUpgradePassIfNeeded sets the flag after the enqueue joins (regression guard for B12)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/note/ClassifierEnqueuer.kt",
        ).readText()
        val block = source.substringAfter("fun runUpgradePassIfNeeded()")
            .substringBefore("private companion object")
        // The pre-fix shape: `enqueueAll(untyped); prefsForFlag.edit().putBoolean(UPGRADE_FLAG_KEY, true).apply()`
        // in the same launch body. The fix shape has a `joinAll()` (or
        // similar) between the enqueue and the flag-set.
        val literal = "enqueueAll(untyped)"
        val flagLiteral = "UPGRADE_FLAG_KEY, true"
        val enqueuePos = block.indexOf(literal)
        val flagPos = block.indexOf(flagLiteral)
        assertTrue(
            "Both `enqueueAll(untyped)` and the UPGRADE_FLAG_KEY flag-set must " +
                "appear in runUpgradePassIfNeeded.",
            enqueuePos >= 0 && flagPos >= 0,
        )
        // The fix shape has a `joinAll()` between the two. The pre-fix
        // shape does not.
        val between = block.substring(enqueuePos, flagPos)
        assertTrue(
            "runUpgradePassIfNeeded must `joinAll()` the enqueued jobs " +
                "before setting UPGRADE_FLAG_KEY — the KDoc promises a " +
                "crash-mid-pass retry that the pre-fix code does not implement.",
            between.contains("joinAll()"),
        )
    }
}
