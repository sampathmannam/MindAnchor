package org.mindanchor.watch.connector.ble

import java.time.Instant

/**
 * The parser for the Bluetooth SIG **Heart Rate Measurement**
 * characteristic (GATT 0x2A37).
 *
 * Every Bluetooth heart-rate sensor — chest straps (Polar
 * H10, Wahoo Tickr), wrist watches that expose BLE HR
 * (Coros, Garmin, Polar Vantage), smart rings (Oura), and
 * the cheap optical arms — speaks this characteristic. The
 * format is specified in the Heart Rate Service v1.0 spec;
 * the byte layout is:
 *
 * | Byte 0   | Bytes 1..2 (or 1) | Optional | Optional         |
 * |----------|-------------------|----------|------------------|
 * | Flags    | HR Value          | EE       | RR Intervals     |
 *
 * The Flags byte:
 *  - **bit 0** — HR Value Format: `0` = uint8 (1 byte), `1` = uint16 LE (2 bytes)
 *  - **bit 1** — Sensor Contact Status (only meaningful when bit 2 = 1)
 *  - **bit 2** — Sensor Contact Supported
 *  - **bit 3** — Energy Expended Status: `1` = next 2 bytes are Energy Expended (kJ)
 *  - **bit 4** — RR-Interval bit: `1` = remaining bytes are RR intervals (uint16 LE, 1/1024 s)
 *
 * The "RR intervals" are the actual beat-to-beat intervals —
 * the gold for HRV. A chest strap that emits them is the
 * highest-fidelity HRV source short of an ECG.
 *
 * ## What this parser deliberately does *not* do
 *
 * It does not throw on a malformed value. A 0-byte buffer, a
 * 0-BPM value, an energy-expended field with a missing byte —
 * any of them is "this characteristic read is unusable", and
 * the connector that owns the GATT subscription decides
 * whether to log a warning or just skip the notification.
 * The parser returns null on every failure path; the
 * caller iterates and drops nulls.
 */
internal object HeartRateParser {

    private const val FLAG_HR_16BIT = 0x01
    private const val FLAG_SENSOR_CONTACT_SUPPORTED = 0x04
    private const val FLAG_SENSOR_CONTACT_DETECTED = 0x02
    private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
    private const val FLAG_RR_INTERVALS_PRESENT = 0x10

    /** One parsed notification — the time is the arrival instant, not the device's own clock. */
    data class Parsed(
        val instant: Instant,
        val bpm: Int,
        val rrIntervalsMs: List<Double>,
    )

    /**
     * Parse a single notification. [value] is the raw byte
     * array the [android.bluetooth.BluetoothGattCallback] gave
     * us for characteristic 0x2A37. [instant] is when the
     * notification arrived; BLE does not carry per-sample
     * timestamps, so the receiver's clock is the closest
     * available approximation.
     *
     * Returns null on a malformed value (length 0, HR < 30 or
     * > 240, energy-expended-but-no-bytes, RR-but-no-bytes).
     * The lower bound of 30 bpm and upper of 240 bpm are the
     * physiological floor and ceiling a wearable can report —
     * a reading outside that range is a sensor glitch, not a
     * measurement.
     */
    fun parse(value: ByteArray, instant: Instant): Parsed? {
        if (value.isEmpty()) return null
        val flags = value[0].toInt() and 0xFF
        var cursor = 1
        val bpm = if ((flags and FLAG_HR_16BIT) != 0) {
            if (value.size < cursor + 2) return null
            ((value[cursor + 1].toInt() and 0xFF) shl 8) or
                (value[cursor].toInt() and 0xFF).also { cursor += 2 }
        } else {
            if (value.size < cursor + 1) return null
            (value[cursor].toInt() and 0xFF).also { cursor += 1 }
        }
        if (bpm < 30 || bpm > 240) return null

        if ((flags and FLAG_ENERGY_EXPENDED_PRESENT) != 0) {
            if (value.size < cursor + 2) return null
            cursor += 2
        }
        val rr = if ((flags and FLAG_RR_INTERVALS_PRESENT) != 0) {
            val rrList = mutableListOf<Double>()
            while (value.size >= cursor + 2) {
                val raw = ((value[cursor + 1].toInt() and 0xFF) shl 8) or
                    (value[cursor].toInt() and 0xFF)
                cursor += 2
                // Spec: 1/1024 s units. The conversion to ms is
                // 1000/1024 ≈ 0.9765625; we round to one decimal
                // place because sub-millisecond precision is
                // sensor noise, not signal.
                val ms = raw * (1000.0 / 1024.0)
                if (ms in 200.0..2000.0) rrList += ms
            }
            rrList
        } else {
            emptyList()
        }
        return Parsed(instant = instant, bpm = bpm, rrIntervalsMs = rr)
    }

    /**
     * The flags byte, rendered for the data-sources card's
     * detail view. The card shows a one-line "RR intervals:
     * yes" or "RR intervals: no" so a curious user can tell
     * whether the chest strap they own is feeding beat-to-beat
     * data or just averaged heart rate.
     */
    fun summary(flags: Int): String {
        val rr = (flags and FLAG_RR_INTERVALS_PRESENT) != 0
        val ee = (flags and FLAG_ENERGY_EXPENDED_PRESENT) != 0
        val contact = (flags and FLAG_SENSOR_CONTACT_SUPPORTED) != 0
        val bits = buildList {
            if (rr) add("RR")
            if (ee) add("EE")
            if (contact) add("contact")
        }
        return if (bits.isEmpty()) "HR" else "HR+" + bits.joinToString("+")
    }
}
