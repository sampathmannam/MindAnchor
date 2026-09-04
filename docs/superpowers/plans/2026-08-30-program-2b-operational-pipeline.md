# Program 2B Operational Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the local, provenance-preserving Program 2B pipeline that reads Health Connect and UsageStats, creates immutable 15-minute and daily revisions, runs the Program 2A estimator deterministically, and carries the resulting history through Room, continuity restore, and research export.

**Architecture:** Keep the existing lossy `HealthConnectSource` API stable for current wellness callers and add a separate operational source that returns typed read outcomes plus normalized provenance. Pure Kotlin aggregation turns raw records into absolute UTC windows and local-day inputs; a Room-backed repository owns point-in-time finality, baseline segments, backfill revisions, and Program 2A estimator writes. Snapshot v3 and research export/dictionary v3 append the operational history while preserving frozen v1/v2 projections, and a unique six-hour WorkManager job drives collection and 14-day raw-value retention.

**Tech Stack:** Kotlin/JVM, Android API 33+, Java time, AndroidX Health Connect 1.1.0, UsageStatsManager, Room 2.6.1 with KSP and exported schemas, kotlinx.serialization 1.7.3, WorkManager, JUnit 4, Robolectric, AndroidX instrumented tests.

## Global Constraints

- Start from Program 2A HEAD `3ded72d7f03baa9ddf6483e430ed445d2e1c271c`; preserve the existing `HealthConnectSource.readDailyVitals`, `effectivePermissions`, `grantedPermissions`, `hasAllPermissions`, `hasAnyPermissions`, `requestPermissionsContract`, and `permissionLabelsInOrder` signatures.
- Add `ACTIVE_MINUTES` to `PassiveFeature` in the `ACTIVITY` domain. Ingest `OxygenSaturationRecord`, but keep `SPO2_PERCENT.scored == false`.
- Program 2 remains observation-only: no diagnosis, intervention, notification, launcher restriction, app blocking, protocol selection, or AnchorCore adaptation.
- Every source read is paginated and records one of `SUCCESS`, `UNAVAILABLE`, `PERMISSION_DENIED`, `READ_FAILURE_TRANSIENT`, or `READ_FAILURE_PERMANENT`; `SUCCESS` with zero records is distinct from every failure state.
- Every normalized record carries event start/end, value/unit, data-origin package, device manufacturer/model/type, nullable source-updated time, ingestion time, zone id/offset, record id, and record version.
- Windows are absolute UTC 15-minute half-open intervals. Store the local zone id and offset used for presentation/alignment; wake-relative values exist only on window quality/alignment records and never enter Program 2A baseline strata.
- Heart-rate coverage is distinct observed one-minute bins divided by 15. Physiology window quality is eligible at coverage `>= 0.5`; resting-heart-rate and HRV features additionally require at least one reading. Exercise suppresses only physiology features in overlapping windows.
- Clip interval records to window/day boundaries. Assign sleep to the local date of its wake/end. Sum clipped steps and active minutes. Derive routine only from raw UsageStats events whose source read was `SUCCESS`.
- Never impute, interpolate, carry forward, or zero-fill an absent feature.
- A day is `AVAILABLE_FINAL` only after its watermark and when at least two scoreable domains are present. Before the watermark it is `AVAILABLE_PROVISIONAL`; after the watermark it is `SUPPRESSED_EXERCISE` only when exercise removal prevented the two-domain floor, otherwise `INSUFFICIENT_DATA`. Program 2A may subsequently expose `BASELINE_BUILDING`.
- Bootstrap finality is 48 hours after local-day end. Once a source family has at least 30 observed lags, use nearest-rank p99 and clamp it to 6 hours through 7 days. Compute lag from `sourceUpdatedTime - eventEnd`; when source-updated time is absent, use `ingestedAt - eventEnd` and persist that fallback fact.
- A baseline segment id is SHA-256 of sorted configured source/device fingerprints for scored physiology, sleep, activity, and routine plus the window and daily transformation versions. A missing configured source on one day does not change the segment; a newly observed fingerprint opens a new segment.
- First successful permissioned collection reads 120 local days when `PERMISSION_READ_HEALTH_DATA_HISTORY` is granted, otherwise the available 30-day Health Connect history. Later runs rescan the latest 7 local days.
- The calibration seed is the first signed 64 bits, in SHA-256 byte order, of `segment|frozenAsOfTime|calibrationVersion`.
- Insert a new append-only window revision, daily revision, or observation decision only when canonical content changes or the run changes finality/backfill state. Never update or replace an earlier revision.
- Room schema version is 8. Raw sample values are pruned after 14 days; raw provenance survives. Window revisions survive for at least one year and are never deleted inside that year. Daily revisions, observation decisions, read outcomes, lag evidence, segments, and run records are retained long term.
- Derived/history DAO writes use `INSERT OR IGNORE`. Database triggers reject `UPDATE` and `DELETE` for immutable operational tables; `passive_raw_samples` is the sole raw-prune exception.
- Raw sample values are absent from continuity backup and research export. Both formats include window/daily/decision rows plus complete raw provenance, coverage, missingness, exclusions, read outcomes, lag fallback, segments, and run provenance.
- Snapshot v3 and research dictionary/export v3 are additive. Keep frozen v1 and v2 projections, add `PROGRAM_ONE_*` constants, reject newer-field smuggling into older versions, and append new serialized fields only after all existing fields.
- Health Connect oxygen/history/background permissions and accurate wording may change in Program 2B only as source permission wiring; do not add a Program 2C screen.
- The worker runs every 6 hours with no network requirement, battery-not-low, and `ExistingPeriodicWorkPolicy.UPDATE`. Retry transient provider failures; unavailable and denied reads are successful worker completion after their status is persisted.
- Use TDD and commit after each independently reviewable task. Do not stage or modify `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` or root `AGENTS.md`.

---

### Task 1: Define operational contracts and pure window/day aggregation

**Files:**
- Modify: `app/src/main/java/org/mindanchor/intelligence/PassiveContracts.kt`
- Create: `app/src/main/java/org/mindanchor/intelligence/PassivePipelineContracts.kt`
- Create: `app/src/main/java/org/mindanchor/intelligence/PassiveAggregation.kt`
- Modify: `app/src/test/java/org/mindanchor/intelligence/PassiveContractsTest.kt`
- Create: `app/src/test/java/org/mindanchor/intelligence/PassiveAggregationTest.kt`

**Interfaces:**
- Consumes: existing `PassiveFeature`, `PassiveDomain`, `PassiveDataStatus`, `PassiveDay`, `ScreenEvent`, `ScreenEventKind`, and `ScreenRhythm.days`.
- Produces exactly:
  - `PassiveFeature.ACTIVE_MINUTES` with `domain == PassiveDomain.ACTIVITY` and `scored == true`.
  - `enum class PassiveSourceFamily { HEART_RATE, RESTING_HEART_RATE, HRV_RMSSD, SLEEP, STEPS, EXERCISE, OXYGEN_SATURATION, USAGE_STATS }`.
  - `enum class PassiveRecordKind { HEART_RATE_SAMPLE, RESTING_HEART_RATE, HRV_RMSSD, SLEEP_SESSION, STEPS_INTERVAL, EXERCISE_SESSION, SPO2, SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE, SCREEN_UNLOCKED }`.
  - `enum class PassiveReadState { SUCCESS, UNAVAILABLE, PERMISSION_DENIED, READ_FAILURE_TRANSIENT, READ_FAILURE_PERMANENT }`.
  - `data class PassiveReadRange(val startInclusive: Long, val endExclusive: Long, val zoneId: String)`.
  - `data class PassiveSourceRecord(val sourceFamily: PassiveSourceFamily, val kind: PassiveRecordKind, val eventStart: Long, val eventEnd: Long, val value: Double?, val unit: String, val dataOriginPackage: String, val deviceManufacturer: String?, val deviceModel: String?, val deviceType: String?, val sourceUpdatedTime: Long?, val ingestedAt: Long, val zoneId: String, val zoneOffsetSeconds: Int, val recordId: String, val recordVersion: Long)`.
  - `data class PassiveSourceRead(val sourceFamily: PassiveSourceFamily, val state: PassiveReadState, val range: PassiveReadRange, val attemptedAt: Long, val records: List<PassiveSourceRecord> = emptyList(), val errorCode: String? = null)`; its initializer requires records only for `SUCCESS` and an empty record list for all other states.
  - `interface PassiveRecordSource { suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> }`.
  - `data class PassiveSourceFingerprint(val sourceFamily: PassiveSourceFamily, val dataOriginPackage: String, val deviceManufacturer: String?, val deviceModel: String?, val deviceType: String?)` with `fun canonical(): String`.
  - `data class PassiveWindowQuality(val heartRateCoverage: Double, val physiologyEligible: Boolean, val exerciseOverlapMillis: Long, val wakeRelativeMinute: Int?)`.
  - `data class PassiveWindowFeature(val feature: PassiveFeature, val value: Double?, val unit: String, val coverage: Double, val eligible: Boolean, val exclusion: String?)`.
  - `data class PassiveFeatureWindow(val startInclusive: Long, val endExclusive: Long, val zoneId: String, val zoneOffsetSeconds: Int, val quality: PassiveWindowQuality, val features: List<PassiveWindowFeature>, val provenanceRecordIds: List<String>)`.
  - `data class PassiveFinalityDecision(val watermark: Long, val final: Boolean, val perSourceLagMillis: Map<PassiveSourceFamily, Long>)`.
  - `data class PassiveDailyAggregate(val passiveDay: PassiveDay, val windows: List<PassiveFeatureWindow>, val readStates: Map<PassiveSourceFamily, PassiveReadState>, val coverageByFeature: Map<PassiveFeature, Double>, val missingFeatures: Set<PassiveFeature>, val exclusions: Map<PassiveFeature, String>, val finality: PassiveFinalityDecision)`.
  - `PassiveWindowAggregator.aggregate(records: List<PassiveSourceRecord>, range: PassiveReadRange, zone: ZoneId, wakeTimeMillis: Long?): List<PassiveFeatureWindow>`.
  - `PassiveDailyAggregator.aggregate(date: LocalDate, zone: ZoneId, windows: List<PassiveFeatureWindow>, records: List<PassiveSourceRecord>, reads: List<PassiveSourceRead>, baselineSegment: String, asOfTime: Long, finality: PassiveFinalityDecision): PassiveDailyAggregate`.
  - `PassiveFinality.watermark(localDayEnd: Long, configuredFamilies: Set<PassiveSourceFamily>, observations: List<SourceLag>, asOfTime: Long): PassiveFinalityDecision`.
  - `PassiveBaselineSegment.id(configuredFingerprints: Set<PassiveSourceFingerprint>, windowTransformationVersion: String, dailyTransformationVersion: String): String`.
  - `PassiveSeed.firstSigned64Bits(material: String): Long`.

- [ ] **Step 1: Add failing contract, UTC-boundary, coverage, and suppression tests**

```kotlin
@Test fun `active minutes is scored activity while oxygen remains context`() {
    assertEquals(PassiveDomain.ACTIVITY, PassiveFeature.ACTIVE_MINUTES.domain)
    assertTrue(PassiveFeature.ACTIVE_MINUTES.scored)
    assertFalse(PassiveFeature.SPO2_PERCENT.scored)
}

@Test fun `windows are UTC quarter hours and exercise removes only physiology`() {
    val zone = ZoneId.of("Asia/Kolkata")
    val range = PassiveReadRange(
        Instant.parse("2026-08-30T00:07:00Z").toEpochMilli(),
        Instant.parse("2026-08-30T00:31:00Z").toEpochMilli(),
        zone.id,
    )
    val records = (0 until 8).map { minute ->
        record(
            family = PassiveSourceFamily.HEART_RATE,
            kind = PassiveRecordKind.HEART_RATE_SAMPLE,
            start = Instant.parse("2026-08-30T00:0${minute}:10Z").toEpochMilli(),
            value = 70.0 + minute,
            id = "hr-$minute",
        )
    } + listOf(
        record(PassiveSourceFamily.RESTING_HEART_RATE, PassiveRecordKind.RESTING_HEART_RATE,
            Instant.parse("2026-08-30T00:08:00Z").toEpochMilli(), 61.0, "rhr"),
        record(PassiveSourceFamily.HRV_RMSSD, PassiveRecordKind.HRV_RMSSD,
            Instant.parse("2026-08-30T00:09:00Z").toEpochMilli(), 42.0, "hrv"),
        record(PassiveSourceFamily.EXERCISE, PassiveRecordKind.EXERCISE_SESSION,
            Instant.parse("2026-08-30T00:10:00Z").toEpochMilli(), null, "exercise",
            Instant.parse("2026-08-30T00:12:00Z").toEpochMilli()),
        record(PassiveSourceFamily.STEPS, PassiveRecordKind.STEPS_INTERVAL,
            Instant.parse("2026-08-30T00:00:00Z").toEpochMilli(), 150.0, "steps",
            Instant.parse("2026-08-30T00:15:00Z").toEpochMilli()),
    )

    val windows = PassiveWindowAggregator.aggregate(records, range, zone, wakeTimeMillis = null)

    assertEquals(Instant.parse("2026-08-30T00:00:00Z").toEpochMilli(), windows.first().startInclusive)
    assertEquals(Instant.parse("2026-08-30T00:15:00Z").toEpochMilli(), windows.first().endExclusive)
    assertEquals(8.0 / 15.0, windows.first().quality.heartRateCoverage, 0.000_001)
    assertFalse(windows.first().quality.physiologyEligible)
    assertFalse(windows.first().features.single { it.feature == PassiveFeature.RESTING_HEART_RATE }.eligible)
    assertTrue(windows.first().features.single { it.feature == PassiveFeature.STEPS }.eligible)
}

private fun record(
    family: PassiveSourceFamily,
    kind: PassiveRecordKind,
    start: Long,
    value: Double?,
    id: String,
    end: Long = start,
) = PassiveSourceRecord(
    sourceFamily = family, kind = kind, eventStart = start, eventEnd = end,
    value = value, unit = if (kind == PassiveRecordKind.STEPS_INTERVAL) "count" else "unit",
    dataOriginPackage = "source.app", deviceManufacturer = "Maker", deviceModel = "Model",
    deviceType = "WATCH", sourceUpdatedTime = start + 1_000L, ingestedAt = start + 2_000L,
    zoneId = "Asia/Kolkata", zoneOffsetSeconds = 19_800, recordId = id, recordVersion = 1L,
)
```

- [ ] **Step 2: Add failing local-day, finality, segment, and seed tests**

```kotlin
@Test fun `sleep belongs to wake date and clipped activity stays on the local day`() {
    val zone = ZoneId.of("Asia/Kolkata")
    val day = LocalDate.parse("2026-08-30")
    val start = day.minusDays(1).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
    val end = day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
    val sleep = record(PassiveSourceFamily.SLEEP, PassiveRecordKind.SLEEP_SESSION, start, null, "sleep", end)
    val exercise = record(
        PassiveSourceFamily.EXERCISE, PassiveRecordKind.EXERCISE_SESSION,
        day.minusDays(1).atTime(23, 50).atZone(zone).toInstant().toEpochMilli(), null, "exercise",
        day.atTime(0, 20).atZone(zone).toInstant().toEpochMilli(),
    )
    val reads = listOf(
        PassiveSourceRead(PassiveSourceFamily.SLEEP, PassiveReadState.SUCCESS,
            PassiveReadRange(start, end + 1, zone.id), end, listOf(sleep)),
        PassiveSourceRead(PassiveSourceFamily.EXERCISE, PassiveReadState.SUCCESS,
            PassiveReadRange(start, end + 1, zone.id), end, listOf(exercise)),
    )
    val finality = PassiveFinalityDecision(end, true, emptyMap())

    val aggregate = PassiveDailyAggregator.aggregate(
        day, zone, emptyList(), listOf(sleep, exercise), reads, "segment-a", end, finality,
    )

    assertEquals(480.0, aggregate.passiveDay.features[PassiveFeature.SLEEP_MINUTES]!!, 0.0)
    assertEquals(20.0, aggregate.passiveDay.features[PassiveFeature.ACTIVE_MINUTES]!!, 0.0)
}

@Test fun `nearest-rank lag and canonical hashes are deterministic`() {
    val lags = (1L..30L).map { hours ->
        SourceLag(PassiveSourceFamily.SLEEP, hours * 3_600_000L, usedIngestedAtFallback = hours == 30L)
    }
    val dayEnd = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli()
    val finality = PassiveFinality.watermark(dayEnd, setOf(PassiveSourceFamily.SLEEP), lags, dayEnd + 1)
    assertEquals(dayEnd + 30L * 3_600_000L, finality.watermark)
    assertFalse(finality.final)
    assertEquals(
        PassiveBaselineSegment.id(setOf(PassiveSourceFingerprint(PassiveSourceFamily.SLEEP,
            "source.app", "Maker", "Model", "WATCH")), "passive-window-v1", "passive-daily-v1"),
        PassiveBaselineSegment.id(setOf(PassiveSourceFingerprint(PassiveSourceFamily.SLEEP,
            "source.app", "Maker", "Model", "WATCH")), "passive-window-v1", "passive-daily-v1"),
    )
    assertEquals(-3_426_751_757_403_841_055L,
        PassiveSeed.firstSigned64Bits("segment|1234|block-calibration-v3"))
}
```

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassiveContractsTest --tests org.mindanchor.intelligence.PassiveAggregationTest`

Expected: compilation fails because `ACTIVE_MINUTES` and the operational aggregation types do not exist.

- [ ] **Step 3: Define the exact operational models and deterministic identity helpers**

Add `ACTIVE_MINUTES(PassiveDomain.ACTIVITY)` immediately after `STEPS` in `PassiveContracts.kt`. Define the contracts in `PassivePipelineContracts.kt` exactly as follows; keep timestamps in epoch milliseconds and offsets in seconds.

```kotlin
data class PassiveReadRange(val startInclusive: Long, val endExclusive: Long, val zoneId: String) {
    init { require(startInclusive < endExclusive) }
}

data class PassiveSourceRead(
    val sourceFamily: PassiveSourceFamily,
    val state: PassiveReadState,
    val range: PassiveReadRange,
    val attemptedAt: Long,
    val records: List<PassiveSourceRecord> = emptyList(),
    val errorCode: String? = null,
) {
    init {
        require(state == PassiveReadState.SUCCESS || records.isEmpty())
        require(state == PassiveReadState.SUCCESS || !errorCode.isNullOrBlank())
        require(records.all { it.sourceFamily == sourceFamily })
    }
}

interface PassiveRecordSource {
    suspend fun read(range: PassiveReadRange): List<PassiveSourceRead>
}

data class PassiveSourceFingerprint(
    val sourceFamily: PassiveSourceFamily,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
) {
    fun canonical(): String = listOf(
        sourceFamily.name, dataOriginPackage,
        deviceManufacturer.orEmpty(), deviceModel.orEmpty(), deviceType.orEmpty(),
    ).joinToString("|")
}

data class SourceLag(
    val sourceFamily: PassiveSourceFamily,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
)

object PassiveSeed {
    fun digest(material: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(material.encodeToByteArray())

    fun sha256(material: String): String =
        digest(material).joinToString("") { "%02x".format(it) }

    fun firstSigned64Bits(material: String): Long = ByteBuffer.wrap(digest(material)).long
}

object PassiveBaselineSegment {
    fun id(
        configuredFingerprints: Set<PassiveSourceFingerprint>,
        windowTransformationVersion: String,
        dailyTransformationVersion: String,
    ): String {
        val canonical = configuredFingerprints.map { it.canonical() }.sorted() +
            listOf(windowTransformationVersion, dailyTransformationVersion)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.joinToString("\n").encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
```

Define `PassiveSourceRecord`, the three window models, `PassiveFinalityDecision`, and `PassiveDailyAggregate` with the fields in **Interfaces**. Validate `eventStart <= eventEnd`, finite non-null values, nonblank unit/package/zone/id, and `recordVersion >= 0`. Define the three enums exactly as listed in **Interfaces**.

- [ ] **Step 4: Implement UTC windows, clipped intervals, honest daily status, and watermarks**

```kotlin
object PassiveWindowAggregator {
    const val WINDOW_MILLIS = 15L * 60_000L
    const val TRANSFORMATION_VERSION = "passive-window-v1"

    fun aggregate(
        records: List<PassiveSourceRecord>,
        range: PassiveReadRange,
        zone: ZoneId,
        wakeTimeMillis: Long?,
    ): List<PassiveFeatureWindow> {
        val first = Math.floorDiv(range.startInclusive, WINDOW_MILLIS) * WINDOW_MILLIS
        val last = Math.floorDiv(range.endExclusive - 1L, WINDOW_MILLIS) * WINDOW_MILLIS
        return generateSequence(first) { it + WINDOW_MILLIS }.takeWhile { it <= last }.map { start ->
            val end = start + WINDOW_MILLIS
            val overlapping = records.filter { it.eventStart < end && it.eventEnd.coerceAtLeast(it.eventStart + 1L) > start }
            val hrBins = overlapping.filter { it.kind == PassiveRecordKind.HEART_RATE_SAMPLE }
                .map { Math.floorDiv(it.eventStart - start, 60_000L) }.filter { it in 0L..14L }.distinct().size
            val coverage = hrBins / 15.0
            val exerciseMillis = overlapping.filter { it.kind == PassiveRecordKind.EXERCISE_SESSION }
                .sumOf { overlapMillis(it.eventStart, it.eventEnd, start, end) }
            val physiologyEligible = coverage >= 0.5 && exerciseMillis == 0L
            val offset = zone.rules.getOffset(Instant.ofEpochMilli(start)).totalSeconds
            PassiveFeatureWindow(
                startInclusive = start,
                endExclusive = end,
                zoneId = zone.id,
                zoneOffsetSeconds = offset,
                quality = PassiveWindowQuality(
                    heartRateCoverage = coverage,
                    physiologyEligible = physiologyEligible,
                    exerciseOverlapMillis = exerciseMillis,
                    wakeRelativeMinute = wakeTimeMillis?.let { ((start - it) / 60_000L).toInt() },
                ),
                features = featureRows(overlapping, start, end, physiologyEligible, coverage),
                provenanceRecordIds = overlapping.map { it.recordId }.distinct().sorted(),
            )
        }.toList()
    }

    internal fun overlapMillis(recordStart: Long, recordEnd: Long, start: Long, end: Long): Long =
        (minOf(recordEnd, end) - maxOf(recordStart, start)).coerceAtLeast(0L)
}

object PassiveFinality {
    const val BOOTSTRAP_LAG_MILLIS = 48L * 3_600_000L
    const val MIN_OBSERVED_LAGS = 30
    const val MIN_LAG_MILLIS = 6L * 3_600_000L
    const val MAX_LAG_MILLIS = 7L * 24L * 3_600_000L

    fun watermark(
        localDayEnd: Long,
        configuredFamilies: Set<PassiveSourceFamily>,
        observations: List<SourceLag>,
        asOfTime: Long,
    ): PassiveFinalityDecision {
        val perSource = configuredFamilies.associateWith { family ->
            val sorted = observations.filter { it.sourceFamily == family }.map { it.lagMillis }.sorted()
            val lag = if (sorted.size < MIN_OBSERVED_LAGS) BOOTSTRAP_LAG_MILLIS else {
                val rank = ceil(0.99 * sorted.size).toInt().coerceAtLeast(1)
                sorted[rank - 1].coerceIn(MIN_LAG_MILLIS, MAX_LAG_MILLIS)
            }
            lag
        }
        val watermark = localDayEnd + (perSource.values.maxOrNull() ?: BOOTSTRAP_LAG_MILLIS)
        return PassiveFinalityDecision(watermark, asOfTime >= watermark, perSource)
    }
}
```

`featureRows` must use arithmetic mean for instantaneous RHR, HRV, and SpO2 values; RHR/HRV are eligible only when at least one corresponding reading exists and `physiologyEligible` is true. SpO2 may be present but always inherits `PassiveFeature.SPO2_PERCENT.scored == false`. For steps, multiply each count by `overlapMillis / (eventEnd - eventStart)` and sum; for active minutes, sum clipped exercise overlap and divide by 60,000. Do not synthesize a feature row when its source records are absent.

`PassiveDailyAggregator` must use this exact public signature and status ordering:

```kotlin
object PassiveDailyAggregator {
    const val TRANSFORMATION_VERSION = "passive-daily-v1"

    fun aggregate(
        date: LocalDate,
        zone: ZoneId,
        windows: List<PassiveFeatureWindow>,
        records: List<PassiveSourceRecord>,
        reads: List<PassiveSourceRead>,
        baselineSegment: String,
        asOfTime: Long,
        finality: PassiveFinalityDecision,
    ): PassiveDailyAggregate {
        val readStates = reads.associate { it.sourceFamily to it.state }
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val features = dailyFeatures(date, zone, dayStart, dayEnd, windows, records, readStates)
        val excluded = dailyExclusions(windows)
        val domainCount = features.keys.filter { it.scored && it !in excluded }
            .mapNotNull { it.domain }.distinct().size
        val exercisePreventedSecondDomain = domainCount < 2 && windows.any {
            it.quality.exerciseOverlapMillis > 0L && it.features.any { row -> row.feature.domain == PassiveDomain.PHYSIOLOGY }
        }
        val status = when {
            !finality.final -> PassiveDataStatus.AVAILABLE_PROVISIONAL
            domainCount >= 2 -> PassiveDataStatus.AVAILABLE_FINAL
            exercisePreventedSecondDomain -> PassiveDataStatus.SUPPRESSED_EXERCISE
            else -> PassiveDataStatus.INSUFFICIENT_DATA
        }
        val updateTimes = records.mapNotNull { it.sourceUpdatedTime }
        return PassiveDailyAggregate(
            passiveDay = PassiveDay(date, status, features, excluded, baselineSegment,
                sourceUpdatedTime = updateTimes.maxOrNull() ?: records.maxOfOrNull { it.ingestedAt } ?: asOfTime,
                ingestedAt = records.maxOfOrNull { it.ingestedAt } ?: asOfTime),
            windows = windows, readStates = readStates,
            coverageByFeature = coverageByFeature(windows),
            missingFeatures = PassiveFeature.entries.filter { it !in features }.toSet(),
            exclusions = excluded.associateWith { "EXERCISE_OVERLAP" },
            finality = finality,
        )
    }
}
```

`dailyFeatures` must: assign each sleep interval wholly to `Instant.ofEpochMilli(eventEnd).atZone(zone).toLocalDate()`; sum sleep minutes and derive `SLEEP_ONSET_AFTER_SIX_PM` from the earliest assigned start relative to 18:00 on the preceding local date; sum proportionally clipped steps; sum clipped active minutes; average only eligible window RHR/HRV/SpO2 rows; and call `ScreenRhythm.days` only when the `USAGE_STATS` read state is `SUCCESS`. Map raw screen kinds to existing `ScreenEventKind`; an unavailable/denied/failed routine read produces neither routine feature. Do not put `wakeRelativeMinute` into `PassiveDay`, `PassiveBaseline`, or `baselineSegment`.

- [ ] **Step 5: Run the pure suite and verify all boundary cases pass**

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassiveContractsTest --tests org.mindanchor.intelligence.PassiveAggregationTest`

Expected: `BUILD SUCCESSFUL`; tests cover half-open boundaries, DST zone offsets, 7/15 versus 8/15 heart-rate bins, instant-reading floors, partial interval clipping, wake-date sleep assignment, successful-empty routine reads, each failure state, status priority, 29/30 lag behavior, p99 clamping, fallback flags, deterministic segment ids, and the pinned signed seed.

- [ ] **Step 6: Commit the operational contracts and pure aggregation**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassiveContracts.kt app/src/main/java/org/mindanchor/intelligence/PassivePipelineContracts.kt app/src/main/java/org/mindanchor/intelligence/PassiveAggregation.kt app/src/test/java/org/mindanchor/intelligence/PassiveContractsTest.kt app/src/test/java/org/mindanchor/intelligence/PassiveAggregationTest.kt
git commit -m "feat: define passive operational aggregation"
```

---

### Task 2: Add Room v8 operational history, migration, and database immutability

**Files:**
- Create: `app/src/main/java/org/mindanchor/data/db/PassiveEntities.kt`
- Create: `app/src/main/java/org/mindanchor/data/db/PassiveDao.kt`
- Modify: `app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt`
- Create: `app/src/test/java/org/mindanchor/data/db/PassiveDaoAppendOnlyTest.kt`
- Modify: `app/src/test/java/org/mindanchor/data/db/ResearchBuilderCallbackTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/data/db/ResearchImmutabilityTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/data/db/PassiveRoomTest.kt`
- Create: `app/schemas/org.mindanchor.data.db.AnchorDatabase/8.json`

**Interfaces:**
- Consumes: Task 1 enums/models by storing enum names and canonical JSON; existing `AnchorDatabase.get`, `AnchorDatabase.migrations`, and `withResearchImmutability` remain callable under their current names.
- Produces nine Room tables: `passive_raw_provenance`, `passive_raw_samples`, `passive_source_reads`, `passive_source_lags`, `passive_baseline_segments`, `passive_pipeline_runs`, `passive_window_revisions`, `passive_daily_revisions`, and `passive_observation_decisions`.
- Produces `abstract fun passive(): PassiveDao` on `AnchorDatabase`.
- Produces the exact DAO methods shown in Step 3. All insert methods except `insertRawSamples` use `OnConflictStrategy.IGNORE`; raw samples also use `IGNORE` so rescans cannot replace values.
- `passive_raw_samples` is the only operational table with a DAO delete. All other operational tables have `BEFORE UPDATE` and `BEFORE DELETE` abort triggers on migration and fresh install.

- [ ] **Step 1: Write failing DAO-shape and instrumented Room tests**

```kotlin
@Test fun `operational history DAO is insert-only except raw value pruning`() {
    val source = File("src/main/java/org/mindanchor/data/db/PassiveDao.kt").readText()
    assertFalse(Regex("@(Update|Delete)\\b").containsMatchIn(source))
    assertFalse(Regex("UPDATE\\s+passive_", RegexOption.IGNORE_CASE).containsMatchIn(source))
    assertEquals(1, Regex("DELETE FROM passive_raw_samples").findAll(source).count())
    assertFalse(source.contains("OnConflictStrategy.REPLACE"))
}
```

```kotlin
@RunWith(AndroidJUnit4::class)
class PassiveRoomTest {
    @Test fun immutableRowsRejectMutationButRawValuesCanBePruned() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AnchorDatabase::class.java)
            .withResearchImmutability().build()
        try {
            val dao = db.passive()
            dao.insertRawProvenance(listOf(rawProvenance("raw-1")))
            dao.insertRawSamples(listOf(PassiveRawSampleEntity("raw-1", 72.0, 1_000L)))
            dao.insertWindowRevisions(listOf(windowRevision("window-1")))
            assertThrows(SQLiteConstraintException::class.java) {
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE passive_window_revisions SET contentHash = 'rewritten' WHERE id = 'window-1'",
                )
            }
            assertEquals(1, dao.pruneRawSamples(2_000L))
            assertEquals(1, dao.rawProvenanceNow().size)
            assertTrue(dao.rawRecords(0L, 10_000L).isEmpty())
        } finally {
            db.close()
        }
    }
}
```

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.data.db.PassiveDaoAppendOnlyTest`

Expected: compilation/test failure because `PassiveDao.kt` and the v8 entities do not exist.

- [ ] **Step 2: Define entities that separate long-term provenance from 14-day raw values**

Create `PassiveEntities.kt` with these exact entities and fields. Use the literal `tableName` and `Index` annotations shown below; each `id` is a caller-supplied lowercase SHA-256 content/identity hash, never an auto-generated key.

```kotlin
@Entity(tableName = "passive_raw_provenance", indices = [Index("eventStart"), Index("eventEnd"), Index("sourceFamily")])
data class PassiveRawProvenanceEntity(
    @PrimaryKey val id: String,
    val sourceFamily: String,
    val recordKind: String,
    val eventStart: Long,
    val eventEnd: Long,
    val unit: String,
    val dataOriginPackage: String,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val sourceUpdatedTime: Long?,
    val ingestedAt: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val recordId: String,
    val recordVersion: Long,
)

@Entity(
    tableName = "passive_raw_samples",
    foreignKeys = [ForeignKey(
        entity = PassiveRawProvenanceEntity::class,
        parentColumns = ["id"], childColumns = ["provenanceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("ingestedAt")],
)
data class PassiveRawSampleEntity(
    @PrimaryKey val provenanceId: String,
    val value: Double?,
    val ingestedAt: Long,
)

data class PassiveStoredRecord(
    @Embedded val provenance: PassiveRawProvenanceEntity,
    @ColumnInfo(name = "rawValue") val value: Double?,
)

@Entity(tableName = "passive_source_reads", indices = [Index("attemptedAt"), Index("sourceFamily")])
data class PassiveSourceReadEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sourceFamily: String,
    val state: String,
    val rangeStart: Long,
    val rangeEnd: Long,
    val zoneId: String,
    val attemptedAt: Long,
    val recordCount: Int,
    val errorCode: String?,
)

@Entity(tableName = "passive_source_lags", indices = [Index("sourceFamily"), Index("observedAt")])
data class PassiveSourceLagEntity(
    @PrimaryKey val id: String,
    val sourceFamily: String,
    val eventEnd: Long,
    val observedUpdatedAt: Long,
    val ingestedAt: Long,
    val lagMillis: Long,
    val usedIngestedAtFallback: Boolean,
    val observedAt: Long,
)

@Entity(tableName = "passive_baseline_segments", indices = [Index("openedAt")])
data class PassiveBaselineSegmentEntity(
    @PrimaryKey val id: String,
    val openedAt: Long,
    val fingerprintsJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
)

@Entity(tableName = "passive_pipeline_runs", indices = [Index("startedAt"), Index("completedAt")])
data class PassivePipelineRunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val completedAt: Long,
    val scanStart: Long,
    val scanEnd: Long,
    val zoneId: String,
    val historyPermissionGranted: Boolean,
    val firstSuccessfulPermissionedRun: Boolean,
    val result: String,
    val sourceStatesJson: String,
)

@Entity(tableName = "passive_window_revisions", indices = [
    Index("windowStart"), Index("baselineSegment"), Index(value = ["windowStart", "contentHash"], unique = true),
])
data class PassiveWindowRevisionEntity(
    @PrimaryKey val id: String,
    val windowStart: Long,
    val windowEnd: Long,
    val asOfTime: Long,
    val zoneId: String,
    val zoneOffsetSeconds: Int,
    val wakeRelativeMinute: Int?,
    val baselineSegment: String,
    val featureRowsJson: String,
    val heartRateCoverage: Double,
    val physiologyEligible: Boolean,
    val exerciseOverlapMillis: Long,
    val provenanceRecordIdsJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val transformationVersion: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val final: Boolean,
    val revisionReason: String,
    val contentHash: String,
)

@Entity(tableName = "passive_daily_revisions", indices = [
    Index("localDate"), Index("baselineSegment"), Index(value = ["localDate", "contentHash"], unique = true),
])
data class PassiveDailyRevisionEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val featuresJson: String,
    val excludedFeaturesJson: String,
    val baselineSegment: String,
    val sourceUpdatedTime: Long,
    val ingestedAt: Long,
    val sourceReadStatesJson: String,
    val coverageJson: String,
    val missingnessJson: String,
    val exclusionsJson: String,
    val provenanceJson: String,
    val windowTransformationVersion: String,
    val dailyTransformationVersion: String,
    val watermark: Long,
    val revisionReason: String,
    val contentHash: String,
)

@Entity(tableName = "passive_observation_decisions", indices = [
    Index("localDate"), Index("baselineSegment"), Index(value = ["localDate", "contentHash"], unique = true),
])
data class PassiveObservationDecisionEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val asOfTime: Long,
    val dataStatus: String,
    val observationState: String,
    val baselineSegment: String,
    val calibrationSeed: Long?,
    val frozenBaselineAsOfTime: Long?,
    val frozenBaselineThroughDay: String?,
    val decisionJson: String,
    val revisionReason: String,
    val contentHash: String,
)
```

- [ ] **Step 3: Add the insert-only DAO and transactional read surface**

```kotlin
@Dao
interface PassiveDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawProvenance(rows: List<PassiveRawProvenanceEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawSamples(rows: List<PassiveRawSampleEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceReads(rows: List<PassiveSourceReadEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourceLags(rows: List<PassiveSourceLagEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBaselineSegment(row: PassiveBaselineSegmentEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPipelineRun(row: PassivePipelineRunEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWindowRevisions(rows: List<PassiveWindowRevisionEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyRevisions(rows: List<PassiveDailyRevisionEntity>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservationDecisions(rows: List<PassiveObservationDecisionEntity>): List<Long>

    @Query("SELECT p.*, s.value AS rawValue FROM passive_raw_provenance p " +
        "JOIN passive_raw_samples s ON s.provenanceId = p.id " +
        "WHERE p.eventStart < :endExclusive AND p.eventEnd >= :startInclusive ORDER BY p.eventStart, p.id")
    suspend fun rawRecords(startInclusive: Long, endExclusive: Long): List<PassiveStoredRecord>
    @Query("DELETE FROM passive_raw_samples WHERE ingestedAt < :cutoff")
    suspend fun pruneRawSamples(cutoff: Long): Int
    @Query("SELECT * FROM passive_raw_provenance ORDER BY eventStart, id")
    suspend fun rawProvenanceNow(): List<PassiveRawProvenanceEntity>
    @Query("SELECT * FROM passive_source_reads ORDER BY attemptedAt, sourceFamily, id")
    suspend fun sourceReadsNow(): List<PassiveSourceReadEntity>
    @Query("SELECT * FROM passive_source_lags ORDER BY observedAt, sourceFamily, id")
    suspend fun sourceLagsNow(): List<PassiveSourceLagEntity>
    @Query("SELECT * FROM passive_source_lags WHERE sourceFamily = :family ORDER BY observedAt, id")
    suspend fun sourceLags(family: String): List<PassiveSourceLagEntity>
    @Query("SELECT * FROM passive_baseline_segments ORDER BY openedAt, id")
    suspend fun baselineSegmentsNow(): List<PassiveBaselineSegmentEntity>
    @Query("SELECT * FROM passive_baseline_segments ORDER BY openedAt DESC, id DESC LIMIT 1")
    suspend fun latestBaselineSegment(): PassiveBaselineSegmentEntity?
    @Query("SELECT * FROM passive_pipeline_runs ORDER BY completedAt, id")
    suspend fun pipelineRunsNow(): List<PassivePipelineRunEntity>
    @Query("SELECT COUNT(*) FROM passive_pipeline_runs WHERE result = 'SUCCESS_PERMISSIONED'")
    suspend fun successfulPermissionedRunCount(): Int
    @Query("SELECT * FROM passive_window_revisions ORDER BY windowStart, asOfTime, id")
    suspend fun windowRevisionsNow(): List<PassiveWindowRevisionEntity>
    @Query("SELECT * FROM passive_window_revisions WHERE windowStart = :windowStart ORDER BY asOfTime DESC, id DESC LIMIT 1")
    suspend fun latestWindowRevision(windowStart: Long): PassiveWindowRevisionEntity?
    @Query("SELECT * FROM passive_daily_revisions ORDER BY localDate, asOfTime, id")
    suspend fun dailyRevisionsNow(): List<PassiveDailyRevisionEntity>
    @Query("SELECT * FROM passive_daily_revisions WHERE localDate = :localDate ORDER BY asOfTime DESC, id DESC LIMIT 1")
    suspend fun latestDailyRevision(localDate: String): PassiveDailyRevisionEntity?
    @Query("SELECT * FROM passive_daily_revisions WHERE localDate < :targetDate AND asOfTime <= :asOfTime ORDER BY localDate, asOfTime, id")
    suspend fun dailyHistory(targetDate: String, asOfTime: Long): List<PassiveDailyRevisionEntity>
    @Query("SELECT * FROM passive_observation_decisions ORDER BY localDate, asOfTime, id")
    suspend fun observationDecisionsNow(): List<PassiveObservationDecisionEntity>
    @Query("SELECT * FROM passive_observation_decisions WHERE localDate = :localDate ORDER BY asOfTime DESC, id DESC LIMIT 1")
    suspend fun latestObservationDecision(localDate: String): PassiveObservationDecisionEntity?
    @Query("SELECT * FROM passive_observation_decisions WHERE localDate < :targetDate AND asOfTime <= :asOfTime ORDER BY localDate, asOfTime, id")
    suspend fun priorDecisions(targetDate: String, asOfTime: Long): List<PassiveObservationDecisionEntity>
}
```

- [ ] **Step 4: Migrate v7 to v8 with exact tables, indexes, and immutability triggers**

Add every new entity to `@Database`, set `version = 8`, add `abstract fun passive(): PassiveDao`, append `MIGRATION_7_8` to `migrations()`, and execute the entity-equivalent SQL. The migration must include these complete table declarations; create the indexes declared on the entities after the tables.

```sql
CREATE TABLE IF NOT EXISTS passive_raw_provenance (id TEXT NOT NULL, sourceFamily TEXT NOT NULL, recordKind TEXT NOT NULL, eventStart INTEGER NOT NULL, eventEnd INTEGER NOT NULL, unit TEXT NOT NULL, dataOriginPackage TEXT NOT NULL, deviceManufacturer TEXT, deviceModel TEXT, deviceType TEXT, sourceUpdatedTime INTEGER, ingestedAt INTEGER NOT NULL, zoneId TEXT NOT NULL, zoneOffsetSeconds INTEGER NOT NULL, recordId TEXT NOT NULL, recordVersion INTEGER NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_raw_samples (provenanceId TEXT NOT NULL, value REAL, ingestedAt INTEGER NOT NULL, PRIMARY KEY(provenanceId), FOREIGN KEY(provenanceId) REFERENCES passive_raw_provenance(id) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE TABLE IF NOT EXISTS passive_source_reads (id TEXT NOT NULL, runId TEXT NOT NULL, sourceFamily TEXT NOT NULL, state TEXT NOT NULL, rangeStart INTEGER NOT NULL, rangeEnd INTEGER NOT NULL, zoneId TEXT NOT NULL, attemptedAt INTEGER NOT NULL, recordCount INTEGER NOT NULL, errorCode TEXT, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_source_lags (id TEXT NOT NULL, sourceFamily TEXT NOT NULL, eventEnd INTEGER NOT NULL, observedUpdatedAt INTEGER NOT NULL, ingestedAt INTEGER NOT NULL, lagMillis INTEGER NOT NULL, usedIngestedAtFallback INTEGER NOT NULL, observedAt INTEGER NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_baseline_segments (id TEXT NOT NULL, openedAt INTEGER NOT NULL, fingerprintsJson TEXT NOT NULL, windowTransformationVersion TEXT NOT NULL, dailyTransformationVersion TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_pipeline_runs (id TEXT NOT NULL, startedAt INTEGER NOT NULL, completedAt INTEGER NOT NULL, scanStart INTEGER NOT NULL, scanEnd INTEGER NOT NULL, zoneId TEXT NOT NULL, historyPermissionGranted INTEGER NOT NULL, firstSuccessfulPermissionedRun INTEGER NOT NULL, result TEXT NOT NULL, sourceStatesJson TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_window_revisions (id TEXT NOT NULL, windowStart INTEGER NOT NULL, windowEnd INTEGER NOT NULL, asOfTime INTEGER NOT NULL, zoneId TEXT NOT NULL, zoneOffsetSeconds INTEGER NOT NULL, wakeRelativeMinute INTEGER, baselineSegment TEXT NOT NULL, featureRowsJson TEXT NOT NULL, heartRateCoverage REAL NOT NULL, physiologyEligible INTEGER NOT NULL, exerciseOverlapMillis INTEGER NOT NULL, provenanceRecordIdsJson TEXT NOT NULL, missingnessJson TEXT NOT NULL, exclusionsJson TEXT NOT NULL, transformationVersion TEXT NOT NULL, sourceUpdatedTime INTEGER NOT NULL, ingestedAt INTEGER NOT NULL, final INTEGER NOT NULL, revisionReason TEXT NOT NULL, contentHash TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_daily_revisions (id TEXT NOT NULL, localDate TEXT NOT NULL, asOfTime INTEGER NOT NULL, dataStatus TEXT NOT NULL, featuresJson TEXT NOT NULL, excludedFeaturesJson TEXT NOT NULL, baselineSegment TEXT NOT NULL, sourceUpdatedTime INTEGER NOT NULL, ingestedAt INTEGER NOT NULL, sourceReadStatesJson TEXT NOT NULL, coverageJson TEXT NOT NULL, missingnessJson TEXT NOT NULL, exclusionsJson TEXT NOT NULL, provenanceJson TEXT NOT NULL, windowTransformationVersion TEXT NOT NULL, dailyTransformationVersion TEXT NOT NULL, watermark INTEGER NOT NULL, revisionReason TEXT NOT NULL, contentHash TEXT NOT NULL, PRIMARY KEY(id));
CREATE TABLE IF NOT EXISTS passive_observation_decisions (id TEXT NOT NULL, localDate TEXT NOT NULL, asOfTime INTEGER NOT NULL, dataStatus TEXT NOT NULL, observationState TEXT NOT NULL, baselineSegment TEXT NOT NULL, calibrationSeed INTEGER, frozenBaselineAsOfTime INTEGER, frozenBaselineThroughDay TEXT, decisionJson TEXT NOT NULL, revisionReason TEXT NOT NULL, contentHash TEXT NOT NULL, PRIMARY KEY(id));
```

Assert these exact Room-generated index names: `index_passive_raw_provenance_eventStart`, `index_passive_raw_provenance_eventEnd`, `index_passive_raw_provenance_sourceFamily`, `index_passive_raw_samples_ingestedAt`, `index_passive_source_reads_attemptedAt`, `index_passive_source_reads_sourceFamily`, `index_passive_source_lags_sourceFamily`, `index_passive_source_lags_observedAt`, `index_passive_baseline_segments_openedAt`, `index_passive_pipeline_runs_startedAt`, `index_passive_pipeline_runs_completedAt`, `index_passive_window_revisions_windowStart`, `index_passive_window_revisions_baselineSegment`, `index_passive_window_revisions_windowStart_contentHash`, `index_passive_daily_revisions_localDate`, `index_passive_daily_revisions_baselineSegment`, `index_passive_daily_revisions_localDate_contentHash`, `index_passive_observation_decisions_localDate`, `index_passive_observation_decisions_baselineSegment`, and `index_passive_observation_decisions_localDate_contentHash`. Extend `installResearchImmutability` without renaming it:

```kotlin
internal fun installResearchImmutability(db: SupportSQLiteDatabase) {
    val immutable = listOf(
        "research_ledger_events", "study_phases", "passive_raw_provenance",
        "passive_source_reads", "passive_source_lags", "passive_baseline_segments",
        "passive_pipeline_runs", "passive_window_revisions", "passive_daily_revisions",
        "passive_observation_decisions",
    )
    immutable.forEach { table ->
        listOf("UPDATE", "DELETE").forEach { operation ->
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS ${table}_no_${operation.lowercase()} " +
                    "BEFORE $operation ON $table " +
                    "BEGIN SELECT RAISE(ABORT, '$table is append-only'); END",
            )
        }
    }
}
```

Call this helper from `MIGRATION_7_8` after table/index creation and retain its existing fresh-install `onCreate`/`onOpen` callback use. Do not trigger `passive_raw_samples`.

- [ ] **Step 5: Validate migration and fresh-install behavior on Android**

In `MigrationTest`, create a real v7 database with a ledger row, open current, assert the row survives, insert one row through each `PassiveDao` method, and assert schema validation succeeds. In `ResearchImmutabilityTest`, assert the exact 20 trigger names (four existing plus update/delete for eight immutable operational tables) and directly prove window/daily/decision `UPDATE` and `DELETE` fail. In `PassiveRoomTest`, prove duplicate inserts return `-1L`, all append-only rows remain, joined raw values disappear after pruning, and raw provenance remains.

Run: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.data.db.MigrationTest,org.mindanchor.data.db.ResearchImmutabilityTest,org.mindanchor.data.db.PassiveRoomTest`

Expected: `BUILD SUCCESSFUL`; Room validates `7 -> 8`, fresh installs contain all v8 tables and triggers, immutable SQL raises `SQLiteConstraintException`, and only raw sample-value pruning succeeds.

- [ ] **Step 6: Generate and inspect the committed Room v8 schema**

Run: `./gradlew.bat kspDebugKotlin`

Expected: `app/schemas/org.mindanchor.data.db.AnchorDatabase/8.json` exists, reports `version: 8`, contains all nine operational tables, contains the declared foreign key/indexes, and leaves `7.json` byte-for-byte unchanged.

Run: `git diff --check && git diff -- app/schemas/org.mindanchor.data.db.AnchorDatabase/7.json`

Expected: no whitespace errors and no output for `7.json`.

- [ ] **Step 7: Commit Room v8**

```bash
git add app/src/main/java/org/mindanchor/data/db/PassiveEntities.kt app/src/main/java/org/mindanchor/data/db/PassiveDao.kt app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt app/src/test/java/org/mindanchor/data/db/PassiveDaoAppendOnlyTest.kt app/src/test/java/org/mindanchor/data/db/ResearchBuilderCallbackTest.kt app/src/androidTest/java/org/mindanchor/data/db/MigrationTest.kt app/src/androidTest/java/org/mindanchor/data/db/ResearchImmutabilityTest.kt app/src/androidTest/java/org/mindanchor/data/db/PassiveRoomTest.kt app/schemas/org.mindanchor.data.db.AnchorDatabase/8.json
git commit -m "feat: persist immutable passive pipeline history"
```

---

### Task 3: Add provenance-preserving Health Connect and UsageStats adapters

**Files:**
- Create: `app/src/main/java/org/mindanchor/vitals/PassiveHealthConnectSource.kt`
- Create: `app/src/main/java/org/mindanchor/usage/PassiveUsageStatsSource.kt`
- Modify: `app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/org/mindanchor/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/org/mindanchor/vitals/PassiveHealthConnectSourceTest.kt`
- Create: `app/src/test/java/org/mindanchor/usage/PassiveUsageStatsSourceTest.kt`
- Modify: `app/src/test/java/org/mindanchor/settings/SettingsHealthConnectButtonTest.kt`

**Interfaces:**
- Consumes Task 1 `PassiveRecordSource`, `PassiveReadRange`, `PassiveSourceRead`, `PassiveSourceRecord`, source/read/kind enums; AndroidX Health Connect 1.1.0 `ReadRecordsRequest.pageToken`/`ReadRecordsResponse.pageToken`; existing `SleepRepository.hasUsageAccess`.
- Produces `class PassiveHealthConnectSource : PassiveRecordSource` and `class PassiveUsageStatsSource : PassiveRecordSource`.
- Produces `suspend fun PassiveHealthConnectSource.historyPermissionGranted(): Boolean` for Task 4.
- Produces test seams `PassiveHealthConnectGateway`, `readAllPages`, `PassiveUsageStatsGateway`, and `RawUsageEvent` with the exact signatures in Steps 2 and 4.
- Keeps every existing public function/property signature in `HealthConnectSource`; only its permission contents and ordered label list grow.

- [ ] **Step 1: Write failing pagination, outcome, normalization, and permission tests**

```kotlin
@Test fun `readAllPages follows every non-null page token`() = runBlocking {
    val seen = mutableListOf<String?>()
    val first = StepsRecord(
        startTime = Instant.ofEpochMilli(1_000L), startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.ofEpochMilli(2_000L), endZoneOffset = ZoneOffset.UTC,
        count = 1L, metadata = Metadata.manualEntry(),
    )
    val second = StepsRecord(
        startTime = Instant.ofEpochMilli(2_000L), startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.ofEpochMilli(3_000L), endZoneOffset = ZoneOffset.UTC,
        count = 2L, metadata = Metadata.manualEntry(),
    )
    val result = readAllPages<StepsRecord> { token ->
        seen += token
        when (token) {
            null -> ReadRecordsResponse(listOf(first), "page-2")
            "page-2" -> ReadRecordsResponse(listOf(second), null)
            else -> error("unexpected token $token")
        }
    }
    assertEquals(listOf(null, "page-2"), seen)
    assertEquals(listOf(1L, 2L), result.map { it.count })
}

@Test fun `successful empty is not permission denied or provider failure`() = runBlocking {
    val source = PassiveHealthConnectSource(
        gateway = FakeGateway(granted = setOf(HealthPermission.getReadPermission(StepsRecord::class))),
        clock = { 10_000L },
    )
    val reads = source.read(PassiveReadRange(1_000L, 2_000L, "UTC"))
    assertEquals(PassiveReadState.SUCCESS,
        reads.single { it.sourceFamily == PassiveSourceFamily.STEPS }.state)
    assertTrue(reads.single { it.sourceFamily == PassiveSourceFamily.STEPS }.records.isEmpty())
    assertEquals(PassiveReadState.PERMISSION_DENIED,
        reads.single { it.sourceFamily == PassiveSourceFamily.SLEEP }.state)
}

@Test fun `oxygen metadata is preserved and remains unscored`() = runBlocking {
    val metadata = Metadata.autoRecorded(
        device = Device(type = Device.TYPE_RING, manufacturer = "Acme", model = "R1"),
        clientRecordId = "oxygen-client", clientRecordVersion = 7L,
    )
    val oxygen = OxygenSaturationRecord(
        time = Instant.parse("2026-08-30T01:02:03Z"), zoneOffset = ZoneOffset.ofHoursMinutes(5, 30),
        percentage = Percentage(97.0), metadata = metadata,
    )
    val source = PassiveHealthConnectSource(FakeGateway.withRecord(oxygen), clock = { 20_000L })
    val record = source.read(PassiveReadRange(0L, 30_000L, "Asia/Kolkata"))
        .single { it.sourceFamily == PassiveSourceFamily.OXYGEN_SATURATION }.records.single()
    assertEquals(PassiveRecordKind.SPO2, record.kind)
    assertEquals("percent", record.unit)
    assertEquals("Acme", record.deviceManufacturer)
    assertEquals("R1", record.deviceModel)
    assertEquals("RING", record.deviceType)
    assertEquals(7L, record.recordVersion)
    assertFalse(PassiveFeature.SPO2_PERCENT.scored)
}
```

Add source-text assertions that `HealthConnectSource.PERMISSIONS` contains oxygen, history, and background reads; `permissionLabelsInOrder` exposes all three; the manifest contains `READ_OXYGEN_SATURATION`; and settings copy says oxygen is context-only, history supports baseline building, and background access supports periodic local reads.

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.vitals.PassiveHealthConnectSourceTest --tests org.mindanchor.usage.PassiveUsageStatsSourceTest --tests org.mindanchor.settings.SettingsHealthConnectButtonTest`

Expected: compilation fails because the two provenance-preserving adapters and pagination helper do not exist.

- [ ] **Step 2: Implement one paginated Health Connect gateway**

```kotlin
internal interface PassiveHealthConnectGateway {
    fun sdkStatus(): Int
    suspend fun grantedPermissions(): Set<String>
    suspend fun <T : Record> readAll(recordType: KClass<T>, range: TimeRangeFilter): List<T>
}

internal suspend fun <T : Record> readAllPages(
    readPage: suspend (pageToken: String?) -> ReadRecordsResponse<T>,
): List<T> {
    val records = mutableListOf<T>()
    var token: String? = null
    do {
        val page = readPage(token)
        records += page.records
        token = page.pageToken
    } while (token != null)
    return records
}

private class AndroidPassiveHealthConnectGateway(
    context: Context,
) : PassiveHealthConnectGateway {
    private val appContext = context.applicationContext
    override fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(appContext)
    override suspend fun grantedPermissions(): Set<String> =
        HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()

    override suspend fun <T : Record> readAll(
        recordType: KClass<T>,
        range: TimeRangeFilter,
    ): List<T> {
        val client = HealthConnectClient.getOrCreate(appContext)
        return readAllPages { pageToken ->
            client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = range,
                    ascendingOrder = true,
                    pageSize = 1_000,
                    pageToken = pageToken,
                ),
            )
        }
    }
}
```

Do not add another direct `HealthConnectClient.readRecords` call anywhere in either source. `PassiveHealthConnectSource.read` must call this gateway separately for `HeartRateRecord`, `RestingHeartRateRecord`, `HeartRateVariabilityRmssdRecord`, `SleepSessionRecord`, `StepsRecord`, `ExerciseSessionRecord`, and `OxygenSaturationRecord` so one family failure cannot erase successful families.

- [ ] **Step 3: Normalize every Health Connect family and classify failures**

```kotlin
class PassiveHealthConnectSource internal constructor(
    private val gateway: PassiveHealthConnectGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : PassiveRecordSource {
    constructor(context: Context) : this(AndroidPassiveHealthConnectGateway(context))

    override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
        val attemptedAt = clock()
        if (gateway.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) {
            return HEALTH_FAMILIES.map {
                PassiveSourceRead(it, PassiveReadState.UNAVAILABLE, range, attemptedAt,
                    errorCode = "HEALTH_CONNECT_UNAVAILABLE")
            }
        }
        val granted = try {
            gateway.grantedPermissions()
        } catch (failure: Throwable) {
            return HEALTH_FAMILIES.map { failedRead(it, range, attemptedAt, failure) }
        }
        val filter = TimeRangeFilter.between(
            Instant.ofEpochMilli(range.startInclusive), Instant.ofEpochMilli(range.endExclusive),
        )
        return listOf(
            readFamily<HeartRateRecord>(PassiveSourceFamily.HEART_RATE, filter, range, granted, attemptedAt) { normalize(it, range, attemptedAt) },
            readFamily<RestingHeartRateRecord>(PassiveSourceFamily.RESTING_HEART_RATE, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<HeartRateVariabilityRmssdRecord>(PassiveSourceFamily.HRV_RMSSD, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<SleepSessionRecord>(PassiveSourceFamily.SLEEP, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<StepsRecord>(PassiveSourceFamily.STEPS, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<ExerciseSessionRecord>(PassiveSourceFamily.EXERCISE, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<OxygenSaturationRecord>(PassiveSourceFamily.OXYGEN_SATURATION, filter, range, granted, attemptedAt) { listOf(normalize(it, range, attemptedAt)) },
        )
    }

    suspend fun historyPermissionGranted(): Boolean =
        gateway.sdkStatus() == HealthConnectClient.SDK_AVAILABLE &&
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in gateway.grantedPermissions()
}
```

For each family, derive its read permission with `HealthPermission.getReadPermission(recordType)`. Return `PERMISSION_DENIED` without calling the gateway when absent. Map `SecurityException` to `PERMISSION_DENIED`; `IOException`, `RemoteException`, and `DeadObjectException` to `READ_FAILURE_TRANSIENT`; all other `Exception` values to `READ_FAILURE_PERMANENT`; rethrow `CancellationException` and every `Error`.

Normalization rules are exact:

- A `HeartRateRecord` produces one `HEART_RATE_SAMPLE` per sample with id `${metadata.id.ifBlank { metadata.clientRecordId }}#${sample.time.toEpochMilli()}#${sampleIndex}`, value bpm, and parent metadata/version.
- RHR is bpm, HRV is ms, SpO2 is percent, steps is count, and sleep/exercise use value `null` with unit `milliseconds`.
- Use interval start/end for interval/series records and `time` for both boundaries of instant records.
- `dataOriginPackage = metadata.dataOrigin.packageName`; device manufacturer/model come directly from `metadata.device`; map known `Device.TYPE_*` integers to `WATCH`, `PHONE`, `SCALE`, `RING`, `HEAD_MOUNTED`, `FITNESS_BAND`, `CHEST_STRAP`, or `SMART_DISPLAY`, else `UNKNOWN`.
- `sourceUpdatedTime = metadata.lastModifiedTime.toEpochMilli()` when it is after `Instant.EPOCH`, otherwise `null`; `recordVersion = metadata.clientRecordVersion`.
- Use the requested zone id. Prefer the record's start/time zone offset; if null, ask `ZoneId.of(range.zoneId).rules` for the offset at event start.
- If both metadata id and client id are blank, derive `recordId` from SHA-256 of family, event bounds, origin, value/unit, manufacturer/model/type, and record version.

- [ ] **Step 4: Implement explicit-success UsageStats ingestion**

```kotlin
internal data class RawUsageEvent(val eventType: Int, val timeStamp: Long)

internal interface PassiveUsageStatsGateway {
    fun hasUsageAccess(): Boolean
    fun queryEvents(startInclusive: Long, endExclusive: Long): List<RawUsageEvent>
}

class PassiveUsageStatsSource internal constructor(
    private val gateway: PassiveUsageStatsGateway,
    private val manufacturer: String,
    private val model: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : PassiveRecordSource {
    constructor(context: Context) : this(
        AndroidPassiveUsageStatsGateway(context.applicationContext),
        Build.MANUFACTURER, Build.MODEL,
    )

    override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
        val attemptedAt = clock()
        if (!gateway.hasUsageAccess()) return listOf(
            PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.PERMISSION_DENIED,
                range, attemptedAt, errorCode = "PACKAGE_USAGE_STATS_DENIED"),
        )
        return try {
            val zone = ZoneId.of(range.zoneId)
            val records = gateway.queryEvents(range.startInclusive, range.endExclusive).mapNotNull { raw ->
                val kind = when (raw.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> PassiveRecordKind.SCREEN_INTERACTIVE
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> PassiveRecordKind.SCREEN_NON_INTERACTIVE
                    UsageEvents.Event.KEYGUARD_HIDDEN -> PassiveRecordKind.SCREEN_UNLOCKED
                    else -> null
                } ?: return@mapNotNull null
                PassiveSourceRecord(
                    PassiveSourceFamily.USAGE_STATS, kind, raw.timeStamp, raw.timeStamp, null, "event",
                    "android.usage_stats", manufacturer, model, "PHONE", null, attemptedAt,
                    zone.id, zone.rules.getOffset(Instant.ofEpochMilli(raw.timeStamp)).totalSeconds,
                    PassiveSeed.sha256("${kind.name}|${raw.timeStamp}|$manufacturer|$model"), 0L,
                )
            }
            listOf(PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.SUCCESS,
                range, attemptedAt, records))
        } catch (failure: SecurityException) {
            listOf(PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.PERMISSION_DENIED,
                range, attemptedAt, errorCode = "PACKAGE_USAGE_STATS_DENIED"))
        } catch (failure: RuntimeException) {
            listOf(PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.READ_FAILURE_PERMANENT,
                range, attemptedAt, errorCode = failure.javaClass.simpleName))
        }
    }
}
```

Use Task 1's `PassiveSeed.sha256(material)` lowercase SHA-256 helper for stable record IDs. `AndroidPassiveUsageStatsGateway.queryEvents` must run the current `UsageStatsManager.queryEvents` loop and copy each mutable platform event into an immutable `RawUsageEvent`. Returning an empty list is a successful source read.

- [ ] **Step 5: Wire additive permissions and accurate existing-screen wording**

Add to `HealthConnectSource.PERMISSIONS`:

```kotlin
HealthPermission.getReadPermission(OxygenSaturationRecord::class),
HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
```

Append ordered labels `oxygen_saturation`, `history`, and `background`. Keep the mindfulness feature gate by subtracting only the mindfulness permission from `base`. Add `<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />`; retain the already declared history/background manifest permissions.

Append three `labelKeyToRes` entries in the existing Health Connect section and use this exact substance in strings:

```xml
<string name="health_connect_explainer">MindAnchor can locally read heart rate, HRV, oxygen saturation, sleep, steps, and exercise from Health Connect. Oxygen saturation is kept as context and is not scored. History access lets your own baseline use older records; background access lets the six-hour local reader run when this screen is closed. You can grant or revoke each permission in Health Connect.</string>
<string name="health_connect_reads_oxygen_saturation">oxygen saturation — retained as context only and never scored</string>
<string name="health_connect_reads_history">health-data history — lets baseline building read up to 120 days on the first successful run</string>
<string name="health_connect_reads_background">background health data — lets the local six-hour reader collect while MindAnchor is closed</string>
```

Change partial/full status copy from “signal types” to “Health Connect permissions” so history/background grants are not mislabeled as sensor signals. Do not add a new route, card, screen, notification, or Program 2 observation surface.

- [ ] **Step 6: Run adapter and permission tests**

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.vitals.PassiveHealthConnectSourceTest --tests org.mindanchor.usage.PassiveUsageStatsSourceTest --tests org.mindanchor.settings.SettingsHealthConnectButtonTest --tests org.mindanchor.settings.HealthConnectLauncherCacheTest`

Expected: `BUILD SUCCESSFUL`; every family paginates, successful-empty is preserved, family failures are isolated, transient/permanent/denied/unavailable states are distinct, metadata fields survive normalization, UsageStats requires explicit read success, and the existing permission launcher still uses `effectivePermissions(context)`.

- [ ] **Step 7: Commit the source adapters**

```bash
git add app/src/main/java/org/mindanchor/vitals/PassiveHealthConnectSource.kt app/src/main/java/org/mindanchor/usage/PassiveUsageStatsSource.kt app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt app/src/main/AndroidManifest.xml app/src/main/java/org/mindanchor/settings/SettingsScreen.kt app/src/main/res/values/strings.xml app/src/test/java/org/mindanchor/vitals/PassiveHealthConnectSourceTest.kt app/src/test/java/org/mindanchor/usage/PassiveUsageStatsSourceTest.kt app/src/test/java/org/mindanchor/settings/SettingsHealthConnectButtonTest.kt
git commit -m "feat: ingest passive sources with provenance"
```

---

### Task 4: Orchestrate ingestion, provenance, backfills, finality, and estimator revisions

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassivePipelineCodec.kt`
- Create: `app/src/main/java/org/mindanchor/intelligence/PassivePipelineRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt`
- Modify: `app/src/main/java/org/mindanchor/research/TransformationRegistry.kt`
- Create: `app/src/test/java/org/mindanchor/intelligence/PassivePipelineCodecTest.kt`
- Create: `app/src/test/java/org/mindanchor/intelligence/PassivePipelineRepositoryTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/TransformationRegistryTest.kt`

**Interfaces:**
- Consumes Task 1 sources/aggregators/models and `PassiveEstimator.observe`; Task 2 `PassiveDao` inserts/queries; Task 3 `PassiveHealthConnectSource.historyPermissionGranted`; existing `ResearchLedgerRepository.build(context).provenance.ensureCurrentPhase(now)` and `.refreshAfterCommit()`.
- Produces `sealed interface PassivePipelineResult { data class Completed(val runId: String, val insertedWindows: Int, val insertedDays: Int, val insertedDecisions: Int) : PassivePipelineResult; data class Retry(val runId: String) : PassivePipelineResult }`.
- Produces `class PassivePipelineRepository internal constructor(database: AnchorDatabase, healthSource: PassiveRecordSource, usageSource: PassiveRecordSource, historyPermissionGranted: suspend () -> Boolean, ensureCurrentPhase: suspend (Long) -> Unit, refreshProvenanceAfterCommit: suspend () -> Unit) { suspend fun run(now: Long, zone: ZoneId): PassivePipelineResult; companion object { fun build(context: Context): PassivePipelineRepository } }`.
- Produces canonical codec methods: `rawIdentity`, `sourceReadEntity`, `sourceLagEntity`, `windowEntity`, `dailyEntity`, `dailyToDomain`, `decisionEntity`, `decisionToDomain`, `contentHash`, and sorted fingerprint JSON. Task 5/6 may map entities to DTOs but must never expose raw sample values.
- Uses Task 2 DAO signatures exactly; no DAO update/replace/delete is introduced.

- [ ] **Step 1: Write failing orchestration tests around scan ranges and source outcomes**

```kotlin
@Test fun `first history-granted run scans 120 days then normal runs rescan seven`() = runBlocking {
    val health = FakeSource()
    val usage = FakeSource()
    val repository = repository(health = health, usage = usage, historyGranted = { true })
    val now = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()

    assertTrue(repository.run(now, ZoneId.of("UTC")) is PassivePipelineResult.Completed)
    assertEquals(LocalDate.parse("2026-05-03").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        health.ranges.single().startInclusive)

    assertTrue(repository.run(now + 6 * 3_600_000L, ZoneId.of("UTC")) is PassivePipelineResult.Completed)
    assertEquals(LocalDate.parse("2026-08-24").atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        health.ranges.last().startInclusive)
}

@Test fun `denied and unavailable reads are persisted while transient provider errors retry`() = runBlocking {
    val denied = PassiveSourceRead(PassiveSourceFamily.SLEEP, PassiveReadState.PERMISSION_DENIED,
        range, 1_000L, errorCode = "READ_SLEEP_DENIED")
    val unavailable = PassiveSourceRead(PassiveSourceFamily.USAGE_STATS, PassiveReadState.UNAVAILABLE,
        range, 1_000L, errorCode = "USAGE_STATS_UNAVAILABLE")
    val transient = PassiveSourceRead(PassiveSourceFamily.HEART_RATE, PassiveReadState.READ_FAILURE_TRANSIENT,
        range, 1_000L, errorCode = "DeadObjectException")
    val result = repository(reads = listOf(denied, unavailable, transient)).run(2_000L, ZoneId.of("UTC"))
    assertTrue(result is PassivePipelineResult.Retry)
    assertEquals(setOf("PERMISSION_DENIED", "UNAVAILABLE", "READ_FAILURE_TRANSIENT"),
        dao.sourceReadsNow().map { it.state }.toSet())
}
```

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassivePipelineRepositoryTest --tests org.mindanchor.intelligence.PassivePipelineCodecTest`

Expected: compilation fails because repository/codec classes do not exist.

- [ ] **Step 2: Implement canonical codecs and exact revision identity**

Use one `Json` instance with `encodeDefaults = true`, `explicitNulls = true`, and `prettyPrint = false`. Canonicalize maps by enum name and lists by stable identity before encoding. `contentHash` is lowercase SHA-256 of canonical JSON.

```kotlin
object PassivePipelineCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = true; prettyPrint = false }

    fun contentHash(canonicalJson: String): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    fun rawIdentity(record: PassiveSourceRecord): String = contentHash(
        listOf(record.sourceFamily.name, record.kind.name, record.recordId,
            record.recordVersion.toString(), record.eventStart.toString(), record.eventEnd.toString())
            .joinToString("|"),
    )

    fun calibrationSeed(segment: String, frozenAsOfTime: Long, calibrationVersion: String): Long =
        PassiveSeed.firstSigned64Bits("$segment|$frozenAsOfTime|$calibrationVersion")

    fun shouldAppend(
        previousContentHash: String?,
        nextContentHash: String,
        reason: RevisionReason,
    ): Boolean = previousContentHash != nextContentHash ||
        reason == RevisionReason.FINALITY || reason == RevisionReason.BACKFILL
}

enum class RevisionReason { INITIAL, CONTENT_CHANGED, FINALITY, BACKFILL }
```

Implement entity conversion with explicit DTOs rather than serializing Room entities directly. Window canonical content includes bounds, zone/offset, wake-relative alignment, segment, sorted feature rows, coverage/eligibility/exercise overlap, sorted provenance ids, missingness, exclusions, transformation version, source-updated/ingested times, and finality; it excludes entity id, revision reason, and content hash. Daily canonical content includes every `PassiveDailyAggregate` field plus window/daily versions and watermark. Decision canonical content includes every `PassiveObservation` field, nested calibration configuration/domain/feature/shift evidence, seed, segment, frozen identity, and revision reason only outside the hash.

`dailyToDomain` must parse exact enum names and construct the existing Program 2A signature:

```kotlin
fun dailyToDomain(entity: PassiveDailyRevisionEntity): PassiveDay = PassiveDay(
    day = LocalDate.parse(entity.localDate),
    dataStatus = PassiveDataStatus.valueOf(entity.dataStatus),
    features = decodeFeatureMap(entity.featuresJson),
    excludedFeatures = decodeFeatureSet(entity.excludedFeaturesJson),
    baselineSegment = entity.baselineSegment,
    sourceUpdatedTime = entity.sourceUpdatedTime,
    ingestedAt = entity.ingestedAt,
)
```

Round-trip tests must compare complete values, reject unknown enum names, prove map/list order does not alter hashes, and pin one raw/window/day/decision hash.

- [ ] **Step 3: Persist reads, raw provenance/value rows, lag evidence, and stable configured segments**

Implement the repository constructor and production factory:

```kotlin
class PassivePipelineRepository internal constructor(
    private val database: AnchorDatabase,
    private val healthSource: PassiveRecordSource,
    private val usageSource: PassiveRecordSource,
    private val historyPermissionGranted: suspend () -> Boolean,
    private val ensureCurrentPhase: suspend (Long) -> Unit,
    private val refreshProvenanceAfterCommit: suspend () -> Unit,
) {
    companion object {
        const val HISTORY_DAYS = 120L
        const val AVAILABLE_HISTORY_DAYS = 30L
        const val RESCAN_DAYS = 7L

        fun build(context: Context): PassivePipelineRepository {
            val app = context.applicationContext
            val health = PassiveHealthConnectSource(app)
            val provenance = ResearchLedgerRepository.build(app).provenance
            return PassivePipelineRepository(
                database = AnchorDatabase.get(app), healthSource = health,
                usageSource = PassiveUsageStatsSource(app),
                historyPermissionGranted = health::historyPermissionGranted,
                ensureCurrentPhase = { provenance.ensureCurrentPhase(it); Unit },
                refreshProvenanceAfterCommit = provenance::refreshAfterCommit,
            )
        }
    }
}
```

At run start, use `successfulPermissionedRunCount() == 0`. A history-granted initial range contains the current local date plus the preceding 119 dates; a non-history initial range contains current plus 29 preceding dates; a normal range contains current plus six preceding dates. End is `now`, not tomorrow, so future data is never requested.

For every source result, insert one `PassiveSourceReadEntity`; for every record insert one `PassiveRawProvenanceEntity` and one `PassiveRawSampleEntity`. For lag evidence compute:

```kotlin
val observedUpdatedAt = record.sourceUpdatedTime ?: record.ingestedAt
val lag = (observedUpdatedAt - record.eventEnd).coerceAtLeast(0L)
val usedFallback = record.sourceUpdatedTime == null
```

Deduplicate lag rows by a hash of source family, record id/version, event end, observed-updated time, ingestion time, and fallback flag.

Build scored fingerprints only from these mappings: RHR/HRV -> physiology; sleep -> sleep; steps/exercise -> activity; UsageStats -> routine. Exclude general heart-rate quality and SpO2. Decode the latest segment's configured fingerprints. If no segment exists, configure the currently observed scored fingerprints. If a later run observes any scored fingerprint outside the configured set, union it into the set and open a new segment. If a configured fingerprint is absent today, retain it unchanged. Hash with both Task 1 transformation versions and `INSERT OR IGNORE` the segment.

- [ ] **Step 4: Aggregate affected windows/days and append only meaningful revisions**

Within one `database.withTransaction`, read joined raw records for the scan range, convert to Task 1 models, build UTC windows, and process each touched local date. Persist source read/lag/raw rows before deriving so a process restart can recompute the same range.

For each window/day choose `RevisionReason` exactly:

```kotlin
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
```

Call `shouldAppend` after this classification; the final `else` is harmless because equal-content/non-backfill/non-finality rows are skipped. Use `asOfTime = now`, id `contentHash("$logicalKey|$now|$nextHash|${reason.name}")`, and `INSERT OR IGNORE`. A backfill with canonically equal output still appends a revision carrying `BACKFILL`; an ordinary rescan with equal content appends nothing.

Compute each day's finality from the latest segment's configured families and all persisted lag observations. `PassiveDailyAggregator` supplies precise provisional/final/insufficient/suppressed status. Never promote an insufficient or suppressed day to final merely because the watermark passed.

- [ ] **Step 5: Run Program 2A with the exact seed and append canonical decisions**

Before the first window/day/decision insert in a run, call `ensureCurrentPhase(now)`; after the Room transaction commits, call `refreshProvenanceAfterCommit()`.

For each newly appended daily revision, decode all prior daily revisions and prior decisions with the codec. Obtain the frozen identity before calibration, then seed and observe:

```kotlin
val day = PassivePipelineCodec.dailyToDomain(dailyEntity)
val history = dao.dailyHistory(dailyEntity.localDate, now).map(PassivePipelineCodec::dailyToDomain)
val prior = dao.priorDecisions(dailyEntity.localDate, now).map(PassivePipelineCodec::decisionToDomain)
val frozen = PassiveBaselineBuilder.freeze(history, day.day, now, day.baselineSegment)
val calibrationVersion = requireNotNull(
    TransformationRegistry.versionOf("passive-block-calibration"),
)
val seed = frozen?.let {
    PassivePipelineCodec.calibrationSeed(day.baselineSegment, it.frozenAsOfTime, calibrationVersion)
} ?: 0L
val observation = PassiveEstimator.observe(day, now, history, prior, seed)
```

Persist `calibrationSeed = observation.calibration?.seed`; a baseline-building decision therefore carries null rather than pretending seed zero was calibrated. Compare against `latestObservationDecision(localDate)`. Append only changed canonical content, `FINALITY`, or `BACKFILL`. Never update the original provisional/final decision.

After all inserts, append a `PassivePipelineRunEntity`. Use result `SUCCESS_PERMISSIONED` when at least one source is `SUCCESS`; `SUCCESS_NO_PERMISSION` when every source is unavailable/denied; `SUCCESS_WITH_FAILURES` for non-transient permanent failures; and `RETRY_TRANSIENT` when any source is transient. Return `Retry` only for `RETRY_TRANSIENT` after recording the run and every source state.

- [ ] **Step 6: Register operational transformations and the active-minutes model change**

Change only the model component:

```kotlin
const val MODEL_SET_VERSION = "personal-robust-baseline-v4"
```

Keep `RULE_SET_VERSION = PassiveEstimator.RULE_VERSION`. Append these transformations:

```kotlin
Transformation(
    id = "passive-window-features",
    version = PassiveWindowAggregator.TRANSFORMATION_VERSION,
    input = "Provenance-preserving Health Connect and UsageStats source records.",
    output = "Absolute UTC 15-minute feature and quality revisions.",
    description = "Clips intervals to half-open windows, measures heart-rate minute-bin coverage, " +
        "marks wake-relative alignment, and suppresses only physiology overlapping exercise. " +
        "No absent value is filled or carried forward.",
),
Transformation(
    id = "passive-daily-features",
    version = PassiveDailyAggregator.TRANSFORMATION_VERSION,
    input = "Versioned passive windows, source-read outcomes, and raw interval provenance.",
    output = "Local-date passive feature revisions with finality, coverage, missingness, and exclusions.",
    description = "Assigns sleep to wake date, clips steps and active minutes to local days, requires " +
        "explicit successful UsageStats reads, and applies source-lag watermarks before final status.",
),
```

Update provenance tests to pin the two ids/versions and `personal-robust-baseline-v4`. Recompute `TransformationRegistry.setVersion` from the failing expected-value assertion and update only that frozen value and current export-content pins whose content includes the registry. Confirm the first Program 2B write opens a transformation/model study phase through the existing coordinator test seam.

- [ ] **Step 7: Run repository, Program 2A regression, and provenance tests**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassivePipelineCodecTest --tests org.mindanchor.intelligence.PassivePipelineRepositoryTest
./gradlew.bat testDebugUnitTest --tests 'org.mindanchor.intelligence.Passive*Test' --tests org.mindanchor.research.ProvenanceVersionsTest --tests org.mindanchor.research.TransformationRegistryTest --tests org.mindanchor.research.ResearchProvenanceCoordinatorTest
```

Expected: both commands end `BUILD SUCCESSFUL`; tests prove initial/normal lookbacks, empty/failure outcomes, fallback lag evidence, 29/30 p99 transition, missing-source segment stability, new-fingerprint segment opening, deterministic seed, exercise-local suppression, source backfill revisions, no-op rescan suppression, append-only provisional-to-final decisions, and unchanged Program 2A weekday/weekend baseline behavior.

- [ ] **Step 8: Commit repository orchestration and provenance**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassivePipelineCodec.kt app/src/main/java/org/mindanchor/intelligence/PassivePipelineRepository.kt app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt app/src/main/java/org/mindanchor/research/TransformationRegistry.kt app/src/test/java/org/mindanchor/intelligence/PassivePipelineCodecTest.kt app/src/test/java/org/mindanchor/intelligence/PassivePipelineRepositoryTest.kt app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt app/src/test/java/org/mindanchor/research/TransformationRegistryTest.kt
git commit -m "feat: orchestrate passive revisions and backfills"
```

---

### Task 5: Add continuity snapshot v3 capture and replacement-phone restore

**Files:**
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuityHashVersionTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt`

**Interfaces:**
- Consumes Task 2 `PassiveDao` long-term row queries/inserts and all operational entities. It never reads or inserts `PassiveRawSampleEntity`.
- Produces `ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION = 2`, current `SNAPSHOT_FORMAT_VERSION = 3`, and supported set `{1, 2, 3}` while retaining `PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1`.
- Appends eight defaulted lists to `ContinuityPayload`, after `studyPhases`, in this exact order: `passiveRawProvenance`, `passiveSourceReads`, `passiveSourceLags`, `passiveBaselineSegments`, `passivePipelineRuns`, `passiveWindowRevisions`, `passiveDailyRevisions`, `passiveObservationDecisions`.
- Produces field-for-field serializable DTOs with the entity names changed from `Entity` to `Dto`; `PassiveRawProvenanceDto` deliberately has no `value`, and no `PassiveRawSampleDto` exists.
- Produces frozen `ContinuityPayloadV2`, `programOneSnapshotContentByField`, `programTwoSnapshotContentByField`, and `mergePassiveRows`.

- [ ] **Step 1: Write failing version/projection/raw-exclusion tests**

```kotlin
@Test fun `snapshot versions one and two stay supported when current becomes three`() {
    assertEquals(1, ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION)
    assertEquals(2, ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION)
    assertEquals(3, ContinuityContract.SNAPSHOT_FORMAT_VERSION)
    assertEquals(setOf(1, 2, 3), ContinuityContract.SUPPORTED_SNAPSHOT_FORMAT_VERSIONS)
}

@Test fun `version two projection remains the exact Program 1 payload`() {
    assertEquals(
        listOf("journalEntries", "contextRows", "morningMeasures", "notes", "letters",
            "readLetterDates", "frictionedApps", "alwaysOpenApps", "continuityChanges",
            "legacyBackupJson", "researchLedgerEvents", "studyPhases"),
        serializer<ContinuityPayloadV2>().descriptor.elementNames.toList(),
    )
}

@Test fun `snapshot carries passive provenance but no raw sample value shape`() {
    val payloadFields = ContinuityPayload::class.java.declaredFields.map { it.name }.toSet()
    assertTrue("passiveRawProvenance" in payloadFields)
    assertFalse("passiveRawSamples" in payloadFields)
    assertFalse(PassiveRawProvenanceDto::class.java.declaredFields.any { it.name == "value" })
}
```

Add one mutation per Program 2 list and assert a v1 or v2 document carrying that one list decodes as `Corrupt`. Add a genuine v1 fixture and genuine v2 fixture and assert both retain their pinned hashes and decode with every Program 2 list empty.

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.continuity.ContinuityContractTest --tests org.mindanchor.continuity.ContinuityHashVersionTest --tests org.mindanchor.continuity.ContinuitySnapshotCodecTest`

Expected: tests fail because snapshot v3, `PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION`, and Program 2 payload lists do not exist.

- [ ] **Step 2: Append v3 DTOs and preserve frozen v1/v2 hashes**

Update constants:

```kotlin
const val SNAPSHOT_FORMAT_VERSION = 3
const val PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION = 1
const val PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION = 2
val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS = setOf(
    PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION,
    PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION,
    SNAPSHOT_FORMAT_VERSION,
)
```

Append the eight lists from **Interfaces** with `= emptyList()`. Define DTO fields exactly equal to the corresponding Task 2 entity fields. For `PassiveRawProvenanceDto`, that means id through recordVersion from `PassiveRawProvenanceEntity`; it has no raw value. Add `toDto`/`toEntity` functions for every operational type.

Extend canonical sorting with these total orders:

```kotlin
passiveRawProvenance = payload.passiveRawProvenance.sortedWith(compareBy({ it.eventStart }, { it.id })),
passiveSourceReads = payload.passiveSourceReads.sortedWith(compareBy({ it.attemptedAt }, { it.sourceFamily }, { it.id })),
passiveSourceLags = payload.passiveSourceLags.sortedWith(compareBy({ it.observedAt }, { it.sourceFamily }, { it.id })),
passiveBaselineSegments = payload.passiveBaselineSegments.sortedWith(compareBy({ it.openedAt }, { it.id })),
passivePipelineRuns = payload.passivePipelineRuns.sortedWith(compareBy({ it.completedAt }, { it.id })),
passiveWindowRevisions = payload.passiveWindowRevisions.sortedWith(compareBy({ it.windowStart }, { it.asOfTime }, { it.id })),
passiveDailyRevisions = payload.passiveDailyRevisions.sortedWith(compareBy({ it.localDate }, { it.asOfTime }, { it.id })),
passiveObservationDecisions = payload.passiveObservationDecisions.sortedWith(compareBy({ it.localDate }, { it.asOfTime }, { it.id })),
```

Freeze Program 1's prior shape:

```kotlin
@Serializable
internal data class ContinuityPayloadV2(
    val journalEntries: List<JournalEntryDto>,
    val contextRows: List<JournalContextDto>,
    val morningMeasures: List<MorningMeasureDto>,
    val notes: List<NoteDto>,
    val letters: List<LetterDto>,
    val readLetterDates: List<String>,
    val frictionedApps: List<String>,
    val alwaysOpenApps: List<String>,
    val continuityChanges: List<ContinuityChangeDto>,
    val legacyBackupJson: String,
    val researchLedgerEvents: List<ResearchLedgerEventDto>,
    val studyPhases: List<StudyPhaseDto>,
)
```

`ContinuityContentHasher.hash` selects `projectV1`, `projectV2`, or the full canonical v3 payload. Do not alter `ContinuityPayloadV1`, `projectV1`, its golden hash, the encoder settings, or existing field order.

- [ ] **Step 3: Reject newer-field smuggling into v1 and v2 snapshots**

```kotlin
internal fun programOneSnapshotContentByField(payload: ContinuityPayload): Map<String, Boolean> = mapOf(
    "researchLedgerEvents" to payload.researchLedgerEvents.isNotEmpty(),
    "studyPhases" to payload.studyPhases.isNotEmpty(),
)

internal fun programTwoSnapshotContentByField(payload: ContinuityPayload): Map<String, Boolean> = mapOf(
    "passiveRawProvenance" to payload.passiveRawProvenance.isNotEmpty(),
    "passiveSourceReads" to payload.passiveSourceReads.isNotEmpty(),
    "passiveSourceLags" to payload.passiveSourceLags.isNotEmpty(),
    "passiveBaselineSegments" to payload.passiveBaselineSegments.isNotEmpty(),
    "passivePipelineRuns" to payload.passivePipelineRuns.isNotEmpty(),
    "passiveWindowRevisions" to payload.passiveWindowRevisions.isNotEmpty(),
    "passiveDailyRevisions" to payload.passiveDailyRevisions.isNotEmpty(),
    "passiveObservationDecisions" to payload.passiveObservationDecisions.isNotEmpty(),
)

private fun smugglesNewerContent(snapshot: ContinuitySnapshot): Boolean = when (snapshot.formatVersion) {
    ContinuityContract.PROGRAM_ZERO_SNAPSHOT_FORMAT_VERSION ->
        programOneSnapshotContentByField(snapshot.payload).values.any { it } ||
            programTwoSnapshotContentByField(snapshot.payload).values.any { it }
    ContinuityContract.PROGRAM_ONE_SNAPSHOT_FORMAT_VERSION ->
        programTwoSnapshotContentByField(snapshot.payload).values.any { it }
    else -> false
}
```

Call this guard in `ContinuitySnapshotCodec.decode` after supported-version validation and before returning `Success`. Reflection tests must prove every post-v1 field belongs to either Program 1 or Program 2 maps and every post-v2 field belongs to Program 2. Mutation tests prove each predicate reads its own field.

- [ ] **Step 4: Capture all long-term operational rows in one Room transaction**

Extend `ContinuitySnapshotRepository.RoomRows` and its existing `database.withTransaction` block. Read all Task 2 long-term lists from `database.passive()` in the same transaction as journal/research data, map to DTOs, and append them to `ContinuityPayload`. Do not call `rawRecords`, access `PassiveRawSampleEntity`, or serialize a field named `value` on raw provenance.

```kotlin
val passive = database.passive()
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
)
```

The instrumented repository test inserts a raw provenance row plus raw value 97.0 and every long-term row, captures, decodes, and asserts all long-term rows are present while encoded JSON contains neither `passiveRawSamples` nor the raw value field/value.

- [ ] **Step 5: Restore operational rows idempotently without recreating raw values**

```kotlin
internal suspend fun mergePassiveRows(database: AnchorDatabase, payload: ContinuityPayload) {
    val dao = database.passive()
    dao.insertRawProvenance(payload.passiveRawProvenance.map { it.toEntity() })
    dao.insertSourceReads(payload.passiveSourceReads.map { it.toEntity() })
    dao.insertSourceLags(payload.passiveSourceLags.map { it.toEntity() })
    payload.passiveBaselineSegments.forEach { dao.insertBaselineSegment(it.toEntity()) }
    payload.passivePipelineRuns.forEach { dao.insertPipelineRun(it.toEntity()) }
    dao.insertWindowRevisions(payload.passiveWindowRevisions.map { it.toEntity() })
    dao.insertDailyRevisions(payload.passiveDailyRevisions.map { it.toEntity() })
    dao.insertObservationDecisions(payload.passiveObservationDecisions.map { it.toEntity() })

    check(dao.rawProvenanceNow().map { it.id }.containsAll(payload.passiveRawProvenance.map { it.id }))
    check(dao.windowRevisionsNow().map { it.id }.containsAll(payload.passiveWindowRevisions.map { it.id }))
    check(dao.dailyRevisionsNow().map { it.id }.containsAll(payload.passiveDailyRevisions.map { it.id }))
    check(dao.observationDecisionsNow().map { it.id }
        .containsAll(payload.passiveObservationDecisions.map { it.id }))
}
```

Call `mergePassiveRows` inside the existing `RestoreCoordinator.build` Room transaction after `mergeResearchRows`. Extend preflight emptiness to require all operational long-term lists empty. Never create `PassiveRawSampleEntity` during restore. Run the production merge twice in `ContinuityRoundTripTest`; assert counts and ids remain identical, raw joined records remain empty for restored historical provenance, the v3 recapture hash equals the staged v3 hash, and original provisional/final revisions both survive.

- [ ] **Step 6: Run JVM codec/restore tests and instrumented capture/restore tests**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests 'org.mindanchor.continuity.Continuity*Test' --tests org.mindanchor.continuity.RestoreCoordinatorTest
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ContinuitySnapshotRepositoryTest,org.mindanchor.continuity.ContinuityRoundTripTest
```

Expected: both commands end `BUILD SUCCESSFUL`; v1/v2 goldens remain unchanged, v3 round-trips, smuggling is rejected, Room capture is transactionally consistent, restore is idempotent, and no raw sample value crosses the snapshot boundary.

- [ ] **Step 7: Commit continuity snapshot v3**

```bash
git add app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshot.kt app/src/main/java/org/mindanchor/continuity/ContinuityContentHasher.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotCodec.kt app/src/main/java/org/mindanchor/continuity/ContinuitySnapshotRepository.kt app/src/main/java/org/mindanchor/continuity/RestoreCoordinator.kt app/src/test/java/org/mindanchor/continuity/ContinuityContractTest.kt app/src/test/java/org/mindanchor/continuity/ContinuityHashVersionTest.kt app/src/test/java/org/mindanchor/continuity/ContinuitySnapshotCodecTest.kt app/src/test/java/org/mindanchor/continuity/RestoreCoordinatorTest.kt app/src/androidTest/java/org/mindanchor/continuity/ContinuitySnapshotRepositoryTest.kt app/src/androidTest/java/org/mindanchor/continuity/ContinuityRoundTripTest.kt
git commit -m "feat: carry passive history in snapshot v3"
```

---

### Task 6: Add research export and frozen data dictionary v3

**Files:**
- Modify: `app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExport.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt`
- Modify: `app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt`
- Modify: `app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt`
- Modify: `app/src/test/java/org/mindanchor/continuity/ResearchExportDisclosureTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt`
- Create: `app/src/test/resources/research/data-dictionary-mindanchor-research-v3.json`
- Modify: `app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt`

**Interfaces:**
- Consumes Task 5 operational DTOs and Task 2 long-term DAO reads; no raw sample entity/value.
- Produces `PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"`, current `RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v3"`, and supported set `{v1, v2, v3}` while retaining the Program 0 v1 constant.
- Appends the same eight Program 2 lists to `ResearchExport`, after `dataDictionarySha256`, in the same order as `ContinuityPayload`.
- Appends eight `DictionaryDataset` values: `PASSIVE_RAW_PROVENANCE`, `PASSIVE_SOURCE_READS`, `PASSIVE_SOURCE_LAGS`, `PASSIVE_BASELINE_SEGMENTS`, `PASSIVE_PIPELINE_RUNS`, `PASSIVE_WINDOW_REVISIONS`, `PASSIVE_DAILY_REVISIONS`, `PASSIVE_OBSERVATION_DECISIONS`.
- Produces frozen `V2Content`, full `V3Content`, `programTwoContentByField`, and v1/v2 smuggling guards.

- [ ] **Step 1: Write failing v3 projection, raw-exclusion, and dictionary coverage tests**

```kotlin
@Test fun `research versions one and two remain readable when current is v3`() {
    assertEquals("mindanchor-research-v1", ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION)
    assertEquals("mindanchor-research-v2", ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION)
    assertEquals("mindanchor-research-v3", ContinuityContract.RESEARCH_DICTIONARY_VERSION)
    assertEquals(setOf("mindanchor-research-v1", "mindanchor-research-v2", "mindanchor-research-v3"),
        ContinuityContract.SUPPORTED_RESEARCH_DICTIONARY_VERSIONS)
}

@Test fun `Program 2 export contains provenance and derived values but no raw values`() {
    val export = sampleV3()
    assertTrue(export.passiveRawProvenance.isNotEmpty())
    assertTrue(export.passiveWindowRevisions.isNotEmpty())
    assertTrue(export.passiveDailyRevisions.isNotEmpty())
    assertTrue(export.passiveObservationDecisions.isNotEmpty())
    assertFalse(PassiveRawProvenanceDto::class.java.declaredFields.any { it.name == "value" })
    assertFalse(ResearchExportCodec.encode(export).contains("passiveRawSamples"))
}
```

Add dictionary reflection coverage from each new dataset to its DTO class. Add v2 golden document verification. For each Program 2 export field, mutate a genuine v1 and genuine v2 document and require `Corrupt`/`verify == false`.

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.continuity.ResearchExportCodecTest --tests org.mindanchor.research.ResearchDataDictionaryTest --tests org.mindanchor.continuity.ResearchExportDisclosureTest`

Expected: failures show missing v3 constants, export fields, dictionary datasets, projections, smuggling checks, and disclosure coverage.

- [ ] **Step 2: Append v3 export fields and preserve v1/v2 projections**

```kotlin
const val RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v3"
const val PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v1"
const val PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION = "mindanchor-research-v2"
val SUPPORTED_RESEARCH_DICTIONARY_VERSIONS = setOf(
    PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION,
    PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION,
    RESEARCH_DICTIONARY_VERSION,
)
```

Append the eight default-empty lists after `dataDictionarySha256`. Keep existing `V1Content` unchanged and freeze current Program 1 content as `V2Content` with every field from `dataDictionaryVersion` through `dataDictionarySha256` in current declaration order. Add `V3Content` with those fields followed by all eight Program 2 lists. Hash dispatch must be:

```kotlin
val text = when (version) {
    ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV1(export))
    ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV2(export))
    ContinuityContract.RESEARCH_DICTIONARY_VERSION -> json.encodeToString(projectV3(export))
    else -> error("no content projection for research export version $version")
}
```

Extend `sorted` with Task 5's eight canonical sort orders. Keep existing v1/v2 golden hashes unchanged.

- [ ] **Step 3: Add Program 2 smuggling checks without weakening Program 1 checks**

```kotlin
internal fun programTwoContentByField(export: ResearchExport): Map<String, Boolean> = mapOf(
    "passiveRawProvenance" to export.passiveRawProvenance.isNotEmpty(),
    "passiveSourceReads" to export.passiveSourceReads.isNotEmpty(),
    "passiveSourceLags" to export.passiveSourceLags.isNotEmpty(),
    "passiveBaselineSegments" to export.passiveBaselineSegments.isNotEmpty(),
    "passivePipelineRuns" to export.passivePipelineRuns.isNotEmpty(),
    "passiveWindowRevisions" to export.passiveWindowRevisions.isNotEmpty(),
    "passiveDailyRevisions" to export.passiveDailyRevisions.isNotEmpty(),
    "passiveObservationDecisions" to export.passiveObservationDecisions.isNotEmpty(),
)

private fun smugglesNewerContent(export: ResearchExport): Boolean = when (export.dataDictionaryVersion) {
    ContinuityContract.PROGRAM_ZERO_RESEARCH_DICTIONARY_VERSION ->
        programOneContentByField(export).values.any { it } || programTwoContentByField(export).values.any { it }
    ContinuityContract.PROGRAM_ONE_RESEARCH_DICTIONARY_VERSION ->
        programTwoContentByField(export).values.any { it }
    else -> false
}
```

Use the guard in both `decode` and `verify`. Extend reflection/mutation tests so no field can escape either the legitimate old-version field set or the matching smuggle predicate.

- [ ] **Step 4: Build export v3 from one transactional Room read**

Extend `ResearchExportBuilder.RoomRows` and `readRoomRows` with all long-term operational rows, using the same DAO calls as Task 5. Append DTO lists when constructing `ResearchExport`. Keep raw samples entirely outside `RoomRows`.

```kotlin
ResearchExport(
    dataDictionaryVersion = ContinuityContract.RESEARCH_DICTIONARY_VERSION,
    exportedAt = now,
    appVersionCode = appVersionCode,
    appVersionName = appVersionName,
    contentSha256 = "",
    journalEntries = rows.entries,
    contextFacts = facts,
    contextInferences = inferences,
    morningMeasures = rows.measures,
    ledgerEvents = rows.ledger.map { it.toDto() },
    ledgerHeadHash = ledgerHeadHash,
    ledgerEventCount = rows.ledger.size,
    ledgerHighWaterCount = highWater,
    ledgerIntegrity = integrity,
    studyPhases = rows.phases,
    protocolRegistry = EvidenceProtocolCatalog.registry.protocols,
    protocolCatalogSha256 = EvidenceProtocolCatalog.registry.catalogSha256,
    transformations = TransformationRegistry.transformations,
    transformationSetVersion = TransformationRegistry.setVersion,
    missingData = missingData.records,
    missingDataWindowStart = missingData.windowStart?.toString(),
    missingDataWindowThrough = missingData.windowThrough?.toString(),
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
)
```

The instrumented test inserts raw provenance/value 97.0 and complete long-term history, builds an export, and asserts every long-term id is present, the raw value table is absent, and canonical verification succeeds.

- [ ] **Step 5: Freeze the complete v3 data dictionary**

Append all eight datasets and add one dictionary variable for every DTO field. Use these provenance rules:

- Raw provenance event/record/device/source fields: `SYSTEM_RECORDED`, no transformation id, and descriptions explicitly state that raw measurement values are omitted.
- Window fields and JSON coverage/missingness/exclusions/provenance: `DERIVED_STRUCTURAL`, transformation id `passive-window-features`.
- Daily fields: `DERIVED_STRUCTURAL`, transformation id `passive-daily-features`.
- Observation decision fields: `DERIVED_STRUCTURAL`; use `passive-personal-baseline`, `passive-block-calibration`, or `passive-observation-explanation` for the field each transformation actually produces.
- Read/lag/segment/run fields: `SYSTEM_RECORDED`; segment transformation-version fields have no transformation id because they identify rather than derive the row.

All units and allowed values are explicit: epoch milliseconds, seconds offset, fraction `0.0..1.0`, milliseconds, enum names from Task 1/Program 2A, SHA-256 hex, ISO local date, JSON, or unitless count/boolean. Descriptions remain observation-only.

Generate `data-dictionary-mindanchor-research-v3.json` from `ResearchDataDictionary.canonicalJson()`, pin its SHA-256 under key `mindanchor-research-v3`, retain the v2 pin/file untouched, and ensure reflection coverage equals all `DictionaryDataset.entries`.

- [ ] **Step 6: Update the existing export privacy disclosure**

Change `continuity_export_research_privacy_body` to state that the plaintext export includes Health Connect/UsageStats source and device provenance, availability/failure states, coverage, missingness, exclusions, window/day revisions, and observation decisions; state explicitly that raw sensor/sample values are not included. Update `ResearchExportDisclosureTest`'s field map so all eight new fields are covered by this wording. Do not add another export screen or consent flow.

- [ ] **Step 7: Run export, dictionary, disclosure, and instrumented builder tests**

Run:

```bash
./gradlew.bat testDebugUnitTest --tests org.mindanchor.continuity.ResearchExportCodecTest --tests org.mindanchor.research.ResearchDataDictionaryTest --tests org.mindanchor.continuity.ResearchExportDisclosureTest
./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.mindanchor.continuity.ResearchExportBuilderTest
```

Expected: both commands end `BUILD SUCCESSFUL`; v1/v2 documents retain frozen projections and hashes, v3 is self-describing and canonical, every DTO field is in the dictionary, every new field is in smuggling/disclosure guards, and raw values are absent.

- [ ] **Step 8: Commit research export/dictionary v3**

```bash
git add app/src/main/java/org/mindanchor/continuity/ContinuityContract.kt app/src/main/java/org/mindanchor/continuity/ResearchExport.kt app/src/main/java/org/mindanchor/continuity/ResearchExportBuilder.kt app/src/main/java/org/mindanchor/continuity/ResearchExportCodec.kt app/src/main/java/org/mindanchor/research/ResearchDataDictionary.kt app/src/main/res/values/strings.xml app/src/test/java/org/mindanchor/continuity/ResearchExportCodecTest.kt app/src/test/java/org/mindanchor/continuity/ResearchExportDisclosureTest.kt app/src/test/java/org/mindanchor/research/ResearchDataDictionaryTest.kt app/src/test/resources/research/data-dictionary-mindanchor-research-v3.json app/src/androidTest/java/org/mindanchor/continuity/ResearchExportBuilderTest.kt
git commit -m "feat: export passive history with dictionary v3"
```

---

### Task 7: Wire six-hour work, retention, end-to-end acceptance, and device runbook

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassivePipelineWorker.kt`
- Create: `app/src/main/java/org/mindanchor/intelligence/PassivePipelineScheduler.kt`
- Modify: `app/src/main/java/org/mindanchor/HomeActivity.kt`
- Create: `app/src/test/java/org/mindanchor/intelligence/PassivePipelineWorkerTest.kt`
- Create: `app/src/test/java/org/mindanchor/intelligence/PassivePipelineSchedulerTest.kt`
- Create: `app/src/androidTest/java/org/mindanchor/intelligence/PassivePipelineAcceptanceTest.kt`
- Create: `docs/research/28-program-2b-device-validation.md`

**Interfaces:**
- Consumes Task 4 `PassivePipelineRepository.build(context).run(now, zone)` and result variants; Task 2 `AnchorDatabase.get(context).passive().pruneRawSamples(cutoff)`.
- Produces `PassivePipelineWorker`, `PassivePipelineScheduler.ensureScheduled`, six-hour `PERIODIC_WORK_NAME = "passive_operational_pipeline"`, and raw retention `RAW_RETENTION_MILLIS = 14 * 24 * 60 * 60 * 1000L`.
- `HomeActivity.onCreate` adds only local WorkManager scheduling; it does not read providers or run aggregation on the main thread.

- [ ] **Step 1: Write failing worker-result and retention tests**

```kotlin
@Test fun `completed runs prune raw values and transient runs retry`() = runBlocking {
    var cutoff: Long? = null
    val completed = runWorker(
        pipeline = { PassivePipelineResult.Completed("run-1", 1, 1, 1) },
        prune = { cutoff = it; 3 }, now = 20L * DAY_MILLIS,
    )
    assertTrue(completed is ListenableWorker.Result.Success)
    assertEquals(6L * DAY_MILLIS, cutoff)

    cutoff = null
    val retry = runWorker(
        pipeline = { PassivePipelineResult.Retry("run-2") },
        prune = { cutoff = it; 0 }, now = 20L * DAY_MILLIS,
    )
    assertTrue(retry is ListenableWorker.Result.Retry)
    assertNull(cutoff)
}
```

The completed fixture includes source states where every read is denied/unavailable and still expects success, because Task 4 records those states before returning `Completed`. Add a structural DAO test that no window/daily/decision delete exists, proving the worker cannot delete within the one-year minimum or long-term retention periods.

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassivePipelineWorkerTest`

Expected: compilation fails because the worker does not exist.

- [ ] **Step 2: Implement the thin worker and 14-day raw-value prune**

```kotlin
class PassivePipelineWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        return run(
            pipeline = PassivePipelineRepository.build(applicationContext),
            dao = AnchorDatabase.get(applicationContext).passive(),
            now = now,
            zone = ZoneId.systemDefault(),
        )
    }

    internal suspend fun run(
        pipeline: PassivePipelineRepository,
        dao: PassiveDao,
        now: Long,
        zone: ZoneId,
    ): Result = when (pipeline.run(now, zone)) {
        is PassivePipelineResult.Retry -> Result.retry()
        is PassivePipelineResult.Completed -> {
            dao.pruneRawSamples(now - RAW_RETENTION_MILLIS)
            Result.success()
        }
    }

    companion object {
        const val RAW_RETENTION_MILLIS = 14L * 24L * 60L * 60L * 1_000L
    }
}
```

Keep the shown `internal suspend fun run(pipeline: PassivePipelineRepository, dao: PassiveDao, now: Long, zone: ZoneId): ListenableWorker.Result` member as the constructor-level JVM test seam and have `doWork()` delegate to it. Do not catch cancellation. Do not add a raw-provenance, window, daily, decision, source-read, lag, segment, or run prune.

- [ ] **Step 3: Write failing WorkManager schedule tests**

```kotlin
@Test fun `periodic request is six-hour battery-aware local work`() {
    val constraints = PassivePipelineScheduler.constraints()
    assertEquals(6L, PassivePipelineScheduler.INTERVAL_HOURS)
    assertTrue(constraints.requiresBatteryNotLow())
    assertEquals(NetworkType.NOT_REQUIRED, constraints.requiredNetworkType)
    assertNotNull(PassivePipelineScheduler.buildRequest())
}

@Test fun `ensureScheduled uses one UPDATE periodic work`() {
    PassivePipelineScheduler.ensureScheduled(context)
    PassivePipelineScheduler.ensureScheduled(context)
    val infos = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(PassivePipelineScheduler.PERIODIC_WORK_NAME).get()
    assertEquals(1, infos.size)
}
```

Run: `./gradlew.bat testDebugUnitTest --tests org.mindanchor.intelligence.PassivePipelineSchedulerTest`

Expected: compilation fails because `PassivePipelineScheduler` does not exist.

- [ ] **Step 4: Schedule unique UPDATE work every six hours with no network constraint**

```kotlin
object PassivePipelineScheduler {
    const val PERIODIC_WORK_NAME = "passive_operational_pipeline"
    const val INTERVAL_HOURS = 6L

    internal fun constraints(): Constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .build()

    internal fun buildRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<PassivePipelineWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()

    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            buildRequest(),
        )
    }
}
```

Call `PassivePipelineScheduler.ensureScheduled(applicationContext)` in `HomeActivity.onCreate` next to the existing local `ContinuityWorkScheduler.ensureNightlyScheduled` call. Add a source-boundary assertion that the scheduler has no network/auth/Drive/COROS imports and that `HomeActivity` calls only the scheduler, never `PassivePipelineRepository.run`.

- [ ] **Step 5: Add instrumented end-to-end acceptance evidence with fake providers**

`PassivePipelineAcceptanceTest` uses a real in-memory `AnchorDatabase.withResearchImmutability`, fake `PassiveRecordSource` instances, and the production repository/codec/aggregators. It must execute this fixed chronology:

```kotlin
@Test fun operationalHistoryIsAppendOnlyRestorableAndRawValuesExpire() = runBlocking {
    val firstNow = Instant.parse("2026-08-30T12:00:00Z").toEpochMilli()
    val first = repository.run(firstNow, ZoneId.of("Asia/Kolkata"))
    assertTrue(first is PassivePipelineResult.Completed)
    assertEquals(120, health.requestedLocalDayCount)
    assertTrue(dao.windowRevisionsNow().isNotEmpty())
    assertTrue(dao.dailyRevisionsNow().any { it.dataStatus == "AVAILABLE_PROVISIONAL" })

    fakeHealth.addLateSleepRecord(sourceUpdatedTime = firstNow + 49L * 3_600_000L)
    val backfill = repository.run(firstNow + 54L * 3_600_000L, ZoneId.of("Asia/Kolkata"))
    assertTrue(backfill is PassivePipelineResult.Completed)
    assertEquals(7, health.requestedLocalDayCount)
    val revisions = dao.dailyRevisionsNow().groupBy { it.localDate }.values.maxBy { it.size }
    assertTrue(revisions.any { it.revisionReason == "BACKFILL" })
    assertTrue(revisions.any { it.dataStatus == "AVAILABLE_FINAL" })
    assertTrue(dao.observationDecisionsNow().groupBy { it.localDate }.values.any { it.size >= 2 })

    val beforeNoOp = dao.dailyRevisionsNow().size
    repository.run(firstNow + 60L * 3_600_000L, ZoneId.of("Asia/Kolkata"))
    assertEquals(beforeNoOp, dao.dailyRevisionsNow().size)

    dao.pruneRawSamples(firstNow + 60L * 3_600_000L - PassivePipelineWorker.RAW_RETENTION_MILLIS)
    assertTrue(dao.rawRecords(0L, firstNow).isEmpty())
    assertTrue(dao.rawProvenanceNow().isNotEmpty())
}
```

The fixture must also assert: eight HR minute bins are eligible and seven are not; exercise removes only overlapping physiology; SpO2 is stored but absent from scoring evidence; successful-empty differs from denied/unavailable/failure; sleep uses wake date; steps/active minutes clip at a DST local-day boundary; routine requires successful raw-event reads; fewer than two domains never become `AVAILABLE_FINAL`; fallback lag is marked; 29 lags use 48 hours and 30 use clamped nearest-rank p99; a missing configured source preserves the segment; a new device fingerprint changes it; the seed equals the first signed digest bytes; provisional/final/backfill rows and decisions all remain; and no path invokes notifications, launcher restrictions, app blocking, or AnchorCore.

- [ ] **Step 6: Create the physical-device validation runbook with checks explicitly pending**

Create `docs/research/28-program-2b-device-validation.md` with exact setup and evidence fields:

```markdown
# Program 2B Physical-Device Validation

Automated acceptance is recorded by `PassivePipelineAcceptanceTest`. The checks below require a real Android phone, Health Connect provider, granted/denied permissions, and elapsed background time. They are pending until a named tester records device evidence; no automated result is presented as physical-device evidence.

## Evidence header

- Tester: Pending
- Device manufacturer/model: Pending
- Android build/API: Pending
- Health Connect provider/version: Pending
- MindAnchor commit/APK: Pending
- Test start/end and local zone: Pending

## Pending checks

- [ ] Pending — grant selected sensor, oxygen, history, and background permissions; capture the system permission screen and the resulting per-family source-read rows.
- [ ] Pending — deny one sensor permission and revoke Usage Access; verify the worker succeeds, records `PERMISSION_DENIED`, and does not write zero-valued features.
- [ ] Pending — leave Health Connect empty for one granted type; verify `SUCCESS` with `recordCount = 0`.
- [ ] Pending — insert or sync a late wearable record after an original run; verify a new backfill revision/decision appears and the earlier row remains unchanged.
- [ ] Pending — allow at least two six-hour periods with MindAnchor closed; record actual start latency, battery state, worker result, p50/p95/p99 observed source lag, longest gap, and backfill count.
- [ ] Pending — cross a local midnight and, where available, a DST transition; verify local-day clipping, wake-date sleep ownership, stored zone/offset, and absolute UTC quarter-hour windows.
- [ ] Pending — restore a v3 encrypted snapshot on a replacement/test phone; verify finalized window/day/decision ids and content hashes match while raw sample values are absent.
- [ ] Pending — export v3 research JSON; verify provenance, coverage, missingness, exclusions, revisions, decisions, versions, and dictionary are present and raw values are absent.

## Evidence attachments

Record artifact paths, timestamps, screenshots, `adb shell dumpsys jobscheduler`/WorkManager diagnostics, exported hashes, and observed deviations here only after each check is performed.
```

Do not mark any hardware checkbox complete in the implementation commit.

- [ ] **Step 7: Run the complete automated final gate**

Run from the repository root, in this order:

```bash
./gradlew.bat testDebugUnitTest
./gradlew.bat detekt
./gradlew.bat lintDebug
./gradlew.bat connectedDebugAndroidTest
git diff --check
git status --short
```

Expected: every Gradle command ends `BUILD SUCCESSFUL`; `git diff --check` prints nothing; connected tests include v7-to-v8 migration, Room immutability, v3 capture/restore, v3 export, and pipeline acceptance. `git status --short` lists only Program 2B files plus the user's pre-existing modified `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` and untracked root `AGENTS.md`; those two protected paths remain unstaged and byte-for-byte as found. The runbook's physical-device checks remain visibly pending.

Run the scope guard:

```bash
git diff --name-only -- app/src/main/java/org/mindanchor/anchorcore app/src/main/java/org/mindanchor/notifications app/src/main/java/org/mindanchor/launcher
```

Expected: no output. `HomeActivity.kt` is outside those directories and contains only the scheduler call.

- [ ] **Step 8: Commit worker wiring and automated acceptance evidence**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassivePipelineWorker.kt app/src/main/java/org/mindanchor/intelligence/PassivePipelineScheduler.kt app/src/main/java/org/mindanchor/HomeActivity.kt app/src/test/java/org/mindanchor/intelligence/PassivePipelineWorkerTest.kt app/src/test/java/org/mindanchor/intelligence/PassivePipelineSchedulerTest.kt app/src/androidTest/java/org/mindanchor/intelligence/PassivePipelineAcceptanceTest.kt docs/research/28-program-2b-device-validation.md
git commit -m "feat: schedule and validate passive pipeline"
```

## Self-review

- Spec coverage: Tasks 1–7 cover `ACTIVE_MINUTES`, non-scored SpO2 ingestion, provenance-preserving paginated sources, explicit read outcomes, normalized record identity/device/time fields, UTC windows, wake-only alignment, clipping, exercise-local suppression, honest daily statuses, source-lag finality, stable segments, initial/backfill scans, deterministic estimator seeds, append-only Room v8, raw/derived retention, snapshot/restore v3, research export/dictionary v3, six-hour work, and automated acceptance. UI observation surfaces and physical-field execution remain outside Program 2B; the required hardware procedure is recorded as pending rather than claimed.
- Placeholder scan: every task names exact files, interfaces, test inputs, implementation rules, commands, expected outcomes, and a commit command. No incomplete implementation marker or cross-task shorthand remains.
- Type consistency: `PassiveReadRange`, `PassiveSourceRead`, `PassiveSourceRecord`, `PassiveFeatureWindow`, `PassiveDailyAggregate`, `PassivePipelineResult`, all entity/DTO names, DAO methods, snapshot/export list names, transformation ids/versions, and Program 2A calls are spelled consistently from producer task through consumers.
- Safety/scope: no task edits `LlmPrefs.kt` or root `AGENTS.md`; no task adds diagnosis, intervention, notification, launcher restriction, app blocking, or AnchorCore adaptation; raw values enter only `passive_raw_samples`, which is excluded from snapshot/export and is the only prunable operational table.
