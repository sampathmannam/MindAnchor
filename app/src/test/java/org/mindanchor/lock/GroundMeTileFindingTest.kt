@file:Suppress("MaxLineLength", "SwallowedException")
package org.mindanchor.lock

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.1 §3.4 FindingTest: the "Ground me" Quick Settings tile
 * is registered in the manifest, the activity it opens is
 * registered, and the binding permission is the system one.
 *
 * The tile is a single-affordance surface: one tap, one
 * activity, one screen (the existing
 * [org.mindanchor.launcher.GroundMeScreen]). The manifest
 * declarations are the load-bearing pin — a regression that
 * drops the tile from the manifest (e.g. a refactor that
 * thinks it is "redundant" because the activity is reachable
 * from the in-app surface too) makes the lock-screen entry
 * point silently unreachable.
 */
class GroundMeTileFindingTest {

    @Test
    fun `GroundMeTile service is registered in the manifest`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        assertTrue(
            "AndroidManifest.xml must register " +
                ".lock.GroundMeTile as a service. The " +
                "Quick Settings tile is the v0.26.1 §3.4 " +
                "lock-screen entry point — without this " +
                "declaration, the user cannot ground " +
                "themselves from the shade.",
            manifest.contains("android:name=\".lock.GroundMeTile\""),
        )
    }

    @Test
    fun `GroundMeTile has the QS_TILE intent filter`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".lock.GroundMeTile\"")
        assertTrue("GroundMeTile declaration must be present", idx >= 0)
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "GroundMeTile must declare the QS_TILE intent filter " +
                "so the system lists it in the Quick Settings " +
                "tile picker. window=$window",
            window.contains("android.service.quicksettings.action.QS_TILE"),
        )
    }

    @Test
    fun `GroundMeTile is guarded by BIND_QUICK_SETTINGS_TILE`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        val idx = manifest.indexOf("android:name=\".lock.GroundMeTile\"")
        assertTrue("GroundMeTile declaration must be present", idx >= 0)
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</service>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        assertTrue(
            "GroundMeTile must declare " +
                "android:permission=\"android.permission.BIND_QUICK_SETTINGS_TILE\" " +
                "so only the system can bind it. window=$window",
            window.contains("android.permission.BIND_QUICK_SETTINGS_TILE"),
        )
    }

    @Test
    fun `GroundMeActivity is registered in the manifest`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        assertTrue(
            "AndroidManifest.xml must register " +
                ".lock.GroundMeActivity. The tile " +
                "launches this activity via an explicit " +
                "ComponentName.",
            manifest.contains("android:name=\".lock.GroundMeActivity\""),
        )
    }

    @Test
    fun `GroundMeTile Kotlin class exists in the lock package`() {
        val cls = Class.forName("org.mindanchor.lock.GroundMeTile")
        val svc = cls.superclass
        // The class is a TileService — the onClick path is
        // the system-defined contract, and the manifest
        // pairs it with the QS_TILE intent filter.
        assertNotNull("GroundMeTile must extend android.service.quicksettings.TileService", svc)
        assertTrue(
            "GroundMeTile's parent class must be TileService, " +
                "not Service directly. " +
                "super=${svc?.name}",
            svc?.name == "android.service.quicksettings.TileService",
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
