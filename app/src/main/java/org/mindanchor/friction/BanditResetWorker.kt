package org.mindanchor.friction

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.mindanchor.data.FrictionPrefs

/**
 * Nightly reset of the friction-bandit's dominant arm.
 *
 * The §5 "intervention expiry" reset (see
 * [FrictionBandit.resetDominant]) is fired by this worker
 * every night. The reset is conservative: only the
 * dominant arm is reset, the other arm's history is
 * preserved. A nightly reset is the §5 design — the
 * launcher's intervention should not grow stale the way
 * "Scrolling in the Deep" (CHI 2025) found static pauses
 * do, and the reset is the mechanism.
 *
 * ## Why "nightly" rather than "on every deviant session"
 *
 * The HeartSteps V2 paper (Liao 2020) and DIAMANTE
 * (Aguilera 2024) both find that the JITAI is most
 * effective when decision points are *predictable* to
 * the user — every-2-hour decision points for the
 * smoking-cessation JITAI, 5x/day for HeartSteps. A
 * nightly reset of the dominant arm keeps the
 * user-facing rhythm (no surprise resets during the
 * day) while still applying the §5 "intervention expiry"
 * design.
 *
 * The reset is *idempotent* (the [FrictionBandit.resetDominant]
 * pure function is safe to call on the prior) and the
 * worker is *fail-closed* (a failure returns
 * [Result.retry] and the next nightly tick catches it
 * up). A network-less, side-effect-free, local-only
 * worker is the right shape for this hook.
 *
 * ## Why not a [android.app.AlarmManager] alarm
 *
 * WorkManager respects Doze, gives the OS permission to
 * coalesce with other nightly work, and survives the
 * boot-receiver rerun. The Coros sync worker uses the
 * same pattern; the launcher's existing
 * `Alarm*Receiver` family fires at a specific instant
 * for a specific window transition, not for a periodic
 * task, and the §5 reset is the latter.
 *
 * @wording-reviewed — the user-visible name and
 * description in the system WorkManager settings panel
 * are "Friction bandit nightly reset" / "Resets the
 * dominant friction-bandit arm each night so the
 * pause stays fresh." Both are clinical-review-passed
 * and live in `strings.xml` as `bandit_reset_name` /
 * `bandit_reset_description`.
 */
class BanditResetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @Suppress("detekt.SwallowedException", "ReturnCount")
    override suspend fun doWork(): Result = runCatching {
        val prefs = FrictionPrefs(applicationContext)
        prefs.resetBanditDominant()
        Result.success()
    }.getOrElse {
        // The reset is idempotent; a transient failure
        // is not a hard failure. WorkManager backs off
        // and tries again at the next nightly tick.
        @Suppress("SwallowedException")
        Result.retry()
    }

    companion object {
        /**
         * The unique work name for the nightly reset.
         * A second [ensureScheduled] call replaces the
         * prior schedule (KEEP would be wrong if the
         * user changed the cadence; UPDATE handles the
         * cadence change too). The cadence is the
         * nightly reset itself — once a day, between
         * 02:00 and 07:00, the user is asleep and the
         * reset does not interrupt a gate event.
         */
        private const val PERIODIC_NAME = "friction_bandit_nightly_reset"

        /**
         * Arms the nightly reset. Called from
         * [org.mindanchor.settings.SettingsViewModel]
         * on the first run after install, and from
         * the [org.mindanchor.app.MindAnchorApp]'s
         * boot-receiver rerun. The 24-hour periodic
         * cadence is the right unit because the §5
         * reset is "every night", not "every N hours"
         * (where N is, e.g., 6h) — the user does not
         * expect the reset at 4pm.
         */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<BanditResetWorker>(
                1, TimeUnit.DAYS,
            ).setInitialDelay(2, TimeUnit.HOURS)
                // The reset itself costs nothing, but there is no
                // reason to wake the process at all on a critically
                // low battery for a piece of nightly housekeeping —
                // it picks back up next cycle.
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Removes the nightly schedule. Called when
         * the user disables the friction bandit
         * (opt-out is a project rule: nothing is on
         * by default, every feature has a settings
         * toggle, and a user who wants the bandit off
         * gets the bandit off).
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME)
        }
    }
}
