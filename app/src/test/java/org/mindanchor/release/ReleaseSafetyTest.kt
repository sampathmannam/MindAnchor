package org.mindanchor.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 13: source-scan tests pinning the release-safety invariants a
 * live GitHub Actions run cannot be exercised for from a JVM test —
 * this JVM can't push a tag or hold real signing secrets, so it reads
 * the actual `.github/workflows/release.yml`, `app/build.gradle.kts`,
 * and `AnchorDatabase.kt` source text and asserts on it, the same
 * source-scan idiom as [org.mindanchor.goinglight.NetworkCallsForbiddenTest]
 * and [org.mindanchor.settings.GoogleDriveBackupSettingsSectionFindingTest].
 *
 * What each test protects against:
 *  - a `fallbackToDestructiveMigration` creeping back into the Room
 *    database (silently wipes on-device data on a migration failure);
 *  - the release workflow regaining a debug-signed "official release"
 *    fallback (Play Protect-blocked, unable to receive a real update);
 *  - the release workflow building before all four signing secrets are
 *    confirmed present, with a real fail-closed guard rather than a
 *    decorative reference to the secret names;
 *  - `versionCode` regressing below the last shipped value;
 *  - Room schema export (`exportSchema`) being turned off, which would
 *    stop the schema-history JSON under app/schemas/ from being kept
 *    up to date on every version bump.
 */
class ReleaseSafetyTest {

    private fun locate(vararg candidates: String): File {
        val found = candidates.map(::File).firstOrNull { it.isFile }
        return found ?: error(
            "None of these paths exist from working directory " +
                "${File(".").absolutePath}: ${candidates.toList()}",
        )
    }

    private val anchorDatabaseSource: String by lazy {
        locate(
            "src/main/java/org/mindanchor/data/db/AnchorDatabase.kt",
            "app/src/main/java/org/mindanchor/data/db/AnchorDatabase.kt",
        ).readText()
    }

    private val releaseWorkflowSource: String by lazy {
        locate(
            ".github/workflows/release.yml",
            "../.github/workflows/release.yml",
            "../../.github/workflows/release.yml",
        ).readText()
    }

    private val appBuildGradleSource: String by lazy {
        locate(
            "build.gradle.kts",
            "app/build.gradle.kts",
        ).readText()
    }

    @Test
    fun `AnchorDatabase never falls back to destructive migration`() {
        assertTrue(
            "AnchorDatabase.kt must not call fallbackToDestructiveMigration — a " +
                "migration failure must surface as a crash the app can report, " +
                "never a silent on-device data wipe.",
            !anchorDatabaseSource.contains("fallbackToDestructiveMigration"),
        )
    }

    @Test
    fun `release workflow has no debug-APK publication fallback`() {
        val offendingPatterns = listOf("assembleDebug", "app-debug.apk")
        val offenders = offendingPatterns.filter { releaseWorkflowSource.contains(it) }
        assertTrue(
            "release.yml must not publish a debug-signed APK as an official " +
                "release under any condition. Found forbidden pattern(s): $offenders. " +
                "Ordinary debug builds already come from ci.yml on every push; this " +
                "workflow must fail closed instead of downgrading to a debug build " +
                "when signing secrets are missing.",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `release workflow checks all four signing secrets before building`() {
        val requiredSecrets = listOf(
            "MINDANCHOR_KEYSTORE_BASE64",
            "MINDANCHOR_KEYSTORE_PASSWORD",
            "MINDANCHOR_KEY_ALIAS",
            "MINDANCHOR_KEY_PASSWORD",
        )
        val missingSecretRefs = requiredSecrets.filter { !releaseWorkflowSource.contains(it) }
        assertTrue(
            "release.yml must reference all four signing secrets. Missing: $missingSecretRefs",
            missingSecretRefs.isEmpty(),
        )
    }

    @Test
    fun `release workflow fails closed with a real guard, not a decorative secret reference`() {
        // A real fail-closed guard: each secret is tested for emptiness
        // (`-z`) and the step exits non-zero when any is missing. This
        // is deliberately stricter than "the secret names appear
        // somewhere in the file" — that would also be true of the old,
        // silently-falls-back-to-debug workflow this task replaces.
        val hasEmptyChecksForAllFour = listOf(
            "MINDANCHOR_KEYSTORE_BASE64",
            "MINDANCHOR_KEYSTORE_PASSWORD",
            "MINDANCHOR_KEY_ALIAS",
            "MINDANCHOR_KEY_PASSWORD",
        ).all { secret -> releaseWorkflowSource.contains("-z \"$$secret\"") }
        assertTrue(
            "release.yml must test each of the four signing secrets for emptiness " +
                "(a literal '-z \"\$SECRET_NAME\"' guard) before building.",
            hasEmptyChecksForAllFour,
        )
        assertTrue(
            "release.yml must exit non-zero when a required signing secret is missing.",
            releaseWorkflowSource.contains("exit 1"),
        )
    }

    @Test
    fun `versionCode is greater than the last shipped Program 0 value`() {
        val match = Regex("""versionCode\s*=\s*(\d+)""").find(appBuildGradleSource)
        val versionCode = match?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Could not find a 'versionCode = N' line in app/build.gradle.kts")
        assertTrue(
            "versionCode must be greater than 94 (the last value before Program 0's " +
                "release-hardening bump). Found: $versionCode",
            versionCode > 94,
        )
    }

    @Test
    fun `versionCode and versionName are pinned to the Program 1 release values`() {
        // Exact-match pin in addition to the ">94" check above: Program 1
        // ships 96 / "0.72.0". This test is expected to need updating on
        // every real version bump; the ">94" test above is the one that
        // survives unmodified, and it guards the property that actually
        // matters -- a versionCode must never regress.
        assertTrue(
            "app/build.gradle.kts must set versionCode = 96.",
            Regex("""versionCode\s*=\s*96\b""").containsMatchIn(appBuildGradleSource),
        )
        assertTrue(
            "app/build.gradle.kts must set versionName = \"0.72.0\".",
            appBuildGradleSource.contains("versionName = \"0.72.0\""),
        )
    }

    @Test
    fun `Room schema export stays enabled on AnchorDatabase`() {
        // Task 2 set exportSchema = true directly on AnchorDatabase's
        // @Database annotation — app/build.gradle.kts's
        // room.schemaLocation ksp arg only sets the OUTPUT location, not
        // whether export happens at all. Scanning the wrong file would
        // let exportSchema regress to false without this test noticing.
        assertTrue(
            "AnchorDatabase.kt's @Database annotation must keep exportSchema = true " +
                "so Room keeps writing the schema-history JSON under app/schemas/ on " +
                "every version bump — the historical record a schema audit or a future " +
                "MigrationTestHelper-based test would need.",
            anchorDatabaseSource.contains("exportSchema = true"),
        )
    }
}
