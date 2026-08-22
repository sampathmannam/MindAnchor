package org.mindanchor.watch.connector

import java.time.Instant

/**
 * A single sample from any connected wearable — the universal
 * type every connector emits and the registry merges.
 *
 * Each sample carries one modality (heart rate, HRV, or step) and
 * the wall-clock [instant] the device sampled it. The [vendorId]
 * is preserved so the data-sources card on the home screen can
 * surface *where today's reading came from* — the watch on the
 * wrist, the watch the user is wearing, or the finger they put
 * on the camera (the PPG sensor the launcher measures itself,
 * kept here for completeness even though it does not flow
 * through the registry).
 *
 * ## Why a single type
 *
 * The five wellness signals on the home card are reduced from
 * many sources, and reducing in a generic way needs a common
 * shape. [HeartRate] is what every wearable emits; [HrvRmssd]
 * is the most useful aggregate; [Steps] closes the loop with
 * the watch's own step counter. RHR is computed as the day's
 * minimum of [HeartRate.samples] plus the standard
 * "lowest-quintile" rule the wellness card already uses; sleep
 * is left to Health Connect and the vendor web APIs because
 * the BLE HR service does not carry it.
 */
sealed interface WearableSample {

    /** The connector that emitted this sample — used for the data-sources card. */
    val vendorId: String

    /** When the device sampled the value. */
    val instant: Instant

    /**
     * An instantaneous heart-rate reading, in beats per minute.
     *
     * The BLE Heart Rate Measurement characteristic (GATT 0x2A37)
     * is the universal source for any Bluetooth heart-rate
     * sensor: chest straps, optical wrist watches, smart rings.
     * The first two bytes are flags + value; the connector that
     * reads from the GATT layer sets the [vendorId] accordingly.
     */
    data class HeartRate(
        override val vendorId: String,
        override val instant: Instant,
        val bpm: Int,
    ) : WearableSample

    /**
     * A single RR-interval reading, in milliseconds. RR is the
     * beat-to-beat interval the BLE spec reports as a
     * little-endian uint16 in units of 1/1024 s; the connector
     * converts to milliseconds before this data class is built.
     *
     * RMSSD is computed downstream by the registry: the rolling
     * root-mean-square of successive differences over the last
     * N intervals, which is exactly the DBT diary-card HRV
     * number the wellness card shows.
     */
    data class RrInterval(
        override val vendorId: String,
        override val instant: Instant,
        val milliseconds: Double,
    ) : WearableSample

    /**
     * A step-count reading. The watch's own step counter — not
     * the launcher walking a step detector — is what the home
     * card prefers when the watch is on the wrist. A step value
     * is a cumulative counter; the [instant] is when the watch
     * emitted it, and the delta to the previous reading is the
     * increment to add to the day's running total.
     */
    data class Steps(
        override val vendorId: String,
        override val instant: Instant,
        val cumulative: Long,
    ) : WearableSample
}
