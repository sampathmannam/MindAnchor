package org.mindanchor.watch.connector

/**
 * The state of a single connector's connection to one device.
 *
 * Sealed so the data-sources card can render each state with
 * a stable affordance: "Connect" for [Disconnected], "Cancel"
 * for [Connecting], a status line for [Connected], an error
 * pill for [Failed]. The string payloads are user-facing —
 * they go through [androidx.compose.ui.res.stringResource]
 * before the card paints them, so the connector never puts
 * raw exception text in the [reason] field.
 */
sealed interface ConnectionState {

    /** The connector has not tried to talk to a device yet. */
    data object Idle : ConnectionState

    /** The user (or auto-pair) asked the connector to open a connection. */
    data class Connecting(val vendorId: String, val deviceAddress: String) :
        ConnectionState

    /** GATT handshake completed and the notify subscriptions are live. */
    data class Connected(
        val vendorId: String,
        val deviceAddress: String,
        val deviceName: String,
    ) : ConnectionState

    /** A connection attempt or a live connection ended. */
    data class Disconnected(
        val vendorId: String,
        val deviceAddress: String,
    ) : ConnectionState

    /** The connector could not start, could not connect, or the GATT link dropped. */
    data class Failed(
        val vendorId: String,
        val deviceAddress: String,
        /** A short, user-facing reason — never an exception message. */
        val reason: String,
    ) : ConnectionState
}
