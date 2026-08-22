package org.mindanchor.fixtures

import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate
import java.time.ZoneId

/**
 * v0.56.0+ end-to-end test fixture — **SparseUser**.
 *
 * The "just-installed, used once" shape. Three notes, zero check-ins, no
 * data sources connected, no wellness data, profile has the name only.
 *
 * Designed to exercise:
 *  - Empty / near-empty home card states
 *  - Onboarding-driven "set up your first source" prompts
 *  - The 3-note list view with no pinning
 *  - "No baseline yet" copy on the wellness surface
 *  - Settings page that shows 0 connected sources
 */
object FixtureSparseUser {

    private val NOW: Long = FixturesSchema.NOW_IST

    /**
     * Three notes: one from yesterday (general), one pinned reminder
     * typed this morning, one general thought from a few hours ago.
     */
    @JvmStatic
    fun notes(): List<Note> = listOf(
        Note(
            id = 1L,
            body = "Just installed MindAnchor. Will explore later.",
            createdAt = NOW - 22L * 3_600_000L,
            updatedAt = NOW - 22L * 3_600_000L,
            pinned = false,
            type = NoteType.GENERAL,
        ),
        Note(
            id = 2L,
            body = "Setting this down before I forget — Akka birthday next week, need to call her.",
            createdAt = NOW - 6L * 3_600_000L,
            updatedAt = NOW - 6L * 3_600_000L,
            pinned = true,
            type = NoteType.REMINDER,
            reminderAt = NOW + 6L * 86_400_000L,
        ),
        Note(
            id = 3L,
            body = "Quick thought: read the onboarding doc properly before the source setup.",
            createdAt = NOW - 90L * 60_000L,
            updatedAt = NOW - 90L * 60_000L,
            pinned = false,
            type = NoteType.GENERAL,
        ),
    )

    /** No check-ins yet — the user has not been prompted or has not accepted. */
    @JvmStatic
    fun checkIns(): List<CheckIn> = emptyList()

    /** Display name + IPS batch only; everything else is the default. */
    @JvmStatic
    fun profile(): UserProfile = UserProfile(
        displayName = "Sampath M",
        batch = "2020",
        goal = "",
        chronotype = "",
        bpdProfileEnabled = false,
        hasCompletedOnboarding = false,
    )

    /**
     * Settings: wizard is not complete, no sources, defaults. `one_thing` is
     * set so the home card can render the single-thing CTA without crashing.
     */
    @JvmStatic
    fun settings(): Map<String, Any> = mapOf(
        "wizard_completed" to false,
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
        "clocks_24h" to false,
        "nature_scene" to "sky",
        "breath_tone_enabled" to true,
        "home_needs_grid_visible" to false,
        "favorites_ordered" to "com.android.settings,com.android.dialer,com.google.android.apps.maps",
        "hidden" to emptySet<String>(),
        "renames" to "",
        "one_thing" to "Add a data source to start the wellness surface",
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

    /** No wellness data — empty ledger. */
    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> = emptyList()

    /** No app watch events — going-light is off, friction is off. */
    @JvmStatic
    fun appEvents(): List<AppEvent> = emptyList()
}
