package org.mindanchor.watch.connector.ble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permission gate for the BLE GATT path.
 *
 * The Bluetooth radio on Android 12+ (API 31+) is gated behind
 * `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`, both runtime.
 * The earlier "location required to scan BLE" hack went away
 * with API 31 — `BLUETOOTH_SCAN` carries the `neverForLocation`
 * flag, and the launcher asks for that flag explicitly in the
 * manifest so the user does not get the "allow location to
 * scan" dialog that 2018-era BLE apps were famous for.
 *
 * On API 30 and below the older `BLUETOOTH`, `BLUETOOTH_ADMIN`
 * and `ACCESS_FINE_LOCATION` are the trio, all installation-time
 * — declared in the manifest, not requested at runtime. The
 * [isGranted] check is therefore trivially true on those
 * releases; the call exists to keep the call site uniform.
 *
 * The launcher never asks for these permissions on the user's
 * behalf — that decision is the settings screen's. The
 * data-sources card on home shows "Tap to grant" when the
 * gate is closed, and the settings screen routes the tap to
 * the system permission dialog.
 */
internal object BlePermissions {

    /** The runtime permissions a v0.34.0 launcher needs to talk BLE. */
    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // On API ≤ 30 the only runtime permission is location
            // (legacy BLE scanning was treated as location). The
            // launcher still does the check explicitly so the
            // path is uniform.
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /**
     * `true` when every required runtime permission is granted
     * *right now*. The settings card calls this on every
     * recomposition; the cost is one PackageManager call per
     * permission and is negligible.
     */
    fun isGranted(context: Context): Boolean =
        requiredRuntimePermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }

    /**
     * The list of permissions the settings screen should pass
     * to [Activity.requestPermissions] when the user taps
     * "Grant" on the data-sources card. The list is empty when
     * the gate is already open — the settings card checks
     * [isGranted] first and skips the request entirely in
     * that case.
     */
    fun toRequest(context: Context): Array<String> =
        requiredRuntimePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) !=
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
