package org.mindanchor.fixtures

import org.mindanchor.data.BpdProfile
import org.mindanchor.model.CheckIn
import org.mindanchor.model.Note
import org.mindanchor.model.NoteType
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal
import java.time.LocalDate

/**
 * Schema reference for the v0.56.0+ end-to-end test fixtures.
 *
 * The MindAnchor data classes the fixtures target (real, in main source set):
 *   - [Note]            — the user-authored note. `id` is a Long, body is free
 *                         text, `pinned` is a single boolean, `type` is one of
 *                         [NoteType.GENERAL] / [NoteType.TASK] /
 *                         [NoteType.REMINDER] / [NoteType.JOURNAL]. TASK notes
 *                         may carry `dueAt`; REMINDER notes may carry
 *                         `reminderAt`; TASK notes may carry `done`.
 *   - [CheckIn]         — the WHO-5-style 1-5 rating + optional reflection.
 *                         This is where "mood" lives in MindAnchor; there is
 *                         no `Note.kind = MOOD` in the real schema. Rating
 *                         1 = "rough", 2 = "low", 3 = "ok", 4 = "good",
 *                         5 = "bright".
 *   - [WellnessLedger.Entry] — the per-signal rolling history. Signal is
 *                         one of [WellnessSignal.HRV] /
 *                         [WellnessSignal.RESTING_HEART_RATE] /
 *                         [WellnessSignal.STEPS] /
 *                         [WellnessSignal.SLEEP_MINUTES] /
 *                         [WellnessSignal.MINDFULNESS_MINUTES]. One row per
 *                         (signal, day). The launcher does not carry
 *                         EXERCISE / CALORIES / HR as separate wellness
 *                         signals — only these five.
 *   - [BpdProfile]      — the BPD self-identification flags (5 booleans).
 *
 * Assumed shapes (not in main source set — written here so the harness has
 * a single importable place to find them):
 *   - [UserProfile]     — display name, IPS batch, goal, chronotype, BPD
 *                         flag, onboarding completion.
 *   - [AppEvent]        — friction / going-light app-arrival record:
 *                         package name, opened-at epoch ms, dwell seconds,
 *                         whether the gate blocked the launch.
 *   - [Settings]        — a flat `Map<String, Any>` keyed by the actual
 *                         MindAnchor DataStore preference keys. The
 *                         `keyToDataStore` map routes each key to the
 *                         DataStore name the harness must write it to.
 */
object FixturesSchema {

    /**
     * The "now" anchor every fixture is built around: 2026-08-19 14:30 IST.
     * All note / check-in / event timestamps are offsets from this anchor
     * so the fixtures are stable across real-clock advances.
     */
    const val NOW_IST: Long = 1_787_130_000_000L

    /** The millis at which 2026-08-19 starts in IST (2026-08-18 18:30 UTC). */
    const val TODAY_IST_START: Long = 1_787_077_800_000L

    /** The millis at which 2026-08-18 starts in IST (2026-08-17 18:30 UTC). */
    const val YESTERDAY_IST_START: Long = TODAY_IST_START - 86_400_000L

    /**
     * Maps each settings-map key to the DataStore name it lives in. The
     * harness calls `prefs[storeName].edit { it[key] = value }` for each
     * entry. This is the routing table the test driver reads.
     */
    val KEY_TO_DATASTORE: Map<String, String> = mapOf(
        // Appearance
        "nature_scene" to "appearance",
        "breath_tone_enabled" to "appearance",
        "home_needs_grid_visible" to "appearance",
        // Launcher / home
        "favorites_ordered" to "launcher",
        "hidden" to "launcher",
        "renames" to "launcher",
        "one_thing" to "launcher",
        "haptics_enabled" to "launcher",
        "grayscale_enabled" to "launcher",
        "sound_enabled" to "launcher",
        "clocks_24h" to "launcher",
        // BPD profile
        "bpd_profile" to "bpd_profile",
        "bpd_long_messages" to "bpd_profile",
        "bpd_late_night" to "bpd_profile",
        "bpd_split" to "bpd_profile",
        "bpd_named_person" to "bpd_profile",
        "bpd_ok_at_night" to "bpd_profile",
        // Setup / onboarding
        "wizard_completed" to "setup",
        "user_dismissed_wizard" to "setup",
        "welcome_seen" to "setup",
        "health_connect_skipped" to "setup",
        "pair_watch_skipped" to "setup",
        "coros_skipped" to "setup",
        "ppg_skipped" to "setup",
        // Sources (logical — the source state lives in the credential stores;
        // for the harness a boolean is enough to drive the UI).
        "source_health_connect" to "sources",
        "source_polar" to "sources",
        "source_coros" to "sources",
        "source_ppg" to "sources",
        "source_baseline" to "sources",
        // Goals / chronotype
        "goal_sleep_minutes" to "goals",
        "goal_steps" to "goals",
        "goal_mindfulness_minutes" to "goals",
        "chrono_preferred_window" to "goals",
        // Friction
        "friction_enabled" to "friction",
        "friction_window_minutes" to "friction",
        "friction_allowance_seconds" to "friction",
        "friction_going_light" to "friction",
    )
}

/**
 * The assumed user profile shape. The harness writes one of these into a
 * single-row "profile" DataStore. NOT a MindAnchor class — a stable shape
 * the test surface needs.
 */
data class UserProfile(
    val displayName: String = "",
    val batch: String = "",
    val goal: String = "",
    val chronotype: String = "",
    val bpdProfileEnabled: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
)

/**
 * The assumed app-arrival event shape. The friction layer logs these to
 * drive the "your day on..." surfaces. NOT a MindAnchor class — the actual
 * friction state is encoded in [org.mindanchor.friction.SessionManager] and
 * the per-app session-length prefs; this is the row shape the harness
 * injects to exercise the UI.
 */
data class AppEvent(
    val packageName: String,
    val openedAt: Long,
    val dwellSeconds: Int,
    val blocked: Boolean,
)
