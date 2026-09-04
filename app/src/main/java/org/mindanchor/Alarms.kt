package org.mindanchor

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import org.mindanchor.model.EmaScheduler
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.report.ReportScheduler
import org.mindanchor.sunset.SunsetController

/**
 * Every alarm this app owns, armed from one place.
 *
 * ## Why this is one function and not five call sites
 *
 * Alarms do not survive a reboot, and until this existed the list of what
 * to put back lived only inside `BootReceiver` — where it had drifted.
 * Batch releases, sunset, the nightly report, and the check-in
 * prompts are re-armed here. (The 2026-08-24 release removed the
 * fortnightly pulse scheduler; this function used to call it as a
 * fifth entry. The original list-of-five bug — see git history — was
 * that two of the five were not being re-armed; the re-arm contract
 * now covers the remaining four and the missing-alarm class of bug
 * is closed for them.)
 *
 * A missing alarm is the worst shape of bug this app can have: nothing
 * fails, nothing is logged, a feature just never speaks again and the
 * person concludes it does not work.
 *
 * ## Idempotence is the contract
 *
 * Every scheduler here is safe to call repeatedly. Re-arming on every
 * boot does not drift the firing times because each scheduler reads
 * the user's own window / cadence from prefs at arm time and bases
 * the next fire on that, not on now().
 */
object Alarms {

    /**
     * Re-arms everything. Never throws: this runs inside broadcast
     * receivers with seconds to live, and one scheduler failing must not
     * cost the others their alarms.
     */
    suspend fun ensureAll(context: Context) {
        val app = context.applicationContext
        runCatching { BatchAlarms.ensureScheduled(app) }
        runCatching { SunsetController.ensureScheduled(app) }
        runCatching { ReportScheduler.ensureScheduled(app) }
        runCatching { EmaScheduler.ensureScheduled(app) }
        // v0.70 (master plan T-1.2): crash-safe re-entry for OS Mode's
        // window suspension. Suspension is never persisted as truth; boot
        // re-derives it from the sunset window, so a process killed
        // mid-window cannot leave apps stranded on either side of the
        // state they should be in. Never throws, like everything here.
        runCatching { org.mindanchor.admin.OsMode.sync(app) }
    }

    /**
     * Whether this phone will honour an exact alarm.
     *
     * From Android 14 an app targeting 34+ is not granted
     * `SCHEDULE_EXACT_ALARM` by default, so the honest answer here is
     * usually "no" until somebody says otherwise — and every scheduler in
     * this app then falls back to an inexact alarm with a window of up to
     * an hour. That is survivable for the nightly report and wrong for a
     * batch the person deliberately placed at 18:00.
     */
    // v0.25.9 (lint sweep): with minSdk=33, the SDK_INT < S check
    // is always false. The early-return path is dead. Drop the guard.
    fun canBeExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }
}
