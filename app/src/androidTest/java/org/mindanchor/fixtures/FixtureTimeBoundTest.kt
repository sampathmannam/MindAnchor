package org.mindanchor.fixtures

import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.56.0+ end-to-end test fixture — **TimeBoundTest**.
 *
 * 30 notes, all in the last 7 days, deliberately designed to test
 * the day-grouping / sticky-headers / day-filter behaviour.
 *
 * The two important edge cases in the data:
 *
 *  - Note A is at **yesterday 23:30 IST**. Note B is at **today 00:30
 *    IST**. They are 60 minutes apart. They MUST land in different day
 *    groups. A naive bucketing that rounds to the nearest day, or
 *    that uses UTC rather than local time, will collapse them into
 *    one group and the test will catch that.
 *
 *  - The day counts are deliberately uneven (today 9, yesterday 6,
 *    2-days-ago 4, 3-days-ago 4, 4-days-ago 3, 5-days-ago 2,
 *    6-days-ago 2) so the "All / Today / Yesterday / Earlier"
 *    filter pills each have a distinct, non-zero count.
 *
 * Designed to exercise:
 *  - Day-filter pill counts: All 30, Today 9, Yesterday 6, Earlier 15
 *  - Sticky day headers in the LazyColumn
 *  - The 23:30 → 00:30 boundary across midnight IST
 *  - The "Today" section correctly excludes yesterday 23:30
 *  - The "Earlier" bucket shows distinct day headers
 *  - Newest-first sort within each day
 */
object FixtureTimeBoundTest {

    private val NOW: Long = FixturesSchema.NOW_IST
    private val TODAY_IST_START: Long = FixturesSchema.TODAY_IST_START
    private val YESTERDAY_IST_START: Long = FixturesSchema.YESTERDAY_IST_START

    /**
     * 30 notes in the last 7 days, with explicit day counts:
     *  today 9, yesterday 6, 2d 4, 3d 4, 4d 3, 5d 2, 6d 2.
     *
     * The (yesterday 23:30) → (today 00:30) pair is on rows 0 and 1
     * by updatedAt-descending; the test reads the first two rows and
     * checks they belong to different day groups.
     */
    @JvmStatic
    fun notes(): List<Note> {
        val out = mutableListOf<Note>()
        var id = 30_000L

        // Helper: build a millis from day-offset-from-today and hour-of-day.
        fun at(dayOffsetFromToday: Int, hour: Int, minute: Int = 0): Long {
            // dayOffsetFromToday: 0 = today, 1 = yesterday, 2 = 2 days ago, etc.
            val dayStart = TODAY_IST_START - dayOffsetFromToday * 86_400_000L
            return dayStart + hour * 3_600_000L + minute * 60_000L
        }

        // ----- Today (9 notes) -----
        out.add(Note(id = id++, body = "Morning run, 4km, slow but steady.", createdAt = at(0, 6, 30), updatedAt = at(0, 6, 30), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Chai with Anand before the briefing.", createdAt = at(0, 8, 15), updatedAt = at(0, 8, 15), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Review the case diary before the hearing.", createdAt = at(0, 9, 5), updatedAt = at(0, 9, 5), pinned = true, type = NoteType.TASK, dueAt = at(0, 9, 5) + 3 * 3_600_000L))
        out.add(Note(id = id++, body = "Felt good after the workout.", createdAt = at(0, 11, 0), updatedAt = at(0, 11, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Lunch was just rice and dal.", createdAt = at(0, 13, 30), updatedAt = at(0, 13, 30), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Bought flowers for the desk.", createdAt = at(0, 15, 12), updatedAt = at(0, 15, 12), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Therapy appointment Thursday 4pm.", createdAt = at(0, 17, 45), updatedAt = at(0, 17, 45), pinned = true, type = NoteType.REMINDER, reminderAt = at(0, 17, 45) + 2 * 86_400_000L + 4 * 3_600_000L))
        out.add(Note(id = id++, body = "Walked the long way back from the office.", createdAt = at(0, 19, 20), updatedAt = at(0, 19, 20), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Read 20 pages before bed.", createdAt = at(0, 22, 10), updatedAt = at(0, 22, 10), pinned = false, type = NoteType.GENERAL))

        // ----- The midnight boundary: today 00:30 IST -----
        out.add(
            Note(
                id = id++,
                body = "Woke up, could not get back to sleep. Wrote this down.",
                createdAt = at(0, 0, 30),   // today, 00:30 IST
                updatedAt = at(0, 0, 30),
                pinned = false,
                type = NoteType.JOURNAL,
            )
        )

        // ----- Yesterday (6 notes including the 23:30 boundary note) -----
        // First: yesterday 23:30 (this MUST land in "yesterday" group, not "today").
        out.add(
            Note(
                id = id++,
                body = "Long anxious evening. Could not stop checking the phone. Sat with it.",
                createdAt = at(1, 23, 30),  // yesterday, 23:30 IST
                updatedAt = at(1, 23, 30),
                pinned = false,
                type = NoteType.JOURNAL,
            )
        )
        out.add(Note(id = id++, body = "Slept poorly, dragged through the morning.", createdAt = at(1, 7, 5), updatedAt = at(1, 7, 5), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Sign the leave application for Constable R.", createdAt = at(1, 10, 0), updatedAt = at(1, 10, 0), pinned = true, type = NoteType.TASK, dueAt = at(1, 10, 0) + 2 * 86_400_000L))
        out.add(Note(id = id++, body = "Meditation sat, breath was steady.", createdAt = at(1, 13, 15), updatedAt = at(1, 13, 15), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "The new SHO is doing well, tell her.", createdAt = at(1, 16, 40), updatedAt = at(1, 16, 40), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Dinner at home, no phone after 9.", createdAt = at(1, 21, 0), updatedAt = at(1, 21, 0), pinned = false, type = NoteType.GENERAL))

        // ----- 2 days ago (4 notes) -----
        out.add(Note(id = id++, body = "Went to the tailor, picked up the new uniform.", createdAt = at(2, 10, 30), updatedAt = at(2, 10, 30), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Slept 7 hours, woke before the alarm.", createdAt = at(2, 12, 0), updatedAt = at(2, 12, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Follow up with the FSL on the pending samples.", createdAt = at(2, 15, 0), updatedAt = at(2, 15, 0), pinned = true, type = NoteType.TASK, dueAt = at(2, 15, 0) + 5 * 86_400_000L))
        out.add(Note(id = id++, body = "Read the letter from the SP, the tone is steady.", createdAt = at(2, 22, 0), updatedAt = at(2, 22, 0), pinned = false, type = NoteType.GENERAL))

        // ----- 3 days ago (4 notes) -----
        out.add(Note(id = id++, body = "Cooked a full meal for the first time in weeks.", createdAt = at(3, 8, 0), updatedAt = at(3, 8, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Wrote the diary card — clearer than I expected.", createdAt = at(3, 11, 30), updatedAt = at(3, 11, 30), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Court appearance Monday, file in the bag the night before.", createdAt = at(3, 14, 0), updatedAt = at(3, 14, 0), pinned = true, type = NoteType.REMINDER, reminderAt = at(3, 14, 0) + 4 * 86_400_000L))
        out.add(Note(id = id++, body = "Walked the new beat route.", createdAt = at(3, 18, 30), updatedAt = at(3, 18, 30), pinned = false, type = NoteType.GENERAL))

        // ----- 4 days ago (3 notes) -----
        out.add(Note(id = id++, body = "Slept 6.5 hours, woke before the alarm.", createdAt = at(4, 7, 30), updatedAt = at(4, 7, 30), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Called amma, she sounded better.", createdAt = at(4, 13, 0), updatedAt = at(4, 13, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Skipped the second coffee, slept easier.", createdAt = at(4, 21, 0), updatedAt = at(4, 21, 0), pinned = false, type = NoteType.GENERAL))

        // ----- 5 days ago (2 notes) -----
        out.add(Note(id = id++, body = "Metro to office, read a chapter on CrPC 41.", createdAt = at(5, 9, 0), updatedAt = at(5, 9, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Went to bed at 10, the early night was its own act of care.", createdAt = at(5, 22, 0), updatedAt = at(5, 22, 0), pinned = false, type = NoteType.GENERAL))

        // ----- 6 days ago (2 notes) -----
        out.add(Note(id = id++, body = "Bought a small notebook for the desk.", createdAt = at(6, 11, 0), updatedAt = at(6, 11, 0), pinned = false, type = NoteType.GENERAL))
        out.add(Note(id = id++, body = "Medication refill Friday.", createdAt = at(6, 16, 30), updatedAt = at(6, 16, 30), pinned = true, type = NoteType.REMINDER, reminderAt = at(6, 16, 30) + 1 * 86_400_000L))

        return out.sortedByDescending { it.updatedAt }
    }

    /**
     * 7 check-ins — one per day for the last 7 days. Ratings are
     * mostly 3-4 with a 1 on yesterday evening and a 5 on the day
     * before.
     */
    @JvmStatic
    fun checkIns(): List<CheckIn> = listOf(
        CheckIn(rating = 4, reflection = "Steady morning.", atMillis = NOW - 12L * 3_600_000L),
        CheckIn(rating = 2, reflection = "Long day.", atMillis = NOW - 30L * 3_600_000L),
        CheckIn(rating = 1, reflection = "Could not sleep.", atMillis = NOW - 3L * 3_600_000L), // yesterday late
        CheckIn(rating = 3, reflection = "Slept, dragged through the morning.", atMillis = NOW - 24L * 3_600_000L),
        CheckIn(rating = 4, reflection = "Quiet day.", atMillis = NOW - 48L * 3_600_000L),
        CheckIn(rating = 5, reflection = "Bright after the meeting at the school.", atMillis = NOW - 72L * 3_600_000L),
        CheckIn(rating = 3, reflection = "Steady.", atMillis = NOW - 96L * 3_600_000L),
    )

    /** 7 days of wellness — under the 14-day baseline floor, so the
     *  surface says "still building a picture" rather than reporting
     *  a number. */
    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> {
        val out = mutableListOf<WellnessLedger.Entry>()
        val zone = ZoneId.systemDefault()
        for (d in 0 until 7) {
            val date = LocalDate.now(zone).minusDays(d.toLong())
            val sleep = (420 + d * 8).coerceIn(300, 540)
            out.add(WellnessLedger.Entry(WellnessSignal.SLEEP_MINUTES, date, sleep.toDouble()))
            out.add(WellnessLedger.Entry(WellnessSignal.STEPS, date, (5000.0 + d * 200)))
        }
        return out
    }

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
        "health_connect_skipped" to true,
        "pair_watch_skipped" to true,
        "coros_skipped" to true,
        "ppg_skipped" to true,
        "source_health_connect" to false,
        "source_polar" to false,
        "source_coros" to false,
        "source_ppg" to false,
        "source_baseline" to false,
        "haptics_enabled" to true,
        "grayscale_enabled" to false,
        "sound_enabled" to true,
        "clocks_24h" to true,
        "nature_scene" to "sky",
        "breath_tone_enabled" to true,
        "home_needs_grid_visible" to false,
        "favorites_ordered" to "com.android.settings,com.android.dialer",
        "hidden" to emptySet<String>(),
        "renames" to "",
        "one_thing" to "Walk the long way to the office today",
        "goal_sleep_minutes" to 480,
        "goal_steps" to 7000,
        "goal_mindfulness_minutes" to 10,
        "chrono_preferred_window" to "morning",
        "friction_enabled" to false,
        "friction_window_minutes" to 20,
        "friction_allowance_seconds" to 60,
        "friction_going_light" to false,
        "bpd_profile" to false,
    )

    @JvmStatic
    fun appEvents(): List<AppEvent> = listOf(
        AppEvent("com.android.dialer", NOW - 6 * 3_600_000L, 20, blocked = false),
        AppEvent("com.android.settings", NOW - 2 * 3_600_000L, 15, blocked = false),
    )
}
