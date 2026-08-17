package org.mindanchor.watch.connector

/**
 * A single wearable the registry has surfaced to the UI — the
 * result of an auto-discovery scan, a paired-Bluetooth-device
 * poll, or a manual re-scan.
 *
 * The registry may surface the same physical device under
 * several [vendorId]s (a Coros Pace 3 with Health Connect
 * permission will show up as both `coros` and `health_connect`).
 * The deduplication is the UI's job: match by MAC address
 * (Android 12+ uses a stable, randomised handle) or, for
 * devices that do not expose one, by the device's
 * [displayName].
 *
 * [rssi] is the last signal strength in dBm; the UI uses it to
 * sort the discovery list with the closest device first, but
 * no path in the launcher rejects a device for being too far
 * away — that is a connection-time concern the connector's
 * GATT client handles.
 */
data class DiscoveredDevice(
    val vendorId: String,
    val displayName: String,
    val address: String,
    val rssi: Int?,
    val isPaired: Boolean,
)
