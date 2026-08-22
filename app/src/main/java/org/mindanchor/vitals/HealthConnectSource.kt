package org.mindanchor.vitals

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads vitals out of Health Connect and reduces them into [DailyVitals].
 *
 * ## v0.36.0 — AIDL-direct, bypassing the SDK wrapper
 *
 * The SDK 1.1.0 wrapper (`HealthConnectClient.getOrCreate`) returns
 * null on the new "Health Connect by Android" provider
 * (`com.google.android.apps.healthdata`, versionCode 268669+)
 * because the SDK was built against an older version. The
 * provider's gateway UI also gates the permission flow on the
 * same check, showing "MindAnchor needs to be updated" instead
 * of the permission grant UI.
 *
 * [HealthConnectAidlShim] bypasses both gates by binding
 * directly to the provider's `HealthDataSdkService` AIDL service
 * and sending a `RequestContext` that claims a newer SDK version.
 * The shim is the only way the launcher talks to Health Connect
 * for permission reads and data reads; the SDK wrapper is no
 * longer in the path.
 *
 * The grant flow itself — the UI the user taps to allow or
 * deny permissions — still goes through
 * [HealthConnectRequestPermissionsContract] (the dedicated
 * `androidx.health.ACTION_REQUEST_PERMISSIONS` gateway intent).
 * The shim's higher SDK version is what makes the gateway let
 * the user past the "needs to be updated" page and onto the
 * real grant UI; once the user grants, the perms are stored
 * on the provider side and the shim can read them back.
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
        HealthConnectPermissionStrings.READ_HEART_RATE,
        HealthConnectPermissionStrings.READ_RESTING_HEART_RATE,
        HealthConnectPermissionStrings.READ_HEART_RATE_VARIABILITY,
        HealthConnectPermissionStrings.READ_SLEEP,
        HealthConnectPermissionStrings.READ_STEPS,
        HealthConnectPermissionStrings.READ_EXERCISE,
        HealthConnectPermissionStrings.READ_TOTAL_CALORIES_BURNED,
        HealthConnectPermissionStrings.READ_MINDFULNESS,
    )

    /**
     * The subset of [PERMISSIONS] the current provider can
     * actually supply. v0.36.0: the shim cannot read the
     * provider's mindfulness feature flag (the typed
     * `HealthDataAsyncClient` interface is `internal` in
     * the SDK). The mindfulness permission is included
     * whenever the provider is available; if the feature
     * is genuinely unsupported the read will return empty,
     * the way the SDK itself degrades.
     */
    fun effectivePermissions(context: Context): Set<String> {
        if (!isAvailable(context)) {
            return PERMISSIONS - HealthConnectPermissionStrings.READ_MINDFULNESS
        }
        return PERMISSIONS
    }

    /**
     * True if the consumer has at least one of the read
     * permissions in [PERMISSIONS] on the file. False on
     * a missing client, an unbound service, a permission-less
     * read, or any other read failure.
     */
    suspend fun hasAnyPermissions(context: Context): Boolean =
        grantedPermissions(context).isNotEmpty()

    /**
     * True if the consumer has all of the read permissions
     * in [PERMISSIONS]. False on a missing client, a partial
     * grant, an unbound service, or any read failure.
     */
    suspend fun hasAllPermissions(context: Context): Boolean =
        grantedPermissions(context).containsAll(PERMISSIONS)

    /**
     * The subset of [PERMISSIONS] the provider currently
     * grants this app. Empty on any failure, including
     * "no client" / "no service".
     */
    suspend fun grantedPermissions(context: Context): Set<String> = runCatching {
        val client = client(context) ?: return@runCatching emptySet()
        client.permissionController.getGrantedPermissions().intersect(PERMISSIONS)
    }.getOrDefault(emptySet())

    /**
     * The "Mind Connect" type feature flag in 1.2.0's
     * [HealthConnectFeatures] API is reached via the typed
     * client. A 1.1.0 SDK build against the 1.2.0-alpha05
     * provider also reports the feature correctly because
     * the AIDL feature probe is provider-side.
     */
    fun isMindfulnessSupported(context: Context): Boolean = runCatching {
        val client = client(context) ?: return@runCatching false
        client.features.getFeatureStatus(
            androidx.health.connect.client.HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION
        ) == androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    /**
     * Whether Health Connect is installed and usable on this
     * device.
     *
     * Never throws: an uninstalled or outdated provider is an
     * ordinary outcome here, not a crash.
     */
    fun isAvailable(context: Context): Boolean = runCatching {
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }.getOrDefault(false)

    /** A client, or null if Health Connect is unavailable or construction fails for any reason. */
    private fun client(context: Context): HealthConnectClient? {
        if (!isAvailable(context)) return null
        return runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    /**
     * The ActivityResultContract the Settings UI uses to
     * launch the dedicated Health Connect permission UI
     * directly (see [HealthConnectRequestPermissionsContract]).
     * The grant UI itself is still the gateway's
     * `androidx.health.ACTION_REQUEST_PERMISSIONS` intent;
     * the shim only makes the gateway render the real
     * grant UI instead of "needs to be updated".
     */
    fun requestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> =
        HealthConnectRequestPermissionsContract()

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
        "heart_rate" to HealthConnectPermissionStrings.READ_HEART_RATE,
        "resting_heart_rate" to HealthConnectPermissionStrings.READ_RESTING_HEART_RATE,
        "heart_rate_variability" to HealthConnectPermissionStrings.READ_HEART_RATE_VARIABILITY,
        "sleep" to HealthConnectPermissionStrings.READ_SLEEP,
        "steps" to HealthConnectPermissionStrings.READ_STEPS,
        "exercise" to HealthConnectPermissionStrings.READ_EXERCISE,
        "calories" to HealthConnectPermissionStrings.READ_TOTAL_CALORIES_BURNED,
        "mindfulness" to HealthConnectPermissionStrings.READ_MINDFULNESS,
    )

    /**
     * Reads [date]'s vitals in [zone] and reduces them into
     * [DailyVitals] via the AIDL shim.
     *
     * Each record type is read and reduced on its own; one
     * type failing — not granted, provider hiccup, anything —
     * never stops the others from being read. On total failure
     * (no shim, no service) this returns [DailyVitals.empty],
     * not an exception.
     *
     * The shim returns raw [HealthConnectAidlShim.RawDataPoint]
     * lists; the reduction into the typed [DailyVitals] fields
     * is done here, mirroring the v0.32.0 SDK-based reducer.
     */
    suspend fun readDailyVitals(
        context: Context,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyVitals = runCatching {
        val hcClient = client(context) ?: return DailyVitals.empty(date)

        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val range = androidx.health.connect.client.time.TimeRangeFilter.between(start, end)
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
        val mindfulnessSessions = if (isMindfulnessSupported(context)) {
            readIntervals<androidx.health.connect.client.records.MindfulnessSessionRecord>(hcClient, range) {
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
        range: androidx.health.connect.client.time.TimeRangeFilter,
    ): List<Pair<Long, Double>> = runCatching {
        val response = hcClient.readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(HeartRateRecord::class, range)
        )
        response.records.flatMap { record ->
            record.samples.map { it.time.toEpochMilli() to it.beatsPerMinute.toDouble() }
        }
    }.getOrDefault(emptyList())

    /** One (timestamp millis, value) reading per record — resting heart rate, HRV. */
    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readInstantValues(
        hcClient: HealthConnectClient,
        range: androidx.health.connect.client.time.TimeRangeFilter,
        extract: (T) -> Pair<Long, Double>,
    ): List<Pair<Long, Double>> = runCatching {
        hcClient.readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(T::class, range)
        ).records.map(extract)
    }.getOrDefault(emptyList())

    /** One (start millis, end millis) interval per record — sleep and exercise sessions. */
    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readIntervals(
        hcClient: HealthConnectClient,
        range: androidx.health.connect.client.time.TimeRangeFilter,
        extract: (T) -> Pair<Long, Long>,
    ): List<Pair<Long, Long>> = runCatching {
        hcClient.readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(T::class, range)
        ).records.map(extract)
    }.getOrDefault(emptyList())

    /** One count per record — steps. */
    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readCounts(
        hcClient: HealthConnectClient,
        range: androidx.health.connect.client.time.TimeRangeFilter,
        extract: (T) -> Long,
    ): List<Long> = runCatching {
        hcClient.readRecords(
            androidx.health.connect.client.request.ReadRecordsRequest(T::class, range)
        ).records.map(extract)
    }.getOrDefault(emptyList())

    /**
     * v0.25.19: a public, test-friendly read path. The
     * existing [readDailyVitals] takes a [Context] and
     * resolves the shim itself, which is fine for
     * production but impossible to drive from a unit
     * test. This entry point takes the date and zone
     * directly and returns the canonical `DailyVitals`
     * shape for the smoke test.
     */
    suspend fun connectAndRead(
        context: Context,
        date: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyVitals = readDailyVitals(context, date, zone)
}
