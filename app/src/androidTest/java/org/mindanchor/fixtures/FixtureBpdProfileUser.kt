package org.mindanchor.fixtures

import org.mindanchor.data.BpdProfile
import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * v0.56.0+ end-to-end test fixture — **BpdProfileUser**.
 *
 * The BPD-acknowledging shape. The user has checked the BPD self-
 * identification flags (all five), the 15 check-ins show rapid
 * rating swings (1 → 5 → 2 → 4 within hours), and the 30 notes are
 * heavily journal-style with the splitting / late-night language the
 * profile is designed to recognise.
 *
 * Designed to exercise:
 *  - The BPD-aware copy in Settings (Settings page shows the five
 *    flags as "on")
 *  - The check-in chart with zig-zag ratings (z-score would be wild,
 *    the chart should show that)
 *  - The diary card screen (where BPD-aware copy lives)
 *  - Notes list dominated by JOURNAL type, some with very long bodies
 *  - Crisis contacts and the safety plan (both empty — they have
 *    *acknowledged* BPD but not filled the safety plan yet, which is
 *    itself a real state to test)
 */
object FixtureBpdProfileUser {

    private val NOW: Long = FixturesSchema.NOW_IST
    private val rng = Random(0x42_50_44L) // "BPD" deterministic

    // ---------- PROFILE ----------

    @JvmStatic
    fun profile(): UserProfile = UserProfile(
        displayName = "Sampath M",
        batch = "2020",
        goal = "ground",
        chronotype = "flexible",
        bpdProfileEnabled = true,
        hasCompletedOnboarding = true,
    )

    @JvmStatic
    fun bpdProfile(): BpdProfile = BpdProfile(
        longMessagesIRegret = true,
        lateNightImpulses = true,
        sometimesISplit = true,
        namedPersonToCall = true,
        okAtNight = false,
    )

    // ---------- CHECK-INS (mood) ----------

    /**
     * 15 check-ins across the last 7 days. Ratings swing deliberately:
     * a 1 at 21:00, a 5 the next morning, a 2 by 18:00, a 4 the day
     * after. The pattern is what `PatternFinder` flags as a "mood
     * swing" signature — the BPD profile is what makes the launcher
     * surface that finding *as a non-judgemental observation*.
     */
    @JvmStatic
    fun checkIns(): List<CheckIn> = listOf(
        // Day 0 (today)
        CheckIn(rating = 4, reflection = "Woke up feeling light, the morning was quiet.", atMillis = NOW - 13L * 3_600_000L),
        CheckIn(rating = 2, reflection = "Anand said something I cannot stop replaying. Does he even care?", atMillis = NOW - 8L * 3_600_000L),
        CheckIn(rating = 1, reflection = "I sent the long message. I should not have. I should not have.", atMillis = NOW - 90L * 60_000L),
        // Day 1
        CheckIn(rating = 3, reflection = "Slept. Not well, but I slept.", atMillis = NOW - 22L * 3_600_000L),
        CheckIn(rating = 5, reflection = "He called. He is sorry. I am sorry. The relief is real.", atMillis = NOW - 33L * 3_600_000L),
        CheckIn(rating = 2, reflection = "And then we argued again. I am exhausted.", atMillis = NOW - 44L * 3_600_000L),
        // Day 2
        CheckIn(rating = 4, reflection = "Quiet day. Walked. Read. The quiet helped.", atMillis = NOW - 70L * 3_600_000L),
        CheckIn(rating = 1, reflection = "Did not leave the bed until 11. The dark is heavy.", atMillis = NOW - 79L * 3_600_000L),
        // Day 3
        CheckIn(rating = 3, reflection = "Steady. The therapy notes helped.", atMillis = NOW - 95L * 3_600_000L),
        CheckIn(rating = 4, reflection = "Appa rang. I cried. I think it was good.", atMillis = NOW - 106L * 3_600_000L),
        // Day 4
        CheckIn(rating = 5, reflection = "Brilliant day at office. The new SHO is sharp.", atMillis = NOW - 121L * 3_600_000L),
        CheckIn(rating = 2, reflection = "He did not reply. I am telling myself it is fine.", atMillis = NOW - 130L * 3_600_000L),
        // Day 5
        CheckIn(rating = 3, reflection = "Quiet. I read for an hour after dinner.", atMillis = NOW - 145L * 3_600_000L),
        CheckIn(rating = 4, reflection = "Walked. The banyan looked good.", atMillis = NOW - 156L * 3_600_000L),
        // Day 6
        CheckIn(rating = 1, reflection = "I cannot stop thinking about the message. I want to delete it. I cannot.", atMillis = NOW - 170L * 3_600_000L),
    ).sortedBy { it.atMillis }

    // ---------- NOTES ----------

    /**
     * 30 notes across 14 days. Heavily journal-style with the BPD
     * signature: long bodies, splitting ("I love him / I cannot bear
     * him"), late-night timestamps, the "long message I sent" pattern.
     * Pin exactly one — a single therapy appointment that is the only
     * thing the user wants the launcher to surface as a reminder.
     */
    @JvmStatic
    fun notes(): List<Note> {
        val out = mutableListOf<Note>()
        var id = 5000L
        val samples = listOf(
            "Sent the long message at 1am. I knew as I was writing it that I should stop. I did not. I cannot unsend it and the regret is already here." to true, // long
            "Therapy appointment Thursday 4pm — please do not move this." to false, // reminder, pinned
            "I love him and I cannot bear him. Both are true at the same time. I do not know how to hold that." to true, // splitting
            "Woke up. He had not replied. The not-knowing is the worst part." to true,
            "Walked the long way to the office. The banyan is taller than I remembered." to false,
            "Appa called. I cried for ten minutes after. It was a relief I had not expected." to true,
            "Cooked a full meal. The act of paying attention to the onion, the mustard, the timing — it helped." to false,
            "I am writing this down because I will not remember it tomorrow: today was a good day. Hold on to that." to true,
            "Skipped the second coffee. Slept better." to false,
            "The new SHO is sharp. I want to be more like her in meetings: less explaining, more listening." to false,
            "Long, anxious evening. I rang Anand three times before I stopped myself. The third time his phone was off. I sat with it." to true,
            "Diary card done. The 'one small thing I did for me today' line: I made the bed. That is enough." to false,
            "He replied at 11pm. I am not ready to read it. I will read it tomorrow." to true,
            "Read it. He is sorry. I am sorry. The relief is real. I do not trust it." to true,
            "Quiet day. The kind I forget to write about, which is why I am writing it down." to false,
            "Went to the tailor. The new uniform fits. Small thing." to false,
            "I keep noticing the urge to check his messages. Each time, I notice. I do not always resist. That is the practice." to true,
            "Sent a shorter message. I do not regret it. That is new." to false,
            "Long day at the station. Held my temper when the witness contradicted himself. Noticed the urge, did not act on it." to true,
            "Could not sleep. Stretched on the floor. Read a few pages. The not-sleeping is part of the picture. I am not in crisis." to true,
            "Slept. Not well, but I slept." to false,
            "Brilliant morning at office. The team is good." to false,
            "He did not reply all day. I am telling myself it is fine. I am telling myself it is fine." to true,
            "Bought flowers for the desk. Small thing, real thing." to false,
            "Appa called again. We talked about the school. He is well. I am well." to false,
            "Went to bed at 10. The early night was its own act of care." to false,
            "I am writing this down because I will forget: the dark is not a fact. It is a weather." to true,
            "Slept poorly. Dragged through the morning." to false,
            "Therapy notes: notice the urge, do not always act. Sit with the not-knowing. I am trying." to true,
            "Read the letter from the SP. The tone is steady. I am glad of the steadiness." to false,
        )
        samples.forEachIndexed { idx, (body, isLong) ->
            // Spread across 14 days; idx % 14 gives the day-offset.
            val dayOffset = idx / 2
            val hourOfDay = if (isLong) listOf(23, 0, 1, 2).random(rng) else listOf(8, 11, 15, 18, 21).random(rng)
            val atMillis = NOW - dayOffset * 86_400_000L - (24 - hourOfDay) * 3_600_000L
            out.add(
                Note(
                    id = id++,
                    body = body,
                    createdAt = atMillis,
                    updatedAt = atMillis,
                    pinned = idx == 1, // exactly one pin: the therapy appointment
                    type = if (idx == 1) NoteType.REMINDER else NoteType.JOURNAL,
                    reminderAt = if (idx == 1) NOW + 3L * 86_400_000L + 4L * 3_600_000L else null,
                )
            )
        }
        return out.sortedByDescending { it.updatedAt }
    }

    // ---------- WELLNESS ----------

    /**
     * 14 days of wellness — just at the 14-day floor, so the baseline
     * is *just* reportable. Mindful minutes deliberately hit-or-miss:
     * 4 days of practice, 10 days of nothing.
     */
    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> {
        val out = mutableListOf<WellnessLedger.Entry>()
        val zone = ZoneId.systemDefault()
        for (dayOffset in 0 until 14) {
            val date = LocalDate.now(zone).minusDays(dayOffset.toLong())
            val sleep = (380 + rng.nextInt(-40, 41)).coerceIn(280, 540)
            val hrv = round1(28.0 + (sleep - 300) / 11.0 + rng.nextDouble(-2.5, 2.5))
            val rhr = round1(64.0 - (sleep - 420) / 38.0 + rng.nextDouble(-1.5, 1.5))
            val steps = (4500 + rng.nextInt(-1500, 2500)).coerceIn(800, 12_000)
            val mindfulness = if (dayOffset % 3 == 0) listOf(5, 8, 10, 12).random(rng) else 0
            out.add(WellnessLedger.Entry(WellnessSignal.SLEEP_MINUTES, date, sleep.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.HRV, date, hrv))
            out.add(WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, date, rhr))
            out.add(WellnessLedger.Entry(WellnessSignal.STEPS, date, steps.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.MINDFULNESS_MINUTES, date, mindfulness.toDouble()))
        }
        return out
    }

    private fun round1(x: Double): Double = kotlin.math.round(x * 10.0) / 10.0

    // ---------- SETTINGS / EVENTS ----------

    @JvmStatic
    fun settings(): Map<String, Any> = mapOf(
        "wizard_completed" to true,
        "welcome_seen" to true,
        "user_dismissed_wizard" to false,
        "health_connect_skipped" to false,
        "pair_watch_skipped" to true,
        "coros_skipped" to true,
        "ppg_skipped" to true,
        "source_health_connect" to true,
        "source_polar" to false,
        "source_coros" to false,
        "source_ppg" to false,
        "source_baseline" to true,
        "haptics_enabled" to true,
        "grayscale_enabled" to false,
        "sound_enabled" to true,
        "clocks_24h" to true,
        "nature_scene" to "forest",
        "breath_tone_enabled" to true,
        "home_needs_grid_visible" to true,
        "favorites_ordered" to "com.android.settings,com.android.dialer,com.google.android.apps.maps",
        "hidden" to emptySet<String>(),
        "renames" to "",
        "one_thing" to "Read the diary card once before you start the day",
        "goal_sleep_minutes" to 480,
        "goal_steps" to 5000,
        "goal_mindfulness_minutes" to 10,
        "chrono_preferred_window" to "flexible",
        "friction_enabled" to true,
        "friction_window_minutes" to 15,
        "friction_allowance_seconds" to 60,
        "friction_going_light" to false,
        "bpd_profile" to true,
    )

    /**
     * The BPD-aware going-light schedule: a 9pm → 7am "do not open
     * these apps" window. The package list is left for the harness to
     * populate; this is the schedule itself.
     */
    @JvmStatic
    fun appEvents(): List<AppEvent> = listOf(
        AppEvent("com.whatsapp", NOW - 6 * 3_600_000L, 14, blocked = false),
        AppEvent("com.whatsapp", NOW - 2 * 3_600_000L, 8, blocked = true), // late-night gate blocked
    )
}
