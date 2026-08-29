package org.mindanchor.research

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.friction.BreathingProtocol

/**
 * Program 1 Task 3 — the catalogue is seeded only from citations this
 * repository has already verified. These tests are the enforcement of that
 * rule, and of the freeze that makes an unversioned edit to a protocol
 * definition fail the build.
 *
 * The important one is `every catalogued title appears in the research
 * index`. An earlier version of this suite checked DOIs and enum values
 * only, and a fabricated paper title passed twelve green tests. Checking
 * the title against `docs/research/22-research-index.md` makes the
 * repository's own verification record the oracle, rather than the memory
 * of whoever wrote the citation.
 */
class EvidenceProtocolCatalogTest {

    private val registry = EvidenceProtocolCatalog.registry

    /** The gradle test working directory is the `app` module, so the repo root is one level up. */
    private val researchIndex = File("../docs/research/22-research-index.md")

    private fun cyclicSighing(): EvidenceProtocol {
        val protocol = registry.latest("cyclic-sighing")
        assertNotNull("the catalogue must hold cyclic-sighing", protocol)
        return requireNotNull(protocol)
    }

    private fun prose(protocol: EvidenceProtocol): List<String> =
        listOf(
            protocol.targetState,
            protocol.intendedPopulation,
            protocol.mechanism,
            protocol.expectedOutcome,
            protocol.successInterpretation,
            protocol.userFacingExplanation,
        ) + protocol.exclusions + protocol.eligibilityRules + protocol.contraindicationRules +
            protocol.steps.map { it.instruction }

    @Test
    fun `the catalogue holds exactly the seeded protocols`() {
        assertEquals(listOf("cyclic-sighing"), registry.protocols.map { it.id })
        assertEquals(listOf(1), registry.protocols.map { it.version })
    }

    @Test
    fun `an unregistered protocol is simply absent`() {
        assertNull(registry.latest("symmetric-slow-paced-breathing"))
        assertNull(registry.find("cyclic-sighing", 2))
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
    fun `every catalogued title and DOI appears in the research index`() {
        assertTrue("the research index must be readable from the test working directory", researchIndex.isFile)
        val index = researchIndex.readText(Charsets.UTF_8)
        registry.protocols.flatMap { it.evidenceSources }.forEach { source ->
            val doi = source.reference.removePrefix("https://doi.org/")
            assertTrue(
                "DOI $doi is not recorded as verified in 22-research-index.md",
                index.contains(doi),
            )
            assertTrue(
                "the title '${source.title}' is not recorded in 22-research-index.md for $doi — " +
                    "either it is wrong, or the paper was never verified",
                index.contains(source.title),
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
    fun `cyclic sighing carries the trialled dose and says how the cycle repeats`() {
        val protocol = cyclicSighing()
        assertEquals(300, protocol.maxDurationSeconds)
        assertEquals(86_400, protocol.cooldownSeconds)
        assertEquals(86_400, protocol.outcomeWindowSeconds)
        // The steps are one 9-second cycle and the maximum is five
        // minutes: without saying so, the definition would be ambiguous
        // about whether the dose is one cycle or thirty-three.
        assertTrue(protocol.steps.last().instruction.contains("begin the cycle again"))
    }

    @Test
    fun `nothing claims a clinical review that has not happened`() {
        registry.protocols.forEach { protocol ->
            assertEquals(ClinicalReviewStatus.NOT_REVIEWED, protocol.clinicalReviewStatus)
        }
    }

    @Test
    fun `no prose field promises an outcome`() {
        // Word forms rather than whole phrases: "reduces stress",
        // "shown to reduce" and "will reduce" are one word apart, and an
        // earlier whole-phrase list let every one of them through. Stems
        // alone are too blunt in the other direction -- "heal" matches
        // "healthy adults", which is a population, not a promise.
        val promises = listOf(
            "reduce", "reduces", "reducing", "reduction",
            "improve", "improves", "improving", "improvement",
            "lowers", "lowering", "relieve", "relieves", "relief",
            "effective", "efficacy", "proven", "proves", "proof",
            "cure", "cures", "guarantee", "guarantees", "heals", "healing",
            "diagnos", "therapy", "therapeutic", "clinically",
        )
        registry.protocols.forEach { protocol ->
            prose(protocol).forEach { text ->
                val lowered = text.lowercase()
                promises.forEach { promise ->
                    assertFalse(
                        "'${protocol.id}' must not promise '$promise': $text",
                        lowered.contains(promise),
                    )
                }
            }
        }
    }

    @Test
    fun `no prose field is empty enough to pass the promise scan by accident`() {
        registry.protocols.forEach { protocol ->
            prose(protocol).forEach { text ->
                assertTrue("a prose field of ${protocol.id} is suspiciously short: '$text'", text.length > 20)
            }
        }
    }

    @Test
    fun `every catalogued definition is frozen row by row`() {
        // Rows rather than one catalogue-wide digest: adding version 2 of
        // a protocol is then an added line, while editing version 1 in
        // place reddens the line that names it — the two cases a single
        // digest cannot tell apart.
        assertEquals(
            listOf(
                "cyclic-sighing@1:1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
            ),
            registry.protocols.map { "${it.id}@${it.version}:${EvidenceProtocolRegistry.definitionSha256(it)}" },
        )
    }

    @Test
    fun `the catalogue hash is frozen`() {
        assertEquals(
            "9f71a3690bf4b0b07ade1ef6963ca8d36c4e6227342cb1911f27dbb4f2cf44ee",
            registry.catalogSha256,
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
