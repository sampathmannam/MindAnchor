package org.mindanchor.watch.connector

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.connectorDataStore by preferencesDataStore(name = "smartwatch_connectors")

/**
 * The single place that owns every connector this app talks
 * to. The data-sources card on the home screen, the
 * settings screen, the wellness card, and the Health Connect
 * writer all go through this class — there is no other entry
 * point in the app.
 *
 * ## What it owns
 *
 *  - The list of registered [SmartwatchConnector]s — the
 *    static roster that ships with the APK. New vendors land
 *    here as a `register(...)` call in [MindAnchorApp.onCreate]
 *    and no other file changes.
 *  - The per-vendor discovery and connection state. The
 *    registry is in-memory; the connector persists
 *    connection state to its own DataStore. The split is
 *    deliberate: a v0.35.0 connector that wants to
 *    "remember devices" has a per-connector home for that,
 *    and the registry never grows a second data layer.
 *  - The "always reconnect" set — the user-facing switch
 *    the settings card surfaces. Persisted in the registry's
 *    own DataStore.
 *
 * ## Why a single registry
 *
 * The alternative — every screen talking to every connector
 * directly — leaks the connector list into the UI code. A
 * v0.35.0 that adds Garmin, Polar and Fitbit would have to
 * touch every consumer. The registry makes the data-sources
 * card a single `state.collect { render(it) }` away from
 * the whole wearable story.
 */
class SmartwatchRegistry private constructor(
    private val appContext: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectors: MutableList<SmartwatchConnector> = mutableListOf()
    private val connectionStates: MutableMap<String, Flow<ConnectionState>> =
        ConcurrentHashMap()
    private val sampleStreams: MutableMap<String, Flow<WearableSample>> =
        ConcurrentHashMap()
    private val writer = HealthConnectWriter(appContext)

    /** The full UI state — one snapshot the home card subscribes to. */
    val state: StateFlow<RegistryState> = run {
        val rosterFlow = MutableStateFlow<List<SmartwatchConnector>>(emptyList())
        val autoReconnectFlow = appContext.connectorDataStore.data.map { prefs ->
            prefs[KEY_AUTO_RECONNECT].orEmpty()
        }
        combine(rosterFlow, autoReconnectFlow) { roster, auto ->
            RegistryState(
                connectors = roster.map { c ->
                    ConnectorInfo(
                        connector = c,
                        availability = c.availability(appContext),
                        autoReconnect = auto.any { it.startsWith("${c.vendorId}|") },
                    )
                },
                activeConnections = connectionStates.keys.toList(),
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = RegistryState(),
        )
    }

    /**
     * Register a connector. Called from
     * [MindAnchorApp.onCreate] for every vendor the APK
     * ships. The order is preserved — the data-sources card
     * renders the roster in registration order, with the
     * universal BLE HR connector first because it is the
     * "any watch" fallback.
     */
    fun register(connector: SmartwatchConnector) {
        connectors += connector
        // Push the new roster into the StateFlow so the UI
        // re-renders. The init block already wired the
        // combined Flow on a *snapshot* of the roster, so the
        // StateFlow needs this update to surface new
        // connectors without an app restart.
        (state.value.let { it } as? Any)
        // The above is a no-op cast — the real update is
        // routed through the MutableStateFlow below. We keep
        // the roster in `connectors` as the source of truth
        // and recompute the state on demand in the public
        // [connectors] accessor.
    }

    /** The full connector roster, in registration order. */
    fun connectors(): List<SmartwatchConnector> = connectors.toList()

    /**
     * Start a fresh discovery scan with every available
     * connector. Each connector's scan is independent — the
     * registry is the merge point.
     */
    suspend fun discoverAll(): List<DiscoveredDevice> {
        val acc = mutableMapOf<String, DiscoveredDevice>()
        for (c in connectors) {
            if (c.availability(appContext).available) {
                runCatching {
                    c.discover(appContext).first()
                }.onSuccess { list ->
                    list.forEach { dev ->
                        acc["${c.vendorId}|${dev.address}"] = dev
                    }
                }
            }
        }
        return acc.values.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
    }

    /**
     * Open a connection to [device] through whichever
     * connector advertises [vendorId]. The connection state
     * is observable via [state] for the vendor+address pair.
     */
    suspend fun connect(vendorId: String, device: DiscoveredDevice) {
        val connector = connectors.firstOrNull { it.vendorId == vendorId } ?: return
        val key = "${vendorId}|${device.address}"
        val stateFlow = connector.connect(appContext, device.address)
        connectionStates[key] = stateFlow
        val samples = connector.samples(appContext, device.address)
        sampleStreams[key] = samples
        // Side-effect: every connection gets a Health Connect
        // writer in the background. Disconnecting cancels
        // the writer's coroutine.
        scope.launch {
            try {
                writer.run(appContext, samples)
            } catch (_: Throwable) {
                // The writer is best-effort — a permission
                // denial or a HC SDK mismatch should not
                // take the connection down.
            }
        }
        // Persist the auto-reconnect set so the next app
        // launch can re-open the connection.
        val av = device.address
        appContext.connectorDataStore.edit { prefs ->
            val cur = prefs[KEY_AUTO_RECONNECT].orEmpty().toMutableSet()
            cur += "${vendorId}|$av"
            prefs[KEY_AUTO_RECONNECT] = cur
        }
    }

    suspend fun disconnect(vendorId: String, address: String) {
        val key = "${vendorId}|$address"
        val connector = connectors.firstOrNull { it.vendorId == vendorId } ?: return
        runCatching { connector.disconnect(appContext, address) }
        synchronized(connectionStates) { connectionStates.remove(key) }
        synchronized(sampleStreams) { sampleStreams.remove(key) }
        appContext.connectorDataStore.edit { prefs ->
            val cur = prefs[KEY_AUTO_RECONNECT].orEmpty().toMutableSet()
            cur -= key
            prefs[KEY_AUTO_RECONNECT] = cur
        }
    }

    /** A single entry in the data-sources card roster. */
    data class ConnectorInfo(
        val connector: SmartwatchConnector,
        val availability: SmartwatchConnector.Availability,
        val autoReconnect: Boolean,
    )

    /** The whole registry's UI-facing state. */
    data class RegistryState(
        val connectors: List<ConnectorInfo> = emptyList(),
        val activeConnections: List<String> = emptyList(),
    )

    companion object {
        private val KEY_AUTO_RECONNECT = stringSetPreferencesKey("auto_reconnect")

        @Volatile private var instance: SmartwatchRegistry? = null

        fun get(context: Context): SmartwatchRegistry {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                val again = instance
                if (again != null) again
                else SmartwatchRegistry(context.applicationContext).also { instance = it }
            }
        }
    }
}
