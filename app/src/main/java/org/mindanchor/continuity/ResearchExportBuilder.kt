package org.mindanchor.continuity

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.journal.ContextRecordType
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.LedgerChain
import org.mindanchor.research.LedgerIntegrity
import org.mindanchor.research.MissingDataPolicy
import org.mindanchor.research.MissingDataRecord
import org.mindanchor.research.ResearchDataDictionary
import org.mindanchor.research.ResearchLedgerEvent
import org.mindanchor.research.TransformationRegistry
import org.mindanchor.research.toDomain

/**
 * Builds the research export, once, here, so every caller writes the same
 * document rather than assembling one inline.
 *
 * Plaintext, unlike [org.mindanchor.continuity.crypto.BackupEnvelopeCodec]'s
 * encrypted continuity snapshot: this is the structured research JSON a
 * person (or a researcher, with explicit consent) can read directly.
 * Callers are responsible for their own privacy warning before invoking
 * [export] — and what that warning has to say is enforced by
 * `ResearchExportDisclosureTest`, because the file carries considerably
 * more than its name suggests.
 */
object ResearchExportBuilder {

    private data class RoomRows(
        val entries: List<JournalEntryDto>,
        val contextRows: List<JournalContextDto>,
        val measures: List<MorningMeasureDto>,
        val ledger: List<org.mindanchor.data.db.ResearchLedgerEventEntity>,
        val phases: List<StudyPhaseDto>,
        val passiveRawProvenance: List<PassiveRawProvenanceDto>,
        val passiveSourceReads: List<PassiveSourceReadDto>,
        val passiveSourceLags: List<PassiveSourceLagDto>,
        val passiveBaselineSegments: List<PassiveBaselineSegmentDto>,
        val passivePipelineRuns: List<PassivePipelineRunDto>,
        val passiveWindowRevisions: List<PassiveWindowRevisionDto>,
        val passiveDailyRevisions: List<PassiveDailyRevisionDto>,
        val passiveObservationDecisions: List<PassiveObservationDecisionDto>,
    )

    private suspend fun readRoomRows(
        database: AnchorDatabase,
        afterLedgerRead: suspend () -> Unit,
    ): RoomRows = database.withTransaction {
        val dao = database.journal()
        val research = database.research()
        val entries = dao.entriesNow().map { it.toDto() }
        val contextRows = dao.allContext().map { it.toDto() }
        val measures = dao.morningMeasuresNow().map { it.toDto() }
        val ledger = research.ledgerEventsNow()
        afterLedgerRead()
        val passive = database.passive()
        RoomRows(
            entries = entries,
            contextRows = contextRows,
            measures = measures,
            ledger = ledger,
            phases = research.studyPhasesNow().map { it.toDto() },
            passiveRawProvenance = passive.rawProvenanceNow().map { it.toDto() },
            passiveSourceReads = passive.sourceReadsNow().map { it.toDto() },
            passiveSourceLags = passive.sourceLagsNow().map { it.toDto() },
            passiveBaselineSegments = passive.baselineSegmentsNow().map { it.toDto() },
            passivePipelineRuns = passive.pipelineRunsNow().map { it.toDto() },
            passiveWindowRevisions = passive.windowRevisionsNow().map { it.toDto() },
            passiveDailyRevisions = passive.dailyRevisionsNow().map { it.toDto() },
            passiveObservationDecisions = passive.observationDecisionsNow().map { it.toDto() },
        )
    }

    /** `mindanchor-research-YYYY-MM-DD.json`, from [today]'s local date (ISO-8601, e.g. "2026-08-29"). */
    fun fileName(today: LocalDate = LocalDate.now()): String = "mindanchor-research-$today.json"

    /** The first 12 hex characters of a content hash, for a short human-comparable success message. */
    fun truncatedHash(contentSha256: String): String = contentSha256.take(TRUNCATED_HASH_LENGTH)

    sealed class ExportOutcome {
        /** [contentSha256] is the FULL hash; callers show [truncatedHash] of it. */
        data class Success(val contentSha256: String) : ExportOutcome()

        /** The document could not be assembled. Nothing was written. */
        data object BuildFailed : ExportOutcome()

        data object WriteFailed : ExportOutcome()
    }

    /**
     * Assembles a [ResearchExport] from [database], seals it, and writes it
     * to [uri].
     *
     * Never throws an [Exception]. The document picker has already created
     * the file by the time this runs, so an escaping exception would leave
     * the person with a zero-byte export, no error line, and — on a scope
     * with no handler — a crash. A typed [ExportOutcome.BuildFailed] is
     * what a caller can actually show.
     *
     * An [Error] is deliberately *not* caught, and a [CancellationException]
     * is rethrown: the first is not a condition a message can help with,
     * and the second is the caller tearing the coroutine down, where
     * "couldn't export the file" would be a wrong statement rather than a
     * wrong outcome.
     */
    @Suppress("detekt.SwallowedException")
    suspend fun export(
        context: Context,
        database: AnchorDatabase,
        uri: Uri,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        writeExport: (Context, Uri, String) -> Boolean = BackupRepository::write,
    ): ExportOutcome {
        val export = try {
            withContext(Dispatchers.IO) {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                build(
                    database = database,
                    highWater = ContinuityPrefs(context).ledgerHighWater.first(),
                    now = now,
                    zone = zone,
                    appVersionCode = packageInfo.longVersionCode.toInt(),
                    appVersionName = packageInfo.versionName.orEmpty(),
                )
            }
        } catch (cancellation: CancellationException) {
            // Not a build failure. Reporting one would show the person
            // "Couldn't export the file" for an export they navigated away
            // from, which is a wrong statement rather than a wrong outcome.
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            return ExportOutcome.BuildFailed
        }

        val wrote = withContext(Dispatchers.IO) {
            writeExport(context, uri, ResearchExportCodec.encode(export))
        }
        return if (wrote) ExportOutcome.Success(export.contentSha256) else ExportOutcome.WriteFailed
    }

    /**
     * The document itself, without any file I/O. Separate from [export] so
     * a test can assemble and verify one without a document picker.
     *
     * [highWater] is the largest ledger this device has ever held, read
     * from [ContinuityPrefs]. It is what makes the exported integrity
     * verdict mean something: the anchor carried *in* the file is derived
     * from the very list the file contains, so on its own it can only tell
     * a recipient whether the file changed after they received it.
     * Comparing against a count recorded elsewhere is what can notice rows
     * having gone missing before the export was taken.
     */
    @Suppress("LongMethod", "LongParameterList")
    suspend fun build(
        database: AnchorDatabase,
        highWater: ContinuityPrefs.LedgerHighWater?,
        now: Long,
        zone: ZoneId,
        appVersionCode: Int,
        appVersionName: String,
        afterLedgerRead: suspend () -> Unit = {},
    ): ResearchExport = withContext(Dispatchers.IO) {
        val rows = readRoomRows(database, afterLedgerRead)
        val entries = rows.entries
        val contextRows = rows.contextRows
        val measures = rows.measures
        val ledger = rows.ledger
        val phases = rows.phases

        val exportDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val window = MissingDataPolicy.windowFor(
            recordDates = (entries.map { it.localDate } + measures.map { it.localDate } + ledger.map { it.localDate })
                .mapNotNull { parseDate(it) },
            exportDate = exportDate,
        )
        val ledgerDomain = ledger.map { it.toDomain() }
        val catalog = EvidenceProtocolCatalog.registry
        val facts = contextRows.filter { it.recordType == ContextRecordType.FACT.name }

        ResearchExportCodec.seal(
            ResearchExport(
                exportedAt = now,
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                contentSha256 = "",
                journalEntries = entries,
                contextFacts = facts,
                contextInferences = contextRows.filter { it.recordType == ContextRecordType.INFERENCE.name },
                morningMeasures = measures,
                ledgerEvents = ledger.map { it.toDto() },
                ledgerHeadHash = LedgerChain.headHash(ledgerDomain),
                ledgerEventCount = ledger.size,
                ledgerIntegrity = integrityOf(ledgerDomain, highWater),
                ledgerHighWaterCount = highWater?.eventCount ?: 0,
                studyPhases = phases,
                protocolRegistry = catalog.protocols,
                protocolCatalogSha256 = catalog.catalogSha256,
                transformations = TransformationRegistry.transformations,
                transformationSetVersion = TransformationRegistry.setVersion,
                missingData = missingDataReport(
                    window = window,
                    entries = entries,
                    measures = measures,
                    entryIdsWithFacts = facts.map { it.entryId }.toSet(),
                    exportDate = exportDate,
                ),
                missingDataWindowStart = window?.start?.toString(),
                missingDataWindowThrough = window?.through?.toString(),
                missingDataPolicyVersion = MissingDataPolicy.VERSION,
                missingDataStatement = MissingDataPolicy.STATEMENT,
                dataDictionary = ResearchDataDictionary.dictionary,
                dataDictionarySha256 = ResearchDataDictionary.sha256,
                passiveRawProvenance = rows.passiveRawProvenance,
                passiveSourceReads = rows.passiveSourceReads,
                passiveSourceLags = rows.passiveSourceLags,
                passiveBaselineSegments = rows.passiveBaselineSegments,
                passivePipelineRuns = rows.passivePipelineRuns,
                passiveWindowRevisions = rows.passiveWindowRevisions,
                passiveDailyRevisions = rows.passiveDailyRevisions,
                passiveObservationDecisions = rows.passiveObservationDecisions,
            ),
        )
    }

    /**
     * The chain's own verdict, downgraded when the ledger has shrunk below
     * the largest this device has ever held.
     *
     * The high-water mark only ever rises, so it being *behind* the current
     * ledger means nothing — a write that did not refresh it, or a ledger
     * restored onto a phone that has not written since. It being *ahead*
     * means events that existed are gone, which the chain cannot see for
     * itself: what remains after a truncation is a shorter but perfectly
     * self-consistent history.
     */
    private fun integrityOf(
        events: List<ResearchLedgerEvent>,
        highWater: ContinuityPrefs.LedgerHighWater?,
    ): LedgerIntegrity {
        val chain = LedgerChain.verify(events)
        if (chain != LedgerIntegrity.VERIFIED) return chain
        val shrunk = highWater != null && highWater.eventCount > events.size
        return if (shrunk) LedgerIntegrity.BROKEN else LedgerIntegrity.VERIFIED
    }

    /**
     * Every absence across [window], which [build] chose from the dates
     * actually recorded.
     *
     * The window choice lives in the policy rather than here because it is
     * policy: which records are allowed to define the span a report claims
     * to cover. This function's only job is to gather the dates.
     */
    private fun missingDataReport(
        window: MissingDataPolicy.ReportWindow?,
        entries: List<JournalEntryDto>,
        measures: List<MorningMeasureDto>,
        entryIdsWithFacts: Set<String>,
        exportDate: LocalDate,
    ): List<MissingDataRecord> {
        if (window == null) return emptyList()
        return MissingDataPolicy.report(
            firstRecordDate = window.start,
            throughDate = window.through,
            // Plausibility-filtered, which for measures is the same set
            // as window-filtered -- the window's start is the earliest
            // plausible record and its end is the export date, so the two
            // coincide. The filter is stated as plausibility because that
            // is the *reason*: an implausible date must not get a vote
            // (one row from the year 1000 made every skipped day read as
            // "had not started yet"), and the equivalence is a property of
            // today's window rule, not a contract.
            allMeasureDates = measures
                .mapNotNull { parseDate(it.localDate) }
                .filter { MissingDataPolicy.isPlausible(it, exportDate) }
                .toSet(),
            entryDatesWithoutContext = entries
                .filterNot { it.id in entryIdsWithFacts }
                .mapNotNull { parseDate(it.localDate) }
                .toSet(),
        )
    }

    /** A stored date that will not parse is skipped rather than crashing an export. */
    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    private const val TRUNCATED_HASH_LENGTH = 12
}
