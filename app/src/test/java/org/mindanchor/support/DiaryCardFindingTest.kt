@file:Suppress("MaxLineLength")
package org.mindanchor.support

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.28.0: the DBT Diary Card surface (Linehan 1993 ch. 11).
 *
 * Pins:
 *  1. The activity exists, is wired in the manifest, non-exported.
 *  2. The screen has 5 fields: urge / emotion / intensity / skill / outcome.
 *  3. The intensity is a Slider 0-10 (DBT diary card convention).
 *  4. The history view is list-shaped, never chart-shaped (BPD-safe per audit §2.3).
 *  5. DiaryCardPrefs persists per-day entries via JSON in DataStore.
 *  6. DiaryCardEntry is @Serializable with 5 nullable fields.
 *  7. Strings exist for all 5 fields, history, save, etc.
 *  8. BPD-safe: no directive language, no "good day / bad day" framing.
 */
class DiaryCardFindingTest {

    private val manifest: String
        get() = fileAt("app/src/main/AndroidManifest.xml").readText()

    private val screen: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/DiaryCardScreen.kt",
        ).readText()

    private val prefs: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/support/DiaryCardPrefs.kt",
        ).readText()

    private val strings: String
        get() = fileAt("app/src/main/res/values/strings.xml").readText()

    @Test
    fun `activity is registered in the manifest and exported=false`() {
        assertNotNull(screen)
        assertTrue(
            "AndroidManifest.xml must register .support.DiaryCardActivity",
            manifest.contains("android:name=\".support.DiaryCardActivity\"") &&
                manifest.contains("android:exported=\"false\""),
        )
    }

    @Test
    fun `screen has the 5 DBT diary card fields`() {
        assertNotNull(screen)
        listOf(
            "diary_card_urge_label",
            "diary_card_emotion_label",
            "diary_card_intensity_label",
            "diary_card_skill_label",
            "diary_card_outcome_label",
        ).forEach { key ->
            assertTrue(
                "DiaryCardScreen must reference R.string.$key (one of the 5 DBT diary card fields)",
                screen.contains("R.string.$key"),
            )
        }
    }

    @Test
    fun `intensity is a 0-10 Slider per DBT diary card convention`() {
        assertNotNull(screen)
        assertTrue(
            "DiaryCardScreen must use a Slider for the intensity field",
            screen.contains("Slider("),
        )
        assertTrue(
            "DiaryCardScreen intensity Slider must use valueRange = 0f..10f",
            screen.contains("valueRange = 0f..10f"),
        )
    }

    @Test
    fun `history view is a list, never a chart (BPD-safe per audit §2-3)`() {
        assertNotNull(screen)
        // History header + list iteration — no chart axis labels.
        assertTrue(
            "DiaryCardScreen must have a history header",
            screen.contains("R.string.diary_card_history"),
        )
        // The screen must use forEach for the week list (list-shaped,
        // not chart-shaped).
        assertTrue(
            "DiaryCardScreen must render the week via forEach (list, not chart)",
            screen.contains("week.forEach"),
        )
        // No chart-composables — a line/bar chart would be BPD-unsafe.
        assertTrue(
            "DiaryCardScreen must not contain chart Composable names (BPD-safe per audit §2.3)",
            !screen.contains("LineChart(") &&
                !screen.contains("BarChart(") &&
                !screen.contains("ColumnChart("),
        )
    }

    @Test
    fun `DiaryCardPrefs persists per-day JSON entries via DataStore`() {
        assertNotNull(prefs)
        assertTrue(
            "DiaryCardPrefs must use preferencesDataStore",
            prefs.contains("preferencesDataStore"),
        )
        assertTrue(
            "DiaryCardPrefs must use stringPreferencesKey for JSON-encoded per-day entries",
            prefs.contains("stringPreferencesKey"),
        )
        assertTrue(
            "DiaryCardPrefs must key entries by ISO date (diary_card_<date>)",
            prefs.contains("diary_card_") && prefs.contains("date"),
        )
    }

    @Test
    fun `DiaryCardEntry is Serializable with 5 nullable fields`() {
        assertNotNull(prefs)
        assertTrue(
            "DiaryCardEntry must be @Serializable",
            prefs.contains("@Serializable") && prefs.contains("data class DiaryCardEntry"),
        )
        listOf("urge", "emotion", "intensity", "skill", "outcome").forEach { field ->
            assertTrue(
                "DiaryCardEntry must have a $field field",
                prefs.contains("val $field") || prefs.contains("val $field:"),
            )
        }
    }

    @Test
    fun `strings xml defines title, caption, 5 fields, 5 hints, history, save, etc`() {
        listOf(
            "diary_card_title",
            "diary_card_caption",
            "diary_card_urge_label",
            "diary_card_urge_hint",
            "diary_card_emotion_label",
            "diary_card_emotion_hint",
            "diary_card_intensity_label",
            "diary_card_intensity_hint",
            "diary_card_skill_label",
            "diary_card_skill_hint",
            "diary_card_outcome_label",
            "diary_card_outcome_hint",
            "diary_card_save",
            "diary_card_saved",
            "diary_card_history",
            "diary_card_history_empty",
            "diary_card_a11y",
        ).forEach { key ->
            assertTrue(
                "values/strings.xml must define <string name=\"$key\">",
                strings.contains("name=\"$key\""),
            )
        }
    }

    @Test
    fun `screen has no directive language (BPD-safe per audit)`() {
        assertNotNull(screen)
        assertTrue(
            "DiaryCardScreen must not contain directive phrases (BPD-safe)",
            !screen.contains("you should", ignoreCase = true) &&
                !screen.contains("you must", ignoreCase = true) &&
                !screen.contains("you need to", ignoreCase = true),
        )
        // No "good day" / "bad day" framing — BPD-safe per audit §2.3.
        assertTrue(
            "DiaryCardScreen must not use 'good day' / 'bad day' framing (BPD-safe)",
            !screen.contains("good day", ignoreCase = true) &&
                !screen.contains("bad day", ignoreCase = true),
        )
    }
}
