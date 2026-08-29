package org.mindanchor.continuity

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindanchor.backup.BackupRepository
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.research.EvidenceProtocolCatalog
import org.mindanchor.research.LedgerChain
import org.mindanchor.research.MissingDataPolicy
import org.mindanchor.research.ResearchDataDictionary
import org.mindanchor.research.TransformationRegistry
import org.mindanchor.research.toDomain

/**
 * Builds the research export, once, here, so every caller writes the same
 * document rather than assembling one inline.
 *
 * Plaintext, unlike [org.mindanchor.continuity.crypto.BackupEnvelopeCodec]'s
 * encrypted continuity snapshot: this is the structured research JSON a
 * person (or a researcher, with explicit consent) can read directly.
 * Callers are responsible for their own privacy warning/consent step
 * before invoking [export] — this object only builds and writes the file.
 */
object ResearchExportBuilder {

    /** `mindanchor-research-YYYY-MM-DD.json`, from [today]'s local date (ISO-8601, e.g. "2026-08-29"). */
    fun fileName(today: LocalDate = LocalDate.now()): String = "mindanchor-research-$today.json"

    /** The first 12 hex characters of a content hash, for a short human-comparable success message. */
    fun truncatedHash(contentSha256: String): String = contentSha256.take(TRUNCATED_HASH_LENGTH)

    sealed class ExportOutcome {
        /** [contentSha256] is the FULL hash; callers show [truncatedHash] of it. */
        data class Success(val contentSha256: String) : ExportOutcome()
        data object WriteFailed : ExportOutcome()
    }

    /**
     * Assembles a [ResearchExport] from [database], seals it, and writes it
     * to [uri]. The whole thing runs on [Dispatchers.IO]: the Room reads
     * and the file write are both blocking.
     */
    suspend fun export(
        context: Context,
        database: AnchorDatabase,
        uri: Uri,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val export = build(
            database = database,
            now = now,
            zone = zone,
            appVersionCode = packageInfo.longVersionCode.toInt(),
            appVersionName = packageInfo.versionName.orEmpty(),
        )
        val wrote = BackupRepository.write(context, uri, ResearchExportCodec.encode(export))
        if (wrote) ExportOutcome.Success(export.contentSha256) else ExportOutcome.WriteFailed
    }

    /**
     * The document itself, without any file I/O. Separate from [export] so
     * a test can assemble and verify one without a document picker.
     */
    suspend fun build(
        database: AnchorDatabase,
        now: Long,
        zone: ZoneId,
        appVersionCode: Int,
        appVersionName: String,
    ): ResearchExport {
        val dao = database.journal()
        val research = database.research()

        val entries = dao.entriesNow().map { it.toDto() }
        val context = dao.allContext().map { it.toDto() }
        val measures = dao.morningMeasuresNow().map { it.toDto() }
        val ledger = research.ledgerEventsNow()
        val phases = research.studyPhasesNow().map { it.toDto() }

        val entryIdsWithContext = context.map { it.entryId }.toSet()
        val catalog = EvidenceProtocolCatalog.registry

        return ResearchExportCodec.seal(
            ResearchExport(
                exportedAt = now,
                appVersionCode = appVersionCode,
                appVersionName = appVersionName,
                contentSha256 = "",
                journalEntries = entries,
                contextFacts = context.filter { it.recordType == FACT },
                contextInferences = context.filter { it.recordType == INFERENCE },
                morningMeasures = measures,
                ledgerEvents = ledger.map { it.toDto() },
                ledgerHeadHash = LedgerChain.headHash(ledger.map { it.toDomain() }),
                ledgerEventCount = ledger.size,
                ledgerIntegrity = LedgerChain.verify(ledger.map { it.toDomain() }),
                studyPhases = phases,
                protocolRegistry = catalog.protocols,
                protocolCatalogSha256 = catalog.catalogSha256,
                transformations = TransformationRegistry.transformations,
                transformationSetVersion = TransformationRegistry.setVersion,
                missingData = MissingDataPolicy.report(
                    firstRecordDate = firstRecordDate(entries, measures, ledger.map { it.localDate }),
                    throughDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
                    allMeasureDates = measures.mapNotNull { parseDate(it.localDate) }.toSet(),
                    entryDatesWithoutContext = entries
                        .filterNot { it.id in entryIdsWithContext }
                        .mapNotNull { parseDate(it.localDate) }
                        .toSet(),
                ),
                missingDataPolicyVersion = MissingDataPolicy.VERSION,
                missingDataStatement = MissingDataPolicy.STATEMENT,
                dataDictionary = ResearchDataDictionary.dictionary,
                dataDictionarySha256 = ResearchDataDictionary.sha256,
            ),
        )
    }

    /**
     * The earliest local date anything was recorded on, across every kind
     * of record. Null when nothing has been — in which case the
     * missing-data report is empty, because inventing absences for a
     * person who has not started is not information.
     */
    private fun firstRecordDate(
        entries: List<JournalEntryDto>,
        measures: List<MorningMeasureDto>,
        ledgerDates: List<String>,
    ): LocalDate? = (entries.map { it.localDate } + measures.map { it.localDate } + ledgerDates)
        .mapNotNull { parseDate(it) }
        .minOrNull()

    /** A stored date that will not parse is skipped rather than crashing an export. */
    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull()

    private const val TRUNCATED_HASH_LENGTH = 12

    /** The two [org.mindanchor.journal.ContextRecordType] names, as stored. */
    private val FACT = org.mindanchor.journal.ContextRecordType.FACT.name
    private val INFERENCE = org.mindanchor.journal.ContextRecordType.INFERENCE.name
}
