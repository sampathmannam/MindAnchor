package org.mindanchor.continuity

import android.content.Context
import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
     * Never throws. The document picker has already created the file by the
     * time this runs, so an escaping exception would leave the person with
     * a zero-byte export, no error line, and — on a scope with no handler —
     * a crash. A typed [ExportOutcome.BuildFailed] is what a caller can
     * actually show.
     */
    suspend fun export(
        context: Context,
        database: AnchorDatabase,
        uri: Uri,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExportOutcome {
        val export = runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            build(
                database = database,
                highWater = ContinuityPrefs(context).ledgerHighWater.first(),
                now = now,
                zone = zone,
                appVersionCode = packageInfo.longVersionCode.toInt(),
                appVersionName = packageInfo.versionName.orEmpty(),
            )
        }.getOrElse { return ExportOutcome.BuildFailed }

        val wrote = withContext(Dispatchers.IO) {
            BackupRepository.write(context, uri, ResearchExportCodec.encode(export))
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
    @Suppress("LongParameterList")
    suspend fun build(
        database: AnchorDatabase,
        highWater: ContinuityPrefs.LedgerHighWater?,
        now: Long,
        zone: ZoneId,
        appVersionCode: Int,
        appVersionName: String,
    ): ResearchExport = withContext(Dispatchers.IO) {
        val dao = database.journal()
        val research = database.research()

        val entries = dao.entriesNow().map { it.toDto() }
        val contextRows = dao.allContext().map { it.toDto() }
        val measures = dao.morningMeasuresNow().map { it.toDto() }
        val ledger = research.ledgerEventsNow()
        val phases = research.studyPhasesNow().map { it.toDto() }

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
                studyPhases = phases,
                protocolRegistry = catalog.protocols,
                protocolCatalogSha256 = catalog.catalogSha256,
                transformations = TransformationRegistry.transformations,
                transformationSetVersion = TransformationRegistry.setVersion,
                missingData = missingDataReport(
                    entries = entries,
                    measures = measures,
                    ledgerDates = ledger.map { it.localDate },
                    entryIdsWithFacts = facts.map { it.entryId }.toSet(),
                    now = now,
                    zone = zone,
                ),
                missingDataPolicyVersion = MissingDataPolicy.VERSION,
                missingDataStatement = MissingDataPolicy.STATEMENT,
                dataDictionary = ResearchDataDictionary.dictionary,
                dataDictionarySha256 = ResearchDataDictionary.sha256,
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
     * Every absence between the first record and the later of "now" and the
     * newest record.
     *
     * Using the newest record rather than the clock alone closes two holes.
     * A device whose clock is behind its own newest row would otherwise
     * produce an empty report while the document still says every absence
     * is listed; and a window that ran backwards would report nothing at
     * all, indistinguishable from a person who had missed nothing.
     *
     * The window is also clamped to the policy's maximum span. A single
     * corrupt or mis-clocked `localDate` — a row stamped 1000-01-01, say —
     * would otherwise ask for four hundred thousand records and throw, and
     * an export that throws leaves the person a zero-byte file. Reporting
     * the most recent span is worse than reporting all of it and much
     * better than reporting none of it, and the clamp is visible: the
     * earliest absence listed is later than the earliest record.
     */
    @Suppress("LongParameterList")
    private fun missingDataReport(
        entries: List<JournalEntryDto>,
        measures: List<MorningMeasureDto>,
        ledgerDates: List<String>,
        entryIdsWithFacts: Set<String>,
        now: Long,
        zone: ZoneId,
    ): List<MissingDataRecord> {
        val recordDates = (entries.map { it.localDate } + measures.map { it.localDate } + ledgerDates)
            .mapNotNull { parseDate(it) }
        val firstRecordDate = recordDates.minOrNull() ?: return emptyList()
        val throughDate = maxOf(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            recordDates.max(),
        )
        val windowStart = maxOf(firstRecordDate, throughDate.minusDays(MissingDataPolicy.MAX_REPORT_DAYS))

        return MissingDataPolicy.report(
            firstRecordDate = windowStart,
            throughDate = throughDate,
            allMeasureDates = measures.mapNotNull { parseDate(it.localDate) }.toSet(),
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
