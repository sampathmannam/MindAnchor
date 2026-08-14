@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.26.2: the letter notification channel.
 *
 * The channel was first introduced in v0.25.x; v0.26.2
 * confirms the shape — `IMPORTANCE_DEFAULT` (gentle morning
 * letter, not an alert), a string resource for the channel
 * name (so the Tamil localization in
 * `app/src/main/res/values-ta/strings.xml` is what a
 * Tamil-locale phone reads), and a one-time-create guard so
 * re-posts do not re-create the channel.
 *
 * The FindingTest pins each leg in turn. A future refactor
 * that bumps importance to `IMPORTANCE_HIGH` (loud alert,
 * wrong shape for a morning letter), or hard-codes the
 * channel name in a Kotlin literal (losing the Tamil
 * localization), or drops the getNotificationChannel guard
 * (creating the channel on every post — harmless but
 * pointless) flips the test red.
 */
class LetterNotificationChannelFindingTest {

    private fun fileAt(relative: String): File {
        val candidates = listOf(relative, "../$relative", "../../$relative")
        return candidates.map(::File).firstOrNull { it.isFile }
            ?: error("$relative not found from working directory ${File(".").absolutePath}.")
    }

    private val scheduler: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterScheduler.kt",
        ).readText()

    private val stringsDefault: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    private val stringsTa: String
        get() = fileAt(
            "app/src/main/res/values-ta/strings.xml",
        ).readText()

    @Test fun `LetterScheduler creates a NotificationChannel for the letters`() {
        // The channel ID is a private const; the public
        // surface is the notification itself. The FindingTest
        // pins that the createNotificationChannel call is in
        // place (a regression that drops it would mean
        // notifications on Android 8+ silently fail to
        // appear — exactly the kind of bug that does not
        // fail tests but fails users).
        assertTrue(
            "LetterScheduler must call createNotificationChannel",
            scheduler.contains("createNotificationChannel("),
        )
    }

    @Test fun `LetterScheduler channel importance is IMPORTANCE_DEFAULT (gentle, not high)`() {
        // The letter is a morning companion, not an alert.
        // IMPORTANCE_HIGH would put it in the heads-up
        // category and make it sound at 7am — wrong shape
        // for a gentle morning letter. The test pins the
        // default; a regression to IMPORTANCE_HIGH (a
        // refactor mistake that's easy to make when a
        // sister channel uses high) flips the test red.
        assertTrue(
            "LetterScheduler channel must use IMPORTANCE_DEFAULT (not HIGH)",
            // The exact call site is `NotificationManager.IMPORTANCE_DEFAULT`
            // inside the NotificationChannel ctor. The regex
            // anchors on the import-style call to avoid matching
            // unrelated references.
            scheduler.contains("NotificationManager.IMPORTANCE_DEFAULT"),
        )
        assertTrue(
            "LetterScheduler must NOT use IMPORTANCE_HIGH (gentle morning letter, not alert)",
            !scheduler.contains("IMPORTANCE_HIGH"),
        )
    }

    @Test fun `LetterScheduler channel name uses the localised string resource (R string letters_channel_name)`() {
        // The channel name is `context.getString(R.string.letters_channel_name)`,
        // not a Kotlin literal. Android's resource resolver
        // picks the right locale at runtime — a Tamil-locale
        // phone reads the Tamil value from
        // `values-ta/strings.xml` without any Kotlin change.
        // A regression to a hard-coded "Daily letter"
        // literal would break the Tamil localisation
        // silently.
        assertTrue(
            "LetterScheduler must use R.string.letters_channel_name (not a hard-coded literal) for the channel name",
            // Match the context.getString call shape
            Regex(
                """getString\(\s*R\.string\.letters_channel_name\s*\)""",
            ).containsMatchIn(scheduler),
        )
        assertTrue(
            "LetterScheduler must NOT hard-code an English channel name literal",
            !scheduler.contains("NotificationChannel(\n") ||
                // The literal that would be wrong is "Daily letter"
                // in a place that bypasses the string resource. A
                // grep for that string in the file, anywhere, is
                // a positive bug signal: a hard-coded literal in
                // the channel ctor.
                !scheduler.contains("\"Daily letter\""),
        )
    }

    @Test fun `values default strings has a letters_channel_name (English default)`() {
        // The English default lives in values/strings.xml;
        // the Tamil localization in values-ta/strings.xml.
        // The default-key test pins that the resource is
        // declared, not just referenced.
        assertTrue(
            "values/strings.xml must define <string name=\"letters_channel_name\">",
            stringsDefault.contains("name=\"letters_channel_name\""),
        )
    }

    @Test fun `values-ta strings has a Tamil letters_channel_name localization`() {
        // The Tamil localization is the user's experience on a
        // Tamil-locale phone. The test pins the file's
        // existence and the localisation entry; a future
        // refactor that drops values-ta/ (a real risk on a
        // non-translated project) flips the test red.
        val ta = stringsTa
        assertTrue(
            "values-ta/strings.xml must exist and be readable",
            ta.isNotBlank(),
        )
        assertTrue(
            "values-ta/strings.xml must define <string name=\"letters_channel_name\"> with a Tamil value",
            Regex(
                """<string\s+name="letters_channel_name">[^<]+</string>""",
            ).containsMatchIn(ta),
        )
        // The Tamil value must contain at least one Tamil
        // character (Unicode range U+0B80–U+0BFF). A
        // regression that pasted the English default into
        // values-ta/ would compile and even work, but the
        // Tamil user would see English. The test catches
        // that.
        val taChannelLine = Regex(
            """<string\s+name="letters_channel_name">([^<]+)</string>""",
        ).find(ta)?.groupValues?.get(1) ?: ""
        val hasTamilScript = taChannelLine.any { c -> c.code in 0x0B80..0x0BFF }
        assertTrue(
            "Tamil letters_channel_name must contain Tamil-script characters (U+0B80-U+0BFF). Value: $taChannelLine",
            hasTamilScript,
        )
    }

    @Test fun `LetterScheduler guards createNotificationChannel with getNotificationChannel (no-op on re-posts)`() {
        // v0.25.11 added the `getNotificationChannel(CHANNEL_ID) == null`
        // guard so the channel is created at most once per
        // install. A regression to bare
        // `createNotificationChannel(...)` on every post is
        // a no-op (Android dedupes) but pointless IO; the
        // guard is what the user-notification spec asks for.
        val guard = Regex(
            """if\s*\(\s*manager\.getNotificationChannel\(\s*CHANNEL_ID\s*\)\s*==\s*null\s*\)\s*\{[\s\S]*?createNotificationChannel\(""",
        ).containsMatchIn(scheduler)
        assertTrue(
            "LetterScheduler must guard createNotificationChannel with `if (manager.getNotificationChannel(CHANNEL_ID) == null)`",
            guard,
        )
    }
}
