package org.mindanchor.grayscale

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v0.72.x: the daltonizer secure settings
 * (`accessibility_display_daltonizer`,
 * `accessibility_display_daltonizer_enabled`) were the
 * only public API for system-wide colour desaturation
 * before Android 14. The two settings are no longer
 * surfaced in the Android 14 SDK and the system no
 * longer applies them to third-party apps that are not
 * accessibility services — see
 * [Grayscale.set] for the timeline.
 *
 * The daltonizer write in [Grayscale.set] is still
 * attempted (older devices respect it), and the toggle
 * state on the settings screen still tracks that write
 * via [Grayscale.isOn]. But on a phone where the
 * secure-settings write no longer changes the display,
 * the *only* greyscale the user can actually see is the
 * in-app one — a saturation-0 [androidx.compose.ui.graphics.ColorMatrix]
 * applied to MindAnchor's own windows. This singleton
 * is the broadcast channel between the settings / alarm
 * paths that decide "grey should be on" and the
 * launcher composables that decide "draw a grey layer
 * over the root".
 *
 * The flow is application-scoped. Updates from any
 * [Grayscale.set] caller are visible to every
 * collector; this is the right shape because there is
 * only ever one "is the launcher currently in the
 * greyscale window" answer at a time, and the sunset
 * alarm and the settings toggle are the two writers
 * that need to agree on it.
 */
object GrayscaleState {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    /** Called by [Grayscale.set] after the secure-settings write. */
    internal fun set(value: Boolean) {
        _on.value = value
    }
}
