package org.mindanchor.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.journal.StructuralContextExtractor

/**
 * Program 2A Task 5 — the transformations this build actually performs are
 * listed, versioned, and hashed, so a change to how a raw record becomes a
 * derived one opens a study phase instead of quietly reinterpreting every
 * day already recorded.
 */
class TransformationRegistryTest {

    @Test
    fun `the registry lists exactly the transformations this build performs`() {
        assertEquals(
            listOf(
                "structural-context",
                "research-export-canonicalisation",
                "passive-personal-baseline",
                "passive-block-calibration",
                "passive-observation-explanation",
            ),
            TransformationRegistry.transformations.map { it.id },
        )
    }

    @Test
    fun `passive transformations record their semantic versions and limitations`() {
        val transformations = TransformationRegistry.transformations.associateBy { it.id }

        assertEquals(null, transformations["passive-daily-features"])

        val baseline = requireNotNull(transformations["passive-personal-baseline"])
        assertEquals("personal-baseline-v2", baseline.version)
        assertTrue(baseline.description.contains("median/MAD"))
        assertTrue(baseline.description.contains("fallback"))
        assertTrue(baseline.description.contains("eligibility floors"))
        assertTrue(baseline.description.contains("point-in-time"))
        assertTrue(baseline.description.contains("frozen reference"))
        assertTrue(baseline.description.contains("trailing candidate"))

        val calibration = requireNotNull(transformations["passive-block-calibration"])
        assertEquals("block-calibration-v2", calibration.version)
        assertTrue(calibration.description.contains("engineering false-observation budget"))
        assertTrue(calibration.description.contains("clinical accuracy"))
        assertTrue(calibration.output.contains("seed"))
        assertTrue(calibration.output.contains("configuration"))

        val explanation = requireNotNull(transformations["passive-observation-explanation"])
        assertEquals("observation-explanation-v1", explanation.version)
        assertTrue(explanation.description.contains("fixed templates"))
        assertTrue(explanation.description.contains("diagnosis"))
    }

    @Test
    fun `structural context tracks the extractor it describes`() {
        assertEquals(
            StructuralContextExtractor.EXTRACTOR_VERSION,
            TransformationRegistry.versionOf("structural-context"),
        )
    }

    @Test
    fun `every transformation is fully described`() {
        TransformationRegistry.transformations.forEach { transformation ->
            assertTrue(transformation.id.isNotBlank())
            assertTrue(transformation.version.isNotBlank())
            assertTrue(transformation.input.isNotBlank())
            assertTrue(transformation.output.isNotBlank())
            assertTrue(transformation.description.isNotBlank())
        }
    }

    @Test
    fun `an unknown transformation has no version`() {
        assertEquals(null, TransformationRegistry.versionOf("feature-windows"))
    }

    @Test
    fun `the set version is frozen`() {
        assertEquals(
            "160a63549fcb1c515daf8083532bbd98aa57c96e2913b66b7b557e5580337aa6",
            TransformationRegistry.setVersion,
        )
    }

    @Test
    fun `documentation prose is not part of the version`() {
        val base = TransformationRegistry.transformations
        val reworded = base.map { it.copy(description = "${it.description} A clearer sentence.") }
        assertEquals(
            "a typo fix must not split the study series",
            TransformationRegistry.setVersionOf(base),
            TransformationRegistry.setVersionOf(reworded),
        )
    }

    @Test
    fun `adding or changing a transformation changes the set version`() {
        val base = TransformationRegistry.transformations
        val added = base + Transformation(
            id = "feature-windows",
            version = "windows-v1",
            input = "Signal samples",
            output = "Rolling feature values",
            description = "A Program 2 transformation that does not exist yet.",
        )
        assertNotEquals(TransformationRegistry.setVersionOf(base), TransformationRegistry.setVersionOf(added))
        assertNotEquals(
            TransformationRegistry.setVersionOf(base),
            TransformationRegistry.setVersionOf(base.map { it.copy(version = "${it.version}x") }),
        )
        assertEquals(TransformationRegistry.setVersionOf(base), TransformationRegistry.setVersion)
    }

    @Test
    fun `the set version does not depend on declaration order`() {
        val base = TransformationRegistry.transformations
        assertEquals(TransformationRegistry.setVersionOf(base), TransformationRegistry.setVersionOf(base.reversed()))
    }
}
