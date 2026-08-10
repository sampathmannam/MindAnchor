package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural test for the "Connect to your watch" button in the
 * SettingsScreen's Measuring group.
 *
 * The runtime test for the permission flow is the project owner's
 * responsibility on a real device with Health Connect installed;
 * this test pins the file's shape so a refactor that disconnects
 * the button from the launcher is caught at build time, not when
 * somebody opens the screen and the dialog does not appear.
 *
 * What this test checks:
 *  1. The button exists inside the Measuring group and is wired
 *     to launch [HealthConnectSource.requestPermissionsContract].
 *  2. The launch argument is [HealthConnectSource.effectivePermissions],
 *     not a hard-coded set, so a future permission added to the
 *     source automatically flows to the system dialog.
 *  3. The status callback in the launcher's remember block calls
 *     [SettingsViewModel.refreshHealthConnectStatus] so the section
 *     re-reads the granted set after the user returns from the
 *     system dialog.
 *  4. The button is *not* shown when the status is Unavailable —
 *     a dialog that fails to open because Health Connect is not
 *     installed is a worse UX than a hidden button.
 *
 * @see HealthConnectSource for the actual contract and effective
 * permission set
 */
class SettingsHealthConnectButtonTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        ).readText()

    private val viewModel: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/settings/SettingsViewModel.kt",
        ).readText()

    private val source: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/vitals/HealthConnectSource.kt",
        ).readText()

    @Test
    fun `the file declares a Health Connect permission launcher bound to the source contract`() {
        // v0.23.0: the contract factory is cached in a `remember`
        // block before the launcher site. The pre-fix inline call
        // (a new ActivityResultContract instance every
        // recomposition) was the silent-failure shape — the click
        // was sent to a launcher no longer in the activity's
        // ActivityResultRegistry. Caching the contract makes the
        // launcher stable across recompositions. See
        // [HealthConnectLauncherCacheTest] for the regression
        // shape of that bug.
        assertTrue(
            "SettingsScreen.kt must declare a cached " +
                "`healthConnectPermissionContract = remember { ... }` val " +
                "and pass it as `contract = healthConnectPermissionContract` " +
                "to rememberLauncherForActivityResult. The pre-fix inline " +
                "call `contract = HealthConnectSource.requestPermissionsContract()` " +
                "created a new ActivityResultContract every recomposition, " +
                "which made the launcher re-register and the click drift to " +
                "a stale launcher. Without the cache, the 'Connect to your " +
                "watch' button is wired to nothing on a slow result pipeline.",
            screen.contains("val healthConnectPermissionContract = remember {") ||
                screen.contains("val healthConnectPermissionContract = remember("),
        )
        assertTrue(
            "The Health Connect launcher site must use the cached " +
                "val as the contract, not the inline factory call.",
            screen.contains("contract = healthConnectPermissionContract"),
        )
    }

    @Test
    fun `the button launches the launcher with effective permissions (not a hard-coded set)`() {
        // The button click must pass HealthConnectSource.effectivePermissions
        // (which is the live set the source manages) — not a literal
        // setOf(...) that would silently fall out of sync the day a
        // new permission is added.
        assertTrue(
            "The button's onClick must call " +
                "healthConnectPermissionLauncher.launch(HealthConnectSource" +
                ".effectivePermissions(context)). " +
                "Passing a literal Set<String> would diverge from the " +
                "source's PERMISSIONS the day a new record type is added.",
            screen.contains(
                "healthConnectPermissionLauncher.launch(\n" +
                    "                            HealthConnectSource.effectivePermissions(context),",
            ),
        )
    }

    @Test
    fun `the launcher callback refreshes the status (so the section re-reads granted permissions)`() {
        // After the system dialog returns, the status must be re-read
        // — otherwise the button stays labelled "Connect to your
        // watch" even after a grant, and the launcher looks broken
        // even though the data is flowing.
        // v0.23.0: the launcher is keyed on the cached contract
        // `healthConnectPermissionContract`, not the inline factory
        // call. Look for the cached form.
        val launcherBlock = Regex(
            """contract\s*=\s*healthConnectPermissionContract,\s*\)\s*\{[^}]*\}""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(screen)?.value ?: error(
            "Could not locate the rememberLauncherForActivityResult " +
                "block in SettingsScreen.kt (looking for " +
                "`contract = healthConnectPermissionContract`).",
        )
        assertTrue(
            "The launcher callback must call " +
                "viewModel.refreshHealthConnectStatus() so the granted " +
                "permission count updates after the system dialog closes. " +
                "Block: $launcherBlock",
            launcherBlock.contains("viewModel.refreshHealthConnectStatus()"),
        )
    }

    @Test
    fun `the button is hidden when Health Connect is unavailable`() {
        // When the source is not installed, the launcher has no
        // intent to dispatch and the button would silently do nothing.
        // The correct response is to hide the button entirely.
        //
        // The guard must be an explicit `is Available` check — not a
        // bare `!is Unavailable` that also lets the `Unknown` initial
        // state render the button. The Unknown case is the window in
        // which the button was visibly tappable but did nothing on
        // a phone without Health Connect, because the launch was
        // dispatched before the availability probe had run.
        val buttonBlock = Regex(
            """val s = hcStatus[\s\S]{0,2000}?TextButton\([\s\S]{0,400}?\)""",
        ).find(screen)?.value ?: error(
            "Could not locate the hcStatus + TextButton block in SettingsScreen.kt",
        )
        // The button must be inside a `when (s)` or `if (s is …)` that
        // matches Available specifically. A bare `!is Unavailable`
        // is the bug — it would render the button for Unknown, which
        // is the un-probed loading state.
        assertTrue(
            "The Connect-to-your-watch button must be wrapped in an " +
                "`is SettingsViewModel.HealthConnectStatus.Available` " +
                "check (NOT a bare `!is Unavailable`, which would render " +
                "the button for the `Unknown` initial state — the launch " +
                "fires before the availability probe has run, so on a " +
                "phone without Health Connect the button silently does " +
                "nothing). " +
                "Block: $buttonBlock",
            buttonBlock.contains("is SettingsViewModel.HealthConnectStatus.Available") &&
                !buttonBlock.contains("!is SettingsViewModel.HealthConnectStatus.Unavailable"),
        )
    }

    @Test
    fun `the button label flips between connect and change based on the granted count`() {
        // When granted == 0, the label is "Connect to your watch" —
        // the on-ramp. When granted > 0, the label is "Change what
        // is shared" — the same button now serves a different mental
        // model (you already have access; this is for re-sharing).
        val buttonBlock = Regex(
            """val s = hcStatus[\s\S]{0,2000}?TextButton\([\s\S]{0,400}?Text\(stringResource\(buttonLabelRes\)\)""",
        ).find(screen)?.value ?: error(
            "Could not locate the buttonLabelRes + Text(stringResource(...)) block",
        )
        assertTrue(
            "The button label must come from buttonLabelRes, computed " +
                "as either R.string.health_connect_button_connect (when " +
                "granted == 0 or status is Unknown) or " +
                "R.string.health_connect_button_change (when granted > 0). " +
                "Block: $buttonBlock",
            buttonBlock.contains("R.string.health_connect_button_connect") &&
                buttonBlock.contains("R.string.health_connect_button_change"),
        )
    }

    @Test
    fun `the source's effective-permission helper gates on isAvailable and the mindfulness feature flag`() {
        // The launcher's input is effectivePermissions — it must not
        // include MindfulnessSessionRecord when the provider does
        // not advertise the feature (Health Connect 1.1.0 stable
        // requires the feature to be enabled per-provider, and a
        // permission for an unadvertised feature raises a runtime
        // exception when the system dialog tries to render it).
        assertTrue(
            "HealthConnectSource.effectivePermissions must return the " +
                "base set (no MindfulnessSessionRecord) when isAvailable " +
                "is false — otherwise the system dialog crashes.",
            source.contains(
                "if (!isAvailable(context)) return base",
            ),
        )
        assertTrue(
            "HealthConnectSource.effectivePermissions must return the " +
                "full PERMISSIONS set only when the provider advertises " +
                "FEATURE_MINDFULNESS_SESSION as AVAILABLE — otherwise " +
                "asking for that permission crashes the dialog.",
            source.contains(
                "if (status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {\n" +
                    "            PERMISSIONS\n" +
                    "        } else {\n" +
                    "            base\n" +
                    "        }",
            ),
        )
    }

    @Test
    fun `the view model recomputes the status on a background dispatcher`() {
        // The permission read can take a few hundred ms on first call
        // (Health Connect has to do a provider check). A main-thread
        // call would block the Compose recomposition the launcher
        // is trying to drive.
        val refreshFn = Regex(
            """fun refreshHealthConnectStatus\(\)[\s\S]{0,400}?\}""",
        ).find(viewModel)?.value ?: error(
            "Could not locate refreshHealthConnectStatus in SettingsViewModel.kt",
        )
        assertTrue(
            "refreshHealthConnectStatus must dispatch off the main " +
                "thread (Dispatchers.IO) — a Health Connect client " +
                "build is not instant and must not block Compose. " +
                "Block: $refreshFn",
            refreshFn.contains("Dispatchers.IO"),
        )
    }
}
