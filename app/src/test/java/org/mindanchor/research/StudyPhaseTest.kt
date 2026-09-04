package org.mindanchor.research

import java.lang.reflect.Modifier
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Program 1 Task 6 — a study phase is opened by a change in the provenance
 * vector and is never closed, because closing one would mean writing to a
 * historical row. The phase in effect at any instant is derived from the
 * starts alone.
 */
@OptIn(ExperimentalSerializationApi::class)
class StudyPhaseTest {

    private val vector = ProvenanceVersions.vector(
        appVersionCode = 95,
        appVersionName = "0.71.0",
        sourceDeviceId = "device-a",
    )

    private fun phase(ordinal: Int = 0, startedAt: Long = 1_000L, on: ProvenanceVector = vector): StudyPhase =
        StudyPhaseDecision.next(
            current = if (ordinal == 0) {
                null
            } else {
                StudyPhase(
                    id = "previous",
                    ordinal = ordinal - 1,
                    startedAt = startedAt - 1,
                    reason = StudyPhaseReason.INITIAL,
                    vector = vector.copy(appVersionName = "older"),
                )
            },
            vector = on,
            now = startedAt,
        )!!

    @Test
    fun `a phase has no end, because ending one would rewrite history`() {
        val fields = StudyPhase::class.java.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertEquals(listOf("id", "ordinal", "startedAt", "reason", "vector"), fields)
        assertTrue(fields.none { it.contains("end", ignoreCase = true) })
    }

    @Test
    fun `the first phase is ordinal zero and reason INITIAL`() {
        val first = StudyPhaseDecision.next(current = null, vector = vector, now = 1_000L)
        assertEquals(0, first?.ordinal)
        assertEquals(StudyPhaseReason.INITIAL, first?.reason)
        assertEquals(1_000L, first?.startedAt)
        assertEquals(vector, first?.vector)
    }

    @Test
    fun `an unchanged vector opens no phase`() {
        val first = phase()
        assertNull(StudyPhaseDecision.next(current = first, vector = vector, now = 2_000L))
    }

    @Test
    fun `each component opens a phase naming itself`() {
        val current = phase()
        val cases = listOf(
            StudyPhaseReason.APP_VERSION_CHANGE to vector.copy(appVersionCode = 96),
            StudyPhaseReason.APP_VERSION_CHANGE to vector.copy(appVersionName = "0.72.0"),
            StudyPhaseReason.PROTOCOL_CATALOG_CHANGE to vector.copy(protocolCatalogSha256 = "other"),
            StudyPhaseReason.RULE_VERSION_CHANGE to vector.copy(ruleSetVersion = "rule-set-sunset-v1"),
            StudyPhaseReason.MODEL_VERSION_CHANGE to vector.copy(modelSetVersion = "model-set-baseline-v1"),
            StudyPhaseReason.TRANSFORMATION_VERSION_CHANGE to vector.copy(transformationSetVersion = "other"),
            StudyPhaseReason.MISSING_DATA_POLICY_CHANGE to
                vector.copy(missingDataPolicyVersion = "not-a-real-policy-version"),
            StudyPhaseReason.INSTRUMENT_VERSION_CHANGE to vector.copy(instrumentVersion = "morning-v2"),
            StudyPhaseReason.DICTIONARY_VERSION_CHANGE to vector.copy(dictionaryVersion = "mindanchor-research-v9"),
            StudyPhaseReason.DEVICE_CHANGE to vector.copy(sourceDeviceId = "device-b"),
        )
        cases.forEach { (expected, changed) ->
            val next = StudyPhaseDecision.next(current, changed, now = 2_000L)
            assertEquals("$expected", expected, next?.reason)
            assertEquals(1, next?.ordinal)
            assertEquals(changed, next?.vector)
        }
        assertEquals(
            "every reason except INITIAL must be reachable",
            StudyPhaseReason.entries.filter { it != StudyPhaseReason.INITIAL }.toSet(),
            cases.map { it.first }.toSet(),
        )
    }

    @Test
    fun `two changes at once report the first differing component`() {
        val current = phase()
        val next = StudyPhaseDecision.next(
            current,
            vector.copy(appVersionCode = 96, sourceDeviceId = "device-b"),
            now = 2_000L,
        )
        assertEquals(StudyPhaseReason.APP_VERSION_CHANGE, next?.reason)
    }

    @Test
    fun `a phase id is deterministic and covers ordinal, start and vector`() {
        val a = StudyPhaseDecision.next(null, vector, now = 1_000L)!!
        val b = StudyPhaseDecision.next(null, vector, now = 1_000L)!!
        assertEquals(a.id, b.id)
        assertNotEquals(a.id, StudyPhaseDecision.next(null, vector, now = 1_001L)!!.id)
        assertNotEquals(a.id, StudyPhaseDecision.next(null, vector.copy(sourceDeviceId = "z"), now = 1_000L)!!.id)
        assertNotEquals(a.id, phase(ordinal = 1, startedAt = 1_000L).id)
    }

    @Test
    fun `the phase id is frozen`() {
        assertEquals(
            "2fa16462defb235fc51ab61a0aaecd642a150961d18db1ce7986cd5f3970a6f0",
            StudyPhaseDecision.next(
                current = null,
                vector = ProvenanceVector(
                    appVersionCode = 95,
                    appVersionName = "0.71.0",
                    protocolCatalogSha256 = "catalogue",
                    ruleSetVersion = "rule-set-none-v1",
                    modelSetVersion = "model-set-none-v1",
                    transformationSetVersion = "transformations",
                    missingDataPolicyVersion = "missing-data-v1",
                    instrumentVersion = "morning-v1",
                    dictionaryVersion = "mindanchor-research-v1",
                    sourceDeviceId = "device-a",
                ),
                now = 1_000L,
            )!!.id,
        )
    }

    @Test
    fun `the canonical phase serialises exactly the frozen field order`() {
        assertEquals(
            listOf("ordinal", "startedAt", "vector"),
            serializer<StudyPhaseCanonical>().descriptor.elementNames.toList(),
        )
        // The vector is nested inside the id, so its field order is wire
        // format too, and a component added without a firstDifference
        // branch fails here rather than throwing inside a research write.
        assertEquals(
            listOf(
                "appVersionCode", "appVersionName", "protocolCatalogSha256", "ruleSetVersion",
                "modelSetVersion", "transformationSetVersion", "missingDataPolicyVersion",
                "instrumentVersion", "dictionaryVersion", "sourceDeviceId",
            ),
            serializer<ProvenanceVector>().descriptor.elementNames.toList(),
        )
    }

    @Test
    fun `a backwards clock cannot start a phase before the one it succeeds`() {
        val first = phase(ordinal = 0, startedAt = 1_700_000_000_000L)
        val second = StudyPhaseDecision.next(first, vector.copy(appVersionCode = 96), now = 1_000L)!!
        assertEquals(first.startedAt + 1, second.startedAt)
        assertEquals(second, StudyPhaseDecision.phaseAt(listOf(first, second), 1_700_000_000_500L))
    }

    @Test
    fun `the phase in effect is the last one started at or before the instant`() {
        val first = phase(ordinal = 0, startedAt = 1_000L)
        val second = phase(ordinal = 1, startedAt = 2_000L, on = vector.copy(appVersionCode = 96))
        val phases = listOf(first, second)

        assertNull(StudyPhaseDecision.phaseAt(phases, 999L))
        assertEquals(first, StudyPhaseDecision.phaseAt(phases, 1_000L))
        assertEquals(first, StudyPhaseDecision.phaseAt(phases, 1_999L))
        assertEquals(second, StudyPhaseDecision.phaseAt(phases, 2_000L))
        assertEquals(second, StudyPhaseDecision.phaseAt(phases, Long.MAX_VALUE))
        assertEquals(second, StudyPhaseDecision.phaseAt(phases.reversed(), 2_000L))
        assertNull(StudyPhaseDecision.phaseAt(emptyList(), 1_000L))
    }

    @Test
    fun `phases starting in the same millisecond are ordered by ordinal`() {
        val first = phase(ordinal = 0, startedAt = 1_000L)
        val second = phase(ordinal = 1, startedAt = 1_000L, on = vector.copy(appVersionCode = 96))
        assertEquals(second, StudyPhaseDecision.phaseAt(listOf(second, first), 1_000L))
        assertEquals(second, StudyPhaseDecision.phaseAt(listOf(first, second), 1_000L))
    }
}
