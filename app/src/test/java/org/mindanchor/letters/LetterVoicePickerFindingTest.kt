package org.mindanchor.letters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.llm.LetterVoice

/**
 * v0.72.x: locks in the contract the LLM section of the
 * Reading tab now depends on. The voice picker is the
 * single source of the system prompt the LLM receives;
 * any change to its contract (default voice, voice set,
 * display names) is a behaviour change for every letter
 * the launcher writes.
 *
 * The test reads the source rather than instantiating
 * the picker — the picker is a Composable, the
 * source-of-truth here is the enum. Reading the
 * source is the cheapest way to assert the contract
 * without a Compose runtime.
 */
class LetterVoicePickerFindingTest {

    private val llmSettingsViewModel = java.io.File(
        "../app/src/main/java/org/mindanchor/settings/LlmSettingsViewModel.kt"
    )

    @Test
    fun `every LetterVoice value has a non-blank display name and sample`() {
        // The picker renders the display name in the chip
        // and the sample in the preview dialog. Either
        // being blank is a user-visible bug.
        for (voice in LetterVoice.values()) {
            assertTrue(
                "${voice.name} needs a non-blank displayName",
                voice.displayName.isNotBlank(),
            )
            assertTrue(
                "${voice.name} needs a non-blank sample",
                voice.sample.isNotBlank(),
            )
        }
    }

    @Test
    fun `every LetterVoice value has a distinct displayName`() {
        // Two voices with the same chip label is a UI
        // collision. The picker is keyed on displayName
        // (the test seam in [LetterVoicePicker] is a chip
        // row, not an enum iteration), so a duplicate
        // would render as one merged chip.
        val names = LetterVoice.values().map { it.displayName }
        assertEquals(
            "voice displayNames must be unique",
            names.size,
            names.toSet().size,
        )
    }

    @Test
    fun `LetterVoice DEFAULT is the one the picker shows as initially selected`() {
        // LlmSettingsViewModel exposes `voice: StateFlow<LetterVoice>`
        // with initialValue = LetterVoice.DEFAULT. If a
        // future contributor changes DEFAULT but forgets
        // the picker, the user will see a voice different
        // from what the system-prompt contract implies.
        // Lock the contract.
        val src = llmSettingsViewModel.readText()
        assertTrue(
            "LlmSettingsViewModel must initialise voice to LetterVoice.DEFAULT",
            src.contains("initialValue = org.mindanchor.llm.LetterVoice.DEFAULT") ||
                src.contains("initialValue = LetterVoice.DEFAULT"),
        )
    }

    @Test
    fun `Insight is the default voice, not Quiet`() {
        // v0.72.x: the user asked for the Insight voice
        // (the psychology-aware one) to be the new-user
        // default. The Quiet voice is still selectable
        // from the picker, but a fresh user without a
        // persisted choice gets Insight. This test pins
        // the choice so a future "let's make Quiet the
        // default again" change is a deliberate commit
        // message, not a regression.
        assertEquals(
            "LetterVoice.DEFAULT should be Insight for new users",
            LetterVoice.INSIGHT,
            LetterVoice.DEFAULT,
        )
    }
}
