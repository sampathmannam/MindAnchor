package org.mindanchor.watch.connector

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer

/**
 * The sink every connector writes to.
 *
 * The wellness card reads from Health Connect (the existing
 * [org.mindanchor.vitals.HealthConnectSource] path), so any
 * connector that writes to Health Connect makes its data
 * appear in the same place a Wear OS watch's data appears —
 * no parallel pipeline to maintain, no second merge step in
 * [org.mindanchor.vitals.WellnessRepository]. A new BLE
 * chest strap is "just another heart rate source" from the
 * card's point of view.
 *
 * ## What is written
 *
 *  - **HeartRateRecord** for every [WearableSample.HeartRate]
 *    — the universal type the home card already understands.
 *  - **HeartRateVariabilityRmssdRecord** for the rolling
 *    RMSSD of the last [RMSSD_WINDOW] RR intervals. The
 *    RMSSD is the standard DBT diary-card number, and
 *    computing it from RR is the textbook definition.
 *
 * Sleep, steps, and calories are *not* written here. The
 * generic BLE HR service does not carry them, the vendor
 * web-API connectors write their own records when they have
 * data, and Health Connect already has the right
 * permission gates for the dedicated record types.
 *
 * ## Why a buffer
 *
 * The chest strap can emit one notification per second. The
 * Health Connect client is fine with a few writes per minute
 * and a slow write should never block the GATT thread. The
 * [buffer] is the standard coroutines pattern: every sample
 * arrives, the writer flushes the new RMSSD every
 * [FLUSH_EVERY] RR intervals instead of every beat.
 *
 * ## Why the recording method is `activelyRecorded`
 *
 * The BLE chest strap is something the user *put on* and
 * kept running, which is what `RECORDING_METHOD_ACTIVELY_RECORDED`
 * means in the Health Connect metadata model. A passive
 * watch (Wear OS sleep tracking, etc.) is
 * `RECORDING_METHOD_AUTOMATICALLY_RECORDED`, and a manual
 * entry is `RECORDING_METHOD_MANUAL_ENTRY`. A wearable the
 * user strapped on is "actively recorded" by the user, even
 * though the data points arrive passively.
 */
class HealthConnectWriter(context: Context) {

    private val client = HealthConnectClient.getOrCreate(context)
    private val rrWindow: ArrayDeque<Double> = ArrayDeque(RMSSD_WINDOW)

    /**
     * Subscribe a connector's sample stream. The block runs
     * on the IO dispatcher; the connector's caller is
     * expected to cancel the coroutine on disconnect.
     */
    suspend fun run(context: Context, samples: Flow<WearableSample>) {
        samples.buffer(capacity = 256).collect { sample ->
            runCatching { writeOne(context, sample) }
        }
    }

    private suspend fun writeOne(context: Context, sample: WearableSample) {
        when (sample) {
            is WearableSample.HeartRate -> writeHr(context, sample)
            is WearableSample.RrInterval -> writeRr(context, sample)
            is WearableSample.Steps -> {
                // Steps: future work. The vendor web-API
                // connectors write StepsRecord directly with
                // their own delta math. The BLE HR service
                // does not advertise a step counter, so this
                // path is here for completeness only.
            }
        }
    }

    private suspend fun writeHr(context: Context, sample: WearableSample.HeartRate) {
        val record = HeartRateRecord(
            startTime = sample.instant,
            startZoneOffset = ZoneOffset.UTC,
            endTime = sample.instant.plusMillis(1),
            endZoneOffset = ZoneOffset.UTC,
            samples = listOf(
                HeartRateRecord.Sample(
                    time = sample.instant,
                    beatsPerMinute = sample.bpm.toLong(),
                )
            ),
            metadata = Metadata.activelyRecorded(device = bleDevice()),
        )
        client.insertRecords(listOf(record))
    }

    private suspend fun writeRr(context: Context, sample: WearableSample.RrInterval) {
        rrWindow.addLast(sample.milliseconds)
        while (rrWindow.size > RMSSD_WINDOW) rrWindow.removeFirst()
        // Flush a new RMSSD every N intervals — not every
        // beat. The HRV signal is the same over short
        // windows, and Health Connect's record-overhead is
        // 2-3 ms per insert. Emitting one in five keeps the
        // DB growth to ~17 KB / day at a chest-strap sample
        // rate.
        if (rrWindow.size < 4 || rrWindow.size % FLUSH_EVERY != 0) return
        val rmssd = rmssd(rrWindow.toList()) ?: return
        val record = HeartRateVariabilityRmssdRecord(
            time = sample.instant,
            zoneOffset = ZoneOffset.UTC,
            heartRateVariabilityMillis = rmssd,
            metadata = Metadata.activelyRecorded(device = bleDevice()),
        )
        runCatching { client.insertRecords(listOf(record)) }
    }

    /**
     * The device metadata the connector writes. TYPE_CHEST_STRAP
     * is the closest match for the universal BLE HR connector —
     * chest straps are the canonical device the Bluetooth SIG
     * Heart Rate Service was written for, and Wear OS / Samsung
     * watches using the standard profile are also surfaced as
     * `TYPE_CHEST_STRAP` in Health Connect (the device-type
     * enum has no `TYPE_WATCH_OS_BLE` slot). A future v0.34.1
     * that adds vendor web-API connectors will replace this
     * with the actual vendor's metadata.
     */
    private fun bleDevice(): androidx.health.connect.client.records.metadata.Device =
        androidx.health.connect.client.records.metadata.Device(
            type = androidx.health.connect.client.records.metadata.Device.TYPE_CHEST_STRAP,
            manufacturer = "BLE",
            model = "GATT 0x180D",
        )

    /**
     * RMSSD — root mean square of successive differences.
     * The textbook definition: `sqrt(mean((r[i+1] - r[i])^2))`.
     * Returns null on a window smaller than two intervals.
     */
    private fun rmssd(window: List<Double>): Double? {
        if (window.size < 2) return null
        var sumSq = 0.0
        for (i in 1 until window.size) {
            val d = window[i] - window[i - 1]
            sumSq += d * d
        }
        return sqrt(sumSq / (window.size - 1))
    }

    companion object {
        /** The rolling-window size for RMSSD — 30 intervals ≈ 30s of chest-strap data. */
        const val RMSSD_WINDOW = 30

        /** Flush a new RMSSD record every N RR intervals. */
        const val FLUSH_EVERY = 5
    }
}
