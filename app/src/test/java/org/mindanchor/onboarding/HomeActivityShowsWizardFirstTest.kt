package org.mindanchor.onboarding

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.35.1: pins HomeActivity's wizard-launch behaviour. The home
 * checks `SetupPrefs.wizardCompleted` and
 * `SetupPrefs.userDismissedWizard` after the goal-elicitation
 * onboarding is done. If both are false, it launches
 * `SetupWizardActivity` and lets the wizard sit on top of the
 * back stack. When the wizard finishes, the user pops back to
 * the home.
 *
 * A future refactor that accidentally drops the wizard-launch
 * (e.g. by moving the check into OnboardingScreen, or by adding
 * a `finish()` that destroys the back stack) would fail this
 * test.
 */
class HomeActivityShowsWizardFirstTest {

    @Test
    fun `HomeActivity launches SetupWizardActivity when the goal onboarding is done and the wizard is not (v0-35-1)`() {
        val source = readHomeActivitySource()
        assertNotNull("HomeActivity.kt must exist", source)
        // 1. The home reads the setup prefs.
        assertTrue(
            "HomeActivity must read SetupPrefs",
            source!!.contains("SetupPrefs"),
        )
        // 2. The home collects the two gate flags.
        assertTrue(
            "HomeActivity must collect wizardCompleted",
            source.contains("wizardCompleted"),
        )
        assertTrue(
            "HomeActivity must collect userDismissedWizard",
            source.contains("userDismissedWizard"),
        )
        // 3. The home launches SetupWizardActivity when both are false.
        assertTrue(
            "HomeActivity must reference SetupWizardActivity as the " +
                "target activity to launch",
            source.contains("SetupWizardActivity::class.java"),
        )
        assertTrue(
            "HomeActivity must gate the launch on both flags being false",
            source.contains("!setupCompleted") &&
                source.contains("!setupDismissed"),
        )
        // 4. The launch uses startActivity, not finish + recreate.
        assertTrue(
            "HomeActivity must use startActivity to push the wizard " +
                "onto the back stack (NOT finish() — that would break " +
                "the back-from-wizard-to-home flow)",
            source.contains("startActivity(wizardIntent)"),
        )
        // 5. The wizard sits on top of the launcher home branch,
        //    not the onboarding branch. The home's `true ->` arm
        //    hosts the wizard check + the LauncherRoot fallback;
        //    both must be inside that arm.
        val trueArmStart = source.indexOf("true ->")
        val launcherRootIdx = source.indexOf("LauncherRoot(")
        val wizardLaunchIdx = source.indexOf("SetupWizardActivity")
        assertTrue(
            "HomeActivity must have a `true ->` arm " +
                "(after goal onboarding is done). " +
                "trueArmStart=$trueArmStart",
            trueArmStart > 0,
        )
        assertTrue(
            "HomeActivity must render LauncherRoot inside the " +
                "`true ->` arm (the post-onboarding home). " +
                "launcherRootIdx=$launcherRootIdx trueArmStart=$trueArmStart",
            launcherRootIdx > trueArmStart,
        )
        assertTrue(
            "HomeActivity must launch the wizard inside the " +
                "`true ->` arm too (same arm as LauncherRoot). " +
                "wizardLaunchIdx=$wizardLaunchIdx trueArmStart=$trueArmStart",
            wizardLaunchIdx > trueArmStart,
        )
    }

    @Test
    fun `AndroidManifest registers SetupWizardActivity as non-exported (v0-35-1)`() {
        val source = readManifestSource()
        assertNotNull("AndroidManifest.xml must exist", source)
        // 1. The activity is declared.
        assertTrue(
            "AndroidManifest must declare SetupWizardActivity",
            source!!.contains("SetupWizardActivity"),
        )
        // 2. The activity is non-exported — no other app can launch it.
        assertTrue(
            "SetupWizardActivity must be non-exported " +
                "(no other app should be able to launch the wizard)",
            // Look for the activity block + the next line containing
            // `exported="false"`.
            source.contains(".onboarding.SetupWizardActivity") &&
                source.indexOf("exported=\"false\"", source.indexOf(".onboarding.SetupWizardActivity")) in
                0..(source.indexOf(".onboarding.SetupWizardActivity") + 400),
        )
    }

    @Test
    fun `SettingsScreen has a Run setup wizard again affordance (v0-35-1)`() {
        val source = readSettingsSource()
        assertNotNull("SettingsScreen.kt must exist", source)
        assertTrue(
            "SettingsScreen must reference the rerun label string",
            source!!.contains("setup_wizard_rerun_label"),
        )
        assertTrue(
            "SettingsScreen must reference the rerun description string",
            source.contains("setup_wizard_rerun_description"),
        )
        assertTrue(
            "SettingsScreen must launch SetupWizardActivity from the " +
                "rerun affordance",
            source.contains("SetupWizardActivity::class.java"),
        )
    }

    private fun readHomeActivitySource(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/HomeActivity.kt",
            "../app/src/main/java/org/mindanchor/HomeActivity.kt",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun readManifestSource(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/AndroidManifest.xml",
            "../app/src/main/AndroidManifest.xml",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun readSettingsSource(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
            "../app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
