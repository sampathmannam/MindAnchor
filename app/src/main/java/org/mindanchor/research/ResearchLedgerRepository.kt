package org.mindanchor.research

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mindanchor.continuity.ContinuityPrefs
import org.mindanchor.continuity.ContinuityWorkScheduler
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.ContinuityChangeEntity
import org.mindanchor.data.db.ResearchLedgerEventEntity
import org.mindanchor.data.db.StudyPhaseEntity
import org.mindanchor.journal.ChangeOperation
import org.mindanchor.journal.DeviceIdentityStore

/**
 * Owns the research ledger: the append-only, hash-chained record of what
 * the person reported about a day and of every version change MindAnchor
 * made underneath them.
 *
 * Also the Room implementation of [ResearchProvenanceStore], so
 * [ResearchProvenanceCoordinator] can open study phases inside the same
 * transaction that appends their events.
 *
 * Nothing here interprets a record. [record] stores the kind the person
 * chose, the time they said it happened, and their own words, and that is
 * the whole of it — no scoring, no inference, and for
 * [LedgerEventKind.MEDICATION_CHANGE] no advice of any kind.
 *
 * `open` for one reason: a test overrides [appendEvents] to fail after the
 * phase insert has succeeded, which is the only way to prove the shared
 * transaction actually rolls both back.
 */
open class ResearchLedgerRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val currentVector: suspend () -> ProvenanceVector,
    private val localDateOf: (Long) -> String = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    },
) : ResearchProvenanceStore {

    private val dao = database.research()
    private val journalDao = database.journal()

    /** Opens study phases. Exposed so Journal and morning-measure writes can share this instance. */
    val provenance = ResearchProvenanceCoordinator(
        store = this,
        currentVector = currentVector,
        localDateOf = localDateOf,
    )

    /**
     * Records one self-reported event: something that might explain a day.
     *
     * [occurredAt] is when the person says it happened, which can be
     * earlier than [now]; [note] is their own words, trimmed and stored
     * verbatim. The whole write runs in one transaction with the phase
     * check, so an event can never be attributed to a phase that failed to
     * open.
     *
     * @throws IllegalArgumentException if [kind] is not self-reported, or
     *   the note is longer than [MAX_LEDGER_NOTE_LENGTH]. Nothing is
     *   written in either case.
     */
    suspend fun record(
        kind: LedgerEventKind,
        occurredAt: Long,
        note: String,
        now: Long = System.currentTimeMillis(),
    ): ResearchLedgerEvent {
        require(kind.isSelfReported) {
            "$kind is recorded by MindAnchor about itself, not by the person"
        }
        val trimmed = note.trim()
        require(trimmed.length <= MAX_LEDGER_NOTE_LENGTH) {
            "a research note must be at most $MAX_LEDGER_NOTE_LENGTH characters, was ${trimmed.length}"
        }

        val event = database.withTransaction {
            val phase = provenance.ensureCurrentPhase(now)
            val head = dao.ledgerHead()?.toDomain()
            val linked = LedgerChain.link(
                UnlinkedLedgerEvent(
                    sequence = LedgerChain.nextSequence(listOfNotNull(head)),
                    kind = kind,
                    occurredAt = occurredAt,
                    recordedAt = now,
                    localDate = localDateOf(occurredAt),
                    studyPhaseId = phase.id,
                    sourceDeviceId = phase.vector.sourceDeviceId,
                    note = trimmed,
                    payloadJson = EMPTY_PAYLOAD,
                ),
                head?.eventHash ?: LedgerChain.GENESIS_PREVIOUS_HASH,
            )
            appendEvents(listOf(linked))
            linked
        }
        ContinuityWorkScheduler.requestCheckpoint(context)
        raiseLedgerHighWater()
        return event
    }

    /**
     * Raises the device's record of the largest ledger it has held.
     *
     * Outside the transaction on purpose: this is a DataStore write, and
     * holding the SQLite write lock across an fsync is the one thing the
     * provenance coordinator already goes out of its way to avoid.
     *
     * Best-effort, and safe to miss. The mark only ever rises, so a path
     * that appends without refreshing it weakens truncation detection
     * until the next write and can never raise a false alarm. A failure
     * here must not cost the person the event they just recorded, which is
     * already committed by this point.
     */
    override suspend fun afterLedgerGrew() = raiseLedgerHighWater()

    @Suppress("detekt.SwallowedException")
    private suspend fun raiseLedgerHighWater() {
        try {
            val head = dao.ledgerHead()
            if (head != null) {
                ContinuityPrefs(context).raiseLedgerHighWater(dao.ledgerEventCount(), head.eventHash)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // Best-effort by design -- see the KDoc above. A failure here
            // must not cost the person the event already committed.
        }
    }

    /** The whole ledger, in chain order. */
    fun events(): Flow<List<ResearchLedgerEvent>> =
        dao.ledgerEvents().map { rows -> rows.map { it.toDomain() } }

    /**
     * Just the events the person recorded on [localDate]. Indexed by date
     * rather than filtered from the whole ledger, so opening Journal does
     * not re-read and re-map every row ever written.
     */
    fun selfReportedOn(localDate: String): Flow<List<ResearchLedgerEvent>> =
        dao.ledgerEventsOn(localDate).map { rows ->
            rows.map { it.toDomain() }.filter { it.kind.isSelfReported }
        }

    // --- ResearchProvenanceStore ------------------------------------

    override suspend fun inTransaction(block: suspend () -> StudyPhase): StudyPhase =
        database.withTransaction { block() }

    override suspend fun latestPhase(): StudyPhase? = dao.latestStudyPhase()?.toDomain()

    override suspend fun ledgerHead(): ResearchLedgerEvent? = dao.ledgerHead()?.toDomain()

    override suspend fun registeredProtocolPayloads(): List<String> =
        dao.payloadsForKind(LedgerEventKind.PROTOCOL_VERSION_REGISTERED.name)

    /**
     * `INSERT OR IGNORE` treats a conflict as success, so the row id is
     * checked rather than assumed. A silently dropped phase would leave
     * every event of that phase pointing at a row that does not exist, in
     * a table that can never be repaired.
     */
    override suspend fun insertPhase(phase: StudyPhase) {
        val rowId = dao.insertStudyPhase(phase.toEntity())
        check(rowId != IGNORED_ROW_ID) {
            "study phase ${phase.ordinal} was ignored: a row with that id or ordinal already exists"
        }
        recordContinuityChange("STUDY_PHASE", phase.id, phase.startedAt)
    }

    override suspend fun appendEvents(events: List<ResearchLedgerEvent>) {
        val rowIds = dao.insertLedgerEvents(events.map { it.toEntity() })
        check(rowIds.none { it == IGNORED_ROW_ID }) {
            "a ledger event was ignored: an event with that hash is already recorded"
        }
        events.forEach { recordContinuityChange("RESEARCH_LEDGER_EVENT", it.id, it.recordedAt) }
    }

    /** Marks the row for the next encrypted checkpoint, the same way Journal and measure writes do. */
    private suspend fun recordContinuityChange(entityType: String, entityId: String, occurredAt: Long) {
        journalDao.insertChange(
            ContinuityChangeEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                operation = ChangeOperation.CREATE.name,
                occurredAt = occurredAt,
                acknowledgedSnapshotId = null,
            ),
        )
    }

    companion object {
        private const val EMPTY_PAYLOAD = "{}"

        /** What SQLite returns from an `INSERT OR IGNORE` that inserted nothing. */
        private const val IGNORED_ROW_ID = -1L

        /** Wires the production dependencies, the same shape `RestoreCoordinator.build` uses. */
        fun build(context: Context): ResearchLedgerRepository {
            val app = context.applicationContext
            val deviceIdentity = DeviceIdentityStore(app)
            return ResearchLedgerRepository(
                context = app,
                database = AnchorDatabase.get(app),
                currentVector = {
                    val packageInfo = app.packageManager.getPackageInfo(app.packageName, 0)
                    ProvenanceVersions.vector(
                        appVersionCode = packageInfo.longVersionCode.toInt(),
                        appVersionName = packageInfo.versionName.orEmpty(),
                        sourceDeviceId = deviceIdentity.id(),
                    )
                },
            )
        }
    }
}

// --- Domain <-> Room mapping. Kept here rather than on the domain types
// so StudyPhase and ResearchLedgerEvent stay free of storage concerns.

internal fun ResearchLedgerEvent.toEntity(): ResearchLedgerEventEntity = ResearchLedgerEventEntity(
    id = id,
    sequence = sequence,
    kind = kind.name,
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    localDate = localDate,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    note = note,
    payloadJson = payloadJson,
    previousEventHash = previousEventHash,
    eventHash = eventHash,
)

internal fun ResearchLedgerEventEntity.toDomain(): ResearchLedgerEvent = ResearchLedgerEvent(
    id = id,
    sequence = sequence,
    kind = LedgerEventKind.valueOf(kind),
    occurredAt = occurredAt,
    recordedAt = recordedAt,
    localDate = localDate,
    studyPhaseId = studyPhaseId,
    sourceDeviceId = sourceDeviceId,
    note = note,
    payloadJson = payloadJson,
    previousEventHash = previousEventHash,
    eventHash = eventHash,
)

internal fun StudyPhase.toEntity(): StudyPhaseEntity = StudyPhaseEntity(
    id = id,
    ordinal = ordinal,
    startedAt = startedAt,
    reason = reason.name,
    appVersionCode = vector.appVersionCode,
    appVersionName = vector.appVersionName,
    protocolCatalogSha256 = vector.protocolCatalogSha256,
    ruleSetVersion = vector.ruleSetVersion,
    modelSetVersion = vector.modelSetVersion,
    transformationSetVersion = vector.transformationSetVersion,
    missingDataPolicyVersion = vector.missingDataPolicyVersion,
    instrumentVersion = vector.instrumentVersion,
    dictionaryVersion = vector.dictionaryVersion,
    sourceDeviceId = vector.sourceDeviceId,
)

internal fun StudyPhaseEntity.toDomain(): StudyPhase = StudyPhase(
    id = id,
    ordinal = ordinal,
    startedAt = startedAt,
    reason = StudyPhaseReason.valueOf(reason),
    vector = ProvenanceVector(
        appVersionCode = appVersionCode,
        appVersionName = appVersionName,
        protocolCatalogSha256 = protocolCatalogSha256,
        ruleSetVersion = ruleSetVersion,
        modelSetVersion = modelSetVersion,
        transformationSetVersion = transformationSetVersion,
        missingDataPolicyVersion = missingDataPolicyVersion,
        instrumentVersion = instrumentVersion,
        dictionaryVersion = dictionaryVersion,
        sourceDeviceId = sourceDeviceId,
    ),
)
