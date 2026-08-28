package org.mindanchor.continuity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.zone.ZoneOffsetTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ContinuityWorkScheduler]'s local WorkManager scheduling, driven through
 * [WorkManagerTestInitHelper]'s in-JVM test driver (Robolectric supplies
 * the Android `Context`; no emulator is needed for any of this).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContinuityWorkSchedulerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `checkpoint constraints require a connected network`() {
        val constraints = ContinuityWorkScheduler.checkpointConstraints()
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun `nightly constraints require a connected network and battery not low`() {
        val constraints = ContinuityWorkScheduler.nightlyConstraints()
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresBatteryNotLow())
    }

    @Test
    fun `requestCheckpoint enqueues unique work under the checkpoint work name`() {
        ContinuityWorkScheduler.requestCheckpoint(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.CHECKPOINT_WORK_NAME).get()

        assertEquals(1, infos.size)
    }

    @Test
    fun `a second requestCheckpoint replaces the first (REPLACE, not KEEP or APPEND)`() {
        ContinuityWorkScheduler.requestCheckpoint(context)
        val first = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.CHECKPOINT_WORK_NAME).get()
        assertEquals(1, first.size)
        val firstId = first.single().id

        ContinuityWorkScheduler.requestCheckpoint(context)
        val second = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.CHECKPOINT_WORK_NAME).get()
        val secondLive = second.filterNot { it.state == WorkInfo.State.CANCELLED }

        // REPLACE: exactly one live entry under the unique name (APPEND
        // would leave two), with a DIFFERENT WorkRequest id (KEEP would
        // leave the original worker running under its original id) —
        // "a new save occurs during upload" must cancel the stale
        // in-flight worker and recapture the complete current state.
        assertEquals(1, secondLive.size)
        assertNotEquals("REPLACE must swap in a new WorkRequest id", firstId, secondLive.single().id)
    }

    @Test
    fun `ensureNightlyScheduled enqueues unique work under the nightly work name`() {
        ContinuityWorkScheduler.ensureNightlyScheduled(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.NIGHTLY_WORK_NAME).get()

        assertEquals(1, infos.size)
    }

    @Test
    fun `cancelAll cancels both the checkpoint and nightly unique works`() {
        ContinuityWorkScheduler.requestCheckpoint(context)
        ContinuityWorkScheduler.ensureNightlyScheduled(context)

        ContinuityWorkScheduler.cancelAll(context)

        val checkpointInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.CHECKPOINT_WORK_NAME).get()
        val nightlyInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ContinuityWorkScheduler.NIGHTLY_WORK_NAME).get()

        assertTrue(checkpointInfos.isNotEmpty())
        assertTrue(checkpointInfos.all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(nightlyInfos.isNotEmpty())
        assertTrue(nightlyInfos.all { it.state == WorkInfo.State.CANCELLED })
    }

    // --- nextNightlyDelayMillis: ordinary boundary cases (no DST) ------

    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `delay just before the target time targets later today`() {
        val now = ZonedDateTime.of(2026, 6, 15, 1, 59, 0, 0, utc)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)

        assertTrue(delay > 0)
        val arrival = now.plus(Duration.ofMillis(delay))
        assertEquals(now.toLocalDate(), arrival.toLocalDate())
        assertEquals(LocalTime.of(2, 0), arrival.toLocalTime())
    }

    @Test
    fun `delay just after the target time targets tomorrow`() {
        val now = ZonedDateTime.of(2026, 6, 15, 2, 1, 0, 0, utc)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)

        assertTrue(delay > 0)
        val arrival = now.plus(Duration.ofMillis(delay))
        assertEquals(now.toLocalDate().plusDays(1), arrival.toLocalDate())
        assertEquals(LocalTime.of(2, 0), arrival.toLocalTime())
    }

    @Test
    fun `delay exactly at the target time rolls to tomorrow and is never zero or negative`() {
        val now = ZonedDateTime.of(2026, 6, 15, 2, 0, 0, 0, utc)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)

        assertTrue(delay > 0)
        val arrival = now.plus(Duration.ofMillis(delay))
        assertEquals(now.toLocalDate().plusDays(1), arrival.toLocalDate())
    }

    // --- nextNightlyDelayMillis: DST safety -----------------------------
    //
    // These tests do not hardcode a transition date (DST rules can change
    // by statute, and a hardcoded date would go stale). Instead they ask
    // America/New_York's own ZoneRules for the next real gap (spring
    // forward) / overlap (fall back) transition after a fixed reference
    // year, then build `now` from that.

    private val nyZone: ZoneId = ZoneId.of("America/New_York")

    private fun nextTransition(fromYear: Int, gap: Boolean): ZoneOffsetTransition {
        var t = nyZone.rules.nextTransition(ZonedDateTime.of(fromYear, 1, 1, 0, 0, 0, 0, nyZone).toInstant())
        while (t != null && t.isGap != gap) {
            t = nyZone.rules.nextTransition(t.instant)
        }
        return requireNotNull(t) { "no ${if (gap) "gap" else "overlap"} transition found after $fromYear" }
    }

    /**
     * `now` is the day before the spring-forward transition, at 03:00 —
     * deliberately NOT the same clock reading as the 02:00 target, so the
     * comparison below is not the one alignment (`now` exactly at the
     * target time) where Java's "push the skipped local time forward by
     * the gap" gap-resolution happens to exactly cancel out the missing
     * hour and coincidentally reproduce a flat 24h.
     *
     * The oracle here ([ZonedDateTime.of] resolving the target date at
     * 02:00 directly) is standard-library gap resolution — not a
     * restatement of production code — while [naiveArrival] models
     * exactly what `now + 24 * 60 * 60 * 1000` would have produced. The
     * two diverge by exactly the DST offset change, which is the concrete
     * bug a fixed-millisecond delay would introduce.
     */
    @Test
    fun `delay across a spring-forward gap lands on the correct wall-clock target, unlike naive fixed 24h math`() {
        val transition = nextTransition(fromYear = 2026, gap = true)
        val transitionDate = transition.dateTimeBefore.toLocalDate()
        val now = ZonedDateTime.of(transitionDate.minusDays(1), LocalTime.of(3, 0), nyZone)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)
        val myArrival = now.plus(Duration.ofMillis(delay))

        val expectedArrival = ZonedDateTime.of(transitionDate, LocalTime.of(2, 0), nyZone)
        val naiveArrival = now.plus(Duration.ofHours(24))

        assertTrue(delay > 0)
        assertEquals(expectedArrival.toInstant(), myArrival.toInstant())
        assertTrue(
            "naive fixed-24h math must land somewhere different from the correctly gap-resolved target",
            expectedArrival.toInstant() != naiveArrival.toInstant(),
        )
    }

    /** See the spring-forward test's KDoc; a fall-back overlap lengthens the day instead of shortening it. */
    @Test
    fun `delay across a fall-back overlap is 24h plus the repeated hour, not naive fixed 24h math`() {
        val transition = nextTransition(fromYear = 2026, gap = false)
        val transitionDate = transition.dateTimeBefore.toLocalDate()
        val now = ZonedDateTime.of(transitionDate.minusDays(1), LocalTime.of(2, 0), nyZone)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)

        val naiveFixed24h = Duration.ofHours(24).toMillis()
        val expected = Duration.ofHours(24).minus(transition.duration).toMillis()
        assertTrue(delay > 0)
        assertTrue("a DST-safe delay must not equal naive fixed-24h math on a fall-back day", delay != naiveFixed24h)
        assertEquals(expected, delay)
    }

    @Test
    fun `on an ordinary day far from any transition, the DST-safe delay matches naive fixed 24h math`() {
        // A control case: confirms the two computations only diverge
        // *because* of a real DST boundary, not for any other reason.
        val transition = nextTransition(fromYear = 2026, gap = true)
        val farDate = transition.dateTimeBefore.toLocalDate().minusMonths(2)
        val now = ZonedDateTime.of(farDate, LocalTime.of(2, 0), nyZone)

        val delay = ContinuityWorkScheduler.nextNightlyDelayMillis(now)

        assertEquals(Duration.ofHours(24).toMillis(), delay)
    }

    // --- Task 10 Step 6: keep startup offline ---------------------------

    /**
     * Resolves [relativeFromModuleRoot] against whichever of "the app
     * module directory" or "the project root" the test process's working
     * directory turns out to be — AGP's unit-test working directory is
     * not itself part of the plan's contract, so this tries both rather
     * than hardcoding one (see [org.mindanchor.goinglight.NetworkCallsForbiddenTest],
     * which hardcodes a root-relative path and silently no-ops its scan
     * if that guess is wrong — `readText()` here fails loudly instead).
     */
    private fun readModuleFile(relativeFromModuleRoot: String): String {
        val direct = File(relativeFromModuleRoot)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app/$relativeFromModuleRoot")
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(
            "could not find $relativeFromModuleRoot relative to either the module root or the repo root " +
                "(cwd=${File(".").absolutePath})",
        )
    }

    @Test
    fun `ContinuityWorkScheduler never references a Drive or auth API`() {
        val src = readModuleFile("src/main/java/org/mindanchor/continuity/ContinuityWorkScheduler.kt")
        for (forbidden in listOf("GoogleDriveAuth", "GoogleDriveObjectStore", "currentAccessToken", "okhttp3", "OkHttpClient")) {
            assertTrue("ContinuityWorkScheduler.kt must not reference $forbidden", forbidden !in src)
        }
    }

    @Test
    fun `HomeActivity never references a Drive or auth API anywhere in the file`() {
        // Whole-file scope (not just onCreate's body) is the simpler,
        // strictly-stronger check the brief allows: if the file as a
        // whole never mentions these symbols, onCreate cannot either.
        val src = readModuleFile("src/main/java/org/mindanchor/HomeActivity.kt")
        for (forbidden in listOf("GoogleDriveAuth", "GoogleDriveObjectStore", "currentAccessToken")) {
            assertTrue("HomeActivity.kt must not reference $forbidden", forbidden !in src)
        }
    }
}
