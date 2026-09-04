package org.mindanchor.advisory

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.intelligence.PassiveDataStatus
import org.mindanchor.intelligence.PassiveObservationState
import org.mindanchor.research.ClinicalReviewStatus
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.EvidenceProtocolRegistry

/**
 * Program 3 Task 3 — the eligibility policy is a pure function, so every
 * gate is provable by changing exactly one field of an otherwise-eligible
 * input. The table below is the enforcement of "every independent gate":
 * a rejection reason that isn't reachable this way is a gate the policy
 * doesn't actually implement, whatever the code looks like.
 */
class AdvisoryPolicyTest {

    private val cyclicSighing = EvidenceProtocolCatalog.registry.latest("cyclic-sighing")!!
    private val cyclicDefinitionHash = EvidenceProtocolRegistry.definitionSha256(cyclicSighing)
    private val cyclicKey = ProtocolKey(cyclicSighing.id, cyclicSighing.version, cyclicDefinitionHash)

    private fun personalAuthorization() = AdvisoryBuildAuthorization.forFlags(
        personalResearchBuild = true,
        operationalEvidenceApproved = true,
    )

    private fun ordinaryAuthorization() = AdvisoryBuildAuthorization.forFlags(
        personalResearchBuild = false,
        operationalEvidenceApproved = false,
    )

    private fun source() = AdvisorySource(
        decisionId = "decision-1",
        decisionContentHash = "decision-hash",
        localDate = "2026-09-02",
        asOfTime = 1_000L,
        dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
        observationState = PassiveObservationState.SUSTAINED_DEVIATION,
        explanation = "explanation",
        baselineSegment = "segment-1",
        passiveRuleVersion = "passive-observation-rules-v6",
        passiveModelVersion = "personal-robust-baseline-v4",
        sourceStudyPhaseId = "phase-1",
        sourceDeviceId = "device-a",
    )

    private fun eligibleInput() = AdvisoryPolicyInput(
        authorization = personalAuthorization(),
        masterAdvisoryEnabled = true,
        deliveryAllowed = true,
        source = source(),
        sourceDecodeSucceeded = true,
        sourceProvenanceComplete = true,
        sourceProducedAfterStudyStart = true,
        protocol = cyclicSighing,
        protocolDefinitionSha256 = cyclicDefinitionHash,
        protocolCatalogSha256 = AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
        opportunityAlreadyRecorded = false,
        opportunityAlreadyHandled = false,
        activeEpisodeExists = false,
        lastStartedAt = null,
        now = 100_000L,
    )

    @Test
    fun `presentation requires final sustained decoded prospective source and every independent gate`() {
        val eligible = eligibleInput()
        assertTrue(AdvisoryPolicy.evaluate(eligible, AdvisoryAction.PRESENT) is AdvisoryPolicyResult.Eligible)

        val cases = listOf(
            eligible.copy(authorization = ordinaryAuthorization()) to
                AdvisoryIneligibleReason.BUILD_NOT_AUTHORIZED,
            eligible.copy(authorization = eligible.authorization.copy(operationalEvidenceApproved = false)) to
                AdvisoryIneligibleReason.OPERATIONAL_EVIDENCE_NOT_APPROVED,
            eligible.copy(masterAdvisoryEnabled = false) to
                AdvisoryIneligibleReason.MASTER_ADVISORY_DISABLED,
            eligible.copy(source = null) to
                AdvisoryIneligibleReason.SOURCE_MISSING,
            eligible.copy(source = eligible.source!!.copy(dataStatus = PassiveDataStatus.AVAILABLE_PROVISIONAL)) to
                AdvisoryIneligibleReason.SOURCE_NOT_FINAL,
            eligible.copy(
                source = eligible.source!!.copy(observationState = PassiveObservationState.WITHIN_PERSON_RANGE),
            ) to AdvisoryIneligibleReason.SOURCE_NOT_SUSTAINED_DEVIATION,
            eligible.copy(sourceDecodeSucceeded = false) to
                AdvisoryIneligibleReason.SOURCE_DECODE_FAILED,
            eligible.copy(sourceProvenanceComplete = false) to
                AdvisoryIneligibleReason.SOURCE_PROVENANCE_INCOMPLETE,
            eligible.copy(sourceProducedAfterStudyStart = false) to
                AdvisoryIneligibleReason.SOURCE_PREDATES_STUDY_PHASE,
            eligible.copy(protocol = null) to
                AdvisoryIneligibleReason.PROTOCOL_MISSING,
            eligible.copy(protocolDefinitionSha256 = "wrong") to
                AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH,
            eligible.copy(protocolDefinitionSha256 = null) to
                AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH,
            eligible.copy(protocolCatalogSha256 = "wrong") to
                AdvisoryIneligibleReason.PROTOCOL_HASH_MISMATCH,
            eligible.copy(opportunityAlreadyRecorded = true) to
                AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_RECORDED,
        )
        cases.forEach { (input, reason) ->
            assertEquals(
                "input mutated for $reason",
                AdvisoryPolicyResult.Ineligible(reason),
                AdvisoryPolicy.evaluate(input, AdvisoryAction.PRESENT),
            )
        }
    }

    @Test
    fun `a protocol outside the build allowlist is refused even when everything else is eligible`() {
        val unlisted = ProtocolKey(
            protocolId = cyclicSighing.id,
            protocolVersion = cyclicSighing.version,
            definitionSha256 = cyclicDefinitionHash,
        )
        val closedAuthorization = personalAuthorization().copy(protocolAllowlist = emptySet())
        assertEquals(unlisted, cyclicKey)
        assertEquals(
            AdvisoryPolicyResult.Ineligible(AdvisoryIneligibleReason.PROTOCOL_NOT_ALLOWLISTED),
            AdvisoryPolicy.evaluate(eligibleInput().copy(authorization = closedAuthorization), AdvisoryAction.PRESENT),
        )
    }

    @Test
    fun `ordinary mode is refused before any protocol check runs`() {
        // BUILD_NOT_AUTHORIZED is checked first, so ordinary mode never
        // reaches the allowlist or clinical-review checks at all — those
        // stay in the code as defense-in-depth for a build mode this
        // gate does not exist to represent, not as reachable behavior.
        assertEquals(ClinicalReviewStatus.NOT_REVIEWED, cyclicSighing.clinicalReviewStatus)
        val hypotheticalOrdinary = AdvisoryBuildAuthorization(
            buildMode = AdvisoryBuildMode.ORDINARY,
            operationalEvidenceApproved = false,
            protocolAllowlist = setOf(cyclicKey),
        )
        assertEquals(
            AdvisoryPolicyResult.Ineligible(AdvisoryIneligibleReason.BUILD_NOT_AUTHORIZED),
            AdvisoryPolicy.evaluate(
                eligibleInput().copy(authorization = hypotheticalOrdinary),
                AdvisoryAction.PRESENT,
            ),
        )
    }

    @Test
    fun `start adds delivery active episode and started cooldown checks`() {
        val input = eligibleInput()
        assertTrue(AdvisoryPolicy.evaluate(input, AdvisoryAction.START) is AdvisoryPolicyResult.Eligible)

        assertEquals(
            AdvisoryIneligibleReason.OPPORTUNITY_ALREADY_HANDLED,
            (
                AdvisoryPolicy.evaluate(input.copy(opportunityAlreadyHandled = true), AdvisoryAction.START)
                    as AdvisoryPolicyResult.Ineligible
                ).reason,
        )
        assertEquals(
            AdvisoryIneligibleReason.DELIVERY_DISABLED,
            (
                AdvisoryPolicy.evaluate(input.copy(deliveryAllowed = false), AdvisoryAction.START)
                    as AdvisoryPolicyResult.Ineligible
                ).reason,
        )
        assertEquals(
            AdvisoryIneligibleReason.ACTIVE_EPISODE_EXISTS,
            (
                AdvisoryPolicy.evaluate(input.copy(activeEpisodeExists = true), AdvisoryAction.START)
                    as AdvisoryPolicyResult.Ineligible
                ).reason,
        )
        val stillCoolingDown = input.copy(lastStartedAt = input.now - input.protocol!!.cooldownSeconds * 1_000L + 1)
        assertEquals(
            AdvisoryIneligibleReason.COOLDOWN_ACTIVE,
            (AdvisoryPolicy.evaluate(stillCoolingDown, AdvisoryAction.START) as AdvisoryPolicyResult.Ineligible).reason,
        )
        val cooldownJustElapsed = input.copy(lastStartedAt = input.now - input.protocol!!.cooldownSeconds * 1_000L)
        assertTrue(AdvisoryPolicy.evaluate(cooldownJustElapsed, AdvisoryAction.START) is AdvisoryPolicyResult.Eligible)
    }

    @Test
    fun `present does not require delivery to be allowed`() {
        // Materializing evidence for later review is not the same act as
        // putting something in front of the person; the delivery kill
        // switch guards the latter only.
        val input = eligibleInput().copy(deliveryAllowed = false)
        assertTrue(AdvisoryPolicy.evaluate(input, AdvisoryAction.PRESENT) is AdvisoryPolicyResult.Eligible)
    }

    @Test
    fun `an eligible result carries only the resolved source protocol and rule version`() {
        val result = AdvisoryPolicy.evaluate(eligibleInput(), AdvisoryAction.PRESENT) as AdvisoryPolicyResult.Eligible
        assertEquals(source(), result.source)
        assertEquals(cyclicSighing, result.protocol)
        assertEquals(cyclicKey, result.protocolKey)
        assertEquals(AdvisoryPolicy.RULE_VERSION, result.advisoryRuleVersion)
        assertEquals(AdvisoryBuildMode.PERSONAL_RESEARCH, result.buildMode)
    }

    @Test
    fun `no ineligible reason names a diagnosis current state success or failure`() {
        val forbidden = listOf(
            "ANXI", "PANIC", "DEPRESS", "BORDERLINE", "ANGER", "CRISIS", "DIAGNOS",
            "ILLNESS", "SUCCESS", "FAILURE", "EFFECTIVE", "IMPROV", "WORSE", "CURED",
        )
        AdvisoryIneligibleReason.entries.forEach { reason ->
            forbidden.forEach { word ->
                assertTrue("${reason.name} must not resemble '$word'", !reason.name.contains(word))
            }
        }
    }

    @Test
    fun `no episode or outcome vocabulary names a diagnosis or a treatment outcome`() {
        val forbidden = listOf(
            "DIAGNOS", "SYMPTOM", "TREAT", "CURE", "EFFECTIVE", "SUCCESS", "FAILURE",
            "IMPROV", "WORSE",
        )
        val names = listOf("Eligible", "Ineligible") +
            EpisodeEventType.entries.map { it.name } +
            MissingOutcomeReason.entries.map { it.name }
        names.forEach { name ->
            forbidden.forEach { word ->
                assertTrue("'$name' must not resemble '$word'", !name.uppercase().contains(word))
            }
        }
    }

    @Test
    fun `the repository never wires a raw passive sample or wearable read path`() {
        // A source-level boundary rather than a runtime one: this file
        // must stay readable only from the host JVM (the gradle test
        // working directory is the `app` module), which is why this
        // check lives here and not in the instrumentation test that
        // exercises the repository against a real database.
        val source = File("src/main/java/org/mindanchor/advisory/AdvisoryRepository.kt")
        assertTrue("the source file must be readable from the test working directory", source.isFile)
        val text = source.readText(Charsets.UTF_8)
        val forbidden = listOf(
            "insertRawProvenance", "insertRawSamples", "rawRecords", "rawProvenanceNow",
            "PassiveRawProvenanceEntity", "PassiveRawSampleEntity", "HealthConnect", "COROS", "Wearable",
        )
        forbidden.forEach { symbol ->
            assertFalse("AdvisoryRepository.kt must not reference $symbol", text.contains(symbol))
        }
    }
}
