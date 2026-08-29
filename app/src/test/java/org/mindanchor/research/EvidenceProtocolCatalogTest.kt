package org.mindanchor.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.friction.BreathingProtocol

/**
 * Program 1 Task 3 — the catalogue is seeded only from citations this
 * repository has already verified (`docs/research/23-citation-audit.md`).
 * These tests are the enforcement of that rule, and of the freeze that
 * makes an unversioned edit to a protocol definition fail the build.
 */
class EvidenceProtocolCatalogTest {

    private val registry = EvidenceProtocolCatalog.registry

    private fun cyclicSighing(): EvidenceProtocol {
        val protocol = registry.latest("cyclic-sighing")
        assertNotNull("the catalogue must hold cyclic-sighing", protocol)
        return requireNotNull(protocol)
    }

    @Test
    fun `an unregistered protocol is simply absent`() {
        assertNull(registry.latest("symmetric-slow-paced-breathing"))
        assertNull(registry.find("cyclic-sighing", 2))
    }

    @Test
    fun `the catalogue holds exactly the seeded protocols`() {
        assertEquals(listOf("cyclic-sighing"), registry.protocols.map { it.id })
        assertEquals(listOf(1), registry.protocols.map { it.version })
    }

    @Test
    fun `every catalogued protocol has a complete evidence contract`() {
        registry.protocols.forEach { protocol ->
            assertEquals(
                "protocol ${protocol.id} must validate",
                ProtocolValidation.Valid,
                EvidenceProtocolRegistry.validate(protocol),
            )
        }
    }

    @Test
    fun `every catalogued source is permitted and resolvable`() {
        registry.protocols.flatMap { it.evidenceSources }.forEach { source ->
            assertTrue("${source.reference} must be a permitted source type", source.sourceType.isPermitted)
            assertTrue(
                "${source.reference} must be a resolvable DOI",
                source.reference.startsWith("https://doi.org/"),
            )
        }
    }

    @Test
    fun `cyclic sighing cites the two audited papers at the right strength`() {
        val protocol = cyclicSighing()
        val byReference = protocol.evidenceSources.associateBy { it.reference }
        assertEquals(
            setOf(
                "https://doi.org/10.1016/j.xcrm.2022.100895",
                "https://doi.org/10.1097/00004872-200112000-00016",
            ),
            byReference.keys,
        )
        assertEquals(
            EvidenceStrength.RANDOMIZED_OR_CONTROLLED_TRIAL,
            byReference.getValue("https://doi.org/10.1016/j.xcrm.2022.100895").strength,
        )
        assertEquals(
            EvidenceStrength.MECHANISTIC_STUDY,
            byReference.getValue("https://doi.org/10.1097/00004872-200112000-00016").strength,
        )
    }

    @Test
    fun `cyclic sighing steps match the breathing protocol the app already ships`() {
        val protocol = cyclicSighing()
        assertEquals(
            listOf(
                (BreathingProtocol.INHALE_MILLIS / 1_000L).toInt(),
                (BreathingProtocol.SIP_MILLIS / 1_000L).toInt(),
                (BreathingProtocol.EXHALE_MILLIS / 1_000L).toInt(),
            ),
            protocol.steps.map { it.durationSeconds },
        )
    }

    @Test
    fun `cyclic sighing carries the trialled dose, not an invented one`() {
        val protocol = cyclicSighing()
        assertEquals(300, protocol.maxDurationSeconds)
        assertEquals(72_000, protocol.cooldownSeconds)
        assertEquals(86_400, protocol.outcomeWindowSeconds)
    }

    @Test
    fun `nothing claims a clinical review that has not happened`() {
        registry.protocols.forEach { protocol ->
            assertEquals(ClinicalReviewStatus.NOT_REVIEWED, protocol.clinicalReviewStatus)
        }
    }

    @Test
    fun `no user-facing explanation promises an outcome`() {
        val promises = listOf(
            "will reduce", "will improve", "will help", "cures", "treats",
            "guarantees", "proven to", "clinically proven", "diagnos",
        )
        registry.protocols.forEach { protocol ->
            val text = protocol.userFacingExplanation.lowercase()
            promises.forEach { promise ->
                assertTrue(
                    "'${protocol.id}' must not promise: $promise",
                    !text.contains(promise),
                )
            }
        }
    }

    @Test
    fun `the catalogue hash is frozen`() {
        assertEquals(
            "bdd624811716bba3907ebbe34103d60e9cdd8e43225165167207358f8ea2d4ac",
            registry.catalogSha256,
        )
    }

    @Test
    fun `the cyclic sighing definition hash is frozen`() {
        assertEquals(
            "6fa65523f63cf97310c6d36774ccebc32d8de5c65df98df23bda912ccfef7114",
            EvidenceProtocolRegistry.definitionSha256(cyclicSighing()),
        )
    }

    @Test
    fun `the deliberately unseeded candidates stay unseeded`() {
        assertEquals(
            setOf(
                "symmetric-slow-paced-breathing",
                "self-compassion-moment",
                "behavioural-activation",
                "friction-gate-breath-trigger",
            ),
            EvidenceProtocolCatalog.DELIBERATELY_NOT_SEEDED,
        )
        registry.protocols.forEach { protocol ->
            assertTrue(
                "${protocol.id} is on the do-not-seed list",
                protocol.id !in EvidenceProtocolCatalog.DELIBERATELY_NOT_SEEDED,
            )
        }
    }
}
