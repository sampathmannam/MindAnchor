package org.mindanchor.research

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mindanchor.continuity.ContinuityContract
import org.mindanchor.intelligence.PassiveEstimator

/**
 * Program 2A Task 5 — the provenance version vector is everything that
 * could change how a record is produced or interpreted. A difference in
 * any component opens a new study phase, which is the mechanism behind the
 * design's rule that historical decisions are never silently
 * reinterpreted.
 */
class ProvenanceVersionsTest {

    private fun vector() = ProvenanceVersions.vector(
        appVersionCode = 95,
        appVersionName = "0.71.0",
        sourceDeviceId = "device-a",
    )

    @Test
    fun `the passive intelligence rule and model versions are registered`() {
        assertEquals("passive-observation-rules-v2", PassiveEstimator.RULE_VERSION)
        assertEquals(PassiveEstimator.RULE_VERSION, ProvenanceVersions.RULE_SET_VERSION)
        assertEquals("personal-robust-baseline-v1", ProvenanceVersions.MODEL_SET_VERSION)
    }

    @Test
    fun `the vector reads its components from the things that own them`() {
        val vector = vector()
        assertEquals(EvidenceProtocolCatalog.registry.catalogSha256, vector.protocolCatalogSha256)
        assertEquals(TransformationRegistry.setVersion, vector.transformationSetVersion)
        assertEquals(MissingDataPolicy.VERSION, vector.missingDataPolicyVersion)
        assertEquals(MorningMeasure.INSTRUMENT_VERSION, vector.instrumentVersion)
        assertEquals(ContinuityContract.RESEARCH_DICTIONARY_VERSION, vector.dictionaryVersion)
        assertEquals(ProvenanceVersions.RULE_SET_VERSION, vector.ruleSetVersion)
        assertEquals(ProvenanceVersions.MODEL_SET_VERSION, vector.modelSetVersion)
        assertEquals(95, vector.appVersionCode)
        assertEquals("0.71.0", vector.appVersionName)
        assertEquals("device-a", vector.sourceDeviceId)
    }

    @Test
    fun `the vector is a pure function of its arguments`() {
        assertEquals(vector(), vector())
    }

    @Test
    fun `changing any single component changes the vector`() {
        val base = vector()
        val mutations: List<Pair<String, ProvenanceVector>> = listOf(
            "appVersionCode" to base.copy(appVersionCode = 96),
            "appVersionName" to base.copy(appVersionName = "0.72.0"),
            "protocolCatalogSha256" to base.copy(protocolCatalogSha256 = "other"),
            "ruleSetVersion" to base.copy(ruleSetVersion = "rule-set-sunset-v1"),
            "modelSetVersion" to base.copy(modelSetVersion = "model-set-baseline-v1"),
            "transformationSetVersion" to base.copy(transformationSetVersion = "other"),
            "missingDataPolicyVersion" to base.copy(missingDataPolicyVersion = "not-a-real-policy-version"),
            "instrumentVersion" to base.copy(instrumentVersion = "morning-v2"),
            "dictionaryVersion" to base.copy(dictionaryVersion = "mindanchor-research-v9"),
            "sourceDeviceId" to base.copy(sourceDeviceId = "device-b"),
        )
        assertEquals(
            "every declared component must be covered by a mutation",
            ProvenanceVector::class.java.declaredFields.count {
                !it.isSynthetic && !Modifier.isStatic(it.modifiers)
            },
            mutations.size,
        )
        mutations.forEach { (component, mutated) ->
            assertNotEquals("changing $component must change the vector", base, mutated)
        }
    }
}
