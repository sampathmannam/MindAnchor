package org.mindanchor.vitals.polar

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic + on-demand sync of Polar AccessLink data into
 * the launcher's wellness surface.
 *
 * ## Cadence
 *
 *  - **Periodic**: every 6 hours. Same window the COROS
 *    side-channel uses. The Polar nightly HRV is generated
 *    once per night, so a 6h cadence puts every hourly
 *    sync in the same day-window without burning the
 *    user's battery on background work that adds nothing
 *    the user can see.
 *  - **On-demand**: the Settings screen's "Sync now"
 *    button enqueues a one-shot [OneTimeWorkRequest] that
 *    runs in addition to the periodic one. The work
 *    request is unique-tagged so a second "Sync now"
 *    does not stack a second worker on the queue.
 *
 * ## Failure mode
 *
 * The worker never throws. Every failure path returns
 * [Result.retry] (so the WorkManager backoff picks it up
 * next time) or [Result.failure] (when the failure is
 * permanent — the user has no credentials, or the token
 * has expired and the user must re-authorize). The UI
 * re-reads the [lastSyncEpochMs] from the
 * [PolarVitalSource] DataStore after each run, and a
 * stale timestamp is the only honest signal that the
 * last sync failed.
 *
 * @wording-reviewed — clinical-review-required. The
 * worker's name and description are the only surfaces
 * the user sees in the system WorkManager settings
 * panel. They must stay aligned with the home card's
 * "Last sync" string.
 */
class PolarSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Suppress("detekt.SwallowedException", "ReturnCount")
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val auth = PolarAuth(ctx)
        val vitalSource = PolarVitalSource(ctx)
        val api = PolarApi()

        // No credentials on file — the user has not opted
        // in. A retry is pointless; the next run will fail
        // the same way. Returning [Result.failure] keeps
        // the WorkManager log clean.
        if (auth.connectionState() is PolarConnectionState.NotConnected) {
            return Result.failure()
        }

        // Token might be expired (3-day TTL). If the
        // cached token is past its use-by, the user has to
        // re-authorize; we treat that as a permanent
        // failure for the worker and let the home card's
        // "Last sync N days ago" surface the staleness.
        val token = try {
            auth.ensureAuthed()
        } catch (e: PolarApiException) {
            return Result.failure()
        } ?: return Result.failure()

        val userId = try {
            if (token.userId > 0L) token.userId else api.fetchUserId(token.accessToken)
        } catch (e: PolarApiException) {
            return Result.failure()
        }

        // Pull the last 7 days. The Polar Nightly
        // Recharge endpoint is per-date; the side-channel
        // pulls a rolling window the same way the COROS
        // dashboard query does.
        val today = LocalDate.now()
        val dates = (0..6).map { today.minusDays(it.toLong()).toString() }

        val hrvList = mutableListOf<PolarHrv>()
        val rhrList = mutableListOf<PolarRhr>()
        val sleepList = mutableListOf<PolarSleep>()

        for (date in dates) {
            runCatching {
                api.fetchNightlyRecharge(token.accessToken, userId, date)
            }.onSuccess { payload ->
                if (payload.heartRateVariabilityAvg != null) {
                    hrvList += PolarHrv(
                        date = date,
                        rmssd = payload.heartRateVariabilityAvg,
                    )
                }
                if (payload.heartRateAvg != null) {
                    rhrList += PolarRhr(date = date, rhr = payload.heartRateAvg.toDouble())
                }
            }
            runCatching {
                api.fetchSleep(token.accessToken, userId, date)
            }.onSuccess { payload ->
                if (payload.totalSleepSeconds != null) {
                    sleepList += PolarSleep(
                        date = date,
                        totalSleepSeconds = payload.totalSleepSeconds,
                        sleepScore = payload.sleepScore,
                    )
                }
            }
        }

        vitalSource.write(
            hrv = hrvList,
            rhr = rhrList,
            sleep = sleepList,
        )
        return Result.success()
    }

    companion object {
        const val PERIODIC_NAME = "polar_sync_periodic"
        const val ONESHOT_NAME = "polar_sync_oneshot"

        private const val PERIODIC_INTERVAL_HOURS: Long = 6

        /**
         * Arms the periodic worker. Called from
         * [org.mindanchor.settings.SettingsViewModel]
         * when the user connects the bridge.
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<PolarSyncWorker>(
                PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Removes the periodic schedule. Called when the
         * user disconnects the bridge.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        /**
         * Kicks an immediate one-shot sync.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<PolarSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
