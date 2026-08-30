# Program 2A Observation Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure, deterministic Program 2 engine that turns eligible daily features into calibrated, non-diagnostic within-person observations without any intervention path.

**Architecture:** A new `org.mindanchor.intelligence` package owns immutable daily inputs, robust personal baselines, deterministic block calibration, episode state, and fixed explanations. Android ingestion and persistence remain outside this subprogram, so every statistical and safety rule runs in ordinary JVM tests. Existing wellness and link-finding behavior remains unchanged.

**Tech Stack:** Kotlin/JVM, JUnit 4, Java time, existing Gradle Android application module.

## Global Constraints

- Program 2 is observation-only: no notifications, protocol selection, app blocking, launcher adaptation, or AnchorCore input.
- Only `AVAILABLE_FINAL` days may emit a deviation.
- No imputation, interpolation, carry-forward, zero-fill, or arbitrary epsilon.
- Baseline floor: 60 eligible days, including at least 8 weekdays and 8 weekend days.
- Feature stratum floor: 14 observations; otherwise pool into the all-days stratum and record that choice.
- Scale: `1.4826 * MAD`, then `IQR / 1.349` only when MAD is zero; an all-zero scale makes the feature unscorable.
- Calibration: deterministic seven-day circular block resampling against an engineering budget of at most one observation episode per 30 valid days; traverse candidates downward from the guaranteed-safe maximum and stop at the first violation so disconnected dense-crossing safe islands are rejected.
- Two crossings among three eligible days are sustained; crossings within 48 hours belong to one episode.
- User-facing text names observable data only and must not contain diagnoses or mental-state predictions.
- Preserve the unrelated modified `app/src/main/java/org/mindanchor/llm/LlmPrefs.kt` and untracked root `AGENTS.md`.

---

### Task 1: Freeze Program 2 domain contracts

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassiveContracts.kt`
- Test: `app/src/test/java/org/mindanchor/intelligence/PassiveContractsTest.kt`

**Interfaces:**
- Produces: `PassiveDomain`, `PassiveFeature`, `PassiveDataStatus`, `PassiveObservationState`, `PassiveDay`, `FeatureEvidence`, `DomainEvidence`, `PassiveObservation`.

- [ ] **Step 1: Write the failing contract test**

```kotlin
package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PassiveContractsTest {
    @Test fun `only final data is estimator eligible`() {
        assertEquals(listOf(PassiveDataStatus.AVAILABLE_FINAL),
            PassiveDataStatus.entries.filter { it.canEstimate })
    }

    @Test fun `SpO2 is context and never a scored feature`() {
        assertFalse(PassiveFeature.SPO2_PERCENT.scored)
    }

    @Test fun `excluded feature is unavailable without deleting its value`() {
        val day = PassiveDay(
            day = LocalDate.parse("2026-08-30"),
            dataStatus = PassiveDataStatus.AVAILABLE_FINAL,
            features = mapOf(PassiveFeature.RESTING_HEART_RATE to 80.0),
            excludedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE),
            baselineSegment = "device-a",
        )
        assertEquals(80.0, day.features[PassiveFeature.RESTING_HEART_RATE])
        assertFalse(day.isEligible(PassiveFeature.RESTING_HEART_RATE))
    }
}
```

- [ ] **Step 2: Run the test and verify the contracts are absent**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveContractsTest`

Expected: compilation fails because the Program 2 types do not exist.

- [ ] **Step 3: Implement the contracts**

```kotlin
package org.mindanchor.intelligence

import java.time.LocalDate

enum class PassiveDomain { PHYSIOLOGY, SLEEP, ACTIVITY, ROUTINE }

enum class PassiveFeature(val domain: PassiveDomain?, val scored: Boolean = true) {
    RESTING_HEART_RATE(PassiveDomain.PHYSIOLOGY),
    HRV_RMSSD(PassiveDomain.PHYSIOLOGY),
    SLEEP_MINUTES(PassiveDomain.SLEEP),
    SLEEP_ONSET_AFTER_SIX_PM(PassiveDomain.SLEEP),
    STEPS(PassiveDomain.ACTIVITY),
    FIRST_UNLOCK_MINUTE(PassiveDomain.ROUTINE),
    SCREEN_MINUTES(PassiveDomain.ROUTINE),
    SPO2_PERCENT(null, scored = false),
}

enum class PassiveDataStatus(val canEstimate: Boolean) {
    AVAILABLE_FINAL(true),
    AVAILABLE_PROVISIONAL(false),
    INSUFFICIENT_DATA(false),
    SUPPRESSED_EXERCISE(false),
    BASELINE_BUILDING(false),
}

enum class PassiveObservationState {
    WITHIN_PERSON_RANGE,
    TRANSIENT_DEVIATION,
    SUSTAINED_DEVIATION,
    BASELINE_SHIFT_CANDIDATE,
    NO_OBSERVATION,
}

data class PassiveDay(
    val day: LocalDate,
    val dataStatus: PassiveDataStatus,
    val features: Map<PassiveFeature, Double>,
    val excludedFeatures: Set<PassiveFeature> = emptySet(),
    val baselineSegment: String,
) {
    fun isEligible(feature: PassiveFeature): Boolean =
        dataStatus.canEstimate && feature.scored && feature !in excludedFeatures && features[feature]?.isFinite() == true
}

data class FeatureEvidence(
    val feature: PassiveFeature,
    val value: Double,
    val centre: Double,
    val scale: Double,
    val zScore: Double,
    val referenceCount: Int,
    val pooledStratum: Boolean,
)

data class DomainEvidence(
    val domain: PassiveDomain,
    val score: Double,
    val features: List<FeatureEvidence>,
)

data class PassiveObservation(
    val day: LocalDate,
    val asOfTime: Long,
    val dataStatus: PassiveDataStatus,
    val state: PassiveObservationState,
    val threshold: Double?,
    val crossed: Boolean,
    val baselineDays: Int,
    val baselineSegment: String,
    val domains: List<DomainEvidence>,
    val explanation: String,
)
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveContractsTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the contracts**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassiveContracts.kt app/src/test/java/org/mindanchor/intelligence/PassiveContractsTest.kt
git commit -m "feat: add passive intelligence contracts"
```

### Task 2: Implement baseline eligibility and robust feature statistics

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassiveBaseline.kt`
- Test: `app/src/test/java/org/mindanchor/intelligence/PassiveBaselineTest.kt`

**Interfaces:**
- Consumes: `PassiveDay`, `PassiveFeature`.
- Produces: `BaselineEligibility`, `FeatureBaseline`, `PassiveBaseline`, `PassiveBaselineBuilder.evaluate`, `PassiveBaselineBuilder.build`.

- [ ] **Step 1: Write failing tests for the 60-day gate, calendar composition, pooling, MAD, IQR fallback, and constant series**

```kotlin
package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PassiveBaselineTest {
    private fun days(count: Int, start: LocalDate = LocalDate.parse("2026-01-01")) =
        List(count) { i ->
            PassiveDay(start.plusDays(i.toLong()), PassiveDataStatus.AVAILABLE_FINAL,
                mapOf(PassiveFeature.STEPS to (5_000 + (i % 9) * 100).toDouble()), baselineSegment = "a")
        }

    @Test fun `baseline stays unavailable below sixty eligible days`() {
        assertFalse(PassiveBaselineBuilder.evaluate(days(59), "a").ready)
    }

    @Test fun `baseline requires weekday and weekend coverage`() {
        val weekdays = generateSequence(LocalDate.parse("2026-01-05")) { it.plusDays(1) }
            .filter { it.dayOfWeek.value <= 5 }.take(60)
            .map { PassiveDay(it, PassiveDataStatus.AVAILABLE_FINAL,
                mapOf(PassiveFeature.STEPS to 5_000.0), baselineSegment = "a") }.toList()
        assertFalse(PassiveBaselineBuilder.evaluate(weekdays, "a").ready)
    }

    @Test fun `zero MAD falls back to nonzero IQR`() {
        val values = List(45) { 10.0 } + List(15) { 20.0 }
        val history = days(60).mapIndexed { i, day ->
            day.copy(features = mapOf(PassiveFeature.STEPS to values[i]))
        }
        val scale = PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
            .features.getValue(PassiveFeature.STEPS).scale
        assertTrue(scale > 0.0)
    }

    @Test fun `constant feature is omitted instead of divided by epsilon`() {
        val history = days(60).map { it.copy(features = mapOf(PassiveFeature.STEPS to 10.0)) }
        assertFalse(PassiveBaselineBuilder.build(history, history.last().day.plusDays(1), "a")!!
            .features.containsKey(PassiveFeature.STEPS))
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveBaselineTest`

Expected: compilation fails because `PassiveBaselineBuilder` does not exist.

- [ ] **Step 3: Implement the minimal baseline builder**

Create `PassiveBaseline.kt` with:

```kotlin
data class BaselineEligibility(val eligibleDays: Int, val weekdays: Int, val weekendDays: Int) {
    val ready get() = eligibleDays >= 60 && weekdays >= 8 && weekendDays >= 8
}

data class FeatureBaseline(
    val feature: PassiveFeature,
    val centre: Double,
    val scale: Double,
    val sampleCount: Int,
    val pooledStratum: Boolean,
)

data class PassiveBaseline(
    val segment: String,
    val referenceDays: Int,
    val features: Map<PassiveFeature, FeatureBaseline>,
)

object PassiveBaselineBuilder {
    const val MIN_DAYS = 60
    const val MIN_WEEKDAY_DAYS = 8
    const val MIN_WEEKEND_DAYS = 8
    const val MIN_STRATUM_VALUES = 14
    private const val MAD_SCALE = 1.4826
    private const val IQR_SCALE = 1.349

    fun evaluate(history: List<PassiveDay>, segment: String): BaselineEligibility {
        val eligible = history.filter { it.baselineSegment == segment && it.dataStatus.canEstimate }
        val weekend = eligible.count { it.day.dayOfWeek.value >= 6 }
        return BaselineEligibility(eligible.size, eligible.size - weekend, weekend)
    }

    fun build(history: List<PassiveDay>, targetDay: LocalDate, segment: String): PassiveBaseline? {
        val eligible = history.filter { it.day.isBefore(targetDay) && it.baselineSegment == segment && it.dataStatus.canEstimate }
        if (!evaluate(eligible, segment).ready) return null
        val targetWeekend = targetDay.dayOfWeek.value >= 6
        val baselines = PassiveFeature.entries.filter { it.scored }.mapNotNull { feature ->
            val all = eligible.filter { it.isEligible(feature) }.mapNotNull { it.features[feature] }
            val stratum = eligible.filter { (it.day.dayOfWeek.value >= 6) == targetWeekend && it.isEligible(feature) }
                .mapNotNull { it.features[feature] }
            val pooled = stratum.size < MIN_STRATUM_VALUES
            val values = if (pooled) all else stratum
            statistics(feature, values, pooled)?.let { feature to it }
        }.toMap()
        return PassiveBaseline(segment, eligible.size, baselines)
    }

    private fun statistics(feature: PassiveFeature, values: List<Double>, pooled: Boolean): FeatureBaseline? {
        if (values.isEmpty()) return null
        val centre = median(values)
        val mad = median(values.map { kotlin.math.abs(it - centre) })
        val q1 = quantile(values, 0.25)
        val q3 = quantile(values, 0.75)
        val scale = if (mad > 0.0) MAD_SCALE * mad else (q3 - q1) / IQR_SCALE
        if (!scale.isFinite() || scale <= 0.0) return null
        return FeatureBaseline(feature, centre, scale, values.size, pooled)
    }

    internal fun median(values: List<Double>): Double = quantile(values, 0.5)

    internal fun quantile(values: List<Double>, probability: Double): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val position = probability * (sorted.lastIndex)
        val lower = position.toInt()
        val upper = kotlin.math.ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }
}
```

- [ ] **Step 4: Run baseline tests**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveBaselineTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the baseline**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassiveBaseline.kt app/src/test/java/org/mindanchor/intelligence/PassiveBaselineTest.kt
git commit -m "feat: add research-gated personal baselines"
```

### Task 3: Add scoring and block-calibrated thresholds

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassiveCalibration.kt`
- Test: `app/src/test/java/org/mindanchor/intelligence/PassiveCalibrationTest.kt`

**Interfaces:**
- Produces: `PassiveScorer.score`, `DayScore`, `CalibrationResult`, `BlockThresholdCalibrator.calibrate`, `BlockThresholdCalibrator.episodeCount`.

- [ ] **Step 1: Write failing scorer and calibrator tests**

```kotlin
package org.mindanchor.intelligence

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PassiveCalibrationTest {
    @Test fun `score requires evidence from two domains`() {
        val baseline = PassiveBaseline("a", 60, mapOf(
            PassiveFeature.STEPS to FeatureBaseline(PassiveFeature.STEPS, 5_000.0, 500.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.STEPS to 8_000.0), baselineSegment = "a")
        assertNull(PassiveScorer.score(day, baseline))
    }

    @Test fun `exercise-excluded physiology cannot contribute`() {
        val baseline = PassiveBaseline("a", 60, mapOf(
            PassiveFeature.RESTING_HEART_RATE to FeatureBaseline(PassiveFeature.RESTING_HEART_RATE, 60.0, 5.0, 60, false),
            PassiveFeature.SLEEP_MINUTES to FeatureBaseline(PassiveFeature.SLEEP_MINUTES, 450.0, 30.0, 60, false)))
        val day = PassiveDay(LocalDate.parse("2026-08-30"), PassiveDataStatus.AVAILABLE_FINAL,
            mapOf(PassiveFeature.RESTING_HEART_RATE to 100.0, PassiveFeature.SLEEP_MINUTES to 300.0),
            excludedFeatures = setOf(PassiveFeature.RESTING_HEART_RATE), baselineSegment = "a")
        assertNull(PassiveScorer.score(day, baseline))
    }

    @Test fun `calibration is deterministic and respects the episode budget`() {
        val scores = List(60) { i -> 0.5 + (i % 10) * 0.1 }
        val first = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        val second = BlockThresholdCalibrator.calibrate(scores, seed = 42L)!!
        assertEquals(first, second)
        assertTrue(first.expectedEpisodesPer30 <= 1.0)
    }

    @Test fun `nearby crossings form one episode`() {
        assertEquals(2, BlockThresholdCalibrator.episodeCount(listOf(4.0, 0.0, 4.0, 0.0, 0.0, 4.0), 3.0))
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveCalibrationTest`

Expected: compilation fails because the scorer and calibrator do not exist.

- [ ] **Step 3: Implement scoring and deterministic block calibration**

Implement `PassiveScorer` by calculating `(value - centre) / scale`, grouping evidence by domain, and using each domain's maximum absolute feature z-score. Return null unless at least two domains are present, and use the second-largest eligible domain magnitude as the corroborated `DayScore.score`, so a crossing requires two domains above the same threshold. Implement `BlockThresholdCalibrator` with constants `BLOCK_DAYS = 7`, `CALIBRATION_DAYS = 30`, `SIMULATIONS = 512`, `TARGET_EPISODES_PER_30 = 1.0`, and `REFRACTORY_DAYS = 2`. Use `java.util.Random(seed)` and circular seven-day blocks. Candidate crossings are strict `score > threshold`, so the maximum candidate is a guaranteed zero-episode starting point. Traverse candidates downward and select the last budget-compliant threshold before the first violation. Do not continue into a disconnected lower-threshold safe island: refractory grouping can merge dense crossings into one episode and make episode count non-monotonic.

```kotlin
data class DayScore(val score: Double, val domains: List<DomainEvidence>)
data class CalibrationResult(val threshold: Double, val expectedEpisodesPer30: Double, val simulations: Int)

object PassiveScorer {
    fun score(day: PassiveDay, baseline: PassiveBaseline): DayScore? {
        if (!day.dataStatus.canEstimate || day.baselineSegment != baseline.segment) return null
        val evidence = baseline.features.values.mapNotNull { reference ->
            if (!day.isEligible(reference.feature)) return@mapNotNull null
            val value = day.features[reference.feature] ?: return@mapNotNull null
            FeatureEvidence(reference.feature, value, reference.centre, reference.scale,
                (value - reference.centre) / reference.scale, reference.sampleCount, reference.pooledStratum)
        }
        val domains = evidence.groupBy { requireNotNull(it.feature.domain) }.map { (domain, features) ->
            DomainEvidence(domain, features.maxOf { kotlin.math.abs(it.zScore) }, features.sortedBy { it.feature.name })
        }.sortedBy { it.domain.name }
        if (domains.size < 2) return null
        return DayScore(domains.map { it.score }.sortedDescending()[1], domains)
    }
}
```

- [ ] **Step 4: Run calibration tests**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveCalibrationTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit scoring and calibration**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassiveCalibration.kt app/src/test/java/org/mindanchor/intelligence/PassiveCalibrationTest.kt
git commit -m "feat: calibrate passive observations on personal history"
```

### Task 4: Implement the observation state machine and safe explanations

**Files:**
- Create: `app/src/main/java/org/mindanchor/intelligence/PassiveEstimator.kt`
- Test: `app/src/test/java/org/mindanchor/intelligence/PassiveEstimatorTest.kt`

**Interfaces:**
- Consumes: all Program 2A contracts, baselines, scorer, and calibrator.
- Produces: `PassiveEstimator.observe`, `PassiveExplanation.render`.

- [ ] **Step 1: Write failing state and safety tests**

Tests must prove all five non-final/ineligible statuses return `NO_OBSERVATION`, 59 days cannot activate a baseline, a first crossing is transient, a second crossing among three eligible observations is sustained, and every generated explanation excludes the case-insensitive terms `anxiety`, `depression`, `bpd`, `panic`, `anger`, `diagnosis`, `illness`, and `disorder`.

Use deterministic history with physiology and sleep features alternating around non-zero dispersion, then append high resting-heart-rate and low-sleep test days. Assert `threshold != null` only after the baseline and calibration are available.

- [ ] **Step 2: Run estimator tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveEstimatorTest`

Expected: compilation fails because `PassiveEstimator` does not exist.

- [ ] **Step 3: Implement the estimator**

```kotlin
object PassiveEstimator {
    const val RULE_VERSION = "passive-observation-rules-v2"

    fun observe(
        day: PassiveDay,
        asOfTime: Long,
        history: List<PassiveDay>,
        prior: List<PassiveObservation>,
        seed: Long,
    ): PassiveObservation {
        if (!day.dataStatus.canEstimate) return noObservation(day, asOfTime, 0)
        val baseline = PassiveBaselineBuilder.build(history, day.day, day.baselineSegment)
            ?: return noObservation(day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING), asOfTime,
                PassiveBaselineBuilder.evaluate(history, day.baselineSegment).eligibleDays)
        val current = PassiveScorer.score(day, baseline)
            ?: return noObservation(day.copy(dataStatus = PassiveDataStatus.INSUFFICIENT_DATA), asOfTime,
                baseline.referenceDays)
        val historicalScores = history.filter { it.day.isBefore(day.day) }
            .mapNotNull { PassiveScorer.score(it, baseline)?.score }
        val calibration = BlockThresholdCalibrator.calibrate(historicalScores, seed)
            ?: return noObservation(day.copy(dataStatus = PassiveDataStatus.BASELINE_BUILDING), asOfTime,
                baseline.referenceDays)
        val crossed = current.score > calibration.threshold
        val previousEligible = prior.filter { it.dataStatus.canEstimate }.sortedByDescending { it.day }.take(2)
        val state = when {
            crossed && previousEligible.any { it.crossed } -> PassiveObservationState.SUSTAINED_DEVIATION
            crossed -> PassiveObservationState.TRANSIENT_DEVIATION
            else -> PassiveObservationState.WITHIN_PERSON_RANGE
        }
        val draft = PassiveObservation(day.day, asOfTime, day.dataStatus, state, calibration.threshold,
            crossed, baseline.referenceDays, day.baselineSegment, current.domains, "")
        return draft.copy(explanation = PassiveExplanation.render(draft))
    }

    private fun noObservation(day: PassiveDay, asOfTime: Long, baselineDays: Int) =
        PassiveObservation(day.day, asOfTime, day.dataStatus, PassiveObservationState.NO_OBSERVATION,
            null, false, baselineDays, day.baselineSegment, emptyList(),
            PassiveExplanation.noObservation(day.dataStatus, baselineDays))
}
```

`PassiveExplanation` uses only fixed templates. For crossed observations it lists the differing domain names in lowercase and ends with “This describes recorded data, not a diagnosis.” For in-range observations it says “Available signals were within your calibrated personal range.” For every no-observation status it names the data limitation instead of interpreting the day.

- [ ] **Step 4: Run estimator tests**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveEstimatorTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the estimator**

```bash
git add app/src/main/java/org/mindanchor/intelligence/PassiveEstimator.kt app/src/test/java/org/mindanchor/intelligence/PassiveEstimatorTest.kt
git commit -m "feat: add non-diagnostic passive estimator"
```

### Task 5: Register Program 2 provenance

**Files:**
- Modify: `app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt`
- Modify: `app/src/main/java/org/mindanchor/research/TransformationRegistry.kt`
- Modify: `app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt`
- Modify: `app/src/test/java/org/mindanchor/research/TransformationRegistryTest.kt`

**Interfaces:**
- Consumes: `PassiveEstimator.RULE_VERSION`.
- Produces: Program 2 rule/model version vector and registered feature/baseline/calibration transformations.

- [ ] **Step 1: Update tests to require Program 2 semantic versions and transformation IDs**

Add assertions that `ProvenanceVersions.RULE_SET_VERSION == PassiveEstimator.RULE_VERSION`, `MODEL_SET_VERSION == "personal-robust-baseline-v1"`, and that the transformation registry contains `passive-daily-features@daily-features-v1`, `passive-personal-baseline@personal-baseline-v1`, and `passive-block-calibration@block-calibration-v1`. Update the frozen registry hash only from the implementation's deterministic output.

- [ ] **Step 2: Run focused provenance tests and verify failure**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.research.ProvenanceVersionsTest --tests org.mindanchor.research.TransformationRegistryTest`

Expected: assertions fail because Program 1 still records no rule/model and only two transformations.

- [ ] **Step 3: Wire Program 2 versions and transformations**

Set:

```kotlin
const val RULE_SET_VERSION = PassiveEstimator.RULE_VERSION
const val MODEL_SET_VERSION = "personal-robust-baseline-v1"
```

Append the three exact transformation records named in Step 1. Descriptions must state that fifteen-minute windows support quality/exercise handling, that the baseline uses median/MAD with declared fallback and eligibility floors, and that calibration targets an engineering false-observation budget rather than clinical accuracy.

- [ ] **Step 4: Run provenance tests and record the new deterministic hash**

Run the two focused tests, copy the actual `TransformationRegistry.setVersion` from the single expected-value failure, update that one expectation, and rerun.

Expected: `BUILD SUCCESSFUL` with no re-pinning of unrelated hashes.

- [ ] **Step 5: Commit provenance**

```bash
git add app/src/main/java/org/mindanchor/research/ProvenanceVersions.kt app/src/main/java/org/mindanchor/research/TransformationRegistry.kt app/src/test/java/org/mindanchor/research/ProvenanceVersionsTest.kt app/src/test/java/org/mindanchor/research/TransformationRegistryTest.kt
git commit -m "feat: register passive intelligence provenance"
```

### Task 6: Add deterministic simulation acceptance tests

**Files:**
- Create: `app/src/test/java/org/mindanchor/intelligence/PassiveSimulationTest.kt`
- Create: `docs/research/27-passive-observation-calibration.md`

**Interfaces:**
- Consumes: complete Program 2A engine.
- Produces: reproducible false-observation and injected-shift mechanics evidence.

- [ ] **Step 1: Write the simulation test**

Generate 240 deterministic daily records using an AR(1)-style residual (`x[t] = 0.65*x[t-1] + seededNoise`) for resting heart rate, sleep, steps, and screen time. Predeclare generator seeds `1`, `7`, `42`, `2026`, and `20260830`; retain `20260830` as the primary injected-shift evidence stream. For every seed, use the first 120 days as reference/calibration and evaluate the remaining 120 chronologically. The aggregate unshifted criterion is at most the declared four-episode budget plus one finite-sample episode per seed: at most 25 episodes across the five 120-day evaluations. Add separate primary-stream copies with injected shifts of 0.5, 1.0, 1.5, and 2.0 scaled units lasting 1, 2, 3, and 7 days.

Assert only mechanics the implementation promises:

- identical seeds produce byte-for-byte equal observation sequences;
- no ineligible day emits a deviation;
- baseline is absent before day 61;
- the five unshifted evaluations satisfy the predeclared aggregate episode criterion and report every seed's count;
- the selected primary-stream injection window has seven unshifted days with zero observation-level and domain-level crossings;
- seven-day 2.0-scale shifts produce at least one crossing;
- a one-domain shift with all other domains still available never produces an observation, while a two-domain shift can, because corroborating domains are required.

- [ ] **Step 2: Run the simulation and verify any genuine implementation defects**

Run: `./gradlew testDebugUnitTest --tests org.mindanchor.intelligence.PassiveSimulationTest`

Expected: pass after fixing only deterministic engine defects; do not tune against the injected test labels.

- [ ] **Step 3: Record the interpretation boundary**

Create `docs/research/27-passive-observation-calibration.md` with the predeclared seeds, generator equation, tested durations and magnitudes, per-seed and aggregate false episode counts, the zero-crossing control-window rule, the connected-safe-region calibration rule, and the explicit statement: “Synthetic shifts test detector mechanics. They do not estimate sensitivity or specificity for anxiety, depression, anger, BPD, or any other condition.”

- [ ] **Step 4: Run Program 2A and repository verification**

Run:

```bash
./gradlew testDebugUnitTest
./gradlew detekt
./gradlew lintDebug
git diff --check
```

Expected: all commands pass; `git status --short` still shows the user's unrelated `LlmPrefs.kt` and `AGENTS.md` changes untouched.

- [ ] **Step 5: Commit Program 2A acceptance evidence**

```bash
git add app/src/test/java/org/mindanchor/intelligence/PassiveSimulationTest.kt docs/research/27-passive-observation-calibration.md docs/superpowers/specs/2026-08-30-program-2-passive-intelligence-design.md docs/superpowers/plans/2026-08-30-program-2a-observation-engine.md
git commit -m "test: validate passive observation mechanics"
```

## Self-review

- Spec coverage: Program 2A covers contracts, finality gates, baseline eligibility, robust scale, block calibration, episode states, explanations, provenance, and simulation. Android collection, persistence, backup/export, UI, and physical-device evidence remain explicitly assigned to Programs 2B and 2C.
- Placeholder scan: no `TBD`, `TODO`, “similar to,” or unspecified error-handling step remains.
- Type consistency: `PassiveDay`, `PassiveBaseline`, `DayScore`, `CalibrationResult`, and `PassiveObservation` signatures match across all tasks.
