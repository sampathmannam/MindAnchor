package org.mindanchor.watch.connector

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.mindanchor.vitals.polar.PolarAuth
import org.mindanchor.vitals.polar.PolarConnectionState

/**
 * v0.35.0: the Polar AccessLink connector. Implements
 * the [SmartwatchConnector] interface for the Polar web
 * bridge.
 *
 * Unlike the BLE HR connector (which is a per-device
 * GATT stream), the Polar web bridge is per-user. The
 * "discovered device" list is therefore a single fixed
 * entry: "Polar Flow account". The connect step is the
 * OAuth2 sign-in flow (see [PolarAuth] + the
 * `mindanchor://polar-oauth-callback` deep link).
 *
 * The samples Flow is empty — the Polar data does not
 * flow through this connector; it flows through
 * [org.mindanchor.vitals.polar.PolarVitalSource] on
 * the periodic worker tick. The connector is here so
 * the [SmartwatchRegistry] roster can show "Polar" as
 * an available wearable source, the user can see the
 * sign-in state, and the home data-sources card has
 * one row to render.
 */
class PolarAccessLinkConnector : SmartwatchConnector {

    override val vendorId: String = VENDOR_ID
    override val displayName: String = "Polar Flow"

    override fun availability(context: Context): SmartwatchConnector.Availability {
        val auth = PolarAuth(context.applicationContext)
        return when (auth.connectionState()) {
            is PolarConnectionState.Connected ->
                SmartwatchConnector.Availability.Ready
            is PolarConnectionState.TokenExpired ->
                SmartwatchConnector.Availability.unavailable("Sign in again to refresh the token")
            is PolarConnectionState.NotConnected ->
                SmartwatchConnector.Availability.unavailable("Sign in to connect")
        }
    }

    /**
     * "Discovery" for the Polar web bridge is the single
     * fixed entry: the user's Polar Flow account, once
     * they have signed in. The Flow emits the current
     * connection state as one element; a fresh sign-in
     * is the way to "discover" a different account.
     */
    override fun discover(context: Context): Flow<List<DiscoveredDevice>> = flow {
        val auth = PolarAuth(context.applicationContext)
        val email = (auth.connectionState() as? PolarConnectionState.Connected)?.email
            ?: (auth.connectionState() as? PolarConnectionState.TokenExpired)?.email
        val list = if (email != null) {
            listOf(
                DiscoveredDevice(
                    vendorId = VENDOR_ID,
                    displayName = "Polar Flow — $email",
                    address = email,
                    rssi = null,
                    isPaired = true,
                ),
            )
        } else {
            emptyList()
        }
        emit(list)
    }

    /**
     * The Polar connector does not have a per-device
     * "connect" step in the same way the BLE HR
     * connector does. The OAuth2 sign-in flow is
     * initiated from the settings UI (a Custom Tab
     * deep link); the redirect handler is what writes
     * the token. This method just exposes the
     * connection state as a Flow.
     */
    override fun connect(context: Context, deviceAddress: String): Flow<ConnectionState> = flow {
        val auth = PolarAuth(context.applicationContext)
        emit(ConnectionState.Connected(vendorId = VENDOR_ID, deviceAddress = deviceAddress, deviceName = "Polar Flow — $deviceAddress"))
        // The Flow is short-lived: the Polar web bridge
        // has no live sample stream; the worker pulls on
        // a 6h cadence. The connector exposes the
        // connection so the registry can render the row
        // as "connected" without holding a sample pipe.
    }

    /**
     * The Polar web bridge does not produce live
     * samples. The periodic worker is the read path.
     */
    override fun samples(context: Context, deviceAddress: String): Flow<WearableSample> = flow {
        // no-op
    }

    override suspend fun disconnect(context: Context, deviceAddress: String) {
        val auth = PolarAuth(context.applicationContext)
        auth.disconnect()
    }

    override val autoReconnect: Boolean = true

    companion object {
        const val VENDOR_ID = "polar"
    }
}
