package org.mindanchor.vitals.coros

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
import java.util.concurrent.TimeUnit

/**
 * Periodic + on-demand sync of COROS Training Hub data into
 * the launcher's wellness surface.
 *
 * ## Cadence
 *
 *  - **Periodic**: every 6 hours. The COROS Training Hub
 *    endpoint does not push, and the well-known wellness
 *    summary HRV + RHR series is a 7-day window. A 6-hour
 *    cadence puts every hour on at least one daily
 *    measurement in the window without spending the
 *    user's battery on background work that adds nothing
 *    the user can see.
 *  - **On-demand**: the Settings screen's "Sync now" button
 *    enqueues a one-shot [OneTimeWorkRequest] that runs in
 *    addition to the periodic one. The work request is
 *    unique-tagged so a second "Sync now" does not stack a
 *    second worker on the queue.
 *
 * ## Failure mode
 *
 * The worker never throws. Every failure path returns
 * [Result.retry] (so the WorkManager backoff picks it up
 * next time) or [Result.failure] (when the failure is
 * permanent, e.g. the user has not stored credentials). The
 * UI re-reads the [lastSyncEpochMs] from the
 * [CorosVitalSource] DataStore after each run, and a stale
 * timestamp is the only honest signal that the last sync
 * failed.
 *
 * @wording-reviewed — the worker's name and description are
 * the only surfaces the user sees in the system WorkManager
 * settings panel. They must stay aligned with
 * docs/research/20.
 *
 * @see CorosVitalSource for the merged-data view the
 *   wellness card reads.
 */
class CorosSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Suppress("detekt.SwallowedException", "ReturnCount")
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val auth = CorosAuth(ctx)
        val vitalSource = CorosVitalSource(ctx)
        val api = CorosApi()

        // No credentials — the user has not opted in. A
        // retry is pointless; the next run will fail the
        // same way. Returning [Result.failure] keeps the
        // WorkManager log clean and the well-being
        // backoff table doesn't have a meaningful row for
        // a missing opt-in.
        if (!auth.connectionState(lastSyncEpochMs = null).isConnectedLike()) {
            return Result.failure()
        }
        var authed = try {
            auth.ensureAuthed()
        } catch (e: CorosApiException) {
            // Authentication failure with stored
            // credentials means the password changed on
            // the COROS side. Surface that as a permanent
            // failure — the next sync will retry the
            // login and fail the same way until the user
            // re-enters the password. The exception's
            // message would only be visible in WorkManager
            // logs, not in the UI; the UI's lastSync
            // timestamp going stale is the honest signal.
            @Suppress("SwallowedException")
            return Result.failure()
        } ?: return Result.failure()

        val dashboard = try {
            api.fetchDashboard(authed)
        } catch (e: CorosApiException) {
            if (e.corosResult != REGION_MISMATCH_RESULT) {
                // Transient: the worker should retry on the
                // next periodic tick rather than fail the
                // whole batch. The exception's `corosResult`
                // field is the structured diagnostic; the UI
                // shows the lastSync timestamp going stale.
                @Suppress("SwallowedException")
                return Result.retry()
            }
            // result=1019 ("Access token is invalid") on a
            // token minted seconds ago is not a bad token.
            // Login is federated across the Training Hub's
            // regional hosts — any of them will mint a token
            // for any account — but the data plane is
            // sharded, and a token only reads data on the
            // host the account actually lives on. The region
            // chosen at connect time is therefore
            // unverifiable at login and only provably wrong
            // here. Probe the other regions with the stored
            // credentials; the first data plane that answers
            // is the account's real home, and it is
            // persisted so every later sync starts right.
            // (Observed in the wild 2026-08-28: an account
            // connected as "eu" — accepted at login — whose
            // data lives on the US host.)
            val healed = healRegion(ctx, api, authed.region)
                ?: return Result.retry()
            authed = healed.auth
            healed.dashboard
        }
        val analyse = try {
            api.fetchAnalyse(authed)
        } catch (e: CorosApiException) {
            // Same as dashboard: a transient error here
            // is not grounds to take down the worker's
            // other calls. The HRV side-channel survives
            // even if the daily-summary side-channel is
            // down.
            @Suppress("SwallowedException")
            return Result.retry()
        }
        // Activities are paginated; the side-channel only
        // fetches one page. A failure here is non-fatal
        // for the HRV / RHR surface — the activity list
        // is "nice to have" — so we log and continue.
        val activities = runCatching { api.fetchActivities(authed, "20260101", "20261231") }
            .getOrDefault(emptyList())

        vitalSource.write(
            hrv = dashboard,
            daily = analyse,
            activities = activities,
        )
        return Result.success()
    }

    private fun CorosConnectionState.isConnectedLike(): Boolean =
        this is CorosConnectionState.Connected

    /**
     * A successful region probe: the token minted on the
     * account's real home host, and the dashboard that host
     * already returned (so the caller does not fetch it a
     * second time).
     */
    private data class HealedRegion(
        val auth: CorosAuthPayload,
        val dashboard: List<CorosHrv>,
    )

    /**
     * Finds the account's real regional host after the
     * stored region's data plane rejected a fresh token —
     * see the call site for why that means "wrong region"
     * rather than "bad credentials". Tries each other
     * region in turn; a host that fails, in any way, is
     * simply not the home region. On success the corrected
     * region is persisted so the next sync — and the
     * Settings screen's connection line — start right.
     */
    @Suppress("SwallowedException", "detekt.TooGenericExceptionCaught")
    private suspend fun healRegion(
        ctx: Context,
        api: CorosApi,
        badRegion: String,
    ): HealedRegion? {
        val store = CorosCredentialStore(ctx)
        val creds = store.read() ?: return null
        val hash = CorosPasswordHasher.md5Hex(creds.second)
        for (region in CANDIDATE_REGIONS) {
            if (region == badRegion) continue
            val payload = try {
                api.login(creds.first, hash, region)
            } catch (e: Exception) {
                continue
            }
            val dashboard = try {
                api.fetchDashboard(payload)
            } catch (e: Exception) {
                continue
            }
            store.write(creds.first, creds.second, region)
            return HealedRegion(payload, dashboard)
        }
        return null
    }

    companion object {
        /**
         * The unique work name for the periodic sync. Used
         * by [ensureScheduled] and [cancel] so a second
         * call to [ensureScheduled] replaces the prior
         * one rather than stacking a second worker.
         */
        const val PERIODIC_NAME = "coros_sync_periodic"

        /**
         * The unique work name for the on-demand sync.
         * Distinct from [PERIODIC_NAME] so the "Sync now"
         * button does not interact with the periodic
         * schedule — a one-shot enqueue is always a
         * one-shot, not a kick at the periodic.
         */
        const val ONESHOT_NAME = "coros_sync_oneshot"

        /**
         * The 6h periodic cadence. Documented in this
         * file's KDoc; the field is here so the same
         * constant is read by every caller (UI button,
         * boot receiver, AppWatchService).
         */
        private const val PERIODIC_INTERVAL_HOURS: Long = 6

        /**
         * The Training Hub's "Access token is invalid"
         * result code. On a token minted moments earlier it
         * means the stored *region* is wrong, not the token
         * — see the [healRegion] call site.
         */
        private const val REGION_MISMATCH_RESULT = "1019"

        /**
         * Every regional host the Training Hub runs, in the
         * order [healRegion] probes them. Must cover the
         * same set [CorosApi.baseUrl] maps.
         */
        private val CANDIDATE_REGIONS = listOf("eu", "us", "cn")

        /**
         * Arms the periodic worker. Called from
         * [org.mindanchor.settings.SettingsViewModel]
         * when the user connects the bridge. The
         * [ExistingPeriodicWorkPolicy.KEEP] policy
         * means a second call is a no-op: an already
         * running schedule is left alone. Use [cancel]
         * to drop the schedule.
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<CorosSyncWorker>(
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
         * user disconnects the bridge. A one-shot sync
         * already in flight is left alone — the result of
         * a one-shot is irrelevant once the credentials
         * are wiped.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }

        /**
         * Kicks an immediate one-shot sync. The unique
         * work name + [ExistingWorkPolicy.REPLACE]
         * policy means a second "Sync now" while the
         * first is still running cancels the in-flight
         * run and starts a fresh one — the user's
         * intent is "sync now", not "sync twice as hard".
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CorosSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
