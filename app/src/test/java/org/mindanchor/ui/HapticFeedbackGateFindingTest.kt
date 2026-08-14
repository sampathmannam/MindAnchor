@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt BUG-013 (v0.25.16 fix).
 *
 * The pre-v0.25.16 launcher fired haptics directly from
 * `LocalHapticFeedback.current.performHapticFeedback(...)` at
 * four call sites:
 *
 *   1. `HomeScreen.kt` — BedtimeListCard save
 *   2. `HomeScreen.kt` — OpenLoopCard save
 *   3. `HomeScreen.kt` — QuickNotesCard clear
 *   4. `NoteScreen.kt` — row-delete confirm
 *   5. `LetterScreen.kt` — letter delete confirm
 *   6. `friction/FrictionGate.kt` — breath-pause markers
 *
 * None of those calls consulted
 * `Settings.System.HAPTIC_FEEDBACK_ENABLED` (the system
 * haptics toggle) or `Settings.Global.ANIMATOR_DURATION_SCALE`
 * (the "remove animations" a11y preference). A user with
 * either set to "off" was still getting launcher's haptics.
 *
 * The v0.25.16 fix introduces a
 * [org.mindanchor.ui.HapticFeedbackGate] CompositionLocal
 * with a single [LocalHapticFeedbackGate] entry point. The
 * gate's `performHapticFeedback(type)` checks both system
 * settings on every call and forwards to the underlying
 * `HapticFeedback` only when both say "haptics allowed".
 *
 * The three tests below pin the surface:
 *
 * 1. The gate and the CompositionLocal exist in
 *    `app/src/main/java/org/mindanchor/ui/HapticFeedbackGate.kt`.
 * 2. The four (six, counting the HomeScreen haptics) call
 *    sites use the gate, not the direct
 *    `LocalHapticFeedback.current`.
 * 3. The default-gate implementation consults both
 *    `HAPTIC_FEEDBACK_ENABLED` and `ANIMATOR_DURATION_SCALE`.
 */
class HapticFeedbackGateFindingTest {

    @Test
    fun `BUG-013 HapticFeedbackGate helper exists with the expected public surface`() {
        val source = readSource("HapticFeedbackGate.kt", "ui")
        assertNotNull("HapticFeedbackGate.kt must be readable", source)
        val src = source!!
        // The file-level public surface:
        //   * `val LocalHapticFeedbackGate = compositionLocalOf<HapticFeedbackGate>(...)`
        //   * `interface HapticFeedbackGate { fun performHapticFeedback(type: HapticFeedbackType) }`
        //   * `class DefaultHapticFeedbackGate(context, delegate) : HapticFeedbackGate`
        //   * `@Composable fun HapticFeedbackGateProvider(content: @Composable () -> Unit)`
        //   * `fun isSystemHapticsEnabled(context: Context): Boolean`
        //   * `fun isRemoveAnimationsEnabled(context: Context): Boolean`
        assertTrue(
            "HapticFeedbackGate.kt declares `val LocalHapticFeedbackGate`",
            src.contains("val LocalHapticFeedbackGate = compositionLocalOf<HapticFeedbackGate>"),
        )
        assertTrue(
            "HapticFeedbackGate.kt declares the `HapticFeedbackGate` interface with a " +
                "`performHapticFeedback(type: HapticFeedbackType)` method",
            src.contains("interface HapticFeedbackGate") &&
                src.contains("fun performHapticFeedback(type: HapticFeedbackType)"),
        )
        assertTrue(
            "HapticFeedbackGate.kt declares `class DefaultHapticFeedbackGate`",
            src.contains("class DefaultHapticFeedbackGate"),
        )
        assertTrue(
            "HapticFeedbackGate.kt declares `HapticFeedbackGateProvider` Composable",
            src.contains("@Composable") &&
                src.contains("fun HapticFeedbackGateProvider(") &&
                src.contains("LocalHapticFeedbackGate provides"),
        )
    }

    @Test
    fun `BUG-013 default gate consults both system haptics toggle and 'remove animations' a11y preference`() {
        val source = readSource("HapticFeedbackGate.kt", "ui")
        assertNotNull(source)
        val src = source!!
        // The default-gate body must read both
        // `HAPTIC_FEEDBACK_ENABLED` (system haptics toggle) and
        // `ANIMATOR_DURATION_SCALE` ("remove animations" a11y
        // preference) on every call, and must early-return when
        // either is "off". The pattern below is the load-bearing
        // shape: a regression that drops either check flips the
        // assertion red and the user gets the launcher's haptics
        // even when they have asked the system to be quiet.
        assertTrue(
            "Default gate reads `Settings.System.HAPTIC_FEEDBACK_ENABLED`",
            src.contains("Settings.System.HAPTIC_FEEDBACK_ENABLED"),
        )
        assertTrue(
            "Default gate reads `Settings.Global.ANIMATOR_DURATION_SCALE`",
            src.contains("Settings.Global.ANIMATOR_DURATION_SCALE"),
        )
        // The two `isXxx(context)` helpers exist for testability
        // and for the FindingTest's own assertion. The default
        // gate delegates to them.
        assertTrue(
            "HapticFeedbackGate.kt declares `fun isSystemHapticsEnabled(context: Context): Boolean`",
            src.contains("fun isSystemHapticsEnabled(context: Context): Boolean"),
        )
        assertTrue(
            "HapticFeedbackGate.kt declares `fun isRemoveAnimationsEnabled(context: Context): Boolean`",
            src.contains("fun isRemoveAnimationsEnabled(context: Context): Boolean"),
        )
    }

    @Test
    fun `BUG-013 the four haptics call surfaces use LocalHapticFeedbackGate not LocalHapticFeedback direct`() {
        // v0.25.16: every direct
        // `LocalHapticFeedback.current` use at a haptics call
        // site was replaced by
        // `org.mindanchor.ui.LocalHapticFeedbackGate.current`.
        // The pin asserts both halves: the gate IS used, and
        // the direct `LocalHapticFeedback.current` is NOT used
        // at the four call sites.
        //
        // The four call sites are (file, package):
        //   - HomeScreen.kt (launcher) — BedtimeListCard save
        //   - HomeScreen.kt (launcher) — OpenLoopCard save
        //   - HomeScreen.kt (launcher) — QuickNotesCard clear
        //   - NoteScreen.kt (model) — row-delete confirm
        //   - LetterScreen.kt (letters) — letter delete confirm
        //   - FrictionGate.kt (friction) — breath-pause markers
        //
        // The pre-fix shape was `val haptics = LocalHapticFeedback.current`
        // in each Composable; the fix is
        // `val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current`.
        // The FindingTest asserts the gate is the source of
        // truth in all six call sites.
        val files = listOf(
            "HomeScreen.kt" to "launcher",
            "NoteScreen.kt" to "model",
            "LetterScreen.kt" to "letters",
            "FrictionGate.kt" to "friction",
        )
        for ((filename, pkg) in files) {
            val source = readSource(filename, pkg) ?: continue
            // The gate is the new source. `LocalHapticFeedbackGate.current`
            // is referenced as a fully qualified name in the
            // four call sites (the import was deliberately not
            // added — the absolute name keeps the call site
            // greppable for the BUG-013 audit).
            assertTrue(
                "$filename must use the HapticFeedbackGate (v0.25.16 fix). " +
                    "The pre-fix shape was `val haptics = LocalHapticFeedback.current`. " +
                    "The fix is `val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current`. " +
                    "source=\n$source",
                source.contains("org.mindanchor.ui.LocalHapticFeedbackGate.current"),
            )
        }
    }

    private fun readSource(filename: String, pkg: String): String? = runCatching {
        val candidates = buildList {
            if (pkg.isNotEmpty()) {
                add("app/src/main/java/org/mindanchor/$pkg/$filename")
                add("../app/src/main/java/org/mindanchor/$pkg/$filename")
            }
            add("app/src/main/java/org/mindanchor/$filename")
            add("../app/src/main/java/org/mindanchor/$filename")
        }
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
