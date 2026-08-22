@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.2: the letter time is configurable in Settings.
 *
 * The Settings screen's Daily letter section renders a
 * `TextButton` showing the current `lettersTime`; tapping it
 * opens a `LetterTimePickerDialog` (a Material 3 `TimePicker`
 * in 24-hour mode); confirming the dialog calls
 * `viewModel.setLettersTime(hour, minute)`, which writes to
 * the same [LetterStore.setTime] the AlarmManager reads.
 *
 * v0.26.2 also changed the default from 08:00 to 07:00; a
 * fresh install on this version should see 07:00, not 08:00.
 *
 * The FindingTest pins the file-shape so a future refactor
 * that drops the dialog, the writer, or the default-time
 * change flips the test red.
 */
class LetterTimeConfigurableFindingTest {

    private val settings: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    private val store: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterStore.kt",
        ).readText()

    @Test fun `LetterTimePickerDialog uses a Material 3 TimePicker (24 hour)`() {
        // The dialog is the user-facing affordance. Pin that
        // the picker is `androidx.compose.material3.TimePicker`
        // (not a custom `TimePickerDialog` or a third-party
        // widget) and that it is in 24-hour mode (a user who
        // picked "07:00" in the toggle row should not have to
        // translate AM/PM in the picker — same posture as the
        // sunset hours and the bedtime nudgers).
        assertTrue(
            "SettingsScreen must define a LetterTimePickerDialog using TimePicker (Material 3)",
            settings.contains("LetterTimePickerDialog") && settings.contains("TimePicker(state = state)"),
        )
        assertTrue(
            "LetterTimePickerDialog must use rememberTimePickerState in 24-hour mode",
            Regex(
                """rememberTimePickerState\([\s\S]*?is24Hour\s*=\s*true""",
            ).containsMatchIn(settings),
        )
    }

    @Test fun `SettingsScreen wires the dialog confirm to setLettersTime`() {
        // The confirm button hands `state.hour` / `state.minute`
        // to `viewModel.setLettersTime(hour, minute)`. A future
        // refactor that wires the wrong writer (e.g. `setTime`
        // on a stale viewmodel field) would silently break
        // AlarmManager reschedule, so pin the call.
        val onConfirmIdx = settings.indexOf("onConfirm = { hour, minute ->")
        assertTrue(
            "LetterTimePickerDialog.onConfirm must be the two-int lambda: ${onConfirmIdx}",
            onConfirmIdx >= 0,
        )
        val slice = settings.substring(onConfirmIdx, minOf(onConfirmIdx + 400, settings.length))
        assertTrue(
            "LetterTimePickerDialog.onConfirm must call viewModel.setLettersTime(hour, minute)",
            slice.contains("viewModel.setLettersTime(hour, minute)"),
        )
    }

    @Test fun `SettingsScreen has a letters-time button that opens the dialog`() {
        // The Settings screen's Daily letter section must have
        // a button that toggles `showLetterTimePicker = true`.
        // The button is the entry point — without it, the
        // dialog is unreachable and the time is effectively
        // hard-coded.
        val buttonIdx = settings.indexOf("showLetterTimePicker = true")
        assertTrue(
            "SettingsScreen must have a button that opens the letter-time dialog (showLetterTimePicker = true)",
            buttonIdx >= 0,
        )
    }

    @Test fun `LetterStore default time is 07 00 (v0_26_2 change)`() {
        // v0.26.2 changed the default from 08:00 to 07:00 —
        // the same default the empty-state copy says the user
        // will see ("…the first one will land here at 7 am.").
        // A regression back to 08:00 would split the user-
        // visible copy and the actual default.
        assertTrue(
            "LetterStore.DEFAULT_TIME must be \"07:00\" (v0.26.2)",
            store.contains("const val DEFAULT_TIME = \"07:00\""),
        )
        assertTrue(
            "LetterStore.DEFAULT_HOUR must be 7 (v0.26.2)",
            store.contains("const val DEFAULT_HOUR = 7"),
        )
    }

    @Test fun `LetterStore has a setTime writer (the dialog writes to it)`() {
        // The viewModel's `setLettersTime` is a pass-through to
        // `letterStore.setTime(hour, minute)`. The shape is
        // the contract the picker relies on; a future refactor
        // that switches the storage to `BpdProfilePrefs` (a
        // route the v0.26.2 task briefly considered) must
        // keep this method as the public surface.
        assertTrue(
            "LetterStore must have a `suspend fun setTime(hour: Int, minute: Int)`",
            store.contains("suspend fun setTime(hour: Int, minute: Int)"),
        )
    }
}
