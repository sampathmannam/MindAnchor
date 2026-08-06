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
    fun theAppCannotReachTheNetworkAtAll() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toSet()

        // No network permission means no server, no analytics and no
        // silent upload is even possible — the guarantee is structural
        // rather than a matter of trusting the code.
        assertFalse(
            "INTERNET would make the zero-backend promise unenforceable",
            android.Manifest.permission.INTERNET in declared,
        )
        assertFalse(
            "ACCESS_NETWORK_STATE is not needed by an offline app",
            android.Manifest.permission.ACCESS_NETWORK_STATE in declared,
        )
    }
}
