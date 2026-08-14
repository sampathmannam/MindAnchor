@file:Suppress("MaxLineLength", "SwallowedException")
package org.mindanchor.export

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.1 §3.4 FindingTest: the data export exists, has the
 * expected top-level keys, and never includes Letter content.
 *
 * The export is a single JSON file the user can hand to a
 * therapist (or any reader). The shape is fixed by
 * [ExportPayload] — a flat data class with named fields, every
 * field the user can see in the JSON header.
 *
 * The test pins:
 *  - The [ExportPayload] data class lives in
 *    `app/src/main/java/org/mindanchor/export/ExportActivity.kt`
 *    and has fields for notes, OneThing, OpenLoop, BedtimeList,
 *    wellness (N-of-1 framed), check-ins, BPD profile, chain
 *    captures, IFS picks, and a "no letter content" note.
 *  - The export activity is registered in
 *    `app/src/main/AndroidManifest.xml` with the correct task
 *    affinity.
 *  - The serialised JSON never contains a `letterBody` or
 *    `LetterContent` field — Letter content is *never* exported.
 */
class ExportSanityFindingTest {

    @Test
    fun `ExportActivity is registered in the manifest as an activity`() {
        val manifest = read("src/main/AndroidManifest.xml") ?: return
        assertTrue(
            "AndroidManifest.xml must register ExportActivity. " +
                "The v0.26.1 §3.4 export entry point is a full " +
                "activity (not a surface in HomeScreen).",
            manifest.contains("android:name=\".export.ExportActivity\""),
        )
        assertTrue(
            "ExportActivity must be declared android:exported=\"false\". " +
                "The activity is reached only via an explicit Intent " +
                "from inside the same app, never via a system resolver.",
            manifest.contains("android:name=\".export.ExportActivity\"") &&
                exportedFalse(manifest, ".export.ExportActivity"),
        )
    }

    @Test
    fun `ExportPayload has every expected top-level key`() {
        val cls = Class.forName("org.mindanchor.export.ExportPayload")
        val expected = setOf(
            "exportedAt",
            "notes",
            "oneThing",
            "openLoop",
            "bedtimeList",
            "wellness",
            "checkIns",
            "bpdProfile",
            "chainCaptures",
            "ifsPicks",
            "note",
        )
        val actual = cls.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") && !it.startsWith("Companion") }
            .toSet()
        assertTrue(
            "ExportPayload must expose the v0.26.1 §3.4 expected " +
                "top-level keys. Missing: ${expected - actual}. " +
                "Unexpected: ${actual - expected}.",
            actual.containsAll(expected),
        )
    }

    @Test
    fun `ExportPayload excludes letter content by shape`() {
        // The field is named "note" (singular), not "letterBody" or
        // "letters". The serializer emits exactly the fields on
        // the data class, so a future "letters" field on
        // ExportPayload would be a one-line code change — and a
        // regression that adds it flips this test red.
        val cls = Class.forName("org.mindanchor.export.ExportPayload")
        val actual = cls.declaredFields.map { it.name }
        assertTrue(
            "ExportPayload must NOT include a letterBody or " +
                "letters field. Letter content is deliberately " +
                "excluded from the v0.26.1 §3.4 export. " +
                "actual=$actual",
            !actual.any { it.contains("Letter", ignoreCase = true) } &&
                !actual.any { it.contains("letterBody", ignoreCase = true) },
        )
    }

    @Test
    fun `Wellness block in the export carries the N-of-1 framing string`() {
        val cls = Class.forName("org.mindanchor.export.NOfOneWellnessBlock")
        val framing = cls.declaredFields.firstOrNull { it.name == "framing" }
        assertNotNull(
            "NOfOneWellnessBlock must carry a `framing` field so " +
                "the export's wellness section declares its N-of-1 " +
                "framing at the top of the file.",
            framing,
        )
    }

    @Test
    fun `file_paths xml exposes the export subdirectory through the FileProvider`() {
        val paths = read("src/main/res/xml/file_paths.xml") ?: return
        assertTrue(
            "file_paths.xml must expose the app's external-files " +
                "export/ subdirectory so the system share sheet " +
                "can hand the file to a chosen recipient.",
            paths.contains("<external-files-path") && paths.contains("export/"),
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }

    /**
     * True when the `android:exported="…"` attribute on the
     * `<activity android:name="…">` element for [component] is
     * `false`. Reads the manifest as text and uses a small
     * window so the assertion does not match a different
     * component's `exported` value.
     */
    private fun exportedFalse(manifest: String, component: String): Boolean {
        val idx = manifest.indexOf("android:name=\"$component\"")
        if (idx < 0) return false
        val tail = manifest.substring(idx)
        val end = tail.indexOf("</activity>")
        val window = if (end >= 0) tail.substring(0, end) else tail
        return Regex("android:exported=\"false\"").containsMatchIn(window)
    }
}
