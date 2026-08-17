package org.mindanchor.onboarding

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.35.1: one finding test per step of the data-source setup
 * wizard. Each test reads the step's Composable file and asserts
 * the Composable exists, the right string keys are referenced, and
 * the next-step callback is wired. A future refactor that drops a
 * step, renames a string, or breaks the wizard's nav chain fails
 * the matching test.
 *
 * The string keys are the contract between this file and
 * `app/src/main/res/values/strings.xml`. They are also the
 * contract between the step Composables and the
 * `HomeActivityShowsWizardFirstTest` (which pins the per-step
 * route from HomeActivity).
 */
class SetupWizardStepTest {

    @Test
    fun `WelcomeStep Composable exists with all 4 source lines and begin button (v0-35-1)`() {
        val source = readStepSource("WelcomeStep.kt")
        assertNotNull("WelcomeStep.kt must exist", source)
        // Composable exists
        assertTrue(
            "WelcomeStep must be a public @Composable function",
            source!!.contains("fun WelcomeStep("),
        )
        // 4 source detail lines
        assertTrue(
            "WelcomeStep must reference health connect label + detail",
            source.contains("setup_wizard_source_health_connect_label") &&
                source.contains("setup_wizard_source_health_connect_detail"),
        )
        assertTrue(
            "WelcomeStep must reference watch label + detail",
            source.contains("setup_wizard_source_watch_label") &&
                source.contains("setup_wizard_source_watch_detail"),
        )
        assertTrue(
            "WelcomeStep must reference polar label + detail",
            source.contains("setup_wizard_source_polar_label") &&
                source.contains("setup_wizard_source_polar_detail"),
        )
        assertTrue(
            "WelcomeStep must reference ppg label + detail",
            source.contains("setup_wizard_source_ppg_label") &&
                source.contains("setup_wizard_source_ppg_detail"),
        )
        // Begin button
        assertTrue(
            "WelcomeStep must surface a Begin button",
            source.contains("setup_wizard_begin"),
        )
    }

    @Test
    fun `HealthConnectStep Composable exists and grants the 8 HC permissions (v0-35-1)`() {
        val source = readStepSource("HealthConnectStep.kt")
        val contract = readStepSource("vitals/HealthConnectRequestContract.kt")
        assertNotNull("HealthConnectStep.kt must exist", source)
        assertNotNull("HealthConnectRequestContract.kt must exist", contract)
        assertTrue(
            "HealthConnectStep must be a public @Composable function",
            source!!.contains("fun HealthConnectStep("),
        )
        // v0.35.2: must use the shared `HealthConnectRequestPermissionsContract`
        // (defined in `vitals/`) which fires the dedicated Health Connect
        // UI directly. The SDK 1.1.0 contract on Android 14+ wraps the
        // system `RequestMultiplePermissions` which dismisses itself
        // immediately for `android.permission.health.*` because those
        // are not standard runtime permissions.
        assertTrue(
            "HealthConnectStep must use HealthConnectRequestPermissionsContract",
            source.contains("HealthConnectRequestPermissionsContract()"),
        )
        // The shared contract must fire the dedicated Health Connect
        // intent. The legacy `android.health.connect.action.REQUEST_HEALTH_PERMISSIONS`
        // is signature-protected on Android 17 (`SecurityException: requires
        // GRANT_RUNTIME_PERMISSIONS`); the modern gateway is
        // `androidx.health.ACTION_REQUEST_PERMISSIONS` handled by
        // `com.google.android.apps.healthdata/.deeplink.DefaultGateway`.
        assertTrue(
            "HealthConnectRequestPermissionsContract must fire the modern Health Connect intent",
            contract!!.contains("androidx.health.ACTION_REQUEST_PERMISSIONS"),
        )
        assertTrue(
            "HealthConnectRequestPermissionsContract must NOT pin a package (works with both new and legacy providers)",
            !contract.contains(".setPackage("),
        )
        // Permission set still comes from HealthConnectSource so
        // Settings and the wizard stay in lockstep.
        assertTrue(
            "HealthConnectStep must delegate to HealthConnectSource.effectivePermissions",
            source.contains("HealthConnectSource.effectivePermissions("),
        )
        // Must NOT use the broken SDK contract.
        assertTrue(
            "HealthConnectStep must NOT use HealthConnectSource.requestPermissionsContract",
            !source.contains("HealthConnectSource.requestPermissionsContract("),
        )
        assertTrue(
            "HealthConnectStep must NOT import the system RequestMultiplePermissions contract",
            !source.contains("import androidx.activity.result.contract.ActivityResultContracts"),
        )
        assertTrue(
            "HealthConnectStep must surface a grant button",
            source.contains("setup_wizard_health_connect_grant"),
        )
        assertTrue(
            "HealthConnectStep must surface a skip button",
            source.contains("setup_wizard_skip"),
        )
    }

    @Test
    fun `PairWatchStep Composable embeds SmartwatchesSection and has continue + skip (v0-35-1)`() {
        val source = readStepSource("PairWatchStep.kt")
        assertNotNull("PairWatchStep.kt must exist", source)
        assertTrue(
            "PairWatchStep must be a public @Composable function",
            source!!.contains("fun PairWatchStep("),
        )
        assertTrue(
            "PairWatchStep must embed the existing SmartwatchesSection " +
                "(BLE scan + tap-to-connect + always-reconnect switch)",
            source.contains("SmartwatchesSection()"),
        )
        assertTrue(
            "PairWatchStep must have a Continue button",
            source.contains("setup_wizard_continue"),
        )
        assertTrue(
            "PairWatchStep must have a Skip button",
            source.contains("setup_wizard_skip"),
        )
    }

    @Test
    fun `PolarStep Composable embeds PolarSection and has continue + skip (v0-37-0)`() {
        // v0.37.0: the step was previously named `CorosStep`. The
        // rename brought the file name in line with what the
        // composable always did — host the `PolarSection` for the
        // Polar Flow OAuth2 web bridge.
        val source = readStepSource("PolarStep.kt")
        assertNotNull("PolarStep.kt must exist", source)
        assertTrue(
            "PolarStep must be a public @Composable function",
            source!!.contains("fun PolarStep("),
        )
        assertTrue(
            "PolarStep must embed the existing PolarSection " +
                "(email + password form for the OAuth2 web bridge)",
            source.contains("PolarSection()"),
        )
        assertTrue(
            "PolarStep must have a Continue button",
            source.contains("setup_wizard_continue"),
        )
        assertTrue(
            "PolarStep must have a Skip button",
            source.contains("setup_wizard_skip"),
        )
    }

    @Test
    fun `PpgStep Composable takes a baseline reading and has continue + skip (v0-35-1)`() {
        val source = readStepSource("PpgStep.kt")
        assertNotNull("PpgStep.kt must exist", source)
        assertTrue(
            "PpgStep must be a public @Composable function",
            source!!.contains("fun PpgStep("),
        )
        assertTrue(
            "PpgStep must embed the existing PpgScreen Composable " +
                "(no separate activity — staying in the wizard reduces " +
                "the cognitive load of 'where am I')",
            source.contains("PpgScreen("),
        )
        assertTrue(
            "PpgStep must have a Take a baseline reading button",
            source.contains("setup_wizard_ppg_take_baseline"),
        )
        assertTrue(
            "PpgStep must have a Continue button",
            source.contains("setup_wizard_continue"),
        )
        assertTrue(
            "PpgStep must have a Skip button",
            source.contains("setup_wizard_skip"),
        )
    }

    @Test
    fun `DoneStep Composable shows 4 source rows and a finish button (v0-35-1)`() {
        val source = readStepSource("DoneStep.kt")
        assertNotNull("DoneStep.kt must exist", source)
        assertTrue(
            "DoneStep must be a public @Composable function",
            source!!.contains("fun DoneStep("),
        )
        // 4 source rows (the same labels as on the Welcome step)
        for (label in listOf(
            "setup_wizard_source_health_connect_label",
            "setup_wizard_source_watch_label",
            "setup_wizard_source_polar_label",
            "setup_wizard_source_ppg_label",
        )) {
            assertTrue(
                "DoneStep must surface a row for $label",
                source.contains(label),
            )
        }
        // The "Set in Settings" acknowledgement text
        assertTrue(
            "DoneStep must surface the per-source acknowledgement text",
            source.contains("setup_wizard_done_set_in_settings"),
        )
        assertTrue(
            "DoneStep must have an Open the home finish button",
            source.contains("setup_wizard_done_finish"),
        )
    }

    @Test
    fun `SetupWizardActivity routes every SetupStep to the matching Composable (v0-35-1)`() {
        val source = readOnboardingSource("SetupWizardActivity.kt")
        assertNotNull("SetupWizardActivity.kt must exist", source)
        // All 6 step Composables wired in the when (step) block
        for (composable in listOf(
            "WelcomeStep(",
            "HealthConnectStep(",
            "PairWatchStep(",
            "PolarStep(",
            "PpgStep(",
            "DoneStep(",
        )) {
            assertTrue(
                "SetupWizardActivity must render $composable for the matching SetupStep",
                source!!.contains(composable),
            )
        }
    }

    @Test
    fun `SetupWizardViewModel drives the per-step skip state and the step chain (v0-35-1)`() {
        val source = readOnboardingSource("SetupWizardViewModel.kt")
        assertNotNull("SetupWizardViewModel.kt must exist", source)
        // The 6 SetupStep enum entries exist
        for (entry in listOf("WELCOME", "HEALTH_CONNECT", "PAIR_WATCH", "POLAR", "PPG", "DONE")) {
            assertTrue(
                "SetupWizardViewModel must reference SetupStep.$entry",
                source!!.contains("SetupStep.$entry"),
            )
        }
        // The 4 nav methods
        assertTrue("ViewModel must expose advance()", source!!.contains("fun advance()"))
        assertTrue("ViewModel must expose back()", source.contains("fun back()"))
        assertTrue("ViewModel must expose skip()", source.contains("fun skip("))
        assertTrue("ViewModel must expose complete()", source.contains("fun complete()"))
    }

    @Test
    fun `SetupPrefs exposes wizard_completed and per-step skipped flags (v0-35-1)`() {
        val source = readOnboardingSource("SetupPrefs.kt")
        assertNotNull("SetupPrefs.kt must exist", source)
        assertTrue(
            "SetupPrefs must use a DataStore named setup_wizard",
            source!!.contains("setup_wizard\""),
        )
        // The 2 gate flags
        assertTrue("SetupPrefs must have wizardCompleted", source.contains("wizardCompleted"))
        assertTrue("SetupPrefs must have userDismissedWizard", source.contains("userDismissedWizard"))
        // The 4 skip flags
        for (flag in listOf("healthConnectSkipped", "pairWatchSkipped", "polarSkipped", "ppgSkipped")) {
            assertTrue(
                "SetupPrefs.progress must include $flag",
                source.contains(flag),
            )
        }
    }

    private fun readStepSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/onboarding/steps/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/onboarding/steps/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun readOnboardingSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/onboarding/$filename",
            "app/src/main/java/org/mindanchor/$filename",
            "../app/src/main/java/org/mindanchor/onboarding/$filename",
            "../app/src/main/java/org/mindanchor/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
