package org.mindanchor

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import org.mindanchor.model.EmaScheduler
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.pulse.PulseReminder
import org.mindanchor.report.ReportScheduler
import org.mindanchor.sunset.SunsetController

/**
 * Every alarm this app owns, armed from one place.
 *
 * ## Why this is one function and not five call sites
 *
 * Alarms do not survive a reboot, and until this existed the list of what
 * to put back lived only inside `BootReceiver` — where it had drifted.
 * Batch releases, sunset and the nightly report were re-armed; the
 * check-in prompts and the fortnightly pulse reminder were not. Both of
 * those simply stopped after a restart and came back only if the person
 * happened to open the screen that arms them, which is not a thing anyone
 * would think to do about a feature that had gone quiet.
 *
 * A missing alarm is the worst shape of bug this app can have: nothing
 * fails, nothing is logged, a feature just never speaks again and the
 * person concludes it does not work.
 *
 * ## Idempotence is the contract
 *
 * Everything here must be safe to call repeatedly. That is why
 * [PulseReminder.ensureScheduled] counts from the last pulse rather than
 * from now — re-arming a "fourteen days from today" alarm on every boot
 * would walk it forward forever on a phone that restarts often.
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
        runCatching { PulseReminder.ensureScheduled(app) }
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
