package org.mindanchor.watch.connector.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mindanchor.watch.connector.ConnectionState
import org.mindanchor.watch.connector.DiscoveredDevice
import org.mindanchor.watch.connector.SmartwatchConnector
import org.mindanchor.watch.connector.WearableSample

/**
 * The universal BLE Heart-Rate connector.
 *
 * The Bluetooth SIG **Heart Rate Service** (UUID 0x180D) is the
 * single GATT service every Bluetooth heart-rate sensor
 * advertises: chest straps (Polar H10, Wahoo Tickr, Garmin HRM
 * Dual), wrist watches that expose the standard profile
 * (Coros, Garmin, Polar Vantage, Suunto), smart rings (Oura
 * on the experimental BLE bridge), and the cheap optical arms
 * on AliExpress. A connector that speaks only 0x180D and 0x2A37
 * therefore talks to *any* of them — which is the "connect to
 * any smart watch" promise, narrowed to the signals the
 * service carries (instantaneous heart rate, RR intervals).
 *
 * ## What this connector does *not* cover
 *
 *  - Sleep, steps, calories, training load — the BLE HR
 *    service does not carry them. They come from Health
 *    Connect (already wired) and the vendor web APIs (Garmin
 *    Connect, Polar AccessLink, Fitbit) that a v0.35.0 build
 *    layers in next.
 *  - Continuous background streaming — a real production
 *    "always-on HR" needs a foreground service and a sticky
 *    GATT connection. The v0.34.0 scope is the on-demand
 *    path: the user opens the data-sources card or the
 *    settings screen, sees the live BPM, and the registry
 *    flushes the latest sample to Health Connect on a
 *    one-minute debounce. The foreground service is a
 *    v0.35.0 follow-up.
 *
 * ## Why no third-party BLE library
 *
 * The Android BluetoothGatt API is the only API that
 * guarantees compatibility with the platform's
 * permission-gate, the new "companion device" pairing
 * dialog (Android 13+), and the LE Audio coexistence rules
 * that arrived with API 33. A wrapper like
 * Nordic-BLE / RxBluetoothLE / RxAndroidBle would buy a
 * friendlier callback surface at the cost of an extra 1-2
 * MB in the APK and a transitive dep on a non-Apache
 * library. The 600 lines below are the entire surface this
 * connector needs and they map 1:1 to the platform docs.
 */
class GenericBleHrConnector : SmartwatchConnector {

    override val vendorId: String = VENDOR_ID
    override val displayName: String = "BLE heart rate"

    /** A class-wide state table: one entry per connected address. */
    private val connections = mutableMapOf<String, Connection>()

    override fun availability(context: Context): SmartwatchConnector.Availability {
        if (!BlePermissions.isGranted(context)) {
            return SmartwatchConnector.Availability.unavailable(
                "Tap to grant Bluetooth access",
            )
        }
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager
        if (mgr == null) {
            return SmartwatchConnector.Availability.unavailable(
                "No Bluetooth radio on this device",
            )
        }
        val adapter: BluetoothAdapter? = mgr.adapter
        if (adapter == null) {
            return SmartwatchConnector.Availability.unavailable(
                "No Bluetooth radio on this device",
            )
        }
        if (!adapter.isEnabled) {
            return SmartwatchConnector.Availability.unavailable(
                "Turn Bluetooth on in system settings",
            )
        }
        return SmartwatchConnector.Availability.Ready
    }

    /**
     * One-shot BLE scan for any device advertising the
     * Heart Rate Service. The Flow emits the *accumulated*
     * list of devices every time a new advertisement
     * arrives, so the UI can show a live "found N more"
     * indicator without restarting the scan. The scan stops
     * when the collector cancels.
     */
    @Suppress("MissingPermission")
    override fun discover(context: Context): Flow<List<DiscoveredDevice>> =
        callbackFlow {
            val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager
            val adapter = mgr?.adapter
            if (adapter == null || !adapter.isEnabled || !BlePermissions.isGranted(context)) {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val scanner = adapter.bluetoothLeScanner ?: run {
                trySend(emptyList())
                close()
                return@callbackFlow
            }
            val seen = mutableMapOf<String, DiscoveredDevice>()
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val dev = result.device
                    val name = runCatching { dev.name }.getOrNull() ?: return
                    val record = result.scanRecord ?: return
                    val hasHr = record.serviceUuids?.any { it.uuid == HR_SERVICE_UUID } == true
                    if (!hasHr) return
                    val address = dev.address
                    val entry = DiscoveredDevice(
                        vendorId = VENDOR_ID,
                        displayName = name,
                        address = address,
                        rssi = result.rssi,
                        isPaired = dev.bondState == BluetoothDevice.BOND_BONDED,
                    )
                    seen[address] = entry
                    trySend(seen.values.sortedByDescending { it.rssi ?: Int.MIN_VALUE })
                }

                override fun onScanFailed(errorCode: Int) {
                    close(ScanFailedException(errorCode))
                }
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HR_SERVICE_UUID))
                .build()
            scanner.startScan(listOf(filter), settings, callback)
            awaitClose { runCatching { scanner.stopScan(callback) } }
        }.flowOn(Dispatchers.Default)

    /**
     * Open a GATT connection to [deviceAddress]. The Flow
     * emits the state transitions and stays open until
     * the connection drops or the collector cancels.
     */
    @Suppress("MissingPermission")
    override fun connect(
        context: Context,
        deviceAddress: String,
    ): Flow<ConnectionState> = callbackFlow {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager
        val adapter = mgr?.adapter
        if (adapter == null || !BlePermissions.isGranted(context)) {
            trySend(
                ConnectionState.Failed(
                    vendorId = VENDOR_ID,
                    deviceAddress = deviceAddress,
                    reason = "Bluetooth unavailable",
                )
            )
            close()
            return@callbackFlow
        }
        runCatching {
            adapter.getRemoteDevice(deviceAddress)
        }.onFailure {
            trySend(
                ConnectionState.Failed(
                    vendorId = VENDOR_ID,
                    deviceAddress = deviceAddress,
                    reason = "Device not found",
                )
            )
            close()
            return@callbackFlow
        }.onSuccess { device ->
            trySend(ConnectionState.Connecting(vendorId = VENDOR_ID, deviceAddress = deviceAddress))
            // Auto-pair the device if it is not already bonded;
            // the GATT connect call below will request the
            // pairing dialog and the user is presented with
            // Android's standard "Pair with this device?" UI.
            val deviceName = runCatching { device.name }.getOrNull().orEmpty()
            val connection = Connection(
                device = device,
                state = MutableStateFlow<ConnectionState>(
                    ConnectionState.Connecting(VENDOR_ID, deviceAddress)
                ),
                samples = MutableSharedFlow(extraBufferCapacity = 64),
            )
            synchronized(connections) {
                connections[deviceAddress] = connection
            }
            val callback = buildGattCallback(connection, deviceAddress, deviceName)
            // autoConnect = false: the user picked this device
            // and expects the connection to come up within a
            // few seconds. true would make the system retry in
            // the background and is what the v0.35.0
            // foreground-service path uses.
            val gatt = device.connectGatt(context, false, callback)
            connection.gatt = gatt
            // Forward the state changes to the consumer.
            val job = launch {
                connection.state.collect { trySend(it) }
            }
            awaitClose {
                job.cancel()
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
                synchronized(connections) {
                    connections.remove(deviceAddress)
                }
            }
        }
    }

    /**
     * The sample stream for an open connection. Returns an
     * empty Flow when the address is unknown — the caller
     * should be a downstream of [connect] and only subscribe
     * after [ConnectionState.Connected] arrives.
     */
    override fun samples(context: Context, deviceAddress: String): Flow<WearableSample> = flow {
        val connection = synchronized(connections) { connections[deviceAddress] }
            ?: return@flow
        connection.samples.collect { emit(it) }
    }

    @Suppress("MissingPermission")
    override suspend fun disconnect(context: Context, deviceAddress: String) {
        val connection = synchronized(connections) { connections.remove(deviceAddress) }
            ?: return
        runCatching { connection.gatt?.disconnect() }
        runCatching { connection.gatt?.close() }
        connection.state.value = ConnectionState.Disconnected(VENDOR_ID, deviceAddress)
    }

    /**
     * The GATT callback. Six overrides; the ones that matter
     * for HR streaming are [onConnectionStateChange] (the
     * state machine driver) and [onCharacteristicChanged]
     * (the HR notification).
     */
    @Suppress("MissingPermission")
    private fun buildGattCallback(
        connection: Connection,
        deviceAddress: String,
        deviceName: String,
    ): BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connection.state.value = ConnectionState.Connected(
                        vendorId = VENDOR_ID,
                        deviceAddress = deviceAddress,
                        deviceName = deviceName.ifBlank { "BLE device" },
                    )
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connection.state.value = ConnectionState.Disconnected(
                        vendorId = VENDOR_ID,
                        deviceAddress = deviceAddress,
                    )
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val hrService = gatt.getService(HR_SERVICE_UUID) ?: return
            val hrChar = hrService.getCharacteristic(HR_MEASUREMENT_CHAR_UUID) ?: return
            gatt.setCharacteristicNotification(hrChar, true)
            val descriptor = hrChar.getDescriptor(CCCD_UUID) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != HR_MEASUREMENT_CHAR_UUID) return
            val value = characteristic.value ?: return
            val instant = Instant.now()
            val parsed = HeartRateParser.parse(value, instant) ?: return
            // Fire-and-forget on a SupervisorJob so a slow
            // collector cannot back-pressure the GATT thread;
            // the buffer is 64 samples which is ~1 minute of
            // chest-strap data and ample.
            connection.scope.launch {
                connection.samples.tryEmit(
                    WearableSample.HeartRate(
                        vendorId = VENDOR_ID,
                        instant = parsed.instant,
                        bpm = parsed.bpm,
                    )
                )
                parsed.rrIntervalsMs.forEach { rrMs ->
                    connection.samples.tryEmit(
                        WearableSample.RrInterval(
                            vendorId = VENDOR_ID,
                            instant = parsed.instant,
                            milliseconds = rrMs,
                        )
                    )
                }
            }
        }
    }

    /**
     * One live GATT connection. The [scope] is a private
     * SupervisorJob so a slow sample collector never cancels
     * the GATT callback.
     */
    private class Connection(
        val device: BluetoothDevice,
        val state: MutableStateFlow<ConnectionState>,
        val samples: MutableSharedFlow<WearableSample>,
    ) {
        @Volatile var gatt: BluetoothGatt? = null
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    class ScanFailedException(val errorCode: Int) :
        Exception("BLE scan failed (code $errorCode)")

    companion object {
        const val VENDOR_ID = "generic_ble_hr"

        // Bluetooth SIG assigned numbers — see
        // https://www.bluetooth.com/specifications/assigned-numbers/
        // Heart Rate Service: 0x180D
        // Heart Rate Measurement characteristic: 0x2A37
        // Client Characteristic Configuration Descriptor: 0x2902
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        val HR_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
