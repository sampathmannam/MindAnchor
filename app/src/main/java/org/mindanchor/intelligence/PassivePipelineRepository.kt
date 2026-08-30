@file:Suppress("LongMethod", "TooManyFunctions")

package org.mindanchor.intelligence

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeMap
import kotlinx.coroutines.CancellationException
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PassiveBaselineSegmentEntity
import org.mindanchor.data.db.PassivePipelineRunEntity
import org.mindanchor.data.db.PassiveRawProvenanceEntity
import org.mindanchor.data.db.PassiveRawSampleEntity
import org.mindanchor.data.db.PassiveStoredRecord
import org.mindanchor.research.ResearchLedgerRepository
import org.mindanchor.research.TransformationRegistry
import org.mindanchor.usage.PassiveUsageStatsSource
import org.mindanchor.vitals.PassiveHealthConnectSource

sealed interface PassivePipelineResult {
    data class Completed(
        val runId: String,
        val insertedWindows: Int,
        val insertedDays: Int,
        val insertedDecisions: Int,
    ) : PassivePipelineResult

    data class Retry(val runId: String) : PassivePipelineResult
}

class PassivePipelineRepository internal constructor(
    private val database: AnchorDatabase,
    private val healthSource: PassiveRecordSource,
    private val usageSource: PassiveRecordSource,
    private val historyPermissionGranted: suspend () -> Boolean,
    private val ensureCurrentPhase: suspend (Long) -> Unit,
    private val refreshProvenanceAfterCommit: suspend () -> Unit,
    private val recordCandidatesObserved: (Int) -> Unit = {},
) {
    suspend fun run(now: Long, zone: ZoneId): PassivePipelineResult {
        val dao = database.passive()
        val firstSuccessfulPermissionedRun = dao.successfulPermissionedRunCount() == 0
        val historyGranted = historyPermissionGrantedSafely()
        val scanStart = scanStart(now, zone, firstSuccessfulPermissionedRun, historyGranted)
        val range = PassiveReadRange(scanStart, now, zone.id)
        val usageRange = PassiveReadRange(priorLocalDayStart(scanStart, zone), now, zone.id)
        val reads = healthSource.read(range) + usageSource.read(usageRange)
        val sourceStatesJson = sourceStatesJson(reads)
        val runId = PassivePipelineCodec.contentHash(
            listOf(now.toString(), scanStart.toString(), zone.id, sourceStatesJson, sourceRevisionMaterial(reads))
                .joinToString("") { canonicalPart(it) },
        )
        val runResult = resultOf(reads)
        val records = reads.flatMap(PassiveSourceRead::records)

        val counts = database.withTransaction {
            dao.insertSourceReads(reads.map { PassivePipelineCodec.sourceReadEntity(it, runId) })

            val provenanceRows = records.map(::rawProvenanceEntity)
            val insertedRaw = dao.insertRawProvenance(provenanceRows)
            dao.insertRawSamples(records.map(::rawSampleEntity))
            dao.insertSourceLags(records.map { PassivePipelineCodec.sourceLagEntity(it, now) })
            val newlyInsertedProvenanceIds = insertedRaw.mapIndexedNotNull { index, rowId ->
                provenanceRows[index].id.takeIf { rowId != IGNORED_ROW_ID }
            }.toSet()

            val segment = configuredSegment(records, now)
            val stored = dao.rawRecords(usageRange.startInclusive, now).map { it.toDomain() }
            val derivationRecords = stored.map { record ->
                record.copy(recordId = PassivePipelineCodec.rawIdentity(record))
            }
            val dates = touchedDates(derivationRecords, range, zone)
            val inserted = if (dates.isEmpty()) {
                InsertCounts()
            } else {
                ensureCurrentPhase(now)
                derive(
                    dates = dates,
                    records = derivationRecords,
                    reads = reads,
                    segment = segment,
                    newlyInsertedProvenanceIds = newlyInsertedProvenanceIds,
                    now = now,
                    zone = zone,
                )
            }

            dao.insertPipelineRun(
                PassivePipelineRunEntity(
                    id = runId,
                    startedAt = now,
                    completedAt = now,
                    scanStart = scanStart,
                    scanEnd = now,
                    zoneId = zone.id,
                    historyPermissionGranted = historyGranted,
                    firstSuccessfulPermissionedRun = firstSuccessfulPermissionedRun,
                    result = runResult,
                    sourceStatesJson = sourceStatesJson,
                ),
            )
            inserted
        }
        refreshProvenanceAfterCommit()
        return if (runResult == RETRY_TRANSIENT) {
            PassivePipelineResult.Retry(runId)
        } else {
            PassivePipelineResult.Completed(runId, counts.windows, counts.days, counts.decisions)
        }
    }

    private suspend fun configuredSegment(
        records: List<PassiveSourceRecord>,
        now: Long,
    ): PassiveBaselineSegmentEntity {
        val dao = database.passive()
        val latest = dao.latestBaselineSegment()
        val configured = latest?.let { PassivePipelineCodec.decodeFingerprints(it.fingerprintsJson) }.orEmpty()
        val observed = records.asSequence()
            .filter { it.sourceFamily in SCORED_FAMILIES }
            .map { it.fingerprint() }
            .toSet()
        val next = configured + observed
        if (
            latest != null &&
            observed.all { it in configured } &&
            latest.windowTransformationVersion == PassiveWindowAggregator.TRANSFORMATION_VERSION &&
            latest.dailyTransformationVersion == PassiveDailyAggregator.TRANSFORMATION_VERSION
        ) {
            return latest
        }
        val id = PassiveBaselineSegment.id(
            next,
            PassiveWindowAggregator.TRANSFORMATION_VERSION,
            PassiveDailyAggregator.TRANSFORMATION_VERSION,
        )
        val entity = PassiveBaselineSegmentEntity(
            id = id,
            openedAt = now,
            fingerprintsJson = PassivePipelineCodec.sortedFingerprintJson(next),
            windowTransformationVersion = PassiveWindowAggregator.TRANSFORMATION_VERSION,
            dailyTransformationVersion = PassiveDailyAggregator.TRANSFORMATION_VERSION,
        )
        dao.insertBaselineSegment(entity)
        return entity
    }

    @Suppress("LongParameterList")
    private suspend fun derive(
        dates: List<LocalDate>,
        records: List<PassiveSourceRecord>,
        reads: List<PassiveSourceRead>,
        segment: PassiveBaselineSegmentEntity,
        newlyInsertedProvenanceIds: Set<String>,
        now: Long,
        zone: ZoneId,
    ): InsertCounts {
        val dao = database.passive()
        val configuredFamilies = PassivePipelineCodec.decodeFingerprints(segment.fingerprintsJson)
            .map { it.sourceFamily }
            .toSet()
        val lagObservations = configuredFamilies.flatMap { family ->
            dao.sourceLags(family.name).map { SourceLag(family, it.lagMillis, it.usedIngestedAtFallback) }
        }
        val recordIndex = DerivationRecordIndex(records, dates, now, zone, recordCandidatesObserved)
        val context = DerivationContext(
            recordIndex,
            reads,
            segment.id,
            newlyInsertedProvenanceIds,
            configuredFamilies,
            lagObservations,
            now,
            zone,
        )
        var counts = InsertCounts()
        dates.forEach { date ->
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            if (minOf(date.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(), now) > dayStart) {
                counts += deriveDate(date, context)
            }
        }
        return counts
    }

    private suspend fun deriveDate(date: LocalDate, context: DerivationContext): InsertCounts {
        val dayStart = date.atStartOfDay(context.zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1L).atStartOfDay(context.zone).toInstant().toEpochMilli()
        val dateRecords = context.recordIndex.recordsForDate(date)
        val wakeTime = dateRecords.filter { it.kind == PassiveRecordKind.SLEEP_SESSION }.maxOfOrNull { it.eventEnd }
        val windows = aggregateWindows(dayStart, minOf(dayEnd, context.now), wakeTime, context)
        val finality = PassiveFinality.watermark(
            dayEnd,
            context.configuredFamilies,
            context.lagObservations,
            context.now,
        )
        val insertedWindows = appendWindowRevisions(windows, dateRecords, finality, context)
        val dailyRecords = context.recordIndex.dailyRecords(
            date,
            context.reads.any {
                it.sourceFamily == PassiveSourceFamily.USAGE_STATS && it.state == PassiveReadState.SUCCESS
            },
        )
        val aggregate = PassiveDailyAggregator.aggregate(
            date,
            context.zone,
            windows,
            dailyRecords,
            context.reads,
            context.segmentId,
            context.now,
            finality,
        )
        return InsertCounts(windows = insertedWindows) + appendDailyAndDecision(
            aggregate,
            dailyRecords.map { it.recordId }.toSet(),
            finality,
            context,
        )
    }

    private fun aggregateWindows(
        dayStart: Long,
        endExclusive: Long,
        wakeTime: Long?,
        context: DerivationContext,
    ): List<PassiveFeatureWindow> {
        val first = Math.floorDiv(dayStart, PassiveWindowAggregator.WINDOW_MILLIS) *
            PassiveWindowAggregator.WINDOW_MILLIS
        val last = Math.floorDiv(endExclusive - 1L, PassiveWindowAggregator.WINDOW_MILLIS) *
            PassiveWindowAggregator.WINDOW_MILLIS
        return generateSequence(first) { it + PassiveWindowAggregator.WINDOW_MILLIS }
            .takeWhile { it <= last }
            .map { start ->
                PassiveWindowAggregator.aggregate(
                    context.recordIndex.recordsForWindow(start),
                    PassiveReadRange(start, start + 1L, context.zone.id),
                    context.zone,
                    wakeTime,
                ).single()
            }
            .toList()
    }

    private suspend fun appendWindowRevisions(
        windows: List<PassiveFeatureWindow>,
        dateRecords: List<PassiveSourceRecord>,
        finality: PassiveFinalityDecision,
        context: DerivationContext,
    ): Int {
        val dao = database.passive()
        val daySourceUpdated = dateRecords.maxOf { it.sourceUpdatedTime ?: it.ingestedAt }
        val dayIngestedAt = dateRecords.maxOf { it.ingestedAt }
        val candidates = windows.mapNotNull { window ->
            val contributing = context.recordIndex.recordsForWindow(window.startInclusive)
            val sourceUpdated = contributing.maxOfOrNull { it.sourceUpdatedTime ?: it.ingestedAt }
                ?: daySourceUpdated
            val ingestedAt = contributing.maxOfOrNull { it.ingestedAt } ?: dayIngestedAt
            val previous = dao.latestWindowRevision(window.startInclusive)
            val draft = PassivePipelineCodec.windowEntity(
                window,
                context.segmentId,
                sourceUpdated,
                ingestedAt,
                finality.final,
                RevisionReason.INITIAL,
                context.now,
            )
            val reason = revisionReason(
                previous?.contentHash,
                previous?.final,
                draft.contentHash,
                finality.final,
                context.newlyInsertedProvenanceIds,
                window.provenanceRecordIds.toSet(),
            )
            PassivePipelineCodec.windowEntity(
                window,
                context.segmentId,
                sourceUpdated,
                ingestedAt,
                finality.final,
                reason,
                context.now,
            ).takeIf { PassivePipelineCodec.shouldAppend(previous?.contentHash, it.contentHash, reason) }
        }
        return dao.insertWindowRevisions(candidates).count { it != IGNORED_ROW_ID }
    }

    private suspend fun appendDailyAndDecision(
        aggregate: PassiveDailyAggregate,
        provenanceIds: Set<String>,
        finality: PassiveFinalityDecision,
        context: DerivationContext,
    ): InsertCounts {
        val dao = database.passive()
        val localDate = aggregate.passiveDay.day.toString()
        val previous = dao.latestDailyRevision(localDate)
        val draft = PassivePipelineCodec.dailyEntity(
            aggregate,
            provenanceIds,
            RevisionReason.INITIAL,
            context.now,
        )
        val reason = revisionReason(
            previous?.contentHash,
            previous?.let { it.asOfTime >= it.watermark },
            draft.contentHash,
            finality.final,
            context.newlyInsertedProvenanceIds,
            provenanceIds,
        )
        val daily = PassivePipelineCodec.dailyEntity(aggregate, provenanceIds, reason, context.now)
        if (!PassivePipelineCodec.shouldAppend(previous?.contentHash, daily.contentHash, reason)) return InsertCounts()
        if (dao.insertDailyRevisions(listOf(daily)).single() == IGNORED_ROW_ID) return InsertCounts()
        return InsertCounts(days = 1, decisions = appendDecision(daily, reason, context.now))
    }

    private suspend fun appendDecision(
        daily: org.mindanchor.data.db.PassiveDailyRevisionEntity,
        dailyReason: RevisionReason,
        now: Long,
    ): Int {
        val dao = database.passive()
        val day = PassivePipelineCodec.dailyToDomain(daily)
        val history = dao.dailyHistory(daily.localDate, now).map(PassivePipelineCodec::dailyToDomain)
        val prior = dao.priorDecisions(daily.localDate, now).map(PassivePipelineCodec::decisionToDomain)
        val frozen = PassiveBaselineBuilder.freeze(history, day.day, now, day.baselineSegment)
        val calibrationVersion = requireNotNull(TransformationRegistry.versionOf("passive-block-calibration"))
        val seed = frozen?.let {
            PassivePipelineCodec.calibrationSeed(day.baselineSegment, it.frozenAsOfTime, calibrationVersion)
        } ?: 0L
        val observation = PassiveEstimator.observe(day, now, history, prior, seed)
        val previous = dao.latestObservationDecision(daily.localDate)
        val reason = when {
            previous == null -> RevisionReason.INITIAL
            dailyReason == RevisionReason.FINALITY -> RevisionReason.FINALITY
            dailyReason == RevisionReason.BACKFILL -> RevisionReason.BACKFILL
            else -> RevisionReason.CONTENT_CHANGED
        }
        val decision = PassivePipelineCodec.decisionEntity(observation, reason)
        if (!PassivePipelineCodec.shouldAppend(previous?.contentHash, decision.contentHash, reason)) return 0
        return dao.insertObservationDecisions(listOf(decision)).count { it != IGNORED_ROW_ID }
    }

    private fun scanStart(
        now: Long,
        zone: ZoneId,
        firstSuccessfulPermissionedRun: Boolean,
        historyGranted: Boolean,
    ): Long {
        val days = when {
            !firstSuccessfulPermissionedRun -> RESCAN_DAYS
            historyGranted -> HISTORY_DAYS
            else -> AVAILABLE_HISTORY_DAYS
        }
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .minusDays(days - 1L)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun priorLocalDayStart(scanStart: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(scanStart).atZone(zone).toLocalDate().minusDays(1L)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    private fun touchedDates(
        records: List<PassiveSourceRecord>,
        range: PassiveReadRange,
        zone: ZoneId,
    ): List<LocalDate> = records.flatMap { record ->
        when (record.kind) {
            PassiveRecordKind.SLEEP_SESSION -> listOf(
                Instant.ofEpochMilli(record.eventEnd).atZone(zone).toLocalDate(),
            )
            PassiveRecordKind.STEPS_INTERVAL, PassiveRecordKind.EXERCISE_SESSION -> {
                val start = maxOf(record.eventStart, range.startInclusive)
                val end = minOf(record.eventEnd, range.endExclusive)
                if (end <= start) emptyList() else datesBetween(
                    Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
                    Instant.ofEpochMilli(end - 1L).atZone(zone).toLocalDate(),
                )
            }
            else -> listOf(Instant.ofEpochMilli(record.eventStart).atZone(zone).toLocalDate())
        }
    }.filter { date ->
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli()
        start < range.endExclusive && end > range.startInclusive
    }.distinct().sorted()

    private fun datesBetween(first: LocalDate, last: LocalDate): List<LocalDate> = buildList {
        var current = first
        while (!current.isAfter(last)) {
            add(current)
            current = current.plusDays(1L)
        }
    }

    private fun revisionReason(
        previousHash: String?,
        previousFinal: Boolean?,
        nextHash: String,
        nextFinal: Boolean,
        newlyInsertedRecordIds: Set<String>,
        provenanceIds: Set<String>,
    ): RevisionReason = when {
        previousHash == null -> RevisionReason.INITIAL
        previousFinal != nextFinal -> RevisionReason.FINALITY
        newlyInsertedRecordIds.any { it in provenanceIds } -> RevisionReason.BACKFILL
        previousHash != nextHash -> RevisionReason.CONTENT_CHANGED
        else -> RevisionReason.CONTENT_CHANGED
    }

    private fun resultOf(reads: List<PassiveSourceRead>): String = when {
        reads.any { it.state == PassiveReadState.READ_FAILURE_TRANSIENT } -> RETRY_TRANSIENT
        reads.any { it.state == PassiveReadState.SUCCESS } -> SUCCESS_PERMISSIONED
        reads.any { it.state == PassiveReadState.READ_FAILURE_PERMANENT } -> SUCCESS_WITH_FAILURES
        else -> SUCCESS_NO_PERMISSION
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun historyPermissionGrantedSafely(): Boolean = try {
        historyPermissionGranted()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    private fun sourceStatesJson(reads: List<PassiveSourceRead>): String = reads
        .sortedWith(compareBy<PassiveSourceRead> { it.sourceFamily.name }.thenBy { it.state.name })
        .joinToString(prefix = "[", postfix = "]", separator = ",") {
            "{\"sourceFamily\":\"${it.sourceFamily.name}\",\"state\":\"${it.state.name}\"}"
        }

    private fun sourceRevisionMaterial(reads: List<PassiveSourceRead>): String = reads
        .sortedWith(compareBy<PassiveSourceRead> { it.sourceFamily.name }.thenBy { it.state.name })
        .joinToString("") { read ->
            listOf(
                read.sourceFamily.name,
                read.state.name,
                read.range.startInclusive.toString(),
                read.range.endExclusive.toString(),
                read.range.zoneId,
                read.attemptedAt.toString(),
                read.errorCode,
                read.records.map(PassivePipelineCodec::rawIdentity).sorted().joinToString(""),
            ).joinToString("") { canonicalPart(it) }
        }

    private fun canonicalPart(value: String?): String = value?.let { "${it.length}:$it" } ?: "null:"

    private fun rawProvenanceEntity(record: PassiveSourceRecord) = PassiveRawProvenanceEntity(
        id = PassivePipelineCodec.rawIdentity(record),
        sourceFamily = record.sourceFamily.name,
        recordKind = record.kind.name,
        eventStart = record.eventStart,
        eventEnd = record.eventEnd,
        unit = record.unit,
        dataOriginPackage = record.dataOriginPackage,
        deviceManufacturer = record.deviceManufacturer,
        deviceModel = record.deviceModel,
        deviceType = record.deviceType,
        sourceUpdatedTime = record.sourceUpdatedTime,
        ingestedAt = record.ingestedAt,
        zoneId = record.zoneId,
        zoneOffsetSeconds = record.zoneOffsetSeconds,
        recordId = record.recordId,
        recordVersion = record.recordVersion,
    )

    private fun rawSampleEntity(record: PassiveSourceRecord) = PassiveRawSampleEntity(
        provenanceId = PassivePipelineCodec.rawIdentity(record),
        value = record.value,
        ingestedAt = record.ingestedAt,
    )

    private fun PassiveStoredRecord.toDomain() = PassiveSourceRecord(
        sourceFamily = PassiveSourceFamily.valueOf(provenance.sourceFamily),
        kind = PassiveRecordKind.valueOf(provenance.recordKind),
        eventStart = provenance.eventStart,
        eventEnd = provenance.eventEnd,
        value = value,
        unit = provenance.unit,
        dataOriginPackage = provenance.dataOriginPackage,
        deviceManufacturer = provenance.deviceManufacturer,
        deviceModel = provenance.deviceModel,
        deviceType = provenance.deviceType,
        sourceUpdatedTime = provenance.sourceUpdatedTime,
        ingestedAt = provenance.ingestedAt,
        zoneId = provenance.zoneId,
        zoneOffsetSeconds = provenance.zoneOffsetSeconds,
        recordId = provenance.recordId,
        recordVersion = provenance.recordVersion,
    )

    private fun PassiveSourceRecord.fingerprint() = PassiveSourceFingerprint(
        sourceFamily,
        dataOriginPackage,
        deviceManufacturer,
        deviceModel,
        deviceType,
    )

    private data class InsertCounts(
        val windows: Int = 0,
        val days: Int = 0,
        val decisions: Int = 0,
    ) {
        operator fun plus(other: InsertCounts) = InsertCounts(
            windows + other.windows,
            days + other.days,
            decisions + other.decisions,
        )
    }

    @Suppress("LongParameterList")
    private data class DerivationContext(
        val recordIndex: DerivationRecordIndex,
        val reads: List<PassiveSourceRead>,
        val segmentId: String,
        val newlyInsertedProvenanceIds: Set<String>,
        val configuredFamilies: Set<PassiveSourceFamily>,
        val lagObservations: List<SourceLag>,
        val now: Long,
        val zone: ZoneId,
    )

    private class DerivationRecordIndex(
        records: List<PassiveSourceRecord>,
        dates: List<LocalDate>,
        private val now: Long,
        private val zone: ZoneId,
        private val candidatesObserved: (Int) -> Unit,
    ) {
        private val requestedDates = dates.toSet()
        private val byDate = mutableMapOf<LocalDate, MutableList<PassiveSourceRecord>>()
        private val byWindow = mutableMapOf<Long, MutableList<PassiveSourceRecord>>()
        private val usageByDate = mutableMapOf<LocalDate, MutableList<PassiveSourceRecord>>()
        private val usageByStart = TreeMap<Long, PassiveSourceRecord>()
        private val firstWindow = Math.floorDiv(
            dates.first().atStartOfDay(zone).toInstant().toEpochMilli(),
            PassiveWindowAggregator.WINDOW_MILLIS,
        ) * PassiveWindowAggregator.WINDOW_MILLIS
        private val windowEnd = run {
            val rangeEnd = minOf(dates.last().plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli(), now)
            (Math.floorDiv(rangeEnd - 1L, PassiveWindowAggregator.WINDOW_MILLIS) + 1L) *
                PassiveWindowAggregator.WINDOW_MILLIS
        }

        init {
            records.forEach { record ->
                recordDates(record).forEach { date -> byDate.getOrPut(date) { mutableListOf() } += record }
                indexWindows(record)
                if (record.sourceFamily == PassiveSourceFamily.USAGE_STATS) {
                    val date = Instant.ofEpochMilli(record.eventStart).atZone(zone).toLocalDate()
                    usageByDate.getOrPut(date) { mutableListOf() } += record
                    usageByStart[record.eventStart] = record
                }
            }
        }

        fun recordsForDate(date: LocalDate): List<PassiveSourceRecord> =
            byDate[date].orEmpty().also { candidatesObserved(it.size) }

        fun recordsForWindow(start: Long): List<PassiveSourceRecord> =
            byWindow[start].orEmpty().also { candidatesObserved(it.size) }

        fun dailyRecords(date: LocalDate, usageSucceeded: Boolean): List<PassiveSourceRecord> {
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val nonUsage = byDate[date].orEmpty().filter { it.sourceFamily != PassiveSourceFamily.USAGE_STATS }
            val usage = if (usageSucceeded) {
                listOfNotNull(usageByStart.lowerEntry(dayStart)?.value) + usageByDate[date].orEmpty().filter {
                    it.eventStart <= now
                }
            } else {
                emptyList()
            }
            return (nonUsage + usage).also { candidatesObserved(it.size) }
        }

        private fun recordDates(record: PassiveSourceRecord): List<LocalDate> = when (record.kind) {
            PassiveRecordKind.SLEEP_SESSION -> listOf(
                Instant.ofEpochMilli(record.eventEnd).atZone(zone).toLocalDate(),
            )
            PassiveRecordKind.STEPS_INTERVAL, PassiveRecordKind.EXERCISE_SESSION -> {
                val effectiveEnd = record.eventEnd.coerceAtLeast(record.eventStart + 1L)
                datesBetween(
                    Instant.ofEpochMilli(record.eventStart).atZone(zone).toLocalDate(),
                    Instant.ofEpochMilli(effectiveEnd - 1L).atZone(zone).toLocalDate(),
                )
            }
            else -> listOf(Instant.ofEpochMilli(record.eventStart).atZone(zone).toLocalDate())
        }.filter { it in requestedDates }

        private fun datesBetween(first: LocalDate, last: LocalDate): List<LocalDate> = buildList {
            var date = maxOf(first, requestedDates.minOrNull() ?: first)
            val end = minOf(last, requestedDates.maxOrNull() ?: last)
            while (!date.isAfter(end)) {
                if (date in requestedDates) add(date)
                date = date.plusDays(1L)
            }
        }

        private fun indexWindows(record: PassiveSourceRecord) {
            val effectiveEnd = record.eventEnd.coerceAtLeast(record.eventStart + 1L)
            var start = maxOf(
                Math.floorDiv(record.eventStart, PassiveWindowAggregator.WINDOW_MILLIS) *
                    PassiveWindowAggregator.WINDOW_MILLIS,
                firstWindow,
            )
            val end = minOf(effectiveEnd, windowEnd)
            while (start < end) {
                if (
                    record.eventStart < start + PassiveWindowAggregator.WINDOW_MILLIS &&
                    effectiveEnd > start
                ) {
                    byWindow.getOrPut(start) { mutableListOf() } += record
                }
                start += PassiveWindowAggregator.WINDOW_MILLIS
            }
        }
    }

    companion object {
        const val HISTORY_DAYS = 120L
        const val AVAILABLE_HISTORY_DAYS = 30L
        const val RESCAN_DAYS = 7L

        private const val IGNORED_ROW_ID = -1L
        private const val SUCCESS_PERMISSIONED = "SUCCESS_PERMISSIONED"
        private const val SUCCESS_NO_PERMISSION = "SUCCESS_NO_PERMISSION"
        private const val SUCCESS_WITH_FAILURES = "SUCCESS_WITH_FAILURES"
        private const val RETRY_TRANSIENT = "RETRY_TRANSIENT"

        private val SCORED_FAMILIES = setOf(
            PassiveSourceFamily.RESTING_HEART_RATE,
            PassiveSourceFamily.HRV_RMSSD,
            PassiveSourceFamily.SLEEP,
            PassiveSourceFamily.STEPS,
            PassiveSourceFamily.EXERCISE,
            PassiveSourceFamily.USAGE_STATS,
        )

        fun build(context: Context): PassivePipelineRepository {
            val app = context.applicationContext
            val health = PassiveHealthConnectSource(app)
            val provenance = ResearchLedgerRepository.build(app).provenance
            return PassivePipelineRepository(
                database = AnchorDatabase.get(app),
                healthSource = health,
                usageSource = PassiveUsageStatsSource(app),
                historyPermissionGranted = health::historyPermissionGranted,
                ensureCurrentPhase = { provenance.ensureCurrentPhase(it); Unit },
                refreshProvenanceAfterCommit = provenance::refreshAfterCommit,
            )
        }
    }
}
