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
 * v0.56.0+ end-to-end test fixture — **HeavyUser**.
 *
 * Sixty days of one plausible IPS officer's launcher use. 200 notes
 * (mixed types, 18 pinned), 60 check-ins (mostly 3-4 with one or two
 * spikes), 60 days of all five wellness signals, Health Connect + Polar
 * connected, friction enabled, BPD profile off.
 *
 * Designed to exercise:
 *  - Notes list with non-trivial scrolling, sticky day headers, day-filter
 *  - Home card pinned section that is *not* collapsed (cap behaviour)
 *  - Wellness baseline with 60-day history (well past the 14-day floor)
 *  - Check-in rate-limit history (no auto-pause; at most 4/day)
 *  - Source indicators in Settings (Health Connect ✓, Polar ✓, others ✗)
 *  - Mixed-type chips: GENERAL / TASK / REMINDER / JOURNAL on the rows
 */
object FixtureHeavyUser {

    private val NOW: Long = FixturesSchema.NOW_IST

    private val rng = Random(0x4E_4C_53L) // deterministic seed

    // ---------- NOTES ----------

    private val generalPool = listOf(
        "Slept 7 hours, woke up before the alarm.",
        "Walked 5,000 steps before the morning briefing.",
        "Felt good after the workout at the officers' mess gym.",
        "Called amma in the evening, she sounded better than last week.",
        "Cooked dinner at home instead of ordering in.",
        "Read 30 pages of the BPRD compendium before bed.",
        "Meditation sat felt short but the breath was steady.",
        "Skipped the second coffee, slept easier.",
        "Metro to office, read a chapter on CrPC 41.",
        "The lights on the verandah were good this morning.",
        "Had chai with Anand before flag-off, talked about the school case.",
        "Felt lighter after the walk around the park.",
        "Worried about the deposition on Thursday.",
        "Long day, but the staff did well on the patrol beat.",
        "Stretched for ten minutes before sleep, neck is happier.",
        "Skipped doomscroll, went to bed at a reasonable time.",
        "Wrote the diary card — clearer than I expected.",
        "Dosa for breakfast, amma would approve.",
        "Rang up the rickshaw wala for the airport run, fixed in advance.",
        "Quick thought: should I move the stand-up to 9:30?",
        "Lunch was just rice and dal, felt light after.",
        "Read the letter from the SP, the tone is steady.",
        "Walked the new beat route, quieter than I remembered.",
        "Bought flowers for the desk, small thing.",
        "Tea at 5pm, no biscuit, kept the promise to myself.",
        "Sent the file back to the reader, the citation was off.",
        "Went to the tailor in the evening, picked up the new uniform.",
        "Watched the test match, did not check work mail.",
        "Quick thought: the new SHO is doing well, tell her.",
        "Slept poorly, but not for any one reason I can name.",
    )

    private val taskPool = listOf(
        "Review the case diary before the magistrate hearing.",
        "File the monthly crime review by Friday.",
        "Pick up the dry-cleaning on the way back from office.",
        "Call back the IO on the chain-snatching case.",
        "Sign the leave application for Constable R.",
        "Follow up with the FSL on the pending samples.",
        "Write the appreciation note for the beat constable.",
        "Pay the electricity bill before the 25th.",
        "Email the SP the weekly return.",
        "Confirm the venue for the community meeting.",
        "Get the new lanyard for the office ID.",
        "Take the car in for the service this weekend.",
        "Refill the medicines — Friday.",
        "Send the FIR copy to the jurisdictional court.",
        "Book the auto for the station visit.",
        "Drop the parcel at the post office.",
        "Reply to the HR query on the LTC advance.",
        "Get the photocopies made for the charge-sheet annexures.",
        "Move the morning drill to 6:30 to beat the heat.",
        "Re-check the closed-circuit footage before the hearing.",
    )

    private val reminderPool = listOf(
        "Therapy appointment Thursday 3pm.",
        "Call Appa on Sunday, it's been a while.",
        "Pick up Akka from the station, 7pm.",
        "Medication refill Friday.",
        "Court appearance Monday, file in the bag the night before.",
        "Akka's birthday — call her.",
        "Submit the appraisal self-assessment by the 28th.",
        "Doctor follow-up next Wednesday.",
        "Take the car in for the service — 22nd.",
        "Community meeting at the school, Saturday 10am.",
    )

    private val journalPool = listOf(
        "Today the patrol was quiet. The night beat constable looked tired. I should check on him tomorrow.",
        "I keep noticing the same anxiety on Sunday evenings. Sleep is the worst part. Want to try the wind-down routine on Tuesday.",
        "A long conversation with the wife about the new posting. We are both a little tired. I think the move will be good in the end.",
        "Three weeks in the new office. The pace is steady. I am sleeping better than I did in the city posting.",
        "The community meeting went well. The older women had real questions. I should bring them a list of follow-ups next time.",
        "A difficult day at the station. I held my temper. Noticed the urge to raise my voice, did not. Tiny thing but real.",
        "I read the line in the diary card again — 'one small thing I did for me today' — and I could not think of one. Worth sitting with.",
        "Walked the long way to the office. The banyan is taller than I remembered. Small things, real things.",
        "I was short with Anand today. He did not deserve it. I will say so tomorrow.",
        "Cooked a full meal for the first time in weeks. Felt like an act of attention.",
    )

    /**
     * Build 200 notes spread over 60 days. Type distribution is roughly
     * GENERAL 50%, TASK 25%, REMINDER 10%, JOURNAL 15%. Pinned: 18 of the
     * 200 (9%) — concentrated on reminders and upcoming deadlines.
     */
    @JvmStatic
    fun notes(): List<Note> {
        val out = mutableListOf<Note>()
        var id = 1000L
        for (dayOffset in 0 until 60) {
            val dayMillis = NOW - dayOffset * 86_400_000L
            val perDay = when {
                dayOffset < 3 -> 5      // recent days are busier
                dayOffset < 14 -> 4
                else -> 3
            }
            repeat(perDay) { i ->
                val type = pickType()
                val body = when (type) {
                    NoteType.GENERAL -> generalPool.random(rng)
                    NoteType.TASK -> taskPool.random(rng)
                    NoteType.REMINDER -> reminderPool.random(rng)
                    NoteType.JOURNAL -> journalPool.random(rng)
                }
                val hourOfDay = listOf(7, 9, 11, 13, 15, 18, 21, 22).random(rng)
                val minuteOfHour = listOf(5, 12, 22, 35, 48, 55).random(rng)
                val created = dayMillis - (24 - hourOfDay) * 3_600_000L - minuteOfHour * 60_000L
                val updated = created + (if (rng.nextDouble() < 0.18) (1..240).random(rng) * 60_000L else 0L)
                val pinned = (dayOffset in 0..6) && rng.nextDouble() < 0.18
                val dueAt = if (type == NoteType.TASK && rng.nextDouble() < 0.6) {
                    created + (1..10).random(rng) * 86_400_000L
                } else null
                val reminderAt = if (type == NoteType.REMINDER) {
                    created + (1..7).random(rng) * 86_400_000L
                } else null
                val done = type == NoteType.TASK && rng.nextDouble() < 0.25
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
        // Sort newest-first (the harness can also rely on the codec to sort).
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

    // ---------- CHECK-INS (mood) ----------

    /**
     * 60 check-ins, one per day for 60 days, mostly 3-4 ("ok"/"good")
     * with one spike to 1 around day 32, one to 5 around day 18. The
     * reflections are short, first-person, in Indian English.
     */
    @JvmStatic
    fun checkIns(): List<CheckIn> {
        val out = mutableListOf<CheckIn>()
        val reflections = listOf(
            "Steady day. Nothing to complain about.",
            "Long at office, but the team carried it.",
            "Felt heavier than yesterday. Not sure why.",
            "Went for the walk after dinner, that helped.",
            "A good day. Talked to amma in the evening.",
            "Slept badly, dragged through the morning.",
            "Bright after the meeting at the school. The kids were good.",
            "On the edge, kept it together.",
            "One of those quiet days where the work is enough.",
            "Tired by 9pm. Took the early night, no regrets.",
        )
        for (dayOffset in 0 until 60) {
            val baseRating = 3
            val spike = when (dayOffset) {
                32 -> 1
                18 -> 5
                41 -> 2
                9 -> 4
                else -> baseRating + if (rng.nextDouble() < 0.30) rng.nextInt(-1, 2) else 0
            }.coerceIn(1, 5)
            val atMillis = NOW - dayOffset * 86_400_000L - (8..20).random(rng) * 3_600_000L
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
     * 60 days of all five wellness signals, generated with a sleep-led
     * correlation so the baseline is meaningful and not noise.
     */
    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> {
        val out = mutableListOf<WellnessLedger.Entry>()
        val zone = ZoneId.systemDefault()
        for (dayOffset in 0 until 60) {
            val date: LocalDate = LocalDate.now(zone).minusDays(dayOffset.toLong())
            val sleep = sleepMinutes(dayOffset)
            val hrv = hrvMs(sleep, dayOffset)
            val rhr = restingHr(sleep, dayOffset)
            val steps = stepsCount(dayOffset)
            val mindfulness = mindfulnessMinutes(dayOffset)
            out.add(WellnessLedger.Entry(WellnessSignal.SLEEP_MINUTES, date, sleep.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.HRV, date, hrv))
            out.add(WellnessLedger.Entry(WellnessSignal.RESTING_HEART_RATE, date, rhr))
            out.add(WellnessLedger.Entry(WellnessSignal.STEPS, date, steps.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.MINDFULNESS_MINUTES, date, mindfulness.toDouble()))
        }
        return out
    }

    private fun sleepMinutes(dayOffset: Int): Int {
        val base = 415
        val dip = if (dayOffset % 9 == 0) -55 else 0
        val noise = rng.nextInt(-22, 23)
        return (base + dip + noise).coerceIn(280, 540)
    }

    private fun hrvMs(sleep: Int, dayOffset: Int): Double {
        val base = 30.0 + (sleep - 300) / 11.0
        val drift = sinDrift(dayOffset, 4.5, 1.5)
        val noise = rng.nextDouble(-3.0, 3.0)
        return round1(base + drift + noise)
    }

    private fun restingHr(sleep: Int, dayOffset: Int): Double {
        val base = 62.0 - (sleep - 420) / 38.0
        val noise = rng.nextDouble(-1.8, 1.8)
        return round1(base + noise)
    }

    private fun stepsCount(dayOffset: Int): Int {
        val restDay = dayOffset % 7 == 0 || dayOffset % 7 == 6
        val base = if (restDay) 4500 else 7800
        val noise = rng.nextInt(-1400, 1401)
        return (base + noise).coerceIn(800, 14_000)
    }

    private fun mindfulnessMinutes(dayOffset: Int): Int {
        val hit = rng.nextDouble() < 0.55
        if (!hit) return 0
        return listOf(5, 8, 10, 12, 15, 20).random(rng)
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
        "coros_skipped" to true,
        "ppg_skipped" to true,
        "source_health_connect" to true,
        "source_polar" to true,
        "source_coros" to false,
        "source_ppg" to false,
        "source_baseline" to true,
        "haptics_enabled" to true,
        "grayscale_enabled" to false,
        "sound_enabled" to true,
        "clocks_24h" to true,
        "nature_scene" to "sky",
        "breath_tone_enabled" to true,
        "home_needs_grid_visible" to false,
        "favorites_ordered" to "com.android.settings,com.android.dialer,com.google.android.apps.maps,com.whatsapp",
        "hidden" to setOf("com.example.casino"),
        "renames" to "com.android.dialer=Phone",
        "one_thing" to "Add a data source to start the wellness surface",
        "goal_sleep_minutes" to 480,
        "goal_steps" to 8000,
        "goal_mindfulness_minutes" to 15,
        "chrono_preferred_window" to "morning",
        "friction_enabled" to true,
        "friction_window_minutes" to 20,
        "friction_allowance_seconds" to 90,
        "friction_going_light" to false,
        "bpd_profile" to false,
    )

    /**
     * A day of friction events: a flagged package the user kept
     * returning to, an allowed utility, and a blocked launch.
     */
    @JvmStatic
    fun appEvents(): List<AppEvent> = listOf(
        AppEvent("com.whatsapp", NOW - 5 * 3_600_000L, 42, blocked = false),
        AppEvent("com.example.casino", NOW - 4 * 3_600_000L, 0, blocked = true),
        AppEvent("com.google.android.apps.maps", NOW - 3 * 3_600_000L, 90, blocked = false),
        AppEvent("com.example.casino", NOW - 90 * 60_000L, 12, blocked = true),
        AppEvent("com.android.settings", NOW - 30 * 60_000L, 18, blocked = false),
    )
}
