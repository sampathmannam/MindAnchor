package org.mindanchor.sleep

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.ZoneId

data class SleepSummary(
    val windows: List<SleepWindow>,
    val regularityScore: Int?,
)

/**
 * Estimates sleep from the retroactive UsageStats event log — no persistent
 * service, no Google APIs, nothing leaves the device (docs/research/05 §7).
 */
class SleepRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Last ~7 nights. Returns null without usage access. */
    fun estimate(): SleepSummary? {
        if (!hasUsageAccess()) return null
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - 8L * 24 * 3_600_000

        val events = usageStats.queryEvents(start, end)
        val gaps = mutableListOf<ScreenGap>()
        var offSince: Long? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    if (offSince == null) offSince = event.timeStamp

                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    offSince?.let { gaps.add(ScreenGap(it, event.timeStamp)) }
                    offSince = null
                }
            }
        }

        val zone = ZoneId.systemDefault()
        val windows = SleepMath.sleepWindows(gaps, zone)
        return SleepSummary(
            windows = windows.takeLast(7),
            regularityScore = SleepMath.regularityScore(windows.takeLast(7), zone),
        )
    }
}
