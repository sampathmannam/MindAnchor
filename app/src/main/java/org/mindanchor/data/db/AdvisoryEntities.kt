package org.mindanchor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Program 3 Task 2 — the two append-only tables the advisory path writes.
 *
 * Both rows are evidence rather than state. An opportunity records that a
 * finalized historical observation was materialized into something the
 * person could be shown, together with every gate value that was true at
 * that moment; an episode is a hash-linked stream of events rather than a
 * row that gets updated, so "what happened" can be reconstructed and
 * cannot be rewritten.
 *
 * Every enum-like column stores `.name` and is decoded with
 * `enumValueOf`. An unknown value is rejected rather than mapped to a
 * neighbour, because a value this code does not recognise was written by
 * software this code is not.
 */

/**
 * One materialized advisory, identified by its own content.
 *
 * The source columns are copied rather than joined. A Program 2 decision
 * may be superseded by a corrected revision, and an advisory must keep
 * saying which decision content it was actually built from — including
 * the finalization time and local date it showed the person, so the
 * historical claim stays checkable after the source line has moved on.
 *
 * The gate columns record the build mode, evidence approval, master
 * opt-in, and delivery switch as they stood at presentation. Reading
 * them later is how a reviewer establishes that a presentation was
 * authorized without trusting today's settings.
 */
@Entity(tableName = "advisory_opportunities", indices = [Index("presentedAt"), Index("sourceDecisionId")])
data class AdvisoryOpportunityEntity(
    @PrimaryKey val id: String,
    val presentedAt: Long,
    val localDate: String,
    val zoneId: String,
    val sourceDecisionId: String,
    val sourceDecisionContentHash: String,
    val sourceLocalDate: String,
    val sourceAsOfTime: Long,
    val sourceDataStatus: String,
    val sourceObservationState: String,
    val sourceExplanation: String,
    val sourceBaselineSegment: String,
    val sourcePassiveRuleVersion: String,
    val sourcePassiveModelVersion: String,
    val sourceStudyPhaseId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val protocolClinicalReviewStatus: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowedAtPresentation: Boolean,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val contentHash: String,
)

/**
 * One event in one episode's hash-linked stream.
 *
 * There is deliberately no episode row. A mutable summary would have to
 * be updated as an episode progressed, and an updatable row is exactly
 * what this design refuses: the sequence, the link to the previous
 * event's hash, and the event's own hash together make an insertion,
 * removal, reordering, or edit detectable rather than merely discouraged.
 *
 * [payloadJson] carries a small fixed schema per event type and never
 * per-second samples, breath timestamps, raw wearable values, or any
 * free text.
 */
@Entity(
    tableName = "intervention_episode_events",
    indices = [
        Index(value = ["episodeId", "sequence"], unique = true),
        Index("opportunityId"),
        Index("occurredAt"),
    ],
)
data class InterventionEpisodeEventEntity(
    @PrimaryKey val id: String,
    val episodeId: String,
    val opportunityId: String,
    val sequence: Long,
    val eventType: String,
    val occurredAt: Long,
    val localDate: String,
    val zoneId: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val protocolDefinitionSha256: String,
    val protocolCatalogSha256: String,
    val advisoryRuleVersion: String,
    val buildMode: String,
    val operationalEvidenceApproved: Boolean,
    val masterAdvisoryEnabled: Boolean,
    val deliveryAllowed: Boolean,
    val payloadSchemaVersion: Int,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
)
