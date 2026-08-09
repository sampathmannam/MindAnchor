package org.mindanchor.goinglight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.friction.GoingLightSchedule
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The BroadcastReceiver that arms the next Going Light
 * window transition and toggles the VpnService on/off.
 *
 * The mechanism is the same AlarmManager pattern the
 * project's existing schedules (sunset, bedtime) use:
 * the receiver fires at the next transition instant,
 * reads the schedule, and either starts or stops the
 * VpnService. The receiver then re-arms itself at the
 * next transition.
 *
 * @wording-reviewed — the user-visible "Going Light is
 * now active" / "Going Light is now off" notifications
 * (the follow-up Composable) are clinical-review-required.
 */
class GoingLightScheduler : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val now = LocalDateTime.now()
        val pending = goAsync()
        scope.launch {
            try {
                val prefs = FrictionPrefs(context)
                val schedule = prefs.goingLightSchedule.first()
                val transition = schedule.nextTransition(now) ?: return@launch
                val transitioningToActive = isTransitioningToActive(now, schedule, transition)
                if (transitioningToActive) {
                    startVpn(context)
                } else {
                    stopVpn(context)
                }
                armNext(context, schedule)
            } finally {
                pending.finish()
            }
        }
    }

    private fun isTransitioningToActive(
        now: LocalDateTime,
        schedule: GoingLightSchedule,
        transition: LocalDateTime,
    ): Boolean = schedule.isActiveAt(transition) && !schedule.isActiveAt(now)

    private fun startVpn(context: Context) {
        val intent = Intent(context, GoingLightVpnService::class.java).apply {
            action = GoingLightVpnService.ACTION_START
        }
        // The launcher Activity must have already obtained
        // consent via prepare(); if not, the OS will reject
        // the start and the user will see the consent
        // dialog. The first-time UX is in the launcher.
        context.startForegroundService(intent)
    }

    private fun stopVpn(context: Context) {
        val intent = Intent(context, GoingLightVpnService::class.java).apply {
            action = GoingLightVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * Arm the next transition via AlarmManager. The
     * alarm fires this same receiver, which reads the
     * schedule, acts, and re-arms. The re-arming means
     * the schedule can change without needing to
     * re-arm the chain.
     */
    private fun armNext(context: Context, schedule: GoingLightSchedule) {
        val now = LocalDateTime.now()
        val next = schedule.nextTransition(now) ?: return
        val zone = ZoneId.systemDefault()
        val triggerAtMillis = next.date.atTime(next.time)
            .atZone(zone).toInstant().toEpochMilli()
        val alarmIntent = Intent(context, GoingLightScheduler::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    companion object {
        /**
         * The single entry point: the home screen (or the
         * settings UI) calls this when the user enables
         * Going Light. The first call sets the alarm; the
         * alarm fires the receiver, which starts the
         * VpnService at the right moment.
         */
        fun enable(context: Context, schedule: GoingLightSchedule) {
            // Persist the schedule; the BroadcastReceiver
            // reads it from FrictionPrefs when it fires.
            val prefs = FrictionPrefs(context)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                prefs.setGoingLightSchedule(schedule)
            }
            // Arm the alarm.
            val now = LocalDateTime.now()
            val next = schedule.nextTransition(now) ?: return
            val zone = ZoneId.systemDefault()
            val triggerAtMillis = next.date.atTime(next.time)
                .atZone(zone).toInstant().toEpochMilli()
            val alarmIntent = Intent(context, GoingLightScheduler::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 0, alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }

        fun disable(context: Context) {
            val prefs = FrictionPrefs(context)
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                prefs.setGoingLightSchedule(GoingLightSchedule(enabled = false))
            }
            // Stop the active VPN. The user just disabled
            // the schedule; an in-flight VpnService must
            // not keep blocking content until the next
            // alarm. The ACTION_STOP handler in the service
            // closes the VPN interface and calls
            // stopSelf().
            val stopIntent = Intent(context, GoingLightVpnService::class.java).apply {
                action = GoingLightVpnService.ACTION_STOP
            }
            context.startService(stopIntent)
            val alarmIntent = Intent(context, GoingLightScheduler::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 0, alarmIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(pi)
                pi.cancel()
            }
        }
    }
}
