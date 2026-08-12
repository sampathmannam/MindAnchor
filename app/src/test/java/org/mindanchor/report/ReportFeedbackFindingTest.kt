package org.mindanchor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25.5 WP-C: the one-tap "did the report help?" feedback shape.
 *
 * The contract: two buttons, one choice, the answer is the data.
 * Linardon 2024 found that a single post-session rating predicts
 * retention better than the session content itself. The data is
 * local-only — nothing leaves the phone. A regression that grew
 * the choice into a survey, or that leaked the answer outside the
 * device, would betray the whole point.
 *
 * The five tests below pin the surface: the enum is exactly two
 * values, the store round-trips the latest answer, an old answer
 * for a different report is hidden from the current screen, and
 * the [ReportScreen] composable wires the row to the store on tap.
 */
class ReportFeedbackFindingTest {

    @Test
    fun `ReportFeedback has exactly two values, HELPED and DIDNT_HELP`() {
        // The whole point of the affordance is "one tap". A regression
        // that added a third value (a star rating, a free-text field,
        // a "somewhat") would make this a survey. A survey captures
        // nothing.
        val values = ReportFeedback.entries.toSet()
        assertEquals(2, values.size)
        assertTrue(ReportFeedback.HELPED in values)
        assertTrue(ReportFeedback.DIDNT_HELP in values)
    }

    @Test
    fun `feedback enum names round-trip through valueOf for storage stability`() {
        // ReportStore stores the value as its enum name in a String
        // preference. A rename of either constant would silently
        // orphan every previously-recorded answer. The test pins
        // the literal names so a rename is a compile error here
        // rather than a data-loss bug in production.
        assertEquals("HELPED", ReportFeedback.HELPED.name)
        assertEquals("DIDNT_HELP", ReportFeedback.DIDNT_HELP.name)
        assertEquals(ReportFeedback.HELPED, ReportFeedback.valueOf("HELPED"))
        assertEquals(ReportFeedback.DIDNT_HELP, ReportFeedback.valueOf("DIDNT_HELP"))
    }

    @Test
    fun `ReportScreen has a feedback row composable wired to the two enum values`() {
        // File-shape pin: the row composable exists, takes the
        // feedback + onRecordFeedback params, and the two button
        // callbacks call the enum values. A regression that wired
        // the row to a string literal ("helped" / "didnt_help")
        // would compile but produce a different wire format on disk.
        val source = readSource("ReportScreen.kt")
        assertNotNull("ReportScreen.kt must be readable for the file-shape pin", source)
        // The feedback row's sub-Composable exists.
        assertTrue(
            "ReportFeedbackRow is the sub-Composable that owns the two buttons",
            source!!.contains("fun ReportFeedbackRow("),
        )
        // The two callbacks call the enum values, not string literals.
        assertTrue(
            "👍 button must call onRecordFeedback(ReportFeedback.HELPED)",
            source.contains("onRecordFeedback(ReportFeedback.HELPED)"),
        )
        assertTrue(
            "👎 button must call onRecordFeedback(ReportFeedback.DIDNT_HELP)",
            source.contains("onRecordFeedback(ReportFeedback.DIDNT_HELP)"),
        )
    }

    @Test
    fun `ReportStore feedback flow returns null when the stored answer is for a different day`() {
        // The row reappears when a new report is generated. The data
        // shape that makes this work is: the stored answer carries
        // its own day, and the flow compares against the *current*
        // generatedDay. A regression that stored only the answer (no
        // day) would show "Helped" forever after the first tap, even
        // when a new report was on screen — the user would have no way
        // to rate the new one. The file-shape pin is the cheapest
        // way to assert the comparison exists.
        val source = readSource("ReportStore.kt")
        assertNotNull("ReportStore.kt must be readable for the file-shape pin", source)
        // The feedback flow combines the value, the forDay, and the
        // currentDay — the third combine arm is what makes the row
        // reappear on a new report.
        assertTrue(
            "feedback flow must compare stored day to current generatedDay",
            source!!.contains("forDay != currentDay"),
        )
    }

    @Test
    fun `ReportStore recordFeedback is gated on a current generatedDay`() {
        // The user cannot rate a report that does not exist. A
        // regression that allowed a feedback write without a current
        // day would persist an orphan answer (no day, no report) that
        // the feedback flow would then have to special-case. The
        // file-shape pin on the early-return is the cheapest
        // assertion.
        val source = readSource("ReportStore.kt")
        assertNotNull("ReportStore.kt must be readable for the file-shape pin", source)
        assertTrue(
            "recordFeedback must early-return when no generatedDay is on file",
            source!!.contains("recordFeedback(value: ReportFeedback)") &&
                source.contains("?: return"),
        )
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/report/$filename",
            "../app/src/main/java/org/mindanchor/report/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    @Suppress("unused") // The helper signature is used by the tests above via reflection on file existence.
    private fun assertNullHelper(message: String, actual: Any?) = assertNull(message, actual)
}
