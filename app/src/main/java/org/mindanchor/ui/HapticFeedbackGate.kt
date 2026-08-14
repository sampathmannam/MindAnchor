package org.mindanchor.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

/**
 * v0.25.16 BUG-013: a CompositionLocal wrapper around
 * [HapticFeedback] that consults the user's accessibility /
 * system-toggle preferences before firing a haptic.
 *
 * Direct `LocalHapticFeedback.current.performHapticFeedback(...)`
 * calls bypass the system haptics toggle and the
 * "remove animations" a11y preference. A user with
 * `Settings.System.HAPTIC_FEEDBACK_ENABLED == 0` (the global
 * haptics-off toggle) or with `Settings.Global.ANIMATOR_DURATION_SCALE
 * == 0` (the "remove animations" a11y preference) still gets
 * haptics fired by the launcher. That is information-poor and
 * counter to the documented Compose / Android accessibility
 * contract (the system "haptics off" toggle is supposed to be
 * the single switch that turns all haptics off).
 *
 * The default value of the CompositionLocal is
 * [DefaultHapticFeedbackGate]: a gate that consults both
 * system settings every call (no caching) and only forwards
 * the haptic to the underlying [HapticFeedback] when both
 * settings are in the "haptics allowed" state.
 *
 * Tests: this file is the source of truth for the BUG-013
 * FindingTest ([HapticFeedbackGateFindingTest]).
 */
val LocalHapticFeedbackGate = compositionLocalOf<HapticFeedbackGate> {
    error("LocalHapticFeedbackGate has no default — wrap a Composable in HapticFeedbackGateProvider")
}

/**
 * The shape the launcher uses. [performHapticFeedback] is the
 * only method the four call sites need; a no-op gate is the
 * "always allow" default and the test-bench is the "always
 * deny" gate that the FindingTest substitutes in to assert
 * that the four surfaces do not call the underlying
 * [HapticFeedback] when the gate is closed.
 */
@Immutable
interface HapticFeedbackGate {
    /**
     * Forwards to the underlying [HapticFeedback] when the
     * system haptics toggle and the "remove animations" a11y
     * preference both say "haptics allowed". A no-op
     * otherwise. The [type] is the standard Compose
     * [HapticFeedbackType].
     */
    fun performHapticFeedback(type: HapticFeedbackType)
}

/**
 * The default gate: reads
 * [Settings.System.HAPTIC_FEEDBACK_ENABLED] and
 * [Settings.Global.ANIMATOR_DURATION_SCALE] from the supplied
 * [Context.contentResolver] on every call and forwards the
 * haptic only when both are in the "haptics allowed" state.
 *
 * Both reads are cheap (ContentResolver calls into a
 * system-managed table) and the call is rare (the four
 * call sites are save / clear / delete-confirm / breath-
 * pause markers — not a hot path), so no caching is
 * needed. A user who toggles haptics off in system
 * settings sees the change on the next haptic the launcher
 * fires, with no Compose-side invalidation.
 */
class DefaultHapticFeedbackGate(
    private val context: Context,
    private val delegate: HapticFeedback,
) : HapticFeedbackGate {

    override fun performHapticFeedback(type: HapticFeedbackType) {
        if (!isSystemHapticsEnabled(context)) return
        if (isRemoveAnimationsEnabled(context)) return
        delegate.performHapticFeedback(type)
    }
}

/**
 * Reads [Settings.System.HAPTIC_FEEDBACK_ENABLED]. Returns
 * `true` when the system haptics toggle is on (the default),
 * `false` when the user has turned haptics off in
 * `Settings → Sound & vibration → Touch feedback`.
 *
 * The default value is `1` (haptics allowed) per the
 * documented Android contract: a `getInt` that returns a
 * missing key (e.g. on a stripped-down ROM) is treated as
 * the user-default state. Some accessibility audits name
 * this exact "1" sentinel; the AndroidX tests cover the
 * same shape.
 */
fun isSystemHapticsEnabled(context: Context): Boolean =
    Settings.System.getInt(
        context.contentResolver,
        Settings.System.HAPTIC_FEEDBACK_ENABLED,
        1,
    ) == 1

/**
 * Reads [Settings.Global.ANIMATOR_DURATION_SCALE]. Returns
 * `true` when the user has asked the system to remove
 * animations (the "remove animations" a11y preference),
 * `false` otherwise.
 *
 * The documented contract: `0f` means "no animations" and
 * any other value (including the default `1f`) means
 * "animations allowed". The launcher is in the "no
 * animations" group (the calm launcher does not animate
 * the home clock, the breath, or the app drawer) and the
 * haptics side of the contract is that a user who wants
 * "no animations" also gets no haptics — the two are
 * coupled in the user's mental model.
 */
fun isRemoveAnimationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f

/**
 * The provider Composable. Wrap the launcher root in this
 * exactly once (in [org.mindanchor.HomeActivity] — the
 * only entry point that mounts [org.mindanchor.launcher.LauncherRoot]).
 * The provider reads the system haptics state on first
 * composition and exposes a stable [HapticFeedbackGate] to
 * descendants via [LocalHapticFeedbackGate].
 *
 * Wrap the test surface in a no-op / deny gate to assert
 * that the four call sites do not bypass the gate.
 *
 * `FunctionNaming` is suppressed: the PascalCase name
 * (`HapticFeedbackGateProvider`) is the documented Compose
 * convention for public Providers and is consistent with
 * `MindAnchorTheme`, `HapticFeedbackGateProvider`, and the
 * other top-level Composable functions in this codebase.
 */
@Suppress("FunctionNaming")
@Composable
fun HapticFeedbackGateProvider(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val delegate = androidx.compose.ui.platform.LocalHapticFeedback.current
    val gate = remember(context, delegate) {
        DefaultHapticFeedbackGate(context, delegate)
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalHapticFeedbackGate provides gate,
        content = content,
    )
}
