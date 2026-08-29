package org.mindanchor.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One immutable, hash-chained research ledger event.
 *
 * [id] is the event's own hash — the row is content-addressed, which is
 * what makes a replacement-phone restore duplicate-free without a
 * de-duplication pass: re-inserting an event the database already holds is
 * an `INSERT OR IGNORE` on the same primary key.
 *
 * The table is append-only, enforced three ways: `ResearchDao` declares no
 * mutating operation, `MIGRATION_6_7` installs `BEFORE UPDATE` and
 * `BEFORE DELETE` triggers that `RAISE(ABORT, ...)`, and every row's hash
 * covers its predecessor's.
 */
@Entity(
    tableName = "research_ledger_events",
    indices = [
        Index(value = ["sequence"], unique = true),
        Index("recordedAt"),
        Index("kind"),
        Index("studyPhaseId"),
        Index("localDate"),
    ],
)
data class ResearchLedgerEventEntity(
    @PrimaryKey val id: String,
    val sequence: Long,
    val kind: String,
    val occurredAt: Long,
    val recordedAt: Long,
    val localDate: String,
    val studyPhaseId: String,
    val sourceDeviceId: String,
    val note: String,
    val payloadJson: String,
    val previousEventHash: String,
    val eventHash: String,
)

/**
 * One study phase: the window during which a particular provenance version
 * vector was in effect.
 *
 * **No end timestamp, deliberately.** A phase runs until the next one
 * starts. Writing an end onto a historical row would be a mutation of
 * history — which the triggers on this table would reject anyway, so the
 * shape has to make the mutation unnecessary rather than merely forbidden.
 *
 * The unique index on [ordinal] is the invariant that keeps the sequence
 * of phases a sequence. Combined with `INSERT OR IGNORE`, a restore that
 * somehow carried a conflicting ordinal is dropped rather than allowed to
 * corrupt the order; the restore preflight already requires this table to
 * be empty, so that path should not be reachable.
 */
@Entity(
    tableName = "study_phases",
    indices = [Index(value = ["ordinal"], unique = true), Index("startedAt")],
)
data class StudyPhaseEntity(
    @PrimaryKey val id: String,
    val ordinal: Int,
    val startedAt: Long,
    val reason: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val protocolCatalogSha256: String,
    val ruleSetVersion: String,
    val modelSetVersion: String,
    val transformationSetVersion: String,
    val missingDataPolicyVersion: String,
    val instrumentVersion: String,
    val dictionaryVersion: String,
    val sourceDeviceId: String,
)
