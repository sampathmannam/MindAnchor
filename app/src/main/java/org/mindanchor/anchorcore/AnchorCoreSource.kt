package org.mindanchor.anchorcore

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.usage.RhythmRepository
import org.mindanchor.vitals.WellnessHistoryStore
import org.mindanchor.vitals.WellnessRepository

/**
 * The Context-carrying face: pulls what the existing repositories already
 * expose, reduces to facts, rolls the day ledgers, and answers with one
 * AnchorState. Recomputed on demand by its callers (PreHome open, letter
 * generation, Home composition) — never on a timer.
 */
class AnchorCoreSource(private val context: Context) {

    suspend fun state(today: LocalDate = LocalDate.now()): AnchorState {
        val prefs = AnchorPrefs(context)
        // Inert sentinel; every hook checks the master toggle before
        // acting, so -1 observed days can never render anywhere.
        if (!prefs.isEnabled()) return AnchorState.WarmingUp(-1)

        val zone = ZoneId.systemDefault()

        // Sleep onsets, the SettingsViewModel:684 pattern: window starts →
        // local time → minutes-after-18:00, so midnight-crossers read late.
        val summary = runCatching { SleepRepository(context).estimate() }.getOrNull()
        val onsets = summary?.windows.orEmpty().map { w ->
            val t = Instant.ofEpochMilli(w.startMillis).atZone(zone).toLocalTime()
            Deviation.minutesAfterSixPm(t.hour * 60 + t.minute)
        }

        // Observed days over the trailing 14: screen-rhythm days union
        // vital-ledger days. Both reads are local and cheap.
        val window = (0L..13L).map { today.minusDays(it) }
        val rhythms = runCatching { RhythmRepository(context).rhythms(window) }.getOrNull()
        val presenceByDay = window.associateWith { d ->
            rhythms?.get(d)?.let { it.firstUnlockMinute ?: it.screenMinutes }
        }
        val vitalDays = runCatching { WellnessHistoryStore(context).all() }
            .getOrDefault(emptyList())
            .map { it.day }
            .filter { it in window }
            .toSet()
        val daysObserved = AnchorCore.observedDays(presenceByDay, vitalDays)

        val readings = runCatching { WellnessRepository(context).readingsFor(today) }
            .getOrDefault(emptyList())

        // Weekly SRI snapshot roll (Task 5 header for why).
        val (prevSlot, curSlot) = prefs.sriSlots()
        val rolled = SriWeekLedger.roll(prevSlot, curSlot, today, summary?.regularityScore)
        prefs.setSriSlots(rolled.prev, rolled.cur)

        val facts = buildList {
            AnchorCore.lateNightCluster(onsets, today)?.let(::add)
            addAll(AnchorCore.vitalFacts(readings, today))
            AnchorCore.sleepIrregular(summary?.regularityScore, rolled.lastWeekSri, today)?.let(::add)
        }

        // Hysteresis: one reduce per calendar day, whatever recomputes first.
        val flaggedToday = facts.isNotEmpty()
        val streak = if (prefs.lastReducedDay() != today) {
            WeekPicture.reduce(flaggedToday, prefs.cleanStreak()).also {
                prefs.setCleanStreak(it)
                prefs.setLastReducedDay(today)
            }
        } else {
            // Same-day recompute: a fact appearing later in the day still
            // resets the streak; a fact disappearing does not refund it.
            if (flaggedToday && prefs.cleanStreak() > 0) {
                prefs.setCleanStreak(0)
                0
            } else prefs.cleanStreak()
        }
        val weekFlagged = WeekPicture.isFlagged(flaggedToday, streak)
        prefs.setWeekFlagged(weekFlagged)

        return AnchorState.of(daysObserved, facts, weekFlagged, System.currentTimeMillis())
    }
}
