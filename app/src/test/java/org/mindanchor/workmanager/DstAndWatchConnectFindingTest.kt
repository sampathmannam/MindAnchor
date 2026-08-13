@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.workmanager

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 Finding #7 (DST): `LetterScheduler.schedule` (LetterScheduler.kt:95-115)
 * and `EmaScheduler.schedule` (EmaScheduler.kt:173-185) build the
 * target time with `now.withHour(hour).withMinute(minute)`. If the
 * user picks 2:30 AM and the current day is a spring-forward day,
 * the `LocalDateTime` is invalid (2:00 jumps to 3:00). The
 * `!target.isAfter(now)` check on the unvalidated `LocalDateTime`
 * may pass even though the wall-clock does not exist on the
 * calendar day.
 *
 * The `atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()`
 * call at the end resolves the invalid time to the *post-DST*
 * offset, so the alarm fires at 3:30 wall-clock instead of the
 * 2:30 the user picked. The ReportSchedule + nextNight code path
 * (v0.25.5 WP fix) does the zone resolution *before* the
 * LocalDateTime construction, which is the correct shape.
 *
 * The fix flips the assertion: the schedulers should resolve
 * the wall-clock-to-instant math at the ZoneId level (as
 * ReportSchedule.nextRun does) rather than producing a
 * LocalDateTime that may be invalid on a DST boundary.
 */
class DstAndWatchConnectFindingTest {

    @Test
    fun `LetterScheduler schedule uses now_withHour which produces invalid LocalDateTime on spring-forward`() {
        val source = readSource("letters/LetterScheduler.kt")
        assertNotNull(source)
        // v0.25.10 fix: the schedule target is built with
        // ZonedDateTime.of(today, time, zone), not LocalDateTime.
        // The ZonedDateTime construction handles the spring-forward
        // gap (2:30 AM → 3:30 AM) and the fall-back overlap correctly
        // (see the comment at the call site). The
        // `.withHour(hour).withMinute(minute)` pattern is gone.
        val usesZonedDateTimeOf = source!!.contains("ZonedDateTime.of(today, LocalTime.of(hour, minute), zone)") ||
            source.contains("ZonedDateTime.of(today,")
        assertTrue(
            "LetterScheduler.schedule should build the target time with " +
                "`ZonedDateTime.of(today, LocalTime.of(hour, minute), zone)` " +
                "so a 2:30 AM pick on a spring-forward day resolves to 3:30 AM " +
                "the right way (per the SpringForwardGap resolver). The " +
                "`withHour(hour).withMinute(minute)` pattern is the pre-fix " +
                "shape and would resolve the invalid time to the post-DST " +
                "offset, firing the alarm at 3:30 wall-clock without telling " +
                "the user. usesZonedDateTimeOf=$usesZonedDateTimeOf.",
            usesZonedDateTimeOf,
        )
    }

    @Test
    fun `EmaScheduler schedule has the same DST shape as LetterScheduler`() {
        val source = readSource("model/EmaScheduler.kt")
        assertNotNull(source)
        // v0.25.10 fix: the schedule target is built with
        // ZonedDateTime.of(today, it, zone), not today.atTime(it).
        val usesZonedDateTimeOf = source!!.contains("ZonedDateTime.of(today, it, zone)") ||
            source.contains("ZonedDateTime.of(today,")
        assertTrue(
            "EmaScheduler.schedule should build the target time with " +
                "`ZonedDateTime.of(today, it, zone)` so a wake-minute in " +
                "the DST gap resolves to the post-DST offset the right " +
                "way. The pre-fix `today.atTime(it)` shape would produce " +
                "an invalid LocalDateTime on a spring-forward day and the " +
                "subsequent `atZone(zone).toInstant()` would fire the " +
                "prompt at a wall-clock the user did not pick. " +
                "usesZonedDateTimeOf=$usesZonedDateTimeOf.",
            usesZonedDateTimeOf,
        )
    }

    /**
     * v2 Finding #8: the periodic `CorosSyncWorker` (6h cadence)
     * returns `Result.failure()` for the "no credentials stored"
     * case (`auth.connectionState(...).isConnectedLike()` is
     * false). For periodic work, `Result.failure()` and
     * `Result.retry()` both do *not* trigger an early retry —
     * the next run is the next periodic tick. So the no-creds
     * case correctly does not spam retries.
     *
     * However, the per-API-call failure paths return
     * `Result.retry()`. For periodic work, `Result.retry()` is
     * the same as `Result.failure()` in terms of scheduling —
     * the next run is the next periodic tick. The choice
     * matters for the *user-visible signal*: a `Result.failure()`
     * in a periodic work does not show up in the WorkManager
     * log as a retry attempt, while `Result.retry()` does.
     *
     * The bug pattern is: a periodic worker that wants to
     * "wait until the next period" should use `Result.success()`
     * (since the next period is *not* a retry), not
     * `Result.retry()`. The current shape uses `Result.retry()`
     * for transient failures and `Result.failure()` for
     * permanent ones, which is the *opposite* of what periodic
     * work semantics reward.
     */
    @Test
    fun `CorosSyncWorker transient failures return Result_retry in a periodic worker - should return Result_success for periodic`() {
        val source = readSource("vitals/coros/CorosSyncWorker.kt")
        assertNotNull(source)
        val isPeriodic = source!!.contains("PeriodicWorkRequestBuilder<CorosSyncWorker>")
        val hasRetryForTransient = source.contains("return Result.retry()")
        // The fix flips: a periodic worker that "will be tried
        // again at the next period" should return Result.success()
        // because the next run is *not* a retry, it's the next
        // scheduled tick. Result.retry() on a periodic work adds
        // the run to the WorkManager-log retry history unnecessarily.
        assertTrue(
            "CorosSyncWorker is periodic (isPeriodic=$isPeriodic) and returns Result.retry() " +
                "for transient API failures (hasRetryForTransient=$hasRetryForTransient). " +
                "Periodic work semantics: Result.retry() and Result.failure() both schedule the " +
                "next run at the next period — the only difference is the WorkManager-log " +
                "shape. A periodic worker that wants 'wait until the next period' should " +
                "return Result.success() to avoid logging a phantom retry. The current shape " +
                "is the opposite of what periodic work rewards.",
            isPeriodic && hasRetryForTransient,
        )
    }

    /**
     * v2 Finding #9: the watch connect real root-cause. The
     * COROS watch (the only one verified on this project) does
     * NOT write HRV to Health Connect — only heart rate and
     * exercise. This is the documented "real root cause" of why
     * the wellness card's HRV signal is NO_DATA for COROS users.
     * The PPG camera path is the workaround (per
     * WellnessSignals.kt:20 and the v0.25.5 PPG feature).
     *
     * The file-shape pin: the wellness surface has a per-signal
     * fallback (HC > COROS > inferred for sleep), but for HRV
     * the COROS fallback is empty because COROS doesn't write
     * it. The fix is *not* a code change — it's the PPG camera
     * path. The test pins the *explanation* in source so a
     * future maintainer doesn't try to "fix" the NO_DATA by
     * switching the source preference order.
     */
    @Test
    fun `watch connect real root cause - COROS does not write HRV to Health Connect (documented in source)`() {
        val source = readSource("vitals/WellnessSignals.kt")
        assertNotNull(source)
        // The documentation in WellnessSignals.kt:20-22 is the
        // source of truth. A regression that removes the
        // "vendor does not write HRV" note would let a future
        // maintainer try to "fix" the NO_DATA by changing the
        // source preference, which would be a misdiagnosis.
        val documentsCorosHrvGap = source!!.contains("vendor does not write HRV to Health Connect") ||
            source.contains("COROS does not write HRV") ||
            (source.contains("HRV") && source.contains("Health Connect") &&
                source.contains("verified") && source.contains("vendor"))
        assertTrue(
            "WellnessSignals.kt must document that the COROS watch vendor does not write HRV " +
                "to Health Connect. The HRV signal's NO_DATA on a COROS+Health Connect setup " +
                "is the *expected* outcome, not a bug to fix. The workaround is the camera " +
                "PPG path (PpgScreen + PpgCapture + MeasuredStore), not a source-preference " +
                "swap. documentsCorosHrvGap=$documentsCorosHrvGap. The fix flips: if a future " +
                "watch vendor writes HRV, the documentation note should be updated; until then, " +
                "the source code is the answer to 'why does HRV show NO_DATA?'.",
            documentsCorosHrvGap,
        )
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
