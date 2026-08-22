package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.ui.NatureScene

private val Context.dataStore by preferencesDataStore(name = "appearance")

/** Home-screen appearance. Local only, like everything else. */
class AppearancePrefs(private val context: Context) {

    private val sceneKey = stringPreferencesKey("nature_scene")
    // v0.40.0: an opt-in soft tone at every 4-7-8 phase transition.
    // Off by default — sound is the kind of thing a person with
    // hyperacusis or in a quiet room has to ask for, never have
    // thrust on them. The Breathing surface still pulses a haptic
    // on every phase change regardless (gated by the system
    // haptics toggle through [org.mindanchor.ui.HapticFeedbackGate]),
    // so the visual rhythm of the breath circle is unchanged for
    // anyone who leaves the sound off.
    private val breathToneKey = booleanPreferencesKey("breath_tone_enabled")
    // v0.58.0: an opt-in soft tone on the Notes tab
    // swipe actions. The pre-v0.58.0 swipes were
    // silent (visual only) — the v0.58.0 pass adds
    // a haptic on every successful swipe (gated by
    // the system haptics toggle through
    // [HapticFeedbackGate]) and an opt-in audio
    // cue. The audio cue is off by default for the
    // same reason as the breath tone: hyperacusis
    // and quiet rooms. When the user enables the
    // swipe tone, a pin swipe fires
    // [ToneGenerator.TONE_PROP_ACK] (a positive
    // ascending tone) and a delete swipe fires
    // [ToneGenerator.TONE_PROP_NACK] (a negative
    // descending tone). The two are system sounds,
    // so no audio assets need to ship.
    private val swipeToneKey = booleanPreferencesKey("swipe_tone_enabled")
    // v0.42.0: toggle for the "What do you need right now?" 2x2
    // grid on the home surface. Default true (current behaviour).
    // When false, the home shows only the clock, greeting, and
    // quick-notes card — a one-purpose launcher for the user who
    // never opens Support from home and uses MindAnchor as a
    // notes-first phone. The "Open Support" top-left button and
    // the support hub inside Settings still work; only the
    // 2x2 doors on the home are removed.
    private val needsGridKey = booleanPreferencesKey("home_needs_grid_visible")

    /** Defaults to a scene that changes daily. */
    val scene: Flow<NatureScene> = context.dataStore.data.map { prefs ->
        NatureScene.fromKey(prefs[sceneKey])
    }

    suspend fun setScene(scene: NatureScene) {
        context.dataStore.edit { it[sceneKey] = scene.name }
    }

    /** v0.40.0: the 4-7-8 phase tone, off by default. */
    val breathToneEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[breathToneKey] ?: false
    }

    suspend fun setBreathToneEnabled(enabled: Boolean) {
        context.dataStore.edit { it[breathToneKey] = enabled }
    }

    /**
     * v0.58.0: the Notes tab swipe tone, off by default.
     * Same opt-in pattern as [breathToneEnabled] — the
     * launcher never thrusts sound on a person with
     * hyperacusis or in a quiet room.
     */
    val swipeToneEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[swipeToneKey] ?: false
    }

    suspend fun setSwipeToneEnabled(enabled: Boolean) {
        context.dataStore.edit { it[swipeToneKey] = enabled }
    }

    /** v0.42.0: the 2x2 needs grid on the home surface. On by default. */
    val needsGridVisible: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[needsGridKey] ?: true
    }

    suspend fun setNeedsGridVisible(visible: Boolean) {
        context.dataStore.edit { it[needsGridKey] = visible }
    }
}
