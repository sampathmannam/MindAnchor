@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.27.0: the DBT Module 4 (Interpersonal Effectiveness) surface.
 *
 * Pins:
 *  1. The activity exists and is registered non-exported.
 *  2. The screen has a menu with 3 buttons (DEAR MAN, GIVE, FAST).
 *  3. Each script has the right number of lines (7 / 4 / 4).
 *  4. The draft text field is *optional* (the placeholder is
 *     "Optional. No one sees this but you.").
 *  5. No directive language in the screen (BPD-safe per audit §3.1).
 *
 * The research basis is Linehan 1993 chapter 10 (DBT Module 4)
 * and McKay et al. 2007 (DBT Skills Workbook). The three skills
 * are the standard DBT interpersonal-effectiveness acronyms:
 *   DEAR MAN — Describe / Express / Assert / Reinforce / (stay)
 *     Mindful / Appear confident / Negotiate. For asking.
 *   GIVE — Gentle / (act) Interested / Validate / Easy manner.
 *     For keeping a relationship OK.
 *   FAST — Fair / (no) Apologies / Stick to values / Truthful.
 *     For keeping self-respect.
 */
class InterpersonalFindingTest {

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/InterpersonalScreen.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertTrue(
            "AndroidManifest.xml must register .support.InterpersonalActivity",
            manifest.contains("android:name=\".support.InterpersonalActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen has 3 menu buttons (DEAR MAN, GIVE, FAST)`() {
        listOf(
            "interpersonal_dear_man_button",
            "interpersonal_give_button",
            "interpersonal_fast_button",
        ).forEach { key ->
            assertTrue(
                "InterpersonalScreen must call stringResource(R.string.$key)",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `DEAR MAN has 7 lines (D E A R M A N), GIVE has 4 (G I V E), FAST has 4 (F A S T)`() {
        // DEAR MAN: 7 lines (Describe / Express / Assert / Reinforce /
        //   Mindful / Appear confident / Negotiate)
        listOf("dear_man_d", "dear_man_e", "dear_man_a", "dear_man_r", "dear_man_m", "dear_man_an", "dear_man_n")
            .forEach { key ->
                assertTrue(
                    "InterpersonalScreen must render the DEAR MAN step $key",
                    screen.contains("R.string.$key"),
                )
            }
        // GIVE: 4 lines (Gentle / Interested / Validate / Easy manner)
        listOf("give_g", "give_i", "give_v", "give_e")
            .forEach { key ->
                assertTrue(
                    "InterpersonalScreen must render the GIVE step $key",
                    screen.contains("R.string.$key"),
                )
            }
        // FAST: 4 lines (Fair / Apologies / Stick to values / Truthful)
        listOf("fast_f", "fast_a", "fast_s", "fast_t")
            .forEach { key ->
                assertTrue(
                    "InterpersonalScreen must render the FAST step $key",
                    screen.contains("R.string.$key"),
                )
            }
    }

    @Test
    fun `draft text field is optional (BPD-safe per audit §3_1)`() {
        // The "no one sees this but you" line is the load-bearing
        // safety copy — the draft is *theirs*, optional, and
        // private. A regression that says "send this" or makes the
        // field required would flip this.
        assertTrue(
            "InterpersonalScreen must reference the optional-draft strings (BPD-safe per audit §3.1)",
            screen.contains("R.string.dear_man_draft_hint") &&
                screen.contains("R.string.give_draft_hint") &&
                screen.contains("R.string.fast_draft_hint"),
        )
    }

    @Test
    fun `strings xml defines all interpersonal keys (EN and TA)`() {
        listOf(
            "interpersonal_title",
            "interpersonal_caption",
            "interpersonal_dear_man_button",
            "interpersonal_give_button",
            "interpersonal_fast_button",
            "dear_man_title",
            "dear_man_caption",
            "dear_man_d", "dear_man_e", "dear_man_a", "dear_man_r",
            "dear_man_m", "dear_man_an", "dear_man_n",
            "dear_man_draft_label", "dear_man_draft_hint", "dear_man_done",
            "give_title", "give_caption",
            "give_g", "give_i", "give_v", "give_e",
            "give_draft_label", "give_draft_hint", "give_done",
            "fast_title", "fast_caption",
            "fast_f", "fast_a", "fast_s", "fast_t",
            "fast_draft_label", "fast_draft_hint", "fast_done",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }
}
