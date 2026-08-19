package org.mindanchor.fixtures

import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * v0.56.0+ end-to-end test fixture — **PowerUser**.
 *
 * The "I have used this app for a quarter" shape. 500 notes over 90
 * days, all four data sources connected (Health Connect, Polar, Coros,
 * PPG / manual), 90 days of all five wellness signals (450 entries),
 * 90 check-ins, ~45 pinned notes spread across the period, friction
 * + going-light on, BPD profile off, goals set, settings populated.
 *
 * Designed to exercise:
 *  - List scrolling with hundreds of rows (the LazyColumn stress test)
 *  - Home card pinned section that *does* hit the cap (the launcher
 *    truncates the pinned list — the test should see the truncation)
 *  - Wellness baseline with the full 90-day history (the deepest
 *    baseline the launcher can use)
 *  - Source indicators in Settings — all four ✓
 *  - Mixed-type chips and the day-filter (Today, Yesterday, This week,
 *    Last week, All) — every filter should be non-empty
 *  - Friction + going-light event log, both enabled
 */
object FixturePowerUser {

    private val NOW: Long = FixturesSchema.NOW_IST
    private val rng = Random(0x50_4F_57L) // "POW" deterministic

    // ---------- NOTES ----------

    private val lines = listOf(
        // GENERAL
        "Slept 6.5 hours, woke before the alarm.",
        "Walked 6000 steps before the briefing.",
        "Felt good after the morning workout.",
        "Called amma, she sounded better.",
        "Cooked dinner at home.",
        "Read 30 pages of the FSL manual.",
        "Meditation sat, breath was steady.",
        "Skipped the second coffee.",
        "Metro to office, read a chapter on BNSS 173.",
        "Banyan looked good this morning.",
        "Chai with Anand before the briefing.",
        "Felt lighter after the walk.",
        "Worried about the deposition.",
        "Long day, the team carried it.",
        "Stretched before sleep, neck is happier.",
        "Skipped doomscroll, slept at a decent time.",
        "Wrote the diary card.",
        "Dosa for breakfast.",
        "Booked the auto for tomorrow.",
        "Should move the stand-up to 9:30.",
        "Lunch was just rice and dal.",
        "Read the letter from the SP.",
        "Walked the new beat route.",
        "Bought flowers for the desk.",
        "Tea at 5, no biscuit, kept the promise.",
        "Sent the file back to the reader.",
        "Went to the tailor, picked up the new uniform.",
        "Watched the test match, did not check work mail.",
        "The new SHO is doing well, tell her.",
        "Slept poorly but for no one reason.",
        // TASK
        "Review the case diary before the hearing.",
        "File the monthly return by Friday.",
        "Pick up the dry-cleaning.",
        "Call back the IO on the chain-snatching case.",
        "Sign the leave application for Constable R.",
        "Follow up with the FSL on the samples.",
        "Write the appreciation note for the beat constable.",
        "Pay the electricity bill.",
        "Email the SP the weekly return.",
        "Confirm the venue for the community meeting.",
        "Get the new lanyard for the ID.",
        "Take the car in for the service.",
        "Refill the medicines on Friday.",
        "Send the FIR copy to the court.",
        "Book the auto for the station visit.",
        "Drop the parcel at the post office.",
        "Reply to the HR query on the LTC advance.",
        "Get the photocopies for the charge-sheet annexures.",
        "Move the morning drill to 6:30.",
        "Re-check the closed-circuit footage.",
        // REMINDER
        "Therapy appointment Thursday 4pm.",
        "Call Appa on Sunday.",
        "Pick up Akka from the station, 7pm.",
        "Medication refill Friday.",
        "Court appearance Monday, file in the bag the night before.",
        "Akka's birthday, call her.",
        "Submit the appraisal self-assessment by the 28th.",
        "Doctor follow-up next Wednesday.",
        "Take the car in for the service, 22nd.",
        "Community meeting at the school, Saturday 10am.",
        // JOURNAL
        "Today the patrol was quiet. The night beat constable looked tired.",
        "I keep noticing the Sunday-evening anxiety. The sleep is the worst part.",
        "A long conversation with the wife about the new posting. We are both a little tired.",
        "Three weeks in the new office. The pace is steady. I am sleeping better.",
        "The community meeting went well. The older women had real questions.",
        "A difficult day at the station. I held my temper.",
        "Read the diary card line 'one small thing I did for me today' and could not think of one.",
        "Walked the long way. The banyan is taller than I remembered.",
        "I was short with Anand. He did not deserve it.",
        "Cooked a full meal for the first time in weeks.",
    )

    /**
     * 500 notes spread over 90 days. Type distribution ~50/25/10/15.
     * About 9% pinned (≈ 45). Updated timestamps drift a little so the
     * list is not all on the hour. Tasks have due times; reminders
     * have reminder times; some tasks are done.
     */
    @JvmStatic
    fun notes(): List<Note> {
        val out = mutableListOf<Note>()
        var id = 10_000L
        for (dayOffset in 0 until 90) {
            val perDay = when {
                dayOffset < 7 -> 7
                dayOffset < 30 -> 6
                else -> 5
            }
            repeat(perDay) { i ->
                val type = pickType()
                val body = lines.random(rng)
                val hour = listOf(6, 8, 9, 11, 13, 15, 17, 19, 21, 22, 23).random(rng)
                val minute = listOf(2, 11, 23, 37, 44, 51, 58).random(rng)
                val created = NOW - dayOffset * 86_400_000L - (24 - hour) * 3_600_000L - minute * 60_000L
                val updated = created + (if (rng.nextDouble() < 0.20) (1..300).random(rng) * 60_000L else 0L)
                val pinned = (dayOffset in 0..45) && rng.nextDouble() < 0.09
                val dueAt = if (type == NoteType.TASK && rng.nextDouble() < 0.6)
                    created + (1..14).random(rng) * 86_400_000L else null
                val reminderAt = if (type == NoteType.REMINDER)
                    created + (1..7).random(rng) * 86_400_000L else null
                val done = type == NoteType.TASK && rng.nextDouble() < 0.30
                out.add(
                    Note(
                        id = id++,
                        body = body,
                        createdAt = created,
                        updatedAt = updated,
                        pinned = pinned,
                        type = type,
                        dueAt = dueAt,
                        reminderAt = reminderAt,
                        done = done,
                    )
                )
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    private fun pickType(): NoteType {
        val roll = rng.nextDouble()
        return when {
            roll < 0.50 -> NoteType.GENERAL
            roll < 0.75 -> NoteType.TASK
            roll < 0.85 -> NoteType.REMINDER
            else -> NoteType.JOURNAL
        }
    }

    // ---------- CHECK-INS ----------

    /**
     * 90 check-ins, one per day, with a realistic distribution:
     * mostly 3-4, occasional 2 (rough patch every ~12 days),
     * occasional 5 (good stretch every ~25 days).
     */
    @JvmStatic
    fun checkIns(): List<CheckIn> {
        val reflections = listOf(
            "Steady day.",
            "Long at office, but the team carried it.",
            "Felt heavier than yesterday.",
            "Walked after dinner, that helped.",
            "Talked to amma in the evening.",
            "Slept badly, dragged through the morning.",
            "Bright after the meeting at the school.",
            "On the edge, kept it together.",
            "One of those quiet days where the work is enough.",
            "Tired by 9pm, took the early night.",
        )
        val out = mutableListOf<CheckIn>()
        for (d in 0 until 90) {
            val base = 3
            val spike = when {
                d % 12 == 5 -> 2
                d % 25 == 9 -> 5
                else -> base + if (rng.nextDouble() < 0.30) rng.nextInt(-1, 2) else 0
            }.coerceIn(1, 5)
            val atMillis = NOW - d * 86_400_000L - (8..20).random(rng) * 3_600_000L
            out.add(
                CheckIn(
                    rating = spike,
                    reflection = if (rng.nextDouble() < 0.7) reflections.random(rng) else "",
                    atMillis = atMillis,
                )
            )
        }
        return out.sortedBy { it.atMillis }
    }

    // ---------- WELLNESS ----------

    /**
     * 90 days of all five wellness signals — the maximum history the
     * launcher holds. Sleep-driven HRV/RHR, weekend step dips,
     * 60%-hit mindfulness.
     */
    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> {
        val out = mutableListOf<WellnessLedger.Entry>()
        val zone = ZoneId.systemDefault()
        for (dayOffset in 0 until 90) {
            val date = LocalDate.now(zone).minusDays(dayOffset.toLong())
            val sleep = (420 + rng.nextInt(-50, 51)).coerceIn(280, 540)
            val hrv = round1(30.0 + (sleep - 300) / 11.0 + sinDrift(dayOffset, 4.0, 18.0) + rng.nextDouble(-2.5, 2.5))
            val rhr = round1(62.0 - (sleep - 420) / 38.0 + rng.nextDouble(-1.6, 1.6))
            val restDay = dayOffset % 7 == 0 || dayOffset % 7 == 6
            val steps = (if (restDay) 5000 else 8000) + rng.nextInt(-1500, 2500)
            val mindfulness = if (rng.nextDouble() < 0.6) listOf(5, 8, 10, 12, 15, 20).random(rng) else 0
            out.add(WellnessLedger.Entry(WellnessSignal.SLEEP_MINUTES, date, sleep.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.HRV, date, hrv))
            out.add(WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, date, rhr))
            out.add(WellnessLedger.Entry(WellnessSignal.STEPS, date, steps.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.MINDFULNESS_MINUTES, date, mindfulness.toDouble()))
        }
        return out
    }

    private fun sinDrift(dayOffset: Int, amplitude: Double, period: Double): Double =
        amplitude * kotlin.math.sin(2.0 * Math.PI * dayOffset / period)

    private fun round1(x: Double): Double = kotlin.math.round(x * 10.0) / 10.0

    // ---------- PROFILE / SETTINGS / EVENTS ----------

    @JvmStatic
    fun profile(): UserProfile = UserProfile(
        displayName = "Sampath M",
        batch = "2020",
        goal = "sustain",
        chronotype = "morning",
        bpdProfileEnabled = false,
        hasCompletedOnboarding = true,
    )

    @JvmStatic
    fun settings(): Map<String, Any> = mapOf(
        "wizard_completed" to true,
        "welcome_seen" to true,
        "user_dismissed_wizard" to false,
        "health_connect_skipped" to false,
        "pair_watch_skipped" to false,
        "coros_skipped" to false,
        "ppg_skipped" to false,
        "source_health_connect" to true,
        "source_polar" to true,
        "source_coros" to true,
        "source_ppg" to true,
        "source_baseline" to true,
        "haptics_enabled" to true,
        "grayscale_enabled" to false,
        "sound_enabled" to true,
        "clocks_24h" to true,
        "nature_scene" to "sky",
        "breath_tone_enabled" to true,
        "home_needs_grid_visible" to true,
        "favorites_ordered" to "com.android.settings,com.android.dialer,com.google.android.apps.maps,com.whatsapp,org.mindanchor",
        "hidden" to setOf("com.example.casino", "com.example.shortvideo"),
        "renames" to "com.android.dialer=Phone,com.google.android.apps.maps=Maps",
        "one_thing" to "Walk the long way to the office at least once this week",
        "goal_sleep_minutes" to 480,
        "goal_steps" to 8000,
        "goal_mindfulness_minutes" to 15,
        "chrono_preferred_window" to "morning",
        "friction_enabled" to true,
        "friction_window_minutes" to 20,
        "friction_allowance_seconds" to 60,
        "friction_going_light" to true,
        "bpd_profile" to false,
    )

    /**
     * A day's worth of friction + going-light events across the four
     * sources, with mix of allowed and blocked launches.
     */
    @JvmStatic
    fun appEvents(): List<AppEvent> = listOf(
        AppEvent("com.whatsapp", NOW - 7 * 3_600_000L, 180, blocked = false),
        AppEvent("org.mindanchor", NOW - 6 * 3_600_000L, 75, blocked = false),
        AppEvent("com.example.casino", NOW - 5 * 3_600_000L, 0, blocked = true),
        AppEvent("com.google.android.apps.maps", NOW - 4 * 3_600_000L, 240, blocked = false),
        AppEvent("com.example.casino", NOW - 3 * 3_600_000L, 0, blocked = true),
        AppEvent("com.example.shortvideo", NOW - 2 * 3_600_000L, 0, blocked = true),
        AppEvent("com.whatsapp", NOW - 1 * 3_600_000L, 12, blocked = true), // late-night blocked
        AppEvent("com.android.settings", NOW - 30 * 60_000L, 25, blocked = false),
        AppEvent("com.android.dialer", NOW - 15 * 60_000L, 30, blocked = false),
    )
}
