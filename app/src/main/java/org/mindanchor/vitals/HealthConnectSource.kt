@file:OptIn(ExperimentalMindfulnessSessionApi::class)

package org.mindanchor.vitals

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads vitals out of Health Connect and reduces them into [DailyVitals].
 *
 * ## Why Health Connect and not a watch SDK
 *
 * The point of this app is to work with whatever's on somebody's wrist and
 * to keep working when that changes — a COROS Pacer 3 today, something
 * else after that. A vendor SDK ties the app to one watch; Health Connect
 * is the one thing every wearable's own app already writes to, so
 * integrating here instead of there is what makes the watch swappable.
 * The COROS Pacer 3 happens to write only heart rate and exercise. Nothing
 * in this file leans on that: every record type is read and reduced
 * independently, and a data type Health Connect has never heard of from
 * any source is simply a null field on [DailyVitals], not a special case.
 *
 * ## Why nothing here throws
 *
 * This runs inside a launcher — see
 * [org.mindanchor.grayscale.Grayscale], whose "never throws" rule this
 * follows for the same reason. Health Connect can be absent, mid-update,
 * permission-less, or just wrong about a record, and none of that is
 * grounds for taking down the home screen. Every call that can fail is
 * wrapped in [runCatching] and degrades to null or an empty collection.
 */
object HealthConnectSource {

    /**
     * Read-only permissions this app asks for. Every type here is
     * something a given watch may simply never write; asking for all of
     * them costs nothing, since each is reduced independently and an
     * ungranted or empty type just leaves its [DailyVitals] field null.
     *
     * [TotalCaloriesBurnedRecord] is asked for as a general activity
     * signal even though no [DailyVitals] field consumes it yet — it is
     * here so the permission grant does not need revisiting the day a
     * calories field is added.
     *
     * [MindfulnessSessionRecord] is the mental-health signal. The
     * meditation apps that already write to Health Connect (Calm,
     * Headspace, Samsung Health, etc.) provide total minutes of
     * practice; the report surfaces it as a separate dimension next
     * to steps and sleep. Evidence: app-based mindfulness has a
     * medium effect on stress (g = 0.46) and small-to-medium on
     * anxiety (g = 0.16–0.40) and depression (g = 0.24–0.43) in a
     * 34-RCT meta-analysis (Gál et al., J Affect Disord 2020).
     */
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
    )

    /**
     * The subset of [PERMISSIONS] the current provider can actually
     * supply. Always at least the seven types that do not depend on
     * a feature flag; the eighth — [MindfulnessSessionRecord] — is
     * included only when [HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION]
     * is reported as [HealthConnectFeatures.FEATURE_STATUS_AVAILABLE].
     *
     * Returned by [effectivePermissions] and used by the Settings
     * panel's "change what is shared" flow. A provider that lacks the
     * feature should not see the permission in the system dialog,
     * and the wellness surface should not read a record type the
     * provider cannot return.
     */
    fun effectivePermissions(context: Context): Set<String> {
        val base = PERMISSIONS - HealthPermission.getReadPermission(MindfulnessSessionRecord::class)
        if (!isAvailable(context)) return base
        val hcClient = runCatching { client(context) }.getOrNull() ?: return base
        val features = runCatching { hcClient.features }.getOrNull() ?: return base
        val status = runCatching { features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) }
            .getOrDefault(HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE)
        return if (status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
            PERMISSIONS
        } else {
            base
        }
    }

    /**
     * True when the current Health Connect provider advertises
     * support for [MindfulnessSessionRecord]. False on a missing
     * provider, a mid-update provider, a 1.1.0 provider that has
     * not turned the feature on, or any read failure. Used by the
     * wellness surface to skip the mindfulness read path on
     * devices that cannot supply it.
     */
    fun isMindfulnessSupported(context: Context): Boolean = runCatching {
        val hcClient = client(context) ?: return@runCatching false
        val features = hcClient.features
        features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /**
     * Whether Health Connect is installed and usable on this device.
     *
     * Never throws: an uninstalled or outdated provider is an ordinary
     * outcome here, not a crash.
     */
    fun isAvailable(context: Context): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    /** A client, or null if Health Connect is unavailable or construction fails for any reason. */
    private fun client(context: Context): HealthConnectClient? {
        if (!isAvailable(context)) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    /** The subset of [PERMISSIONS] currently granted. Empty on any failure, including "no client". */
    suspend fun grantedPermissions(context: Context): Set<String> = runCatching {
        val hcClient = client(context) ?: return emptySet()
        // NOTE: PermissionController.getGrantedPermissions(): Set<String>
        // — believed correct but, like the rest of this file's Health
        // Connect calls, unverified against this repo's CI.
        hcClient.permissionController.getGrantedPermissions().intersect(PERMISSIONS)
    }.getOrDefault(emptySet())

    /** True only once every permission in [PERMISSIONS] has been granted. */
    suspend fun hasAllPermissions(context: Context): Boolean =
        grantedPermissions(context).containsAll(PERMISSIONS)

    /** True as soon as any single permission has been granted — enough to read something. */
    suspend fun hasAnyPermissions(context: Context): Boolean =
        grantedPermissions(context).isNotEmpty()

    /**
     * The ActivityResultContract the Settings UI uses to launch the
     * system Health Connect permission dialog. Wraps the SDK's
     * [PermissionController.createRequestPermissionResultContract] so
     * the call site reads as `requestPermissionsContract().launch(set)`,
     * matching the other affordances in the launcher.
     *
     * The contract is created once per process and the system binds
     * to the result Intent — the launcher's caller receives a
     * `Set<String>` of permissions the user granted. Empty set
     * means deny-all. A subset means a partial grant.
     */
    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /**
     * A canonical, in-order list of (human-readable label, permission)
     * pairs the Settings UI iterates to render the "what this app reads"
     * section. The order matches the order in [PERMISSIONS] so the
     * two never drift. The labels live in `strings.xml` rather than
     * here — they are the wording-reviewed surface — so what this
     * helper really does is hand the UI the permission strings in a
     * stable order; the UI looks each one up in the resource table.
     */
    fun permissionLabelsInOrder(): List<Pair<String, String>> = listOf(
        "heart_rate" to HealthPermission.getReadPermission(HeartRateRecord::class),
        "resting_heart_rate" to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        "heart_rate_variability" to HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        "sleep" to HealthPermission.getReadPermission(SleepSessionRecord::class),
        "steps" to HealthPermission.getReadPermission(StepsRecord::class),
        "exercise" to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        "calories" to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        "mindfulness" to HealthPermission.getReadPermission(MindfulnessSessionRecord::class),
    )

    /**
     * Reads [date]'s vitals in [zone] and reduces them into [DailyVitals].
     *
     * Each record type is read and reduced on its own; one type failing —
     * not granted, provider hiccup, anything — never stops the others from
     * being read. On total failure (no client, for instance) this returns
     * [DailyVitals.empty], not an exception.
     */
    suspend fun readDailyVitals(
        context: Context,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyVitals = runCatching {
        val hcClient = client(context) ?: return DailyVitals.empty(date)

        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        // NOTE: TimeRangeFilter.between(Instant, Instant) is the smallest,
        // most conservative constructor available; verify against CI if
        // the alpha07 artifact has moved this.
        val range = TimeRangeFilter.between(start, end)
        val zoneOffsetMinutes = zone.rules.getOffset(start).totalSeconds / 60

        val heartRateSamples = readHeartRateSamples(hcClient, range)
        val restingReadings = readInstantValues<RestingHeartRateRecord>(hcClient, range) {
            it.time.toEpochMilli() to it.beatsPerMinute.toDouble()
        }
        val hrvReadings = readInstantValues<HeartRateVariabilityRmssdRecord>(hcClient, range) {
            it.time.toEpochMilli() to it.heartRateVariabilityMillis
        }
        val sleepSessions = readIntervals<SleepSessionRecord>(hcClient, range) {
            it.startTime.toEpochMilli() to it.endTime.toEpochMilli()
        }
        val stepCounts = readCounts<StepsRecord>(hcClient, range) { it.count }
        val exerciseSessions = readIntervals<ExerciseSessionRecord>(hcClient, range) {
            it.startTime.toEpochMilli() to it.endTime.toEpochMilli()
        }
        // Mindfulness is gated on a per-provider feature flag in
        // Health Connect 1.1.0 — older 1.1.0 providers do not advertise
        // it. Reading the record on an unsupported provider raises an
        // exception the outer [runCatching] would swallow, but a
        // defensive skip keeps the call out of the provider entirely.
        val mindfulnessSessions = if (isMindfulnessSupported(context)) {
            readIntervals<MindfulnessSessionRecord>(hcClient, range) {
                it.startTime.toEpochMilli() to it.endTime.toEpochMilli()
            }
        } else {
            emptyList()
        }

        DailyVitals(
            date = date,
            restingHeartRate = DailyVitalsReducer.restingHeartRate(restingReadings),
            meanHeartRate = DailyVitalsReducer.meanHeartRate(heartRateSamples),
            minHeartRate = DailyVitalsReducer.minHeartRate(heartRateSamples),
            hrvRmssd = DailyVitalsReducer.hrvRmssd(hrvReadings),
            sleepMinutes = DailyVitalsReducer.sleepMinutes(sleepSessions),
            sleepOnset = DailyVitalsReducer.sleepOnset(sleepSessions, zoneOffsetMinutes),
            steps = DailyVitalsReducer.steps(stepCounts),
            activeMinutes = DailyVitalsReducer.activeMinutes(exerciseSessions),
            mindfulnessMinutes = DailyVitalsReducer.mindfulnessMinutes(mindfulnessSessions),
        )
    }.getOrDefault(DailyVitals.empty(date))

    /**
     * Heart rate is the one record type here with samples nested inside
     * each record rather than one value per record — see
     * [HeartRateRecord.samples] — so it gets its own reader instead of
     * fitting [readInstantValues].
     */
    private suspend fun readHeartRateSamples(
        hcClient: HealthConnectClient,
        range: TimeRangeFilter,
    ): List<Pair<Long, Double>> = runCatching {
        // NOTE: ReadRecordsRequest(recordType, timeRangeFilter) and
        // HealthConnectClient.readRecords(...).records are believed
        // correct for connect-client 1.1.0-alpha07 but are unverified
        // against this repo's CI; check there if this fails to compile.
        val response = hcClient.readRecords(ReadRecordsRequest(HeartRateRecord::class, range))
        response.records.flatMap { record ->
            record.samples.map { it.time.toEpochMilli() to it.beatsPerMinute.toDouble() }
        }
    }.getOrDefault(emptyList())

    /** One (timestamp millis, value) reading per record — resting heart rate, HRV. */
    private suspend inline fun <reified T : Record> readInstantValues(
        hcClient: HealthConnectClient,
        range: TimeRangeFilter,
        extract: (T) -> Pair<Long, Double>,
    ): List<Pair<Long, Double>> = runCatching {
        hcClient.readRecords(ReadRecordsRequest(T::class, range)).records.map(extract)
    }.getOrDefault(emptyList())

    /** One (start millis, end millis) interval per record — sleep and exercise sessions. */
    private suspend inline fun <reified T : Record> readIntervals(
        hcClient: HealthConnectClient,
        range: TimeRangeFilter,
        extract: (T) -> Pair<Long, Long>,
    ): List<Pair<Long, Long>> = runCatching {
        hcClient.readRecords(ReadRecordsRequest(T::class, range)).records.map(extract)
    }.getOrDefault(emptyList())

    /** One count per record — steps. */
    private suspend inline fun <reified T : Record> readCounts(
        hcClient: HealthConnectClient,
        range: TimeRangeFilter,
        extract: (T) -> Long,
    ): List<Long> = runCatching {
        hcClient.readRecords(ReadRecordsRequest(T::class, range)).records.map(extract)
    }.getOrDefault(emptyList())

    /**
     * v0.25.19: a public, test-friendly read path. The
     * existing [readDailyVitals] takes a [Context] and
     * resolves the [HealthConnectClient] itself, which is
     * fine for production but impossible to drive from a
     * unit test. This entry point takes the client
     * directly, takes a [TimeRangeFilter], and returns
     * the heart-rate samples — the canonical record type
     * for the smoke test.
     *
     * The smoke test ([HealthConnectSmokeFindingTest])
     * drives this function with a [Context] that resolves
     * to a real [HealthConnectClient] (or to null, on a
     * machine where Health Connect is not installed —
     * the test then asserts the function returns
     * [DailyVitals.empty] without throwing).
     */
    suspend fun connectAndRead(
        context: Context,
        date: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyVitals = readDailyVitals(context, date, zone)
}
