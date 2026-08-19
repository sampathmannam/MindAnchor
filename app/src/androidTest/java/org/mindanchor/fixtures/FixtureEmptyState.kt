package org.mindanchor.fixtures

import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.vitals.WellnessLedger

/**
 * v0.56.0+ end-to-end test fixture — **EmptyState**.
 *
 * The "literally just installed, never opened" shape. No notes, no
 * check-ins, no wellness data, no profile, wizard not completed, no
 * sources. The launcher should *only* show first-run surfaces — no
 * history, no patterns, no card content.
 *
 * Designed to exercise:
 *  - First-launch onboarding wizard (still active)
 *  - "No notes yet" empty state on Notes tab
 *  - "Add a data source to start the wellness surface" CTA on home
 *  - "Still building a picture" copy on the wellness card
 *  - Settings page where the only toggles are defaults
 *  - No check-in prompt yet (rate-limit state is fresh)
 */
object FixtureEmptyState {

    @JvmStatic
    fun notes(): List<Note> = emptyList()

    @JvmStatic
    fun checkIns(): List<CheckIn> = emptyList()

    /**
     * No profile. The harness should treat `null` / empty displayName
     * as "the onboarding wizard is still on step 1."
     */
    @JvmStatic
    fun profile(): UserProfile = UserProfile(
        displayName = "",
        batch = "",
        goal = "",
        chronotype = "",
        bpdProfileEnabled = false,
        hasCompletedOnboarding = false,
    )

    /**
     * All defaults: wizard incomplete, no sources, the system's default
     * "one thing" copy.
     */
    @JvmStatic
    fun settings(): Map<String, Any> = mapOf(
        "wizard_completed" to false,
        "welcome_seen" to false,
        "user_dismissed_wizard" to false,
        "health_connect_skipped" to false,
        "pair_watch_skipped" to false,
        "coros_skipped" to false,
        "ppg_skipped" to false,
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

    @JvmStatic
    fun wellness(): List<WellnessLedger.Entry> = emptyList()

    @JvmStatic
    fun appEvents(): List<AppEvent> = emptyList()
}
