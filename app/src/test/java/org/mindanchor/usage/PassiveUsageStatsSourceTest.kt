package org.mindanchor.usage

import android.app.usage.UsageEvents
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.intelligence.PassiveReadRange
import org.mindanchor.intelligence.PassiveReadState
import org.mindanchor.intelligence.PassiveRecordKind
import org.mindanchor.intelligence.PassiveSeed

class PassiveUsageStatsSourceTest {

    @Test
    fun `denied usage access does not query or emit records`() = runBlocking {
        val gateway = FakeGateway(hasAccess = false)

        val read = PassiveUsageStatsSource(gateway, "Acme", "Phone", clock = { 9_000L })
            .read(PassiveReadRange(1_000L, 2_000L, "UTC")).single()

        assertEquals(PassiveReadState.PERMISSION_DENIED, read.state)
        assertEquals("PACKAGE_USAGE_STATS_DENIED", read.errorCode)
        assertEquals(0, gateway.queryCount)
        assertTrue(read.records.isEmpty())
    }

    @Test
    fun `successful empty usage query is a successful source read`() = runBlocking {
        val read = PassiveUsageStatsSource(FakeGateway(hasAccess = true), "Acme", "Phone", clock = { 9_000L })
            .read(PassiveReadRange(1_000L, 2_000L, "UTC")).single()

        assertEquals(PassiveReadState.SUCCESS, read.state)
        assertNull(read.errorCode)
        assertTrue(read.records.isEmpty())
    }

    @Test
    fun `successful query copies supported screen events with stable provenance`() = runBlocking {
        val events = listOf(
            RawUsageEvent(UsageEvents.Event.SCREEN_INTERACTIVE, 1_000L),
            RawUsageEvent(UsageEvents.Event.SCREEN_NON_INTERACTIVE, 2_000L),
            RawUsageEvent(UsageEvents.Event.KEYGUARD_HIDDEN, 3_000L),
            RawUsageEvent(UsageEvents.Event.ACTIVITY_RESUMED, 4_000L),
        )
        val range = PassiveReadRange(500L, 5_000L, "Asia/Kolkata")

        val records = PassiveUsageStatsSource(
            FakeGateway(hasAccess = true, events = events),
            manufacturer = "Acme",
            model = "P1",
            clock = { 6_000L },
        ).read(range).single().records

        assertEquals(
            listOf(
                PassiveRecordKind.SCREEN_INTERACTIVE,
                PassiveRecordKind.SCREEN_NON_INTERACTIVE,
                PassiveRecordKind.SCREEN_UNLOCKED,
            ),
            records.map { it.kind },
        )
        val first = records.first()
        assertEquals(1_000L, first.eventStart)
        assertEquals(1_000L, first.eventEnd)
        assertNull(first.value)
        assertEquals("event", first.unit)
        assertEquals("android.usage_stats", first.dataOriginPackage)
        assertEquals("Acme", first.deviceManufacturer)
        assertEquals("P1", first.deviceModel)
        assertEquals("PHONE", first.deviceType)
        assertNull(first.sourceUpdatedTime)
        assertEquals(6_000L, first.ingestedAt)
        assertEquals(ZoneId.of(range.zoneId).id, first.zoneId)
        assertEquals(
            ZoneId.of(range.zoneId).rules.getOffset(Instant.ofEpochMilli(1_000L)).totalSeconds,
            first.zoneOffsetSeconds,
        )
        assertEquals(
            PassiveSeed.sha256("SCREEN_INTERACTIVE|1000|Acme|P1"),
            first.recordId,
        )
        assertEquals(0L, first.recordVersion)
    }

    @Test
    fun `security exception after access check is permission denied with no records`() = runBlocking {
        val read = PassiveUsageStatsSource(
            FakeGateway(hasAccess = true, failure = SecurityException("revoked")),
            "Acme",
            "P1",
        ).read(PassiveReadRange(1L, 2L, "UTC")).single()

        assertEquals(PassiveReadState.PERMISSION_DENIED, read.state)
        assertEquals("PACKAGE_USAGE_STATS_DENIED", read.errorCode)
        assertTrue(read.records.isEmpty())
    }

    @Test
    fun `runtime query failure is permanent with no records`() = runBlocking {
        val read = PassiveUsageStatsSource(
            FakeGateway(hasAccess = true, failure = IllegalStateException("service missing")),
            "Acme",
            "P1",
        ).read(PassiveReadRange(1L, 2L, "UTC")).single()

        assertEquals(PassiveReadState.READ_FAILURE_PERMANENT, read.state)
        assertEquals("IllegalStateException", read.errorCode)
        assertTrue(read.records.isEmpty())
    }

    private class FakeGateway(
        private val hasAccess: Boolean,
        private val events: List<RawUsageEvent> = emptyList(),
        private val failure: RuntimeException? = null,
    ) : PassiveUsageStatsGateway {
        var queryCount: Int = 0

        override fun hasUsageAccess(): Boolean = hasAccess

        override fun queryEvents(startInclusive: Long, endExclusive: Long): List<RawUsageEvent> {
            queryCount += 1
            failure?.let { throw it }
            return events
        }
    }
}
