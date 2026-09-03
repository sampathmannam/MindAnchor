package org.mindanchor.continuity

import android.content.Context
import androidx.room.withTransaction
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.advisory.AdvisoryOutcomeReconciler
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.journal.DeviceIdentityStore
import org.mindanchor.letters.LetterStore

/**
 * Captures the one canonical [ContinuitySnapshot] for this device: every
 * Room table and DataStore Task 7 protects, gathered, sorted into
 * canonical order, and hashed. Local-only — no network call is made
 * anywhere in this class; the actual upload is a later task.
 */
@Suppress("LongParameterList")
class ContinuitySnapshotRepository(
    private val context: Context,
    private val database: AnchorDatabase,
    private val notesPrefs: NotesPrefs,
    private val letterStore: LetterStore,
    private val frictionPrefs: FrictionPrefs,
    private val deviceIdentity: DeviceIdentityStore,
    private val backupRepository: BackupRepository,
    private val afterResearchLedgerRead: suspend () -> Unit = {},
    private val advisoryOutcomeReconciler: AdvisoryOutcomeReconciler? = null,
) {

    private data class RoomRows(
        val journalEntries: List<JournalEntryDto>,
        val contextRows: List<JournalContextDto>,
        val morningMeasures: List<MorningMeasureDto>,
        val continuityChanges: List<ContinuityChangeDto>,
        val researchLedgerEvents: List<ResearchLedgerEventDto>,
        val studyPhases: List<StudyPhaseDto>,
        val passiveRawProvenance: List<PassiveRawProvenanceDto>,
        val passiveSourceReads: List<PassiveSourceReadDto>,
        val passiveSourceLags: List<PassiveSourceLagDto>,
        val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
        val passivePipelineRuns: List<PassivePipelineRunDto>,
        val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
        val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
        val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
        val advisoryOpportunities: List<AdvisoryOpportunityDto>,
        val interventionEpisodeEvents: List<InterventionEpisodeEventDto>,
    )

    @Suppress("LongMethod")
    suspend fun capture(
        now: Long,
        reconcileDueOutcomes: Boolean = true,
    ): ContinuitySnapshot = withContext(Dispatchers.IO) {
        if (reconcileDueOutcomes) {
            advisoryOutcomeReconciler?.reconcile(now = now, zoneId = ZoneId.systemDefault(), requestCheckpoint = false)
        }
        val dao = database.journal()
        val researchDao = database.research()
        val passive = database.passive()
        val advisory = database.advisory()
        val roomRows = database.withTransaction {
            val journalEntries = dao.entriesNow().map { it.toDto() }
            val contextRows = dao.allContext().map { it.toDto() }
            val morningMeasures = dao.morningMeasuresNow().map { it.toDto() }
            val continuityChanges = dao.allChangesNow().map { it.toDto() }
            val researchLedgerEvents = researchDao.ledgerEventsNow().map { it.toDto() }
            afterResearchLedgerRead()
            RoomRows(
                journalEntries = journalEntries,
                contextRows = contextRows,
                morningMeasures = morningMeasures,
                continuityChanges = continuityChanges,
                researchLedgerEvents = researchLedgerEvents,
                studyPhases = researchDao.studyPhasesNow().map { it.toDto() },
                passiveRawProvenance = passive.rawProvenanceNow().map { it.toDto() },
                passiveSourceReads = passive.sourceReadsNow().map { it.toDto() },
                passiveSourceLags = passive.sourceLagsNow().map { it.toDto() },
                passiveBaselineSegments = passive.baselineSegmentsNow().map { it.toDto() },
                passivePipelineRuns = passive.pipelineRunsNow().map { it.toDto() },
                passiveWindowRevisions = passive.windowRevisionsNow().map { it.toDto() },
                passiveDailyRevisions = passive.dailyRevisionsNow().map { it.toDto() },
                passiveObservationDecisions = passive.observationDecisionsNow().map { it.toDto() },
                advisoryOpportunities = advisory.opportunitiesNow().map { it.toDto() },
                interventionEpisodeEvents = advisory.eventsNow().map { it.toDto() },
            )
        }

        val rawPayload = ContinuityPayload(
            journalEntries = roomRows.journalEntries,
            contextRows = roomRows.contextRows,
            morningMeasures = roomRows.morningMeasures,
            notes = notesPrefs.notes.first().notes.map { it.toDto() },
            letters = letterStore.letters.first().map { it.toDto() },
            readLetterDates = letterStore.readDates.first().map { it.toString() },
            frictionedApps = frictionPrefs.flaggedApps.first().toList(),
            alwaysOpenApps = frictionPrefs.alwaysOpen.first().toList(),
            continuityChanges = roomRows.continuityChanges,
            legacyBackupJson = backupRepository.export(now),
            researchLedgerEvents = roomRows.researchLedgerEvents,
            studyPhases = roomRows.studyPhases,
            passiveRawProvenance = roomRows.passiveRawProvenance,
            passiveSourceReads = roomRows.passiveSourceReads,
            passiveSourceLags = roomRows.passiveSourceLags,
            passiveBaselineSegments = roomRows.passiveBaselineSegments,
            passivePipelineRuns = roomRows.passivePipelineRuns,
            passiveWindowRevisions = roomRows.passiveWindowRevisions,
            passiveDailyRevisions = roomRows.passiveDailyRevisions,
            passiveObservationDecisions = roomRows.passiveObservationDecisions,
            advisoryOpportunities = roomRows.advisoryOpportunities,
            interventionEpisodeEvents = roomRows.interventionEpisodeEvents,
        )
        val payload = ContinuityContentHasher.sorted(rawPayload)
        // Explicit rather than defaulted: the version stamped on the
        // snapshot below and the version the hash is computed under must be
        // the same one, and a default argument makes that coupling
        // invisible.
        val contentSha256 = ContinuityContentHasher.hash(payload, ContinuitySnapshot.CURRENT_FORMAT_VERSION)

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

        ContinuitySnapshot(
            formatVersion = ContinuitySnapshot.CURRENT_FORMAT_VERSION,
            snapshotId = UUID.randomUUID().toString(),
            createdAt = now,
            appVersionCode = packageInfo.longVersionCode.toInt(),
            appVersionName = packageInfo.versionName.orEmpty(),
            sourceDeviceId = deviceIdentity.id(),
            payload = payload,
            contentSha256 = contentSha256,
        )
    }
}
