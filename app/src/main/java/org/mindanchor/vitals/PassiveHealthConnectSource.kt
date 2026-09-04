package org.mindanchor.vitals

import android.content.Context
import android.os.DeadObjectException
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass
import org.mindanchor.intelligence.PassiveReadRange
import org.mindanchor.intelligence.PassiveReadState
import org.mindanchor.intelligence.PassiveRecordKind
import org.mindanchor.intelligence.PassiveRecordSource
import org.mindanchor.intelligence.PassiveSeed
import org.mindanchor.intelligence.PassiveSourceFamily
import org.mindanchor.intelligence.PassiveSourceRead
import org.mindanchor.intelligence.PassiveSourceRecord

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

class PassiveHealthConnectSource internal constructor(
    private val gateway: PassiveHealthConnectGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : PassiveRecordSource {
    constructor(context: Context) : this(AndroidPassiveHealthConnectGateway(context))

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
        val attemptedAt = clock()
        if (gateway.sdkStatus() != HealthConnectClient.SDK_AVAILABLE) {
            return HEALTH_FAMILIES.map { family ->
                PassiveSourceRead(
                    sourceFamily = family,
                    state = PassiveReadState.UNAVAILABLE,
                    range = range,
                    attemptedAt = attemptedAt,
                    errorCode = "HEALTH_CONNECT_UNAVAILABLE",
                )
            }
        }

        val granted = try {
            gateway.grantedPermissions()
        } catch (failure: Throwable) {
            return HEALTH_FAMILIES.map { failedRead(it, range, attemptedAt, failure) }
        }
        val filter = TimeRangeFilter.between(
            Instant.ofEpochMilli(range.startInclusive),
            Instant.ofEpochMilli(range.endExclusive),
        )
        return listOf(
            readFamily<HeartRateRecord>(PassiveSourceFamily.HEART_RATE, filter, range, granted, attemptedAt) {
                normalize(it, range, attemptedAt)
            },
            readFamily<RestingHeartRateRecord>(
                PassiveSourceFamily.RESTING_HEART_RATE,
                filter,
                range,
                granted,
                attemptedAt,
            ) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<HeartRateVariabilityRmssdRecord>(
                PassiveSourceFamily.HRV_RMSSD,
                filter,
                range,
                granted,
                attemptedAt,
            ) { listOf(normalize(it, range, attemptedAt)) },
            readFamily<SleepSessionRecord>(PassiveSourceFamily.SLEEP, filter, range, granted, attemptedAt) {
                listOf(normalize(it, range, attemptedAt))
            },
            readFamily<StepsRecord>(PassiveSourceFamily.STEPS, filter, range, granted, attemptedAt) {
                listOf(normalize(it, range, attemptedAt))
            },
            readFamily<ExerciseSessionRecord>(PassiveSourceFamily.EXERCISE, filter, range, granted, attemptedAt) {
                listOf(normalize(it, range, attemptedAt))
            },
            readFamily<OxygenSaturationRecord>(
                PassiveSourceFamily.OXYGEN_SATURATION,
                filter,
                range,
                granted,
                attemptedAt,
            ) { listOf(normalize(it, range, attemptedAt)) },
        )
    }

    suspend fun historyPermissionGranted(): Boolean =
        gateway.sdkStatus() == HealthConnectClient.SDK_AVAILABLE &&
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in gateway.grantedPermissions()

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <reified T : Record> readFamily(
        family: PassiveSourceFamily,
        filter: TimeRangeFilter,
        range: PassiveReadRange,
        granted: Set<String>,
        attemptedAt: Long,
        normalize: (T) -> List<PassiveSourceRecord>,
    ): PassiveSourceRead {
        if (HealthPermission.getReadPermission(T::class) !in granted) {
            return PassiveSourceRead(
                sourceFamily = family,
                state = PassiveReadState.PERMISSION_DENIED,
                range = range,
                attemptedAt = attemptedAt,
                errorCode = "HEALTH_CONNECT_PERMISSION_DENIED",
            )
        }
        return try {
            PassiveSourceRead(
                sourceFamily = family,
                state = PassiveReadState.SUCCESS,
                range = range,
                attemptedAt = attemptedAt,
                records = gateway.readAll(T::class, filter).flatMap(normalize),
            )
        } catch (failure: Throwable) {
            failedRead(family, range, attemptedAt, failure)
        }
    }
}

private val HEALTH_FAMILIES = listOf(
    PassiveSourceFamily.HEART_RATE,
    PassiveSourceFamily.RESTING_HEART_RATE,
    PassiveSourceFamily.HRV_RMSSD,
    PassiveSourceFamily.SLEEP,
    PassiveSourceFamily.STEPS,
    PassiveSourceFamily.EXERCISE,
    PassiveSourceFamily.OXYGEN_SATURATION,
)

private fun failedRead(
    family: PassiveSourceFamily,
    range: PassiveReadRange,
    attemptedAt: Long,
    failure: Throwable,
): PassiveSourceRead {
    if (failure is CancellationException || failure is Error) throw failure
    val state = when (failure) {
        is SecurityException -> PassiveReadState.PERMISSION_DENIED
        is DeadObjectException, is RemoteException, is IOException -> PassiveReadState.READ_FAILURE_TRANSIENT
        is Exception -> PassiveReadState.READ_FAILURE_PERMANENT
        else -> throw failure
    }
    return PassiveSourceRead(
        sourceFamily = family,
        state = state,
        range = range,
        attemptedAt = attemptedAt,
        errorCode = failure.javaClass.simpleName.ifBlank { failure.javaClass.name },
    )
}

private fun normalize(
    record: HeartRateRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): List<PassiveSourceRecord> = record.samples.mapIndexed { index, sample ->
    val eventTime = sample.time.toEpochMilli()
    val parentId = preferredMetadataId(record.metadata)
    normalizedRecord(
        family = PassiveSourceFamily.HEART_RATE,
        kind = PassiveRecordKind.HEART_RATE_SAMPLE,
        eventStart = eventTime,
        eventEnd = eventTime,
        value = sample.beatsPerMinute.toDouble(),
        unit = "bpm",
        metadata = record.metadata,
        zoneOffset = record.startZoneOffset,
        range = range,
        attemptedAt = attemptedAt,
        recordId = parentId?.let { "$it#$eventTime#$index" },
    )
}

private fun normalize(
    record: RestingHeartRateRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.RESTING_HEART_RATE,
    PassiveRecordKind.RESTING_HEART_RATE,
    record.time.toEpochMilli(),
    record.time.toEpochMilli(),
    record.beatsPerMinute.toDouble(),
    "bpm",
    record.metadata,
    record.zoneOffset,
    range,
    attemptedAt,
)

private fun normalize(
    record: HeartRateVariabilityRmssdRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.HRV_RMSSD,
    PassiveRecordKind.HRV_RMSSD,
    record.time.toEpochMilli(),
    record.time.toEpochMilli(),
    record.heartRateVariabilityMillis,
    "ms",
    record.metadata,
    record.zoneOffset,
    range,
    attemptedAt,
)

private fun normalize(
    record: SleepSessionRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.SLEEP,
    PassiveRecordKind.SLEEP_SESSION,
    record.startTime.toEpochMilli(),
    record.endTime.toEpochMilli(),
    null,
    "milliseconds",
    record.metadata,
    record.startZoneOffset,
    range,
    attemptedAt,
)

private fun normalize(
    record: StepsRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.STEPS,
    PassiveRecordKind.STEPS_INTERVAL,
    record.startTime.toEpochMilli(),
    record.endTime.toEpochMilli(),
    record.count.toDouble(),
    "count",
    record.metadata,
    record.startZoneOffset,
    range,
    attemptedAt,
)

private fun normalize(
    record: ExerciseSessionRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.EXERCISE,
    PassiveRecordKind.EXERCISE_SESSION,
    record.startTime.toEpochMilli(),
    record.endTime.toEpochMilli(),
    null,
    "milliseconds",
    record.metadata,
    record.startZoneOffset,
    range,
    attemptedAt,
)

private fun normalize(
    record: OxygenSaturationRecord,
    range: PassiveReadRange,
    attemptedAt: Long,
): PassiveSourceRecord = normalizedRecord(
    PassiveSourceFamily.OXYGEN_SATURATION,
    PassiveRecordKind.SPO2,
    record.time.toEpochMilli(),
    record.time.toEpochMilli(),
    record.percentage.value,
    "percent",
    record.metadata,
    record.zoneOffset,
    range,
    attemptedAt,
)

@Suppress("LongParameterList")
private fun normalizedRecord(
    family: PassiveSourceFamily,
    kind: PassiveRecordKind,
    eventStart: Long,
    eventEnd: Long,
    value: Double?,
    unit: String,
    metadata: Metadata,
    zoneOffset: ZoneOffset?,
    range: PassiveReadRange,
    attemptedAt: Long,
    recordId: String? = preferredMetadataId(metadata),
): PassiveSourceRecord {
    val device = metadata.device
    val origin = metadata.dataOrigin.packageName.ifBlank { "unknown" }
    val deviceType = device?.let { deviceTypeName(it.type) }
    val resolvedRecordId = recordId ?: PassiveSeed.sha256(
        listOf(
            family.name,
            eventStart.toString(),
            eventEnd.toString(),
            origin,
            value?.toString(),
            unit,
            device?.manufacturer,
            device?.model,
            deviceType,
            metadata.clientRecordVersion.toString(),
        ).joinToString(separator = "") { canonicalPart(it) },
    )
    return PassiveSourceRecord(
        sourceFamily = family,
        kind = kind,
        eventStart = eventStart,
        eventEnd = eventEnd,
        value = value,
        unit = unit,
        dataOriginPackage = origin,
        deviceManufacturer = device?.manufacturer,
        deviceModel = device?.model,
        deviceType = deviceType,
        sourceUpdatedTime = metadata.lastModifiedTime
            .takeIf { it.isAfter(Instant.EPOCH) }
            ?.toEpochMilli(),
        ingestedAt = attemptedAt,
        zoneId = range.zoneId,
        zoneOffsetSeconds = zoneOffset?.totalSeconds ?: ZoneId.of(range.zoneId).rules
            .getOffset(Instant.ofEpochMilli(eventStart)).totalSeconds,
        recordId = resolvedRecordId,
        recordVersion = metadata.clientRecordVersion,
    )
}

private fun preferredMetadataId(metadata: Metadata): String? =
    metadata.id.takeIf { it.isNotBlank() }
        ?: metadata.clientRecordId?.takeIf { it.isNotBlank() }

private fun canonicalPart(value: String?): String =
    value?.let { "${it.length}:$it" } ?: "null:"

private fun deviceTypeName(type: Int): String = when (type) {
    Device.TYPE_WATCH -> "WATCH"
    Device.TYPE_PHONE -> "PHONE"
    Device.TYPE_SCALE -> "SCALE"
    Device.TYPE_RING -> "RING"
    Device.TYPE_HEAD_MOUNTED -> "HEAD_MOUNTED"
    Device.TYPE_FITNESS_BAND -> "FITNESS_BAND"
    Device.TYPE_CHEST_STRAP -> "CHEST_STRAP"
    Device.TYPE_SMART_DISPLAY -> "SMART_DISPLAY"
    else -> "UNKNOWN"
}
