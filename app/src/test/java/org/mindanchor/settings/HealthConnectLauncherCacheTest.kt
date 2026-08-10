package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Finding test for the v0.23.0 Health Connect launcher cache fix
 * in [SettingsScreen].
 *
 * v0.22.x called [HealthConnectSource.requestPermissionsContract]
 * inline in the `rememberLauncherForActivityResult` site. The
 * factory returns a *new* ActivityResultContract instance every
 * call. Compose's launcher keys on the contract INSTANCE, so a
 * new contract every recomposition forced the launcher to
 * re-register with the activity's ActivityResultRegistry. On a
 * phone with a slower result pipeline (the Tamil Nadu Police
 * test device sits behind a corporate-managed ActivityManager
 * that buffers dispatches), the onClick lambda drifted from
 * the registered launcher between recomposition and tap, and
 * the click was silently swallowed.
 *
 * The fix caches the contract with `remember { ... }` so the
 * key is stable, the launcher is stable, the click is stable.
 *
 * What this test checks:
 *  1. The contract factory call is inside a `remember { ... }`
 *     block, not inline at the call site.
 *  2. The cached contract is the one passed to
 *     `rememberLauncherForActivityResult`.
 *  3. The launcher variable name is `healthConnectPermissionLauncher`
 *     (matches the existing convention; a refactor that re-uses
 *     the outer `permissionLauncher` would silently bind to the
 *     wrong contract).
 *  4. The fix preserves the existing post-permission callback
 *     that re-reads the status.
 */
class HealthConnectLauncherCacheTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    @Test
    fun `the Health Connect contract factory is wrapped in remember`() {
        // v0.22.x: contract = HealthConnectSource.requestPermissionsContract()
        //   — inline, new instance per recomposition, silent failure.
        // v0.23.0: val healthConnectPermissionContract = remember { ... }
        //   — cached, stable key, click reaches the registered launcher.
        assertTrue(
            "The Health Connect permission contract must be cached in a " +
                "`val ... = remember { ... }` block, NOT called inline at the " +
                "rememberLauncherForActivityResult site. Inline call creates " +
                "a new ActivityResultContract every recomposition, which " +
                "forces the launcher to re-register with the activity's " +
                "ActivityResultRegistry and the click ends up at a launcher " +
                "no longer in the registry. Same shape as the v0.22.1 EMA / " +
                "Batching silent-toggle bug.",
            screen.contains("val healthConnectPermissionContract = remember {") ||
                screen.contains("val healthConnectPermissionContract = remember("),
        )
    }

    /**
     * Locates the Health Connect rememberLauncherForActivityResult
     * call site by its preceding variable name. The file has
     * multiple `rememberLauncherForActivityResult` invocations
     * (the outer activity launcher, the permission launcher, the
     * Health Connect one); only the last is the one we want, and
     * it is preceded by the comment block "The request-permissions
     * launcher. Created here...".
     */
    private fun healthConnectLauncherSlice(width: Int = 2000): String {
        val marker = "val healthConnectPermissionContract = remember {"
        val markerIdx = screen.indexOf(marker)
        if (markerIdx < 0) error(
            "Could not locate the cached `healthConnectPermissionContract` " +
                "val in SettingsScreen.kt — the v0.23.0 fix is not in place.",
        )
        val slice = screen.substring(markerIdx, minOf(markerIdx + width, screen.length))
        return slice
    }

    @Test
    fun `the rememberLauncherForActivityResult site uses the cached contract`() {
        // The fix is incomplete if the contract is cached but never
        // wired to the launcher. The line must read
        // `contract = healthConnectPermissionContract` (the cached val),
        // NOT `contract = HealthConnectSource.requestPermissionsContract()`
        // (the inline factory call, which is the bug).
        val slice = healthConnectLauncherSlice()
        assertTrue(
            "The rememberLauncherForActivityResult site must pass " +
                "`contract = healthConnectPermissionContract` (the cached " +
                "val), not the inline factory call. " +
                "Slice: $slice",
            slice.contains("contract = healthConnectPermissionContract"),
        )
    }

    @Test
    fun `no inline HealthConnectSource requestPermissionsContract call at the launcher site`() {
        // Belt-and-braces: even if the cached val exists for the
        // happy path, a future maintainer adding an inline call
        // back at the site would re-introduce the bug. The exact
        // form `contract = HealthConnectSource.requestPermissionsContract()`
        // must not appear in the file.
        assertTrue(
            "The pre-fix shape `contract = HealthConnectSource.requestPermissionsContract()` " +
                "must not appear anywhere in SettingsScreen.kt. A " +
                "re-introduction here is the silent-failure bug returning. " +
                "Use the cached val `healthConnectPermissionContract` " +
                "instead.",
            !screen.contains("contract = HealthConnectSource.requestPermissionsContract()"),
        )
    }

    @Test
    fun `the launcher is bound to a different name from the outer permissionLauncher`() {
        // The outer `permissionLauncher` (line ~345) is the
        // notification-batching POST_NOTIFICATIONS launcher. A
        // refactor that re-uses the same name for the Health
        // Connect launcher would silently bind to the wrong
        // contract because the contracts are different shapes.
        // The convention in this file is `*PermissionLauncher`
        // for the more specific one.
        val slice = healthConnectLauncherSlice(width = 600)
        assertTrue(
            "The Health Connect launcher variable must be " +
                "`healthConnectPermissionLauncher`, not the outer " +
                "`permissionLauncher`. A re-use would silently bind " +
                "the wrong contract. " +
                "Slice: $slice",
            slice.contains("val healthConnectPermissionLauncher = "),
        )
    }

    @Test
    fun `the post-permission callback still re-reads the Health Connect status`() {
        // The fix must not over-correct: the launcher callback
        // must still call viewModel.refreshHealthConnectStatus() so
        // the section re-reads the granted permission count after
        // the system dialog closes. A missing call would leave the
        // section showing the pre-grant state after a grant, which
        // is the same shape of UX bug that the connection-status
        // re-read was added to prevent.
        val slice = healthConnectLauncherSlice(width = 1500)
        assertTrue(
            "The Health Connect launcher callback must call " +
                "viewModel.refreshHealthConnectStatus() after the " +
                "system dialog returns. Without it the granted " +
                "permission count is stale until the next ON_RESUME. " +
                "Slice: $slice",
            slice.contains("viewModel.refreshHealthConnectStatus()"),
        )
    }
}
