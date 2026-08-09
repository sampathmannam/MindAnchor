package org.mindanchor

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The About screen tells people their data never leaves this phone. That
 * is a promise about a safety plan, a list of who to call at their worst,
 * a mood history and the text of held notifications — so it is checked
 * against the app as actually installed, not against the manifest as
 * written. A manifest can be edited without anyone noticing; a failing
 * test cannot.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun theAppIsNotEligibleForCloudBackup() {
        val flags = context.applicationInfo.flags
        assertEquals(
            "allowBackup would copy the safety plan and crisis contacts off the device",
            0,
            flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }

    @Test
    fun theAppHasNoNetworkingPermissionOtherThanVpn() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()

        // Going Light v1.1 (docs/research/18) needs INTERNET
        // because the VpnService API requires it to capture
        // loopback traffic. The local VPN never tunnels
        // anywhere — the only path that has access to the
        // network is the VpnService itself, which is a
        // sinkhole (see GoingLightVpnService KDoc). The
        // privacy promise is therefore not "no INTERNET
        // permission" (which used to be the structural
        // guarantee) but "the only thing the INTERNET
        // permission can be used for is the local VpnService,
        // and the VpnService cannot reach the network." The
        // second half is enforced by NetworkCallsForbiddenTest.
        //
        // What this test now asserts: no other networking
        // permission is declared, so the only way out is via
        // INTERNET, and INTERNET is gated to the VpnService.
        assertFalse(
            "ACCESS_NETWORK_STATE is not needed by an offline app",
            android.Manifest.permission.ACCESS_NETWORK_STATE in declared,
        )
        assertFalse(
            "CHANGE_WIFI_STATE is not needed by an offline app",
            android.Manifest.permission.CHANGE_WIFI_STATE in declared,
        )
        assertFalse(
            "CHANGE_NETWORK_STATE is not needed by an offline app",
            android.Manifest.permission.CHANGE_NETWORK_STATE in declared,
        )
    }
}
