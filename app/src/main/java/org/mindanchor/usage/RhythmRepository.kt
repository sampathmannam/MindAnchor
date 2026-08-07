package org.mindanchor.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import org.mindanchor.sleep.SleepRepository

/**
 * Reads the screen event log and hands it to [ScreenRhythm] — the same
 * retroactive UsageStats source, the same grant, and the same shape of
 * adapter as [SleepRepository], which is also where the grant check
 * lives so the two can never disagree about whether access exists.
 */
class RhythmRepository(private val context: Context) {

    /** Rhythms for [days], or null without the usage-access grant. */
    fun rhythms(days: List<LocalDate>): Map<LocalDate, DayRhythm>? {
        if (days.isEmpty()) return emptyMap()
        if (!SleepRepository(context).hasUsageAccess()) return null
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return null

        val zone = ZoneId.systemDefault()
        val until = System.currentTimeMillis()
        // One day before the earliest requested day, so ScreenRhythm's
        // observed-from guard can tell "the log starts here" apart from
        // "this day genuinely had no events".
        val from = days.min().minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val events = mutableListOf<ScreenEvent>()
        val raw = usageStats.queryEvents(from, until)
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> ScreenEventKind.INTERACTIVE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ScreenEventKind.NON_INTERACTIVE
                UsageEvents.Event.KEYGUARD_HIDDEN -> ScreenEventKind.UNLOCKED
                else -> null
            }
            if (kind != null) events.add(ScreenEvent(kind, event.timeStamp))
        }
        return ScreenRhythm.days(events, days, zone, until)
    }
}
