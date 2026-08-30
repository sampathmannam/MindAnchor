package org.mindanchor.vitals

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
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Percentage
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.intelligence.PassiveFeature
import org.mindanchor.intelligence.PassiveReadRange
import org.mindanchor.intelligence.PassiveReadState
import org.mindanchor.intelligence.PassiveRecordKind
import org.mindanchor.intelligence.PassiveSourceFamily

class PassiveHealthConnectSourceTest {

    @Test
    fun `readAllPages follows every non-null page token`() = runBlocking {
        val seen = mutableListOf<String?>()
        val first = StepsRecord(
            startTime = Instant.ofEpochMilli(1_000L),
            startZoneOffset = ZoneOffset.UTC,
            endTime = Instant.ofEpochMilli(2_000L),
            endZoneOffset = ZoneOffset.UTC,
            count = 1L,
            metadata = Metadata.manualEntry(),
        )
        val second = StepsRecord(
            startTime = Instant.ofEpochMilli(2_000L),
            startZoneOffset = ZoneOffset.UTC,
            endTime = Instant.ofEpochMilli(3_000L),
            endZoneOffset = ZoneOffset.UTC,
            count = 2L,
            metadata = Metadata.manualEntry(),
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

    @Test
    fun `successful empty is not permission denied or provider failure`() = runBlocking {
        val source = PassiveHealthConnectSource(
            gateway = FakeGateway(
                granted = setOf(HealthPermission.getReadPermission(StepsRecord::class)),
            ),
            clock = { 10_000L },
        )

        val reads = source.read(PassiveReadRange(1_000L, 2_000L, "UTC"))

        assertEquals(
            PassiveReadState.SUCCESS,
            reads.single { it.sourceFamily == PassiveSourceFamily.STEPS }.state,
        )
        assertTrue(reads.single { it.sourceFamily == PassiveSourceFamily.STEPS }.records.isEmpty())
        assertEquals(
            PassiveReadState.PERMISSION_DENIED,
            reads.single { it.sourceFamily == PassiveSourceFamily.SLEEP }.state,
        )
    }

    @Test
    fun `oxygen metadata is preserved and remains unscored`() = runBlocking {
        val metadata = Metadata.autoRecorded(
            device = Device(type = Device.TYPE_RING, manufacturer = "Acme", model = "R1"),
            clientRecordId = "oxygen-client",
            clientRecordVersion = 7L,
        )
        val oxygen = OxygenSaturationRecord(
            time = Instant.parse("2026-08-30T01:02:03Z"),
            zoneOffset = ZoneOffset.ofHoursMinutes(5, 30),
            percentage = Percentage(97.0),
            metadata = metadata,
        )
        val source = PassiveHealthConnectSource(FakeGateway.withRecord(oxygen), clock = { 20_000L })

        val record = source.read(PassiveReadRange(0L, 2_000_000_000_000L, "Asia/Kolkata"))
            .single { it.sourceFamily == PassiveSourceFamily.OXYGEN_SATURATION }.records.single()

        assertEquals(PassiveRecordKind.SPO2, record.kind)
        assertEquals("percent", record.unit)
        assertEquals(97.0, record.value!!, 0.0)
        assertEquals("Acme", record.deviceManufacturer)
        assertEquals("R1", record.deviceModel)
        assertEquals("RING", record.deviceType)
        assertEquals(7L, record.recordVersion)
        assertFalse(PassiveFeature.SPO2_PERCENT.scored)
    }

    @Test
    @Suppress("LongMethod")
    fun `all Health Connect families normalize independently`() = runBlocking {
        val metadata = serverMetadata(
            id = "provider-record",
            origin = "com.example.provider",
            lastModifiedTime = Instant.ofEpochMilli(7_500L),
            clientRecordVersion = 3L,
            device = Device(type = Device.TYPE_WATCH, manufacturer = "Example", model = "W2"),
        )
        val records: List<Record> = listOf(
            HeartRateRecord(
                startTime = Instant.ofEpochMilli(1_000L),
                startZoneOffset = null,
                endTime = Instant.ofEpochMilli(2_000L),
                endZoneOffset = null,
                samples = listOf(
                    HeartRateRecord.Sample(Instant.ofEpochMilli(1_100L), 61L),
                    HeartRateRecord.Sample(Instant.ofEpochMilli(1_200L), 62L),
                ),
                metadata = metadata,
            ),
            RestingHeartRateRecord(Instant.ofEpochMilli(2_100L), ZoneOffset.UTC, 58L, metadata),
            HeartRateVariabilityRmssdRecord(Instant.ofEpochMilli(2_200L), ZoneOffset.UTC, 42.5, metadata),
            SleepSessionRecord(
                Instant.ofEpochMilli(3_000L), ZoneOffset.UTC,
                Instant.ofEpochMilli(4_000L), ZoneOffset.UTC, metadata,
            ),
            StepsRecord(
                Instant.ofEpochMilli(4_000L), ZoneOffset.UTC,
                Instant.ofEpochMilli(5_000L), ZoneOffset.UTC, 321L, metadata,
            ),
            ExerciseSessionRecord(
                Instant.ofEpochMilli(5_000L), ZoneOffset.UTC,
                Instant.ofEpochMilli(7_000L), ZoneOffset.UTC, metadata,
                ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ),
            OxygenSaturationRecord(Instant.ofEpochMilli(2_300L), ZoneOffset.UTC, Percentage(98.0), metadata),
        )
        val source = PassiveHealthConnectSource(FakeGateway.withRecords(records), clock = { 8_000L })

        val reads = source.read(PassiveReadRange(1L, 9_000L, "Asia/Kolkata"))

        assertEquals(
            PassiveSourceFamily.entries.toSet() - PassiveSourceFamily.USAGE_STATS,
            reads.map { it.sourceFamily }.toSet(),
        )
        assertTrue(reads.all { it.state == PassiveReadState.SUCCESS })
        val heart = reads.single { it.sourceFamily == PassiveSourceFamily.HEART_RATE }.records
        assertEquals(listOf(61.0, 62.0), heart.map { it.value })
        assertEquals(listOf("provider-record#1100#0", "provider-record#1200#1"), heart.map { it.recordId })
        assertEquals(19_800, heart.first().zoneOffsetSeconds)
        assertEquals("bpm", reads.record(PassiveSourceFamily.RESTING_HEART_RATE).unit)
        assertEquals("ms", reads.record(PassiveSourceFamily.HRV_RMSSD).unit)
        assertEquals("milliseconds", reads.record(PassiveSourceFamily.SLEEP).unit)
        assertNull(reads.record(PassiveSourceFamily.SLEEP).value)
        assertEquals(321.0, reads.record(PassiveSourceFamily.STEPS).value!!, 0.0)
        assertEquals("count", reads.record(PassiveSourceFamily.STEPS).unit)
        assertEquals("milliseconds", reads.record(PassiveSourceFamily.EXERCISE).unit)
        assertEquals("percent", reads.record(PassiveSourceFamily.OXYGEN_SATURATION).unit)
        assertEquals(7_500L, reads.record(PassiveSourceFamily.OXYGEN_SATURATION).sourceUpdatedTime)
        assertEquals(
            "com.example.provider",
            reads.record(PassiveSourceFamily.OXYGEN_SATURATION).dataOriginPackage,
        )
    }

    @Test
    fun `family failures and missing grants keep distinct states without erasing successes`() = runBlocking {
        val grantedTypes = setOf(
            HeartRateRecord::class,
            RestingHeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            StepsRecord::class,
            OxygenSaturationRecord::class,
        )
        val gateway = FakeGateway(
            granted = grantedTypes.mapTo(mutableSetOf()) { HealthPermission.getReadPermission(it) },
            failures = mapOf(
                HeartRateRecord::class to SecurityException("revoked"),
                RestingHeartRateRecord::class to IOException("provider busy"),
                HeartRateVariabilityRmssdRecord::class to IllegalStateException("bad response"),
            ),
        )

        val reads = PassiveHealthConnectSource(gateway, clock = { 9_000L })
            .read(PassiveReadRange(1L, 10_000L, "UTC"))

        assertEquals(PassiveReadState.PERMISSION_DENIED, reads.state(PassiveSourceFamily.HEART_RATE))
        assertEquals(PassiveReadState.READ_FAILURE_TRANSIENT, reads.state(PassiveSourceFamily.RESTING_HEART_RATE))
        assertEquals(PassiveReadState.READ_FAILURE_PERMANENT, reads.state(PassiveSourceFamily.HRV_RMSSD))
        assertEquals(PassiveReadState.PERMISSION_DENIED, reads.state(PassiveSourceFamily.SLEEP))
        assertEquals(PassiveReadState.SUCCESS, reads.state(PassiveSourceFamily.STEPS))
        assertEquals(PassiveReadState.PERMISSION_DENIED, reads.state(PassiveSourceFamily.EXERCISE))
        assertEquals(PassiveReadState.SUCCESS, reads.state(PassiveSourceFamily.OXYGEN_SATURATION))
        assertTrue(SleepSessionRecord::class !in gateway.requested)
        assertTrue(ExerciseSessionRecord::class !in gateway.requested)
    }

    @Test
    fun `unavailable provider returns unavailable for every Health Connect family`() = runBlocking {
        val reads = PassiveHealthConnectSource(
            FakeGateway(status = HealthConnectClient.SDK_UNAVAILABLE),
            clock = { 4_000L },
        ).read(PassiveReadRange(1L, 2L, "UTC"))

        assertEquals(7, reads.size)
        assertTrue(reads.all { it.state == PassiveReadState.UNAVAILABLE })
        assertTrue(reads.all { it.errorCode == "HEALTH_CONNECT_UNAVAILABLE" })
    }

    @Test
    fun `permission query failure is classified for every family`() = runBlocking {
        val reads = PassiveHealthConnectSource(
            FakeGateway(permissionFailure = RemoteException("binder")),
            clock = { 4_000L },
        ).read(PassiveReadRange(1L, 2L, "UTC"))

        assertEquals(7, reads.size)
        assertTrue(reads.all { it.state == PassiveReadState.READ_FAILURE_TRANSIENT })
    }

    @Test
    fun `cancellation and errors are rethrown`() {
        val allGranted = FakeGateway.allPermissions()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                PassiveHealthConnectSource(
                    FakeGateway(
                        granted = allGranted,
                        failures = mapOf(HeartRateRecord::class to CancellationException()),
                    ),
                ).read(PassiveReadRange(1L, 2L, "UTC"))
            }
        }
        assertThrows(AssertionError::class.java) {
            runBlocking {
                PassiveHealthConnectSource(
                    FakeGateway(
                        granted = allGranted,
                        failures = mapOf(HeartRateRecord::class to AssertionError("fatal")),
                    ),
                ).read(PassiveReadRange(1L, 2L, "UTC"))
            }
        }
    }

    @Test
    fun `blank server timestamps and ids use null update time and stable fallback id`() = runBlocking {
        val steps = StepsRecord(
            Instant.ofEpochMilli(1_000L), null,
            Instant.ofEpochMilli(2_000L), null, 4L, Metadata.manualEntry(),
        )
        val source = PassiveHealthConnectSource(FakeGateway.withRecord(steps), clock = { 5_000L })

        val first = source.read(PassiveReadRange(1L, 3_000L, "UTC")).record(PassiveSourceFamily.STEPS)
        val second = source.read(PassiveReadRange(1L, 3_000L, "UTC")).record(PassiveSourceFamily.STEPS)

        assertNull(first.sourceUpdatedTime)
        assertEquals(first.recordId, second.recordId)
        assertTrue(first.recordId.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `history permission helper reports only an explicit grant`() = runBlocking {
        assertTrue(
            PassiveHealthConnectSource(
                FakeGateway(granted = setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)),
            ).historyPermissionGranted(),
        )
        assertFalse(PassiveHealthConnectSource(FakeGateway()).historyPermissionGranted())
        assertFalse(
            PassiveHealthConnectSource(
                FakeGateway(status = HealthConnectClient.SDK_UNAVAILABLE),
            ).historyPermissionGranted(),
        )
    }

    private fun List<org.mindanchor.intelligence.PassiveSourceRead>.state(
        family: PassiveSourceFamily,
    ): PassiveReadState = single { it.sourceFamily == family }.state

    private fun List<org.mindanchor.intelligence.PassiveSourceRead>.record(
        family: PassiveSourceFamily,
    ) = single { it.sourceFamily == family }.records.single()

    private fun serverMetadata(
        id: String,
        origin: String,
        lastModifiedTime: Instant = Instant.EPOCH,
        clientRecordId: String? = null,
        clientRecordVersion: Long = 0L,
        device: Device? = null,
    ): Metadata {
        val constructor = Metadata::class.java.constructors.single { it.parameterCount == 7 }
        return constructor.newInstance(
            Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            id,
            DataOrigin(origin),
            lastModifiedTime,
            clientRecordId,
            clientRecordVersion,
            device,
        ) as Metadata
    }

    private class FakeGateway(
        private val granted: Set<String> = emptySet(),
        private val records: Map<KClass<out Record>, List<Record>> = emptyMap(),
        private val failures: Map<KClass<out Record>, Throwable> = emptyMap(),
        private val status: Int = HealthConnectClient.SDK_AVAILABLE,
        private val permissionFailure: Throwable? = null,
    ) : PassiveHealthConnectGateway {
        val requested = mutableListOf<KClass<out Record>>()

        override fun sdkStatus(): Int = status

        override suspend fun grantedPermissions(): Set<String> {
            permissionFailure?.let { throw it }
            return granted
        }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Record> readAll(
            recordType: KClass<T>,
            range: TimeRangeFilter,
        ): List<T> {
            requested += recordType
            failures[recordType]?.let { throw it }
            return records[recordType].orEmpty() as List<T>
        }

        companion object {
            private val recordTypes = listOf(
                HeartRateRecord::class,
                RestingHeartRateRecord::class,
                HeartRateVariabilityRmssdRecord::class,
                SleepSessionRecord::class,
                StepsRecord::class,
                ExerciseSessionRecord::class,
                OxygenSaturationRecord::class,
            )

            fun allPermissions(): Set<String> =
                recordTypes.mapTo(mutableSetOf()) { HealthPermission.getReadPermission(it) }

            fun withRecord(record: Record): FakeGateway = withRecords(listOf(record))

            fun withRecords(records: List<Record>): FakeGateway = FakeGateway(
                granted = records.mapTo(mutableSetOf()) { HealthPermission.getReadPermission(it::class) },
                records = records.groupBy { it::class },
            )
        }
    }
}
