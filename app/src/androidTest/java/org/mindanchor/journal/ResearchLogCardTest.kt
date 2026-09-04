package org.mindanchor.journal

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mindanchor.research.LedgerChain
import org.mindanchor.research.LedgerEventKind
import org.mindanchor.research.MAX_LEDGER_NOTE_LENGTH
import org.mindanchor.research.ResearchLedgerEvent
import org.mindanchor.research.UnlinkedLedgerEvent

/**
 * Program 1 Task 12 — the research log card.
 *
 * The assertions that matter most are the negative ones: no edit control,
 * no delete control, no chip for anything MindAnchor records about itself,
 * and a medication change that says in plain words it is only being
 * written down.
 */
class ResearchLogCardTest {

    @get:Rule
    val compose = createComposeRule()

    private fun event(kind: LedgerEventKind, note: String, sequence: Long = 1L): ResearchLedgerEvent =
        LedgerChain.link(
            UnlinkedLedgerEvent(
                sequence = sequence,
                kind = kind,
                occurredAt = 1_000L,
                recordedAt = 1_000L,
                localDate = "2026-08-29",
                studyPhaseId = "phase-0",
                sourceDeviceId = "device-a",
                note = note,
                payloadJson = "{}",
            ),
            LedgerChain.GENESIS_PREVIOUS_HASH,
        )

    private fun render(
        todaysEvents: List<ResearchLedgerEvent> = emptyList(),
        recordError: Boolean = false,
        recorded: MutableList<Pair<LedgerEventKind, String>> = mutableListOf(),
    ): MutableList<Pair<LedgerEventKind, String>> {
        compose.setContent {
            ResearchLogCard(
                todaysEvents = todaysEvents,
                onRecord = { kind, note -> recorded += kind to note },
                recordError = recordError,
            )
        }
        return recorded
    }

    @Test
    fun everySelfReportedKindHasAChipAndNoOtherKindDoes() {
        render()
        LedgerEventKind.entries.forEach { kind ->
            val chip = compose.onAllNodesWithTag("research_log_chip_${kind.name}")
            if (kind.isSelfReported) {
                assertTrue(
                    "every self-reported kind needs a chip: $kind",
                    chip.fetchSemanticsNodes().isNotEmpty(),
                )
            } else {
                assertEquals(
                    "MindAnchor records $kind about itself; a person must not be offered a chip for it",
                    0,
                    chip.fetchSemanticsNodes().size,
                )
            }
        }
    }

    @Test
    fun recordingAnEventPassesTheKindAndTheTypedNote() {
        val recorded = render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.EXERCISE.name}").performClick()
        compose.onNodeWithTag("research_log_note_field").performTextInput("a walk before the rain")
        compose.onNodeWithTag("research_log_save").performClick()

        assertEquals(listOf(LedgerEventKind.EXERCISE to "a walk before the rain"), recorded)
    }

    @Test
    fun cancellingRecordsNothing() {
        val recorded = render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.ILLNESS.name}").performClick()
        compose.onNodeWithTag("research_log_note_field").performTextInput("a sore throat")
        compose.onNodeWithTag("research_log_cancel").performClick()

        assertEquals(emptyList<Pair<LedgerEventKind, String>>(), recorded)
    }

    @Test
    fun anEmptyNoteIsAllowed() {
        val recorded = render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.CAFFEINE.name}").performClick()
        compose.onNodeWithTag("research_log_save").performClick()

        assertEquals(listOf(LedgerEventKind.CAFFEINE to ""), recorded)
    }

    @Test
    fun aMedicationChangeSaysItIsOnlyBeingWrittenDown() {
        render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.MEDICATION_CHANGE.name}").performClick()
        compose.onNodeWithTag("research_log_medication_notice").assertExists()
        compose.onNodeWithText("It does not give medication advice.", substring = true).assertExists()
    }

    @Test
    fun anotherKindCarriesNoMedicationNotice() {
        render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.EXERCISE.name}").performClick()
        assertEquals(
            0,
            compose.onAllNodesWithTag("research_log_medication_notice").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun anOverLongNoteCannotBeRecorded() {
        render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.LIFE_EVENT.name}").performClick()
        compose.onNodeWithTag("research_log_note_field").performTextInput("x".repeat(MAX_LEDGER_NOTE_LENGTH + 1))
        compose.onNodeWithTag("research_log_save").assertIsNotEnabled()
    }

    @Test
    fun trailingWhitespaceCountsTowardTheVerbatimNoteLimit() {
        render()
        compose.onNodeWithTag("research_log_chip_${LedgerEventKind.LIFE_EVENT.name}").performClick()
        compose.onNodeWithTag("research_log_note_field").performTextInput(
            "x".repeat(MAX_LEDGER_NOTE_LENGTH) + " ",
        )

        compose.onNodeWithTag("research_log_save").assertIsNotEnabled()
        compose.onNodeWithText("Too long by 1", substring = true).assertExists()
    }

    @Test
    fun todaysRecordsAreShownNewestFirstAndCannotBeChanged() {
        render(
            todaysEvents = listOf(
                event(LedgerEventKind.EXERCISE, "a walk", sequence = 1L),
                event(LedgerEventKind.ILLNESS, "a sore throat", sequence = 2L),
            ),
        )
        compose.onNodeWithTag("research_log_today").assertExists()
        compose.onNodeWithText("Exercise — a walk").assertExists()
        compose.onNodeWithText("Illness — a sore throat").assertExists()
        // Newest first: the later sequence must render above the earlier
        // one. Without this the test's name claimed an ordering nothing
        // checked.
        val illness = compose.onNodeWithText("Illness — a sore throat")
            .fetchSemanticsNode().positionInRoot.y
        val exercise = compose.onNodeWithText("Exercise — a walk")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("the newer record must appear above the older one", illness < exercise)
        // The rows are append-only in the database. Offering an edit or a
        // delete would be an affordance that cannot work.
        assertEquals(0, compose.onAllNodesWithTag("research_log_edit").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithTag("research_log_delete").fetchSemanticsNodes().size)
    }

    @Test
    fun aFailedRecordIsSurfaced() {
        render(recordError = true)
        compose.onNodeWithTag("research_log_error").assertExists()
    }

    @Test
    fun noFailureMeansNoErrorLine() {
        render()
        assertEquals(0, compose.onAllNodesWithTag("research_log_error").fetchSemanticsNodes().size)
    }

    @Test
    fun everyChipIsReadableByAScreenReader() {
        render()
        // One description per chip, and every self-reported kind has one:
        // a row of unlabelled chips is unusable with a screen reader.
        assertEquals(
            LedgerEventKind.entries.count { it.isSelfReported },
            compose.onAllNodesWithContentDescription("Record ", substring = true).fetchSemanticsNodes().size,
        )
        compose.onNodeWithContentDescription("Record Medication change").assertExists()
    }
}
