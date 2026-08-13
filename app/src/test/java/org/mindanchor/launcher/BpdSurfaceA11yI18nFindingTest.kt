package org.mindanchor.launcher

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.12 FindingTest: the 4 v0.26.0 §3.2/3.3/3.5 BPD-launcher
 * surfaces must not contain hardcoded user-facing English strings
 * in their accessibility contentDescriptions.
 *
 * Background: the v0.25.11 SOTA sweep migrated 121 TextButtons to
 * `Modifier.semantics { role = Role.Button }` and 30+
 * `stringResource(...)` calls for hardcoded text — but the
 * `contentDescription = "..."` strings in the 3 new BPD surfaces
 * were missed. They are user-facing accessibility text: a Tamil
 * user running a Tamil-localised build hears English in TalkBack.
 *
 * The fix is a single `stringResource(R.string.X_a11y, …)` local
 * val computed in the @Composable scope, then assigned to
 * `contentDescription` inside the `Modifier.semantics { … }`
 * lambda (the lambda is not @Composable, so `stringResource` must
 * be hoisted).
 *
 * The strings live in `app/src/main/res/values/strings.xml` as:
 *   - ground_me_a11y  = "Ground me right now. Three options."
 *   - now_what_a11y   = "It's late. What do you need right now?"
 *   - bys_a11y        = "Before you send. %1$s."
 *
 * This test pins the file shape: a regression that re-hardcodes
 * any of the three strings (e.g. someone re-introduces
 * `contentDescription = "Ground me right now. Three options."`
 * after a content-copylint pass) flips the test red.
 */
class BpdSurfaceA11yI18nFindingTest {

    private fun readSource(filename: String, pkg: String): String? = runCatching {
        val candidates = buildList {
            if (pkg.isNotEmpty()) {
                add("app/src/main/java/org/mindanchor/$pkg/$filename")
                add("../app/src/main/java/org/mindanchor/$pkg/$filename")
            }
            add("app/src/main/java/org/mindanchor/$filename")
            add("../app/src/main/java/org/mindanchor/$filename")
        }
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun readStrings(): String? = runCatching {
        val candidates = listOf(
            "app/src/main/res/values/strings.xml",
            "../app/src/main/res/values/strings.xml",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    @Test
    fun `NowWhatShell uses stringResource for its contentDescription`() {
        val source = readSource("NowWhatShell.kt", "launcher")
        assertNotNull("NowWhatShell.kt must be readable", source)
        assertTrue(
            "NowWhatShell must use stringResource(R.string.now_what_a11y) — the literal " +
                "English string is the v0.25.11 hardcoded-i18n bug shape",
            source!!.contains("stringResource(R.string.now_what_a11y)"),
        )
        assertTrue(
            "NowWhatShell must NOT contain the hardcoded English " +
                "'It\\'s late. What do you need right now?' string in its source — " +
                "that is the v0.25.11 i18n bug shape",
            !source.contains("What do you need right now?"),
        )
    }

    @Test
    fun `GroundMeScreen uses stringResource for its picker contentDescription`() {
        val source = readSource("GroundMeScreen.kt", "launcher")
        assertNotNull("GroundMeScreen.kt must be readable", source)
        assertTrue(
            "GroundMeScreen must use stringResource(R.string.ground_me_a11y) — the literal " +
                "English string is the v0.25.11 hardcoded-i18n bug shape",
            source!!.contains("stringResource(R.string.ground_me_a11y)"),
        )
        assertTrue(
            "GroundMeScreen must NOT contain the hardcoded English " +
                "'Ground me right now. Three options.' string in its source — " +
                "that is the v0.25.11 i18n bug shape",
            !source.contains("Ground me right now. Three options."),
        )
    }

    @Test
    fun `BeforeYouSendInterstitial uses stringResource for its contentDescription`() {
        val source = readSource("BeforeYouSendInterstitial.kt", "friction")
        assertNotNull("BeforeYouSendInterstitial.kt must be readable", source)
        assertTrue(
            "BeforeYouSendInterstitial must use stringResource(R.string.bys_a11y) — the literal " +
                "English string is the v0.25.11 hardcoded-i18n bug shape",
            source!!.contains("stringResource(R.string.bys_a11y"),
        )
        // The shape of the fix is a hoisted `val a11y = stringResource(R.string.bys_a11y, template.label)`
        // then `contentDescription = a11y` inside the Modifier.semantics lambda.
        assertTrue(
            "BeforeYouSendInterstitial must hoist the stringResource into a local val " +
                "(stringResource is @Composable and cannot be called inside a Modifier.semantics lambda)",
            source.contains("val a11y = stringResource(R.string.bys_a11y"),
        )
        assertTrue(
            "BeforeYouSendInterstitial must NOT contain the hardcoded English " +
                "'Before you send.' prefix in a contentDescription — that is the v0.25.11 i18n bug shape",
            !source.contains("\"Before you send. \${template.label}.\""),
        )
    }

    @Test
    fun `strings_xml defines the three a11y keys for the BPD surfaces`() {
        val strings = readStrings()
        assertNotNull("strings.xml must be readable", strings)
        assertTrue(
            "strings.xml must define <string name=\"now_what_a11y\">",
            strings!!.contains("<string name=\"now_what_a11y\">"),
        )
        assertTrue(
            "strings.xml must define <string name=\"ground_me_a11y\">",
            strings.contains("<string name=\"ground_me_a11y\">"),
        )
        assertTrue(
            "strings.xml must define <string name=\"bys_a11y\"> with a %1\$s placeholder",
            strings.contains("<string name=\"bys_a11y\">") && strings.contains("%1\$s"),
        )
    }
}
