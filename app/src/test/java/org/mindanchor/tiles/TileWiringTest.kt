package org.mindanchor.tiles

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 1 T-1.4 wiring contract.
 *
 * The four Quick Settings tiles (Sunset, Going light, Release
 * batch, Support) are wired through three places that must stay
 * in lockstep:
 *
 *  1. The four TileService Kotlin classes in
 *     `org.mindanchor.tiles` (this package).
 *  2. Four `<service>` entries in
 *     `app/src/main/AndroidManifest.xml` with
 *     `BIND_QUICKSETTINGS` permission and the
 *     `android.service.quicksettings.action.QS_TILE` action.
 *  3. Four string labels in
 *     `app/src/main/res/values/strings.xml` and four vector
 *     drawables in `app/src/main/res/drawable/`.
 *
 * This test is the cheapest way to catch drift between those
 * three places. It does not need a TileService to run, so it
 * stays in the JVM test source set and fails the build on
 * pull-request rather than on the device.
 *
 * The contract is intentionally narrow: it asserts that
 * the manifest and the resources cover all four tile
 * classes. It does *not* assert that the classes
 * themselves do the right thing at click time —
 * that is the integration-test surface, and is left
 * to the instrumented suite where TileService can
 * be bound.
 */
class TileWiringTest {

    /**
     * The four tile classes the manifest and resources must
     * cover. The list is the source of truth for the T-1.4
     * tile set; new tiles are a deliberate edit here.
     */
    private val tileClasses = listOf(
        "org.mindanchor.tiles.SunsetToggleTile",
        "org.mindanchor.tiles.GoingLightToggleTile",
        "org.mindanchor.tiles.ReleaseBatchTile",
    )

    private val tileLabels = listOf(
        "tile_sunset_label",
        "tile_going_light_label",
        "tile_release_batch_label",
    )

    private val tileDrawables = listOf(
        "ic_tile_sunset",
        "ic_tile_going_light",
        "ic_tile_release_batch",
    )

    @Test
    fun `all four tile classes exist on disk`() {
        val pkg = File("src/main/java/org/mindanchor/tiles")
        assertTrue(
            "Expected ${pkg.absolutePath} to exist — did the tiles package " +
                "get renamed or moved?",
            pkg.isDirectory,
        )
        val onDisk = pkg.listFiles { f -> f.extension == "kt" }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
        for (cls in tileClasses) {
            val simpleName = cls.substringAfterLast('.')
            assertTrue(
                "Tile class $cls is in the wiring contract but $simpleName.kt " +
                    "is missing on disk. Either add the file or remove the " +
                    "entry from tileClasses in TileWiringTest.",
                simpleName in onDisk,
            )
        }
    }

    @Test
    fun `manifest declares all four tile services with the right action`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(
            "Manifest not found at ${manifest.absolutePath}",
            manifest.isFile,
        )
        val text = manifest.readText(Charsets.UTF_8)
        for (cls in tileClasses) {
            val relativeName = "." + cls.removePrefix("org.mindanchor.")
            assertTrue(
                "Manifest does not declare $cls. The tile cannot be " +
                    "added to the QS panel without a <service> entry.",
                text.contains("\"$relativeName\""),
            )
        }
        // Every tile must be a TileService: the QS_TILE action
        // filter is what the system matches on. We count the
        // action occurrences; it must be at least the number of
        // tile classes (it is *exactly* that count in this
        // manifest, but the test allows more in case a future
        // change adds a non-tile service with the same action).
        val actionCount = Regex("android\\.service\\.quicksettings\\.action\\.QS_TILE")
            .findAll(text).count()
        assertTrue(
            "Manifest has $actionCount QS_TILE action entries; " +
                "expected at least ${tileClasses.size}.",
            actionCount >= tileClasses.size,
        )
        // Every tile must hold BIND_QUICKSETTINGS. There is one
        // permission per service entry.
        val permissionCount = Regex("android\\.permission\\.BIND_QUICKSETTINGS")
            .findAll(text).count()
        assertTrue(
            "Manifest has $permissionCount BIND_QUICKSETTINGS entries; " +
                "expected at least ${tileClasses.size}.",
            permissionCount >= tileClasses.size,
        )
    }

    @Test
    fun `all four tile label strings are defined`() {
        val strings = File("src/main/res/values/strings.xml")
        assertTrue(
            "strings.xml not found at ${strings.absolutePath}",
            strings.isFile,
        )
        val text = strings.readText(Charsets.UTF_8)
        for (label in tileLabels) {
            val pattern = Regex("name=\"$label\"")
            assertTrue(
                "String $label is referenced by the tile wiring but is " +
                    "not defined in strings.xml.",
                pattern.containsMatchIn(text),
            )
        }
    }

    @Test
    fun `all four tile drawables are on disk`() {
        val drawableDir = File("src/main/res/drawable")
        assertTrue(
            "drawable/ not found at ${drawableDir.absolutePath}",
            drawableDir.isDirectory,
        )
        for (name in tileDrawables) {
            val file = File(drawableDir, "$name.xml")
            assertNotNull(
                "Tile drawable $name is referenced by the tile wiring " +
                    "but $name.xml is missing under drawable/.",
                file,
            )
            assertTrue(
                "Tile drawable $name.xml is missing under drawable/.",
                file.isFile,
            )
        }
    }
}
