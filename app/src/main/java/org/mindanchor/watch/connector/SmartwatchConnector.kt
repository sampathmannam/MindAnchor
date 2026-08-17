package org.mindanchor.watch.connector

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * The single interface every wearable connector implements —
 * the "any smart watch" promise in code.
 *
 * The launcher ships with several concrete connectors
 * ([org.mindanchor.watch.connector.ble.GenericBleHrConnector]
 * is the universal one — any Bluetooth heart-rate sensor with
 * the standard GATT service just works). Vendor-specific
 * connectors (Garmin Connect, Polar AccessLink, Fitbit) plug
 * in the same way. The [SmartwatchRegistry] holds the list of
 * registered connectors, the data-sources card on the home
 * screen surfaces the ones that are available, and the
 * settings screen lets the user pick which one to activate.
 *
 * ## Design contract
 *
 *  1. **Every connector reads via the same [samples] Flow.**
 *     The registry does not know whether a sample came from a
 *     BLE GATT characteristic, a vendor OAuth2 REST call, or
 *     a Health Connect read. The merge step in
 *     [WellnessRepository] consumes one Flow and that is the
 *     only contract the connector owes the rest of the app.
 *  2. **Every connector owns its own permissions and errors.**
 *     [isAvailable] returns false when the connector cannot
 *     run on the current device (no BLE radio, no Health
 *     Connect install, the vendor's app missing). [discover]
 *     and [connect] never throw — failures land in the
 *     [ConnectionState] stream as a [ConnectionState.Failed]
 *     with a user-facing [reason].
 *  3. **Every connector persists its own connection state.**
 *     The registry is in-memory; the connector is the
 *     durable side. The user's "always reconnect" choice
 *     survives a process kill because the connector writes
 *     it to its own DataStore.
 */
interface SmartwatchConnector {

    /**
     * A stable, lowercase identifier — `garmin`, `polar`,
     * `fitbit`, `withings`, `generic_ble_hr`, `health_connect`.
     * The data-sources card shows this as the source of a
     * reading; the registry uses it as the dedup key when the
     * same physical watch surfaces under several connectors.
     */
    val vendorId: String

    /**
     * The label the settings and data-sources cards show.
     * "Garmin Connect", "Polar Flow", "BLE heart rate", etc.
     * No emojis, no abbreviation — the home card has a 14 em
     * column to fit this into.
     */
    val displayName: String

    /**
     * `true` when the connector can run on the current device
     * right now — the radio is on, the vendor app is
     * installed, the runtime permission is granted. The
     * data-sources card filters the list through this gate;
     * the settings screen uses it to grey out an option
     * instead of letting a tap silently fail.
     *
     * `false` does not mean "broken"; it means "try me when
     * the radio is on" or "install Garmin Connect first".
     * The [reason] string is a one-line message for the
     * settings screen, not a developer-facing error.
     */
    fun availability(context: Context): Availability

    /**
     * The list of wearables this connector can see *right
     * now* — the result of a BLE scan, a paired-Bluetooth
     * poll, or a vendor web-API "list devices" call.
     *
     * The Flow is cold: a single subscription runs the scan
     * once and completes. The settings screen calls
     * `flow.take(1)` to do a one-shot scan; auto-discovery
     * keeps the subscription open and re-scans on the
     * 6h-interval the existing Coros worker uses.
     */
    fun discover(context: Context): Flow<List<DiscoveredDevice>>

    /**
     * Open a connection to [deviceAddress] (the stable
     * identifier the discovery step returned — BLE MAC for
     * the generic connector, a vendor device id for web
     * APIs). The Flow emits the state transitions
     * [Connecting] → [Connected] (or [Failed]). A second
     * collector gets the live [samples] Flow.
     */
    fun connect(context: Context, deviceAddress: String): Flow<ConnectionState>

    /**
     * The live sample stream for an open connection. Cold;
     * collector opens it after [connect] emits [Connected].
     * The Flow completes when the connection drops — the
     * collector reacts by surfacing a "disconnected" pill
     * and offering a reconnect.
     */
    fun samples(context: Context, deviceAddress: String): Flow<WearableSample>

    /**
     * Close the connection and forget the device. Idempotent:
     * a call when no connection is open is a no-op, not an
     * error. The data-sources card uses this for the
     * "Disconnect" affordance; the registry uses it on
     * process shutdown to release the BLE GATT handle.
     */
    suspend fun disconnect(context: Context, deviceAddress: String)

    /**
     * Whether the connector's last connection should be
     * restored on the next app launch. The settings screen
     * surfaces this as a switch. `false` by default — the
     * user is opt-in to a persistent connection because
     * BLE is the kind of thing that quietly drains a phone.
     */
    val autoReconnect: Boolean
        get() = false

    /**
     * The pair returned by [availability]. [available] is the
     * bool the data-sources card uses; [reason] is the
     * explanation a one-line settings label can show next to
     * the greyed-out entry ("No Bluetooth radio",
     * "Install Garmin Connect", "Tap to grant Health Connect").
     */
    data class Availability(
        val available: Boolean,
        val reason: String? = null,
    ) {
        companion object {
            /** The connector is fully usable. */
            val Ready = Availability(available = true)

            /**
             * The connector cannot run right now. [reason] is
             * the one-line, user-facing message the settings
             * card paints next to the greyed-out entry.
             */
            fun unavailable(reason: String) = Availability(
                available = false,
                reason = reason,
            )
        }
    }
}
