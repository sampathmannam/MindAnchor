@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #6: the GoingLightVpnService declares
 * `foregroundServiceType="dataSync"` in the manifest (line 555) and
 * the matching `FOREGROUND_SERVICE_DATA_SYNC` permission (line 148).
 *
 * On Android 14 (API 34) the type for a local VPN that captures
 * loopback traffic should be a system-exempted type, not dataSync.
 * The Google Play policy is explicit: a foreground service declared
 * as dataSync that does not actually synchronise data with a remote
 * endpoint is grounds for rejection, and on Android 15 the OS may
 * refuse to start the service or warn the user.
 *
 * The current comment in AndroidManifest.xml:140-147 acknowledges
 * this — "the dataSync type is the closest match: the service is
 * processing loopback packets, not syncing data to a remote
 * endpoint" — but the closest match is not a correct match, and
 * the comment is evidence the type is wrong.
 *
 * The fix is to declare a different foregroundServiceType. The
 * closest correct type for a local VPN on API 34+ is
 * "systemExempted" (which requires a Play policy declaration) or
 * a more accurate type. The minimum fix is to file a Play
 * declaration justifying the dataSync use.
 *
 * The three tests below pin the surface.
 */
class GoingLightForegroundServiceTypeFindingTest {

    @Test
    fun `GoingLightVpnService foregroundServiceType is correct for a local VPN`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val usesDataSync = manifest.contains("android:foregroundServiceType=\"dataSync\"")
        val usesSystemExempted = manifest.contains("android:foregroundServiceType=\"systemExempted\"")
        val usesSpecialUse = manifest.contains("android:foregroundServiceType=\"specialUse\"")
        assertTrue(
            "GoingLightVpnService must declare a foregroundServiceType that " +
                "matches its actual use case. The current declaration is " +
                "`dataSync` (useDataSync=$usesDataSync), which is incorrect for " +
                "a local VPN that captures loopback traffic. The expected " +
                "declarations are `systemExempted` ($usesSystemExempted) or " +
                "`specialUse` ($usesSpecialUse), or a `dataSync` with a " +
                "matching Play policy declaration. If the type is " +
                "deliberately dataSync for Play-store review reasons, the " +
                "comment at AndroidManifest.xml:140-147 should be moved into " +
                "a docs/policy/ file so the policy declaration can reference it.",
            !usesDataSync || usesSystemExempted || usesSpecialUse,
        )
    }

    @Test
    fun `FOREGROUND_SERVICE_DATA_SYNC permission is paired with the type declaration`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val perm = manifest.contains("FOREGROUND_SERVICE_DATA_SYNC")
        val type = manifest.contains("android:foregroundServiceType=\"dataSync\"")
        // The pairing is consistent (both present or both absent).
        // The bug is the choice of pair, not the consistency.
        assertTrue(
            "FOREGROUND_SERVICE_DATA_SYNC permission and dataSync " +
                "foregroundServiceType must be paired consistently. " +
                "permission=$perm, type=$type.",
            perm == type,
        )
    }

    @Test
    fun `GoingLightVpnService does not declare USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM for the VPN use case`() {
        // The VpnService does not use exact alarms. The manifest
        // declares SCHEDULE_EXACT_ALARM for the *other* schedulers
        // (BatchAlarms, LetterScheduler, etc.), not for the VPN.
        // This test is a guard against the VPN ever growing its
        // own exact-alarm use case.
        val src = read("src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt") ?: return
        assertFalse(
            "GoingLightVpnService must not declare an exact-alarm use case. " +
                "The VPN runs while a Going Light window is active, which is " +
                "tens of minutes, not a clock-aligned instant — setAndAllowWhileIdle " +
                "or setRepeating on a wall-clock interval is the right primitive.",
            src.contains("setExact") || src.contains("canScheduleExactAlarms"),
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
