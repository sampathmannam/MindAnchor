package org.mindanchor.research

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 2 — the design's §15 promise that "no protocol can run
 * without a complete evidence contract" is enforced here, one missing
 * field at a time. A protocol that fails any check is not registered at
 * all; there is no partial registration and no "register it and warn".
 */
class EvidenceProtocolRegistryTest {

    private fun validProtocol(): EvidenceProtocol = EvidenceProtocol(
        id = "example-protocol",
        version = 1,
        targetState = "Elevated self-reported tension the person has noticed themselves.",
        intendedPopulation = "One adult using MindAnchor for personal self-experimentation.",
        exclusions = listOf("Not established for anyone under 18."),
        evidenceSources = listOf(
            EvidenceSource(
                citation = "Someone A et al. (2020). A Trial. Journal 1(1):1-2.",
                reference = "https://doi.org/10.0000/example",
                strength = EvidenceStrength.RANDOMIZED_OR_CONTROLLED_TRIAL,
                sourceType = EvidenceSourceType.PEER_REVIEWED_ARTICLE,
            ),
        ),
        mechanism = "A described physiological mechanism.",
        expectedOutcome = "A described change in a self-reported measure.",
        eligibilityRules = listOf("The person chose to start it."),
        contraindicationRules = listOf("Stop if it becomes uncomfortable."),
        steps = listOf(ProtocolStep(ordinal = 1, instruction = "Breathe in.", durationSeconds = 2)),
        permittedModalities = setOf(Modality.VISUAL, Modality.TEXT),
        maxDurationSeconds = 300,
        stopRules = setOf(StopRule.USER_STOPPED, StopRule.MAX_DURATION_REACHED),
        cooldownSeconds = 3_600,
        outcomeWindowSeconds = 86_400,
        successInterpretation = "Compared only against this person's own recent days.",
        clinicalReviewStatus = ClinicalReviewStatus.NOT_REVIEWED,
        userFacingExplanation = "What this is, in plain words.",
    )

    private fun assertInvalid(field: String, protocol: EvidenceProtocol) {
        val result = EvidenceProtocolRegistry.validate(protocol)
        assertTrue("expected $field to be rejected, got $result", result is ProtocolValidation.Invalid)
        assertEquals(field, (result as ProtocolValidation.Invalid).field)
    }

    @Test
    fun `a complete protocol validates`() {
        assertEquals(ProtocolValidation.Valid, EvidenceProtocolRegistry.validate(validProtocol()))
    }

    @Test
    fun `a blank prose field is rejected`() {
        assertInvalid("targetState", validProtocol().copy(targetState = "  "))
        assertInvalid("intendedPopulation", validProtocol().copy(intendedPopulation = ""))
        assertInvalid("mechanism", validProtocol().copy(mechanism = " "))
        assertInvalid("expectedOutcome", validProtocol().copy(expectedOutcome = ""))
        assertInvalid("successInterpretation", validProtocol().copy(successInterpretation = ""))
        assertInvalid("userFacingExplanation", validProtocol().copy(userFacingExplanation = "\n"))
    }

    @Test
    fun `an empty rule or step collection is rejected`() {
        assertInvalid("exclusions", validProtocol().copy(exclusions = emptyList()))
        assertInvalid("eligibilityRules", validProtocol().copy(eligibilityRules = emptyList()))
        assertInvalid("contraindicationRules", validProtocol().copy(contraindicationRules = emptyList()))
        assertInvalid("steps", validProtocol().copy(steps = emptyList()))
        assertInvalid("permittedModalities", validProtocol().copy(permittedModalities = emptySet()))
        assertInvalid("stopRules", validProtocol().copy(stopRules = emptySet()))
        assertInvalid("evidenceSources", validProtocol().copy(evidenceSources = emptyList()))
    }

    @Test
    fun `a blank entry inside a rule collection is rejected`() {
        assertInvalid("exclusions", validProtocol().copy(exclusions = listOf("ok", " ")))
        assertInvalid("eligibilityRules", validProtocol().copy(eligibilityRules = listOf("")))
        assertInvalid("contraindicationRules", validProtocol().copy(contraindicationRules = listOf("  ")))
    }

    @Test
    fun `a non-positive or out-of-range number is rejected`() {
        assertInvalid("version", validProtocol().copy(version = 0))
        assertInvalid("maxDurationSeconds", validProtocol().copy(maxDurationSeconds = 0))
        assertInvalid("outcomeWindowSeconds", validProtocol().copy(outcomeWindowSeconds = 0))
        assertInvalid("cooldownSeconds", validProtocol().copy(cooldownSeconds = -1))
    }

    @Test
    fun `a malformed identifier is rejected`() {
        assertInvalid("id", validProtocol().copy(id = ""))
        assertInvalid("id", validProtocol().copy(id = "Example-Protocol"))
        assertInvalid("id", validProtocol().copy(id = "example protocol"))
        assertInvalid("id", validProtocol().copy(id = "-example"))
        assertInvalid("id", validProtocol().copy(id = "example--protocol"))
    }

    @Test
    fun `a malformed step is rejected`() {
        assertInvalid("steps", validProtocol().copy(steps = listOf(ProtocolStep(1, " ", 2))))
        assertInvalid("steps", validProtocol().copy(steps = listOf(ProtocolStep(1, "Breathe.", 0))))
        assertInvalid("steps", validProtocol().copy(steps = listOf(ProtocolStep(2, "Breathe.", 2))))
        assertInvalid(
            "steps",
            validProtocol().copy(
                steps = listOf(ProtocolStep(1, "In.", 2), ProtocolStep(1, "Out.", 6)),
            ),
        )
    }

    @Test
    fun `an excluded evidence source type is rejected`() {
        EvidenceSourceType.EXCLUDED.forEach { excluded ->
            val protocol = validProtocol().let { base ->
                base.copy(evidenceSources = base.evidenceSources.map { it.copy(sourceType = excluded) })
            }
            assertInvalid("evidenceSources", protocol)
        }
    }

    @Test
    fun `an unsourced evidence entry is rejected`() {
        val base = validProtocol()
        assertInvalid(
            "evidenceSources",
            base.copy(evidenceSources = base.evidenceSources.map { it.copy(citation = " ") }),
        )
        assertInvalid(
            "evidenceSources",
            base.copy(evidenceSources = base.evidenceSources.map { it.copy(reference = "") }),
        )
    }

    @Test
    fun `the evidence hierarchy is ordered strongest first`() {
        assertEquals(
            listOf(
                EvidenceStrength.CLINICAL_GUIDELINE_OR_SYSTEMATIC_REVIEW,
                EvidenceStrength.RANDOMIZED_OR_CONTROLLED_TRIAL,
                EvidenceStrength.VALIDATED_TREATMENT_MANUAL,
                EvidenceStrength.MECHANISTIC_STUDY,
                EvidenceStrength.EXPERT_BOOK_CONSISTENT_WITH_STRONGER_EVIDENCE,
            ),
            EvidenceStrength.entries.toList(),
        )
    }

    @Test
    fun `excluded source types are exactly the four the design names`() {
        assertEquals(
            setOf(
                EvidenceSourceType.BLOG,
                EvidenceSourceType.INFLUENCER,
                EvidenceSourceType.MARKETING,
                EvidenceSourceType.AI_GENERATED,
            ),
            EvidenceSourceType.EXCLUDED,
        )
        assertTrue(EvidenceSourceType.entries.none { it.isPermitted && it in EvidenceSourceType.EXCLUDED })
    }

    @Test
    fun `a definition hash is stable and covers every field`() {
        val base = validProtocol()
        assertEquals(
            EvidenceProtocolRegistry.definitionSha256(base),
            EvidenceProtocolRegistry.definitionSha256(base.copy()),
        )

        val mutations: List<Pair<String, EvidenceProtocol>> = listOf(
            "id" to base.copy(id = "other-protocol"),
            "version" to base.copy(version = 2),
            "targetState" to base.copy(targetState = "${base.targetState}."),
            "intendedPopulation" to base.copy(intendedPopulation = "${base.intendedPopulation}."),
            "exclusions" to base.copy(exclusions = base.exclusions + "Another."),
            "evidenceSources" to base.copy(
                evidenceSources = base.evidenceSources.map { it.copy(strength = EvidenceStrength.MECHANISTIC_STUDY) },
            ),
            "mechanism" to base.copy(mechanism = "${base.mechanism}."),
            "expectedOutcome" to base.copy(expectedOutcome = "${base.expectedOutcome}."),
            "eligibilityRules" to base.copy(eligibilityRules = base.eligibilityRules + "Another."),
            "contraindicationRules" to base.copy(contraindicationRules = base.contraindicationRules + "Another."),
            "steps" to base.copy(steps = base.steps + ProtocolStep(2, "Breathe out.", 6)),
            "permittedModalities" to base.copy(permittedModalities = base.permittedModalities + Modality.AUDIO),
            "maxDurationSeconds" to base.copy(maxDurationSeconds = 301),
            "stopRules" to base.copy(stopRules = base.stopRules + StopRule.DISCOMFORT_REPORTED),
            "cooldownSeconds" to base.copy(cooldownSeconds = 3_601),
            "outcomeWindowSeconds" to base.copy(outcomeWindowSeconds = 86_401),
            "successInterpretation" to base.copy(successInterpretation = "${base.successInterpretation}."),
            "clinicalReviewStatus" to base.copy(clinicalReviewStatus = ClinicalReviewStatus.REVIEW_REQUESTED),
            "userFacingExplanation" to base.copy(userFacingExplanation = "${base.userFacingExplanation}."),
        )
        assertEquals(
            "every declared field must be covered by a mutation",
            EvidenceProtocol::class.java.declaredFields.count {
                !it.isSynthetic && !Modifier.isStatic(it.modifiers)
            },
            mutations.size,
        )
        mutations.forEach { (field, mutated) ->
            assertNotEquals(
                "changing $field must change the definition hash",
                EvidenceProtocolRegistry.definitionSha256(base),
                EvidenceProtocolRegistry.definitionSha256(mutated),
            )
        }
    }

    @Test
    fun `set ordering does not change the definition hash`() {
        val base = validProtocol()
        val reordered = base.copy(
            permittedModalities = setOf(Modality.TEXT, Modality.VISUAL),
            stopRules = setOf(StopRule.MAX_DURATION_REACHED, StopRule.USER_STOPPED),
        )
        assertEquals(
            EvidenceProtocolRegistry.definitionSha256(base),
            EvidenceProtocolRegistry.definitionSha256(reordered),
        )
    }

    @Test
    fun `a registry rejects an invalid protocol outright`() {
        val invalid = validProtocol().copy(evidenceSources = emptyList())
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            EvidenceProtocolRegistry.of(listOf(invalid))
        }
        assertTrue(thrown.message.orEmpty().contains("evidenceSources"))
        assertTrue(thrown.message.orEmpty().contains("example-protocol"))
    }

    @Test
    fun `a registry rejects a duplicate id and version`() {
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            EvidenceProtocolRegistry.of(listOf(validProtocol(), validProtocol()))
        }
        assertTrue(thrown.message.orEmpty().contains("example-protocol@1"))
    }

    @Test
    fun `a registry accepts two versions of the same protocol`() {
        val registry = EvidenceProtocolRegistry.of(listOf(validProtocol(), validProtocol().copy(version = 2)))
        assertEquals(2, registry.protocols.size)
        assertEquals(1, registry.find("example-protocol", 1)?.version)
        assertEquals(2, registry.latest("example-protocol")?.version)
        assertEquals(null, registry.find("example-protocol", 3))
        assertEquals(null, registry.latest("missing-protocol"))
    }

    @Test
    fun `a catalog hash is stable and order-independent`() {
        val a = validProtocol()
        val b = validProtocol().copy(id = "other-protocol")
        assertEquals(
            EvidenceProtocolRegistry.of(listOf(a, b)).catalogSha256,
            EvidenceProtocolRegistry.of(listOf(b, a)).catalogSha256,
        )
        assertNotEquals(
            EvidenceProtocolRegistry.of(listOf(a, b)).catalogSha256,
            EvidenceProtocolRegistry.of(listOf(a)).catalogSha256,
        )
    }
}
