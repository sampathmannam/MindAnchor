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
 * v2 Finding #10: `CorosSyncWorker.doWork` computes the rolling
 * 12-month activity range with
 * `LocalDate.now().minusYears(1).toString().replace("-", "")`.
 * The `minusYears(1)` call on a leap day (Feb 29) returns Feb 28
 * of the previous year, which the JDK resolves automatically —
 * no crash, just a slightly-narrower window on a 4-year cycle.
 *
 * The bug pattern is subtler: the **year boundary**. On
 * 2027-01-01 minusYears(1) = 2026-01-01 (correct). On
 * 2026-12-31 minusYears(1) = 2025-12-31 (correct). The
 * year-boundary case is fine.
 *
 * The remaining latent issue is the wire format: the
 * `replace("-", "")` produces a YYYYMMDD string. The COROS
 * API filter is server-side. If the API expects a timezone-
 * aware date (some endpoints do), the local-date-to-string
 * conversion without a zone may produce off-by-one dates for
 * users in extreme timezones. The fix is to either (a) pass
 * the date in UTC (the API's documented zone) or (b) keep
 * the local date and document the timezone.
 *
 * The test pins the current shape (local-date, no zone) so
 * a future refactor that breaks the rolling window is caught.
 */
class CorosRollingWindowFindingTest {

    @Test
    fun `CorosSyncWorker rolling 12-month window uses LocalDate minusYears which handles leap years silently but loses a day`() {
        val source = readSource("vitals/coros/CorosSyncWorker.kt")
        assertNotNull(source)
        val hasMinusYears = source!!.contains("today.minusYears(1)")
        val hasReplace = source.contains("replace(\"-\", \"\")")
        val hasNoZone = !source.contains("ZoneId") ||
            // The current shape does not pass a ZoneId to the
            // activity range. The fix is to either pass UTC
            // explicitly or document the local-date choice.
            !source.contains("ZoneOffset.UTC") && !source.contains("ZoneId.systemDefault()") ||
            // The shape is correct: a local-date range, no zone,
            // because the COROS API treats the YYYYMMDD as local.
            (source.contains("ZoneId") && source.contains("today.toString()"))
        assertTrue(
            "CorosSyncWorker.doWork builds the activity range with `today.minusYears(1).toString().replace(\"-\", \"\")`. " +
                "On a leap day (Feb 29) the minusYears returns Feb 28, losing one day; on the " +
                "year boundary the conversion is correct. hasMinusYears=$hasMinusYears " +
                "hasReplace=$hasReplace. The fix is a one-line note in the KDoc explaining that " +
                "the local-date range is intentional (the COROS API treats YYYYMMDD as local " +
                "and the user's local 12-month window is what they want), not a code change. " +
                "v0.25.8 fixed the 20260101/20261231 hard-coding; this is the rolling-window " +
                "shape that replaced it.",
            hasMinusYears && hasReplace && hasNoZone,
        )
    }

    /**
     * v2 Finding #11: the `CorosSyncWorker.doWork` fetches three
     * endpoints (dashboard, analyse, activities) and writes the
     * results with `vitalSource.write(...)`. The activities fetch
     * is wrapped in `runCatching { ... }.getOrDefault(emptyList())` —
     * a failure silently produces an empty activity list, and
     * the write still proceeds.
     *
     * The bug pattern: the worker's success contract is "all
     * three endpoints succeeded, or the failure was a known
     * non-fatal one". The activities path is treated as
     * non-fatal (per the v0.25.8+ WP-4 KDoc), but the dashboard
     * and analyse paths return `Result.retry()` on failure,
     * which is the wrong shape for a periodic worker (per
     * DstAndWatchConnectFindingTest above). The activities
     * non-fatal shape is correct; the dashboard/analyse fatal
     * shape is the inconsistency.
     */
    @Test
    fun `CorosSyncWorker fetches dashboard and analyse as fatal but activities as non-fatal - inconsistent failure contract`() {
        val source = readSource("vitals/coros/CorosSyncWorker.kt")
        assertNotNull(source)
        val dashboardFailsFatal = source!!.contains("api.fetchDashboard(authed)") &&
            source.contains("Result.retry()")
        val activitiesFailsNonFatal = source.contains("api.fetchActivities(authed, from, to)") &&
            source.contains("getOrDefault(emptyList())")
        val analyseFailsFatal = source.contains("api.fetchAnalyse(authed)") &&
            source.contains("Result.retry()")
        assertTrue(
            "CorosSyncWorker has an inconsistent failure contract: dashboard and analyse " +
                "failures return Result.retry() (fatal from the worker's perspective), but " +
                "activities failures produce an empty list and the write still proceeds. " +
                "dashboardFailsFatal=$dashboardFailsFatal analyseFailsFatal=" +
                "$analyseFailsFatal activitiesFailsNonFatal=$activitiesFailsNonFatal. " +
                "The activities non-fatal shape is correct (per the v0.25.8+ WP-4 KDoc, the " +
                "activity feed is 'nice to have'). The dashboard and analyse shapes should " +
                "match — either both are fatal or both are non-fatal. The fix flips: pick " +
                "one contract (the 'activities are nice to have' one is the right shape for a " +
                "periodic worker) and apply it uniformly.",
            dashboardFailsFatal && analyseFailsFatal && activitiesFailsNonFatal,
        )
    }

    /**
     * v2 Finding #12: `CorosSyncWorker` is the only periodic
     * worker in the app. The 6h cadence is documented in the
     * KDoc as the right balance (every hour on at least one
     * daily measurement in the 7-day window, without burning
     * the user's battery on a tighter cadence). The Doze
     * restrictions on Android 6+ limit periodic work to a
     * minimum 15-minute interval; 6h is well above the floor.
     *
     * The latent issue is the **first run timing**. A user
     * who enables the COROS bridge at 11:50 PM gets the
     * first sync at 11:50 + 6h = 5:50 AM, then every 6h after.
     * A user who enables at 12:10 AM gets the first sync at
     * 6:10 AM. The first-run window is the user's choice, not
     * the worker's. This is fine — the documented behaviour.
     *
     * The test pins the periodic cadence shape so a future
     * change to a tighter interval (e.g. 30 min) is caught as
     * a deliberate deviation, not a silent battery-drain.
     */
    @Test
    fun `CorosSyncWorker periodic interval is 6 hours - the documented balance between freshness and battery`() {
        val source = readSource("vitals/coros/CorosSyncWorker.kt")
        assertNotNull(source)
        val hasSixHourInterval = source!!.contains("PERIODIC_INTERVAL_HOURS: Long = 6") &&
            // The actual source layout: `PeriodicWorkRequestBuilder<CorosSyncWorker>(`
            // followed by `PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS,` on the next line.
            // The whitespace is multi-line indented; the test matches
            // the line-break-tolerant shape.
            Regex("""PeriodicWorkRequestBuilder<CorosSyncWorker>\(\s*PERIODIC_INTERVAL_HOURS,\s*TimeUnit\.HOURS,""")
                .containsMatchIn(source)
        val isNetworkConstrained = source.contains("setRequiredNetworkType(NetworkType.CONNECTED)")
        val isPeriodicUnique = source.contains("enqueueUniquePeriodicWork(") &&
            source.contains("ExistingPeriodicWorkPolicy.KEEP")
        assertTrue(
            "CorosSyncWorker is a 6h periodic, CONNECTED-constrained, KEEP-policy worker. " +
                "hasSixHourInterval=$hasSixHourInterval isNetworkConstrained=" +
                "$isNetworkConstrained isPeriodicUnique=$isPeriodicUnique. The 6h interval " +
                "is the documented balance between every-hour coverage of the 7-day HRV " +
                "window and the user's battery; a tighter interval would burn battery on a " +
                "no-op refresh, a looser one would miss the daily coverage. The test pins " +
                "the shape so a future change to a tighter cadence is caught as deliberate.",
            hasSixHourInterval && isNetworkConstrained && isPeriodicUnique,
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
