package org.mindanchor.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import org.mindanchor.intelligence.PassiveReadRange
import org.mindanchor.intelligence.PassiveReadState
import org.mindanchor.intelligence.PassiveRecordKind
import org.mindanchor.intelligence.PassiveRecordSource
import org.mindanchor.intelligence.PassiveSeed
import org.mindanchor.intelligence.PassiveSourceFamily
import org.mindanchor.intelligence.PassiveSourceRead
import org.mindanchor.intelligence.PassiveSourceRecord
import org.mindanchor.sleep.SleepRepository

internal data class RawUsageEvent(val eventType: Int, val timeStamp: Long)

internal interface PassiveUsageStatsGateway {
    fun hasUsageAccess(): Boolean
    fun queryEvents(startInclusive: Long, endExclusive: Long): List<RawUsageEvent>
}

private class AndroidPassiveUsageStatsGateway(
    private val appContext: Context,
) : PassiveUsageStatsGateway {
    override fun hasUsageAccess(): Boolean = SleepRepository(appContext).hasUsageAccess()

    override fun queryEvents(startInclusive: Long, endExclusive: Long): List<RawUsageEvent> {
        val manager = checkNotNull(appContext.getSystemService(UsageStatsManager::class.java))
        val events = manager.queryEvents(startInclusive, endExclusive)
        val platformEvent = UsageEvents.Event()
        val result = mutableListOf<RawUsageEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(platformEvent)
            result += RawUsageEvent(platformEvent.eventType, platformEvent.timeStamp)
        }
        return result
    }
}

class PassiveUsageStatsSource internal constructor(
    private val gateway: PassiveUsageStatsGateway,
    private val manufacturer: String,
    private val model: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : PassiveRecordSource {
    constructor(context: Context) : this(
        AndroidPassiveUsageStatsGateway(context.applicationContext),
        Build.MANUFACTURER,
        Build.MODEL,
    )

    @Suppress("LongMethod", "SwallowedException", "TooGenericExceptionCaught")
    override suspend fun read(range: PassiveReadRange): List<PassiveSourceRead> {
        val attemptedAt = clock()
        if (!gateway.hasUsageAccess()) {
            return listOf(
                PassiveSourceRead(
                    sourceFamily = PassiveSourceFamily.USAGE_STATS,
                    state = PassiveReadState.PERMISSION_DENIED,
                    range = range,
                    attemptedAt = attemptedAt,
                    errorCode = "PACKAGE_USAGE_STATS_DENIED",
                ),
            )
        }
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
                    sourceFamily = PassiveSourceFamily.USAGE_STATS,
                    kind = kind,
                    eventStart = raw.timeStamp,
                    eventEnd = raw.timeStamp,
                    value = null,
                    unit = "event",
                    dataOriginPackage = "android.usage_stats",
                    deviceManufacturer = manufacturer,
                    deviceModel = model,
                    deviceType = "PHONE",
                    sourceUpdatedTime = null,
                    ingestedAt = attemptedAt,
                    zoneId = zone.id,
                    zoneOffsetSeconds = zone.rules
                        .getOffset(Instant.ofEpochMilli(raw.timeStamp)).totalSeconds,
                    recordId = PassiveSeed.sha256("${kind.name}|${raw.timeStamp}|$manufacturer|$model"),
                    recordVersion = 0L,
                )
            }
            listOf(
                PassiveSourceRead(
                    sourceFamily = PassiveSourceFamily.USAGE_STATS,
                    state = PassiveReadState.SUCCESS,
                    range = range,
                    attemptedAt = attemptedAt,
                    records = records,
                ),
            )
        } catch (failure: SecurityException) {
            listOf(
                PassiveSourceRead(
                    sourceFamily = PassiveSourceFamily.USAGE_STATS,
                    state = PassiveReadState.PERMISSION_DENIED,
                    range = range,
                    attemptedAt = attemptedAt,
                    errorCode = "PACKAGE_USAGE_STATS_DENIED",
                ),
            )
        } catch (failure: RuntimeException) {
            listOf(
                PassiveSourceRead(
                    sourceFamily = PassiveSourceFamily.USAGE_STATS,
                    state = PassiveReadState.READ_FAILURE_PERMANENT,
                    range = range,
                    attemptedAt = attemptedAt,
                    errorCode = failure.javaClass.simpleName,
                ),
            )
        }
    }
}
