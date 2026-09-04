package org.mindanchor.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural test for the second step of the Health Connect
 * permission flow: background reads and history reads.
 *
 * ## The bug this pins against
 *
 * `READ_HEALTH_DATA_IN_BACKGROUND` and `READ_HEALTH_DATA_HISTORY`
 * are "additional" permissions, not record-read permissions.
 * When a permission request mixes the two kinds, Health Connect
 * renders grant toggles for the record reads only — the
 * additional permissions are silently dropped from the dialog,
 * come back ungranted, and nothing logs why. Observed on the
 * project owner's phone: both stayed ungranted through every
 * connect pass, because the one launch site passed the whole
 * bundle every time.
 *
 * The fix is a second launch that passes ONLY the two
 * additional permissions. This test pins:
 *
 *  1. The source keeps the additional permissions in their own
 *     set — never inside [PERMISSIONS], where they would be
 *     bundled back into the record-read request.
 *  2. The additional set is feature-gated per provider before
 *     launch, the same shape as the mindfulness gating — a
 *     permission the provider does not advertise crashes the
 *     system dialog.
 *  3. The screen's second launch site passes only the
 *     feature-gated additional set.
 *  4. The row that hosts the second launch shows only when
 *     record reads are granted (the additional permissions do
 *     nothing without them) and at least one of the two is
 *     still missing.
 *  5. The view model carries the additional-grant state so the
 *     row's visibility is recomputed by the same refresh that
 *     drives the rest of the section.
 *
 * Why background matters: the nightly report reads Health
 * Connect from a BroadcastReceiver at ~03:00 with no activity
 * in the foreground — without the background grant those reads
 * are denied and degrade to empty. Why history matters: without
 * the history grant, reads reach back at most 30 days before
 * the first record-read grant, which starves the personal
 * baseline of backfill.
 *
 * @see SettingsHealthConnectButtonTest for the first step
 */
class HealthConnectAdditionalAccessTest {

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

    private val strings: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    @Test
    fun `the additional permissions live in their own set and never inside PERMISSIONS`() {
        assertTrue(
            "HealthConnectSource must declare ADDITIONAL_PERMISSIONS with " +
                "HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND and " +
                "HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY. These are " +
                "the two grants the connect flow's second step launches on their own.",
            source.contains("val ADDITIONAL_PERMISSIONS") &&
                source.contains("HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND") &&
                source.contains("HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY"),
        )
        // The record-read set must stay free of the additional
        // permissions. Bundling them back in is the original bug:
        // Health Connect renders no toggle for an additional
        // permission that arrives mixed with record reads, so the
        // grant silently never happens.
        val permissionsBlock = Regex(
            """val PERMISSIONS: Set<String> = setOf\([\s\S]*?\n    \)""",
        ).find(source)?.value ?: error(
            "Could not locate the PERMISSIONS declaration in HealthConnectSource.kt",
        )
        assertTrue(
            "PERMISSIONS must contain record-read permissions only. Found an " +
                "additional (PERMISSION_READ_HEALTH_DATA_*) constant inside it — " +
                "that re-creates the silent-drop bundling bug this flow exists " +
                "to avoid. Block: $permissionsBlock",
            !permissionsBlock.contains("PERMISSION_READ_HEALTH_DATA"),
        )
    }

    @Test
    fun `the effective additional set is feature-gated per provider`() {
        // Same shape as the mindfulness gating in effectivePermissions:
        // asking the system dialog for a permission the provider does
        // not advertise raises at render time. Each of the two rides
        // its own feature flag.
        assertTrue(
            "HealthConnectSource must declare effectiveAdditionalPermissions " +
                "and gate each permission on its HealthConnectFeatures flag " +
                "(FEATURE_READ_HEALTH_DATA_IN_BACKGROUND / " +
                "FEATURE_READ_HEALTH_DATA_HISTORY).",
            source.contains("fun effectiveAdditionalPermissions") &&
                source.contains("HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND") &&
                source.contains("HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY"),
        )
    }

    @Test
    fun `granted additional permissions are read against the additional set`() {
        assertTrue(
            "HealthConnectSource must declare grantedAdditionalPermissions " +
                "intersecting the provider's granted set with " +
                "ADDITIONAL_PERMISSIONS — grantedPermissions intersects with " +
                "PERMISSIONS and so filters the additional grants out.",
            source.contains("fun grantedAdditionalPermissions") &&
                source.contains(".intersect(ADDITIONAL_PERMISSIONS)"),
        )
    }

    @Test
    fun `the second launch site passes only the effective additional set`() {
        val argIdx = screen.indexOf("HealthConnectSource.effectiveAdditionalPermissions(context)")
        assertTrue(
            "SettingsScreen must launch the permission contract a second " +
                "time with HealthConnectSource.effectiveAdditionalPermissions" +
                "(context) as the only input. argIdx=$argIdx",
            argIdx >= 0,
        )
        // The call must be the argument of a launch on the cached
        // launcher — not a stray reference. Locate the nearest
        // preceding launch( and require it within a short window.
        val launchToken = "healthConnectPermissionLauncher.launch("
        val launchIdx = screen.lastIndexOf(launchToken, argIdx)
        assertTrue(
            "effectiveAdditionalPermissions(context) must be the argument " +
                "of healthConnectPermissionLauncher.launch(...) — the same " +
                "cached launcher the record-read request uses. " +
                "launchIdx=$launchIdx argIdx=$argIdx",
            launchIdx >= 0 && argIdx - (launchIdx + launchToken.length) < 80,
        )
    }

    @Test
    fun `the row shows only when record reads exist and an additional grant is missing`() {
        val gateIdx = screen.indexOf("s.granted > 0 && s.additionalGranted < s.additionalTotal")
        val availableIdx = screen.indexOf("is SettingsViewModel.HealthConnectStatus.Available")
        assertTrue(
            "The allow-background-and-history row must be gated on " +
                "`s.granted > 0 && s.additionalGranted < s.additionalTotal` " +
                "inside the Available branch: without a record-read grant the " +
                "additional permissions read nothing, and once both are " +
                "granted the row has nothing left to ask. " +
                "gateIdx=$gateIdx availableIdx=$availableIdx",
            gateIdx >= 0 && availableIdx in 0 until gateIdx,
        )
    }

    @Test
    fun `the view model carries the additional grant state`() {
        assertTrue(
            "HealthConnectStatus.Available must carry additionalGranted and " +
                "additionalTotal so the row's visibility is recomputed by " +
                "refreshHealthConnectStatus like the rest of the section.",
            viewModel.contains("val additionalGranted: Int") &&
                viewModel.contains("val additionalTotal: Int"),
        )
        assertTrue(
            "refreshHealthConnectStatus must read the additional state from " +
                "the source helpers (effectiveAdditionalPermissions + " +
                "grantedAdditionalPermissions), not recompute it inline.",
            viewModel.contains("HealthConnectSource.effectiveAdditionalPermissions(") &&
                viewModel.contains("HealthConnectSource.grantedAdditionalPermissions("),
        )
    }

    @Test
    fun `the row's strings exist and state the 30-day mechanic`() {
        assertTrue(
            "strings.xml must declare health_connect_additional_explainer and " +
                "health_connect_additional_button for the second-step row.",
            strings.contains("health_connect_additional_explainer") &&
                strings.contains("health_connect_additional_button"),
        )
        assertTrue(
            "The explainer must state the concrete limit the history grant " +
                "lifts — reads reaching at most 30 days back — in plain " +
                "language, per the say-it-out-loud rule for this section.",
            Regex("health_connect_additional_explainer\">[^<]*30 days")
                .containsMatchIn(strings),
        )
    }
}
