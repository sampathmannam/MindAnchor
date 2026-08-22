/*
 * SupportOrderFindingTest.kt — pins the in-the-moment → reflective
 * order of the entries in SupportScreen's "More moments" group.
 *
 * v0.28.2 finding: while driving v0.28.0–v0.28.1 on the phone
 * (Motorola ZD2232FCR5), the Support screen rendered the v0.27.0
 * entries (Self-compassion break, Radical acceptance, Interpersonal
 * skills = DEAR MAN / GIVE / FAST) BEFORE the v0.28.0 entries
 * (Opposite Action, Distress Thermometer, ACCEPTS, Letter to a
 * Part, Diary Card). The v0.28.0 design spec
 * (docs/superpowers/specs/2026-08-15-v0.28.0-bpd-strict-design.md
 * §D) said the in-the-moment skills must come first, reflective
 * skills later — the opposite of what shipped.
 *
 * Why this matters for the target user: a person in crisis who has
 * just scrolled past the DBT crisis skills (STOP / TIPP / 5-4-3-2-1)
 * is still in a distressed window. The reflective practices
 * (Self-compassion, Radical acceptance, Diary Card) need a person
 * who has already settled. Putting them before the in-the-moment
 * skills makes the wrong surface the closest tap.
 *
 * The fix in v0.28.2: move the v0.27.0 reflective entries to AFTER
 * the v0.28.0 in-the-moment entries.
 *
 * v0.29.0: ACT values clarification (Hayes 2004) is added at the
 * end of the in-the-moment → reflective group — the *slowest*
 * reflective practice (per docs/research/14-v0.26.6-audit.md
 * §3.5). The intended order is:
 *   1. Opposite Action (v0.28.0)
 *   2. Distress Thermometer (v0.28.0)
 *   3. ACCEPTS (v0.28.0)
 *   4. Letter to a Part (v0.28.0)
 *   5. Self-compassion break (v0.27.0)
 *   6. Radical acceptance (v0.27.0)
 *   7. DBT Diary Card (v0.28.0)
 *   8. Interpersonal skills = DEAR MAN / GIVE / FAST (v0.27.0)
 *   9. ACT values (v0.29.0)
 *
 * These tests pin the order as a positive shape (must appear in
 * this sequence) — a regression that puts any reflective entry
 * (Self-compassion, Radical acceptance, Interpersonal) before an
 * in-the-moment entry (Opposite Action, Distress Thermometer,
 * ACCEPTS, Letter to a Part) flips the test red with a clear
 * per-step failure message.
 */
@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
class SupportOrderFindingTest {
    private val supportScreenPath =
        "app/src/main/java/org/mindanchor/support/SupportScreen.kt"

    private fun source(): String {
        val file = fileAt(supportScreenPath)
        assertTrue(
            "SupportScreen.kt must exist at $supportScreenPath — " +
                "if you moved the file, update this test path too.",
            file.exists(),
        )
        return file.readText()
    }

    /**
     * Locate the byte offset of the `TextButton` call site for a
     * given activity. We search for `::class.java,` inside the
     * `context.startActivity(Intent(... <Activity>::class.java, ...`
     * pattern; that uniquely matches the startActivity block for
     * a given support entry. The indexOf call scopes to the
     * substring starting at the Intent, which is always inside
     * the per-entry TextButton onClick.
     *
     * If the activity is not referenced in the file, returns -1
     * and the corresponding test will fail with a "must contain"
     * message.
     */
    private fun textButtonIndex(source: String, activityClass: String): Int {
        // Match "context.startActivity(Intent(<whitespace>
        // context, org.mindanchor.support.<activity>::class.java,
        val pattern = Regex(
            """context\.startActivity\(\s*Intent\(\s*context,\s*org\.mindanchor\.support\.$activityClass::class\.java,""",
        )
        val match = pattern.find(source) ?: return -1
        // Walk backwards to the enclosing TextButton( — the
        // activity intent is nested inside a `runCatching {
        // context.startActivity(...) }` block inside the
        // TextButton onClick lambda. We want the byte offset of
        // the startActivity call as a proxy for the TextButton
        // call site, since startActivity is the unique per-entry
        // marker.
        return match.range.first
    }

    @Test
    fun `in-the-moment skills come before reflective skills in SupportScreen`() {
        val s = source()
        // Per-entry order in the "More moments" group, top to
        // bottom as the user reads the screen.
        val expected = listOf(
            "OppositeActionActivity" to "Opposite Action (v0.28.0, in-the-moment)",
            "DistressThermometerActivity" to "Distress Thermometer (v0.28.0, in-the-moment)",
            "AcceptsActivity" to "ACCEPTS (v0.28.0, in-the-moment)",
            "LetterToPartActivity" to "Letter to a Part (v0.28.0, in-the-moment)",
            "SelfCompassionActivity" to "Self-compassion break (v0.27.0, reflective)",
            "RadicalAcceptanceActivity" to "Radical acceptance (v0.27.0, reflective)",
            "DiaryCardActivity" to "DBT Diary Card (v0.28.0, reflective)",
            "InterpersonalActivity" to "Interpersonal = DEAR MAN/GIVE/FAST (v0.27.0, reflective)",
            "ValuesActivity" to "ACT values (v0.29.0, reflective, slowest)",
        )
        val positions = expected.map { (cls, label) -> cls to (textButtonIndex(s, cls) to label) }
        // Negative: missing activity
        for ((cls, pos) in positions) {
            assertTrue(
                "SupportScreen.kt must contain a TextButton that opens " +
                    "$cls but no such startActivity(Intent(... $cls::class.java, ...) " +
                    "block was found. Check the support_more_skills Section rendering.",
                pos.first >= 0,
            )
        }
        // Order: positions must be strictly increasing
        for (i in 1 until positions.size) {
            val (prevCls, prev) = positions[i - 1]
            val (currCls, curr) = positions[i]
            assertTrue(
                "SupportScreen.kt renders the entries in the wrong order. " +
                    "Expected '$curr' to come AFTER '$prev' (in-the-moment → reflective per " +
                    "docs/superpowers/specs/2026-08-15-v0.28.0-bpd-strict-design.md §D), " +
                    "but '$prev' appears at byte offset ${prev.first} and '$curr' at " +
                    "${curr.first}. Reflective practices (Self-compassion, Radical " +
                    "acceptance, Diary Card, Interpersonal) must come AFTER in-the-moment " +
                    "practices (Opposite Action, Distress Thermometer, ACCEPTS, Letter to " +
                    "a Part) so a person in crisis sees the right surface closest to the " +
                    "top of 'More moments'.",
                curr.first > prev.first,
            )
        }
    }

    @Test
    fun `no reflective entry appears before any in-the-moment entry`() {
        // Stronger negative pin: a regression that re-orders even
        // ONE reflective entry (Self-compassion / Radical /
        // DiaryCard / Interpersonal) ahead of an in-the-moment
        // entry (Opposite / Distress / ACCEPTS / Letter) trips
        // this test, regardless of how the rest of the list is
        // ordered. The previous test only checks the full
        // expected sequence; this one checks the partition.
        val s = source()
        val inTheMoment = listOf(
            "OppositeActionActivity",
            "DistressThermometerActivity",
            "AcceptsActivity",
            "LetterToPartActivity",
        )
        val reflective = listOf(
            "SelfCompassionActivity",
            "RadicalAcceptanceActivity",
            "DiaryCardActivity",
            "InterpersonalActivity",
        )
        val itmPositions = inTheMoment.map { it to textButtonIndex(s, it) }
        val reflPositions = reflective.map { it to textButtonIndex(s, it) }
        for ((reflCls, reflPos) in reflPositions) {
            if (reflPos < 0) continue // previous test catches the missing-activity case
            for ((itmCls, itmPos) in itmPositions) {
                if (itmPos < 0) continue
                assertTrue(
                    "SupportScreen.kt places the reflective entry $reflCls " +
                        "(byte offset $reflPos) BEFORE the in-the-moment entry " +
                        "$itmCls (byte offset $itmPos). The in-the-moment → " +
                        "reflective ordering per the v0.28.0 spec means every " +
                        "in-the-moment entry must come before every reflective " +
                        "entry in the 'More moments' group.",
                    reflPos > itmPos,
                )
            }
        }
    }

    @Test
    fun `all eight 'More moments' activities are still wired in SupportScreen`() {
        // Coverage pin: removing any of the 8 entries from the
        // support_more_skills Section flips this test red. The
        // v0.28.2 fix is a pure reorder, not a removal.
        val s = source()
        val all = listOf(
            "OppositeActionActivity",
            "DistressThermometerActivity",
            "AcceptsActivity",
            "LetterToPartActivity",
            "SelfCompassionActivity",
            "RadicalAcceptanceActivity",
            "DiaryCardActivity",
            "InterpersonalActivity",
        )
        for (cls in all) {
            assertTrue(
                "SupportScreen.kt must still wire up $cls in the 'More moments' " +
                    "group. The v0.28.2 fix is a reorder, not a removal.",
                s.contains("$cls::class.java"),
            )
        }
    }
}
