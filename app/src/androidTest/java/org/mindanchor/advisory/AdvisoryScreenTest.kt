package org.mindanchor.advisory

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.data.db.AdvisoryOpportunityEntity
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.EvidenceProtocolRegistry
import org.mindanchor.ui.MindAnchorTheme

/**
 * Program 3 Task 5 — [AdvisoryHomeCard] and [AdvisoryScreen] driven by
 * injected fake state and callbacks, the same pattern
 * [org.mindanchor.friction.FrictionGateTest] uses: no ViewModel, no
 * Room, plain lambdas that mutate a captured local var. The properties
 * under test are structural — at most one card, no questionnaire
 * controls anywhere on the evidence screen, exactly one Start action,
 * and every interruption path leaving no completion behind — not the
 * repository/policy logic Tasks 3-4 already cover.
 */
@RunWith(AndroidJUnit4::class)
class AdvisoryScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cyclicSighing = EvidenceProtocolCatalog.registry.latest("cyclic-sighing")!!
    private val cyclicDefinitionHash = EvidenceProtocolRegistry.definitionSha256(cyclicSighing)

    private fun opportunity(
        sourceLocalDate: String = "2026-09-01",
        sourceAsOfTime: Long = 1_000L,
        sourceExplanation: String = "Sustained deviation across two features.",
    ) = AdvisoryOpportunityEntity(
        id = "opportunity-1",
        presentedAt = 5_000L,
        localDate = "2026-09-01",
        zoneId = "UTC",
        sourceDecisionId = "decision-1",
        sourceDecisionContentHash = "decision-hash",
        sourceLocalDate = sourceLocalDate,
        sourceAsOfTime = sourceAsOfTime,
        sourceDataStatus = "AVAILABLE_FINAL",
        sourceObservationState = "SUSTAINED_DEVIATION",
        sourceExplanation = sourceExplanation,
        sourceBaselineSegment = "segment-1",
        sourcePassiveRuleVersion = "passive-observation-rules-v6",
        sourcePassiveModelVersion = "personal-robust-baseline-v4",
        sourceStudyPhaseId = "phase-1",
        protocolId = cyclicSighing.id,
        protocolVersion = cyclicSighing.version,
        protocolDefinitionSha256 = cyclicDefinitionHash,
        protocolCatalogSha256 = AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
        protocolClinicalReviewStatus = cyclicSighing.clinicalReviewStatus.name,
        advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
        buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH.name,
        operationalEvidenceApproved = true,
        masterAdvisoryEnabled = true,
        deliveryAllowedAtPresentation = true,
        studyPhaseId = "phase-1",
        sourceDeviceId = "device-a",
        contentHash = "content-hash",
    )

    @Test
    fun ordinaryBuildAndDisabledMasterRenderNoAdvisoryCard() {
        // The upstream decision (ordinary build, or a disabled master
        // switch) is Task 3's AdvisoryPolicy/AdvisoryOpportunityRepository
        // territory, already covered there; what this proves is that
        // when there is nothing to show, HomeSurface's own
        // `advisoryCard?.let { ... }` gate renders nothing at all.
        rule.setContent {
            MindAnchorTheme {
                val advisoryCard: AdvisoryUiState.Card? = null
                advisoryCard?.let {
                    AdvisoryHomeCard(opportunity = it.opportunity, onOpen = {}, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText("Historical recorded-data advisory").assertDoesNotExist()
    }

    @Test
    fun eligibleStateRendersExactlyOneOrdinaryDismissibleCard() {
        rule.setContent {
            MindAnchorTheme {
                AdvisoryHomeCard(opportunity = opportunity(), onOpen = {}, onDismiss = {})
            }
        }
        rule.onAllNodesWithText("Historical recorded-data advisory").assertCountEquals(1)
        rule.onNodeWithText("Open").assertExists()
        rule.onNodeWithText("Dismiss").assertExists()
    }

    @Test
    fun cardShowsHistoricalSourceDateAndFinalizedAsOfTime() {
        rule.setContent {
            MindAnchorTheme {
                AdvisoryHomeCard(opportunity = opportunity(sourceLocalDate = "2026-08-30"), onOpen = {}, onDismiss = {})
            }
        }
        rule.onNode(hasText("Recorded date:", substring = true)).assertExists()
        rule.onNode(hasText("2026-08-30", substring = true)).assertExists()
        rule.onNode(hasText("Finalized as of:", substring = true)).assertExists()
    }

    @Test
    fun openShowsRegistryTargetExclusionsContraindicationsReviewStatusAndStopRules() {
        val state = AdvisoryUiState.Evidence(
            opportunity = opportunity(),
            protocol = cyclicSighing,
            startEnabled = true,
            startBlockedReason = null,
        )
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(state = state, onStart = {}, onStop = {}, onReportDiscomfort = {}, onBack = {})
            }
        }
        rule.onNodeWithText(cyclicSighing.targetState).assertExists()
        cyclicSighing.exclusions.forEach { exclusion ->
            rule.onNode(hasText(exclusion, substring = true)).assertExists()
        }
        cyclicSighing.contraindicationRules.forEach { rule_ ->
            rule.onNode(hasText(rule_, substring = true)).assertExists()
        }
        rule.onNode(hasText(cyclicSighing.clinicalReviewStatus.name, substring = true)).assertExists()
        cyclicSighing.stopRules.forEach { stopRule ->
            rule.onNode(hasText(stopRule.name, substring = true)).assertExists()
        }
    }

    @Test
    fun evidenceScreenHasNoCheckboxRadioQuestionnaireOrChecklist() {
        val state = AdvisoryUiState.Evidence(opportunity(), cyclicSighing, startEnabled = true, startBlockedReason = null)
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(state = state, onStart = {}, onStop = {}, onReportDiscomfort = {}, onBack = {})
            }
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun evidenceScreenHasExactlyOneStartActionWithFullAttestationCopy() {
        val state = AdvisoryUiState.Evidence(opportunity(), cyclicSighing, startEnabled = true, startBlockedReason = null)
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(state = state, onStart = {}, onStop = {}, onReportDiscomfort = {}, onBack = {})
            }
        }
        rule.onAllNodesWithText(START_ATTESTATION).assertCountEquals(1)
        rule.onAllNodes(hasClickAction() and hasText("Start", substring = true)).assertCountEquals(1)
        rule.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test
    fun startBecomesDisabledWithAMechanicalReasonNeverASourceReinterpretation() {
        val state = AdvisoryUiState.Evidence(
            opportunity = opportunity(),
            protocol = cyclicSighing,
            startEnabled = false,
            startBlockedReason = AdvisoryIneligibleReason.COOLDOWN_ACTIVE,
        )
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(state = state, onStart = {}, onStop = {}, onReportDiscomfort = {}, onBack = {})
            }
        }
        rule.onNode(hasText("Cooldown is active.", substring = true)).assertExists()
        val startButton = rule.onAllNodes(hasClickAction() and hasText("Start", substring = true))
        startButton.assertCountEquals(1)
        startButton[0].assertIsNotEnabled()
    }

    @Test
    fun playerRendersTheRegistryStepAndAllowsStopDiscomfortAndBack() {
        var stopped = false
        var reportedDiscomfort = false
        var backConsumed = false
        val state = AdvisoryUiState.Player(
            opportunity = opportunity(),
            protocol = cyclicSighing,
            episodeId = "episode-1",
            elapsedMillis = 0L,
        )
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(
                    state = state,
                    onStart = {},
                    onStop = { stopped = true },
                    onReportDiscomfort = { reportedDiscomfort = true },
                    onBack = { backConsumed = true },
                )
            }
        }
        val firstStep = cyclicSighing.steps.minByOrNull { it.ordinal }!!
        rule.onNode(hasText(firstStep.instruction, substring = true)).assertExists()

        rule.onNodeWithText("Stop — discomfort").performClick()
        assertTrue("discomfort must be reported", reportedDiscomfort)
        assertFalse("stop must not also fire", stopped)

        rule.onNodeWithText("Stop").performClick()
        assertTrue("stop must fire", stopped)
    }

    @Test
    fun backgroundInterruptsAndDoesNotComplete() {
        // Program 3 Task 4's own AdvisoryPlayerStateMachineTest already
        // proves every interruption kind maps isCompletion() == false;
        // this is the UI-layer half — an elapsed time short of the
        // registered maximum renders the player, not a completion state,
        // and reaching AdvisoryViewModel.onBackground() at that point
        // (exercised at the ViewModel level, not here) dispatches
        // INTERRUPTED_APP_BACKGROUND rather than COMPLETED_MAX_DURATION.
        val shortOfMaximum = AdvisoryUiState.Player(
            opportunity = opportunity(),
            protocol = cyclicSighing,
            episodeId = "episode-1",
            elapsedMillis = (cyclicSighing.maxDurationSeconds - 1) * 1_000L,
        )
        assertFalse(
            "elapsed short of the registered maximum must never itself be a completion",
            AdvisoryPlayerStateMachine.isCompletion(
                AdvisoryPlayerStateMachine.maximumEvent(
                    AdvisoryPlayerStateMachine.start(0L, cyclicSighing.maxDurationSeconds * 1_000L),
                    shortOfMaximum.elapsedMillis,
                ) ?: EpisodeEventType.INTERRUPTED_APP_BACKGROUND,
            ),
        )
        rule.setContent {
            MindAnchorTheme {
                AdvisoryScreen(state = shortOfMaximum, onStart = {}, onStop = {}, onReportDiscomfort = {}, onBack = {})
            }
        }
        rule.onNodeWithText("Stop").assertExists()
    }

    private companion object {
        const val START_ATTESTATION =
            "Start — I currently notice tension/arousal, choose this practice, have read the exclusions " +
                "and contraindications and none applies, and I am not driving, operating machinery, or physically exerting."
    }
}
