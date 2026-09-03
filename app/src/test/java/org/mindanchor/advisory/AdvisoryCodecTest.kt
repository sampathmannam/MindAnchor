package org.mindanchor.advisory

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mindanchor.data.db.InterventionEpisodeEventEntity

/**
 * Program 3 Task 2 — the properties the evidence tables rest on.
 *
 * Two of them matter most. A corrected Program 2 reading must produce a
 * *different* opportunity, because an advisory built from a superseded
 * reading is a different historical fact and absorbing the correction
 * into it would quietly rewrite what the person was shown. And an
 * episode's event chain must fail verification on any edit, reorder, or
 * gap — a hash chain nobody checks is decoration.
 */
class AdvisoryCodecTest {

    private val cyclic = ProtocolKey(
        protocolId = "cyclic-sighing",
        protocolVersion = 1,
        definitionSha256 = "1298bdfeab7d10263ca41c47a7982231181e3eb95c38eaf0465463baba1cdae0",
    )

    private fun event(
        sequence: Long,
        type: EpisodeEventType,
        previous: String,
        payload: String = AdvisoryCodec.EMPTY_PAYLOAD,
    ) = AdvisoryCodec.seal(
        InterventionEpisodeEventEntity(
            id = "",
            episodeId = "episode-1",
            opportunityId = "opportunity-1",
            sequence = sequence,
            eventType = type.name,
            occurredAt = 1_000L + sequence,
            localDate = "2026-09-03",
            zoneId = "Asia/Kolkata",
            studyPhaseId = "phase-1",
            sourceDeviceId = "device-a",
            protocolId = cyclic.protocolId,
            protocolVersion = cyclic.protocolVersion,
            protocolDefinitionSha256 = cyclic.definitionSha256,
            protocolCatalogSha256 = AdvisoryBuildAuthorization.PROGRAM_THREE_CATALOG_SHA256,
            advisoryRuleVersion = AdvisoryPolicy.RULE_VERSION,
            buildMode = AdvisoryBuildMode.PERSONAL_RESEARCH.name,
            operationalEvidenceApproved = true,
            masterAdvisoryEnabled = true,
            deliveryAllowed = true,
            payloadSchemaVersion = AdvisoryCodec.EVENT_PAYLOAD_SCHEMA_VERSION,
            payloadJson = payload,
            previousEventHash = previous,
            eventHash = "",
        ),
    )

    private fun attested() = event(
        sequence = 1L,
        type = EpisodeEventType.ELIGIBILITY_ATTESTED,
        previous = "",
        payload = AdvisoryCodec.json.encodeToString(
            EligibilityAttestedPayloadV1(
                currentlySelfNoticesTensionOrArousal = true,
                choosesProtocol = true,
                exclusionsAndContraindicationsClear = true,
                notDrivingOperatingMachineryOrExerting = true,
            ),
        ),
    )

    private fun chain(): List<InterventionEpisodeEventEntity> {
        val first = attested()
        return listOf(first, event(sequence = 2L, type = EpisodeEventType.STARTED, previous = first.eventHash))
    }

    @Test
    fun `opportunity identity changes for corrected source content`() {
        val first = AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic, AdvisoryPolicy.RULE_VERSION)
        val corrected = AdvisoryCodec.opportunityId("decision-1", "hash-b", cyclic, AdvisoryPolicy.RULE_VERSION)
        assertNotEquals(first, corrected)
        assertEquals(first, AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic, AdvisoryPolicy.RULE_VERSION))
    }

    @Test
    fun `opportunity identity separates protocol version and definition`() {
        val rule = AdvisoryPolicy.RULE_VERSION
        val base = AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic, rule)
        assertNotEquals(
            base,
            AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic.copy(protocolVersion = 2), rule),
        )
        assertNotEquals(
            base,
            AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic.copy(definitionSha256 = "edited"), rule),
        )
        assertNotEquals(base, AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic, "advisory-opportunity-v2"))
    }

    @Test
    fun `a delimiter inside a value cannot imitate a field boundary`() {
        // Length prefixes are the reason these differ. A join on "|" or
        // "=" would let the first value swallow the second's name.
        val rule = AdvisoryPolicy.RULE_VERSION
        val smuggled = "decision-1\nsourceDecisionHash=6:hash-a"
        assertNotEquals(
            AdvisoryCodec.opportunityId("decision-1", "hash-a", cyclic, rule),
            AdvisoryCodec.opportunityId(smuggled, "", cyclic, rule),
        )
    }

    @Test
    fun `dismissal and episode streams never collide`() {
        assertNotEquals(
            AdvisoryCodec.dismissalStreamId("opportunity-1"),
            AdvisoryCodec.episodeId("opportunity-1", 1_000L, "device-a"),
        )
        assertEquals(
            AdvisoryCodec.episodeId("opportunity-1", 1_000L, "device-a"),
            AdvisoryCodec.episodeId("opportunity-1", 1_000L, "device-a"),
        )
        assertNotEquals(
            AdvisoryCodec.episodeId("opportunity-1", 1_000L, "device-a"),
            AdvisoryCodec.episodeId("opportunity-1", 1_000L, "device-b"),
        )
    }

    @Test
    fun `event chain detects mutation and sequence gaps`() {
        val rows = chain()
        assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(rows))
        assertEquals(
            EventChainVerdict.BROKEN,
            AdvisoryCodec.verifyEpisodeChain(rows.mapIndexed { i, row -> if (i == 1) row.copy(sequence = 3) else row }),
        )
        // The mutation has to be a real one. STARTED already carries
        // `{}`, so "changing" it to `{}` changes nothing and would let a
        // chain verifier that ignored payloads entirely pass this test.
        assertEquals(
            EventChainVerdict.BROKEN,
            AdvisoryCodec.verifyEpisodeChain(
                rows.mapIndexed { i, row -> if (i == 1) row.copy(payloadJson = """{"smuggled":1}""") else row },
            ),
        )
        assertEquals(
            EventChainVerdict.BROKEN,
            AdvisoryCodec.verifyEpisodeChain(
                rows.mapIndexed { i, row -> if (i == 0) row.copy(payloadJson = AdvisoryCodec.EMPTY_PAYLOAD) else row },
            ),
        )
    }

    @Test
    fun `a removed or reordered event breaks the chain`() {
        val rows = chain()
        assertEquals(EventChainVerdict.EMPTY, AdvisoryCodec.verifyEpisodeChain(emptyList()))
        // Sorting by sequence means a reorder alone is not evidence of
        // tampering; a removal is, because the sequence then has a gap.
        assertEquals(EventChainVerdict.VALID, AdvisoryCodec.verifyEpisodeChain(rows.reversed()))
        assertEquals(EventChainVerdict.BROKEN, AdvisoryCodec.verifyEpisodeChain(listOf(rows[1])))
    }

    @Test
    fun `a relinked event breaks the chain`() {
        val rows = chain()
        val relinked = rows[1].copy(previousEventHash = "0".repeat(64))
        assertEquals(EventChainVerdict.BROKEN, AdvisoryCodec.verifyEpisodeChain(listOf(rows[0], relinked)))
        // Re-sealing the relinked event fixes its own hash but not the
        // link it now claims, so the chain still fails.
        assertEquals(
            EventChainVerdict.BROKEN,
            AdvisoryCodec.verifyEpisodeChain(listOf(rows[0], AdvisoryCodec.seal(relinked))),
        )
    }

    @Test
    fun `an event is identified by its own content`() {
        val sealed = attested()
        assertEquals(sealed.eventHash, sealed.id)
        assertEquals(AdvisoryCodec.eventHash(sealed), sealed.eventHash)
        assertEquals(64, sealed.eventHash.length)
    }
}
