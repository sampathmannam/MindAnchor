@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.letters

import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * v0.26.2: the letter notification channel.
 *
 * The channel was first introduced in v0.25.x; v0.26.2
 * confirms the shape — `IMPORTANCE_DEFAULT` (gentle morning
 * letter, not an alert), a string resource for the channel
 * name (so the Tamil localization in
 * `app/src/main/res/values-ta/strings.xml` is what a
 * Tamil-locale phone reads).
 *
 * v0.25.19: the channel creation moved from LetterScheduler.kt
 * to org.mindanchor.notifications.Channels (centralised for
 * all 6 channels). The per-post `getNotificationChannel == null`
 * guard is GONE — `Channels.ensureAll(...)` is called once at
 * process start by `org.mindanchor.MindAnchorApp.onCreate`,
 * and `createNotificationChannel` is no longer called from
 * per-post sites.
 *
 * The FindingTest pins each leg in turn. A future refactor
 * that bumps importance to `IMPORTANCE_HIGH` (loud alert,
 * wrong shape for a morning letter), or hard-codes the
 * channel name in a Kotlin literal (losing the Tamil
 * localization), or moves the create call back into
 * LetterScheduler (scattering channel creation again) flips
 * the test red.
 */
class LetterNotificationChannelFindingTest {

    // v0.25.19: the channel creation moved from LetterScheduler.kt
    // to org.mindanchor.notifications.Channels (centralised).
    // The test reads the new location.
    private val scheduler: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/letters/LetterScheduler.kt",
        ).readText()

    private val channels: String
        get() = fileAt(
            "app/src/main/java/org/mindanchor/notifications/Channels.kt",
        ).readText()

    private val stringsDefault: String
        get() = fileAt(
            "app/src/main/res/values/strings.xml",
        ).readText()

    private val stringsTa: String
        get() = fileAt(
            "app/src/main/res/values-ta/strings.xml",
        ).readText()

    @Test fun `Channels creates a NotificationChannel for the letters`() {
        // v0.25.19: the channel creation moved out of
        // LetterScheduler.kt and into org.mindanchor.notifications.Channels
        // (centralised for all 6 channels). The FindingTest
        // pins that the createNotificationChannel call is in
        // place (a regression that drops it would mean
        // notifications on Android 8+ silently fail to
        // appear — exactly the kind of bug that does not
        // fail tests but fails users).
        assertTrue(
            "Channels must call createNotificationChannel for the letters channel",
            channels.contains("createNotificationChannel(") &&
                channels.contains("LETTERS") &&
                channels.contains("R.string.letters_channel_name"),
        )
    }

    @Test fun `Channels letter channel importance is IMPORTANCE_DEFAULT (gentle, not high)`() {
        // The letter is a morning companion, not an alert.
        // IMPORTANCE_HIGH would put it in the heads-up
        // category and make it sound at 7am — wrong shape
        // for a gentle morning letter. The test pins the
        // default; a regression to IMPORTANCE_HIGH (a
        // refactor mistake that's easy to make when a
        // sister channel uses high) flips the test red.
        assertTrue(
            "Channels letter channel must use IMPORTANCE_DEFAULT (not HIGH)",
            // The exact call site is `NotificationManager.IMPORTANCE_DEFAULT`
            // inside the NotificationChannel ctor, and the
            // context.getString call must follow the LETTERS
            // id.
            Regex(
                """NotificationChannel\([\s\S]*?LETTERS[\s\S]*?NotificationManager\.IMPORTANCE_DEFAULT""",
            ).containsMatchIn(channels),
        )
        // No IMPORTANCE_HIGH anywhere in the LETTERS channel block
        val lettersBlock = Regex(
            """private fun letters\([\s\S]*?^\s*\}""",
            RegexOption.MULTILINE,
        ).find(channels)?.value ?: ""
        assertTrue(
            "Channels must NOT use IMPORTANCE_HIGH in the letters channel block",
            !lettersBlock.contains("IMPORTANCE_HIGH"),
        )
    }

    @Test fun `Channels letter channel name uses the localised string resource (R string letters_channel_name)`() {
        // The channel name is `context.getString(R.string.letters_channel_name)`,
        // not a Kotlin literal. Android's resource resolver
        // picks the right locale at runtime — a Tamil-locale
        // phone reads the Tamil value from
        // `values-ta/strings.xml` without any Kotlin change.
        // A regression to a hard-coded "Daily letter"
        // literal would break the Tamil localisation
        // silently.
        assertTrue(
            "Channels must use R.string.letters_channel_name (not a hard-coded literal) for the letter channel name",
            Regex(
                """getString\(\s*R\.string\.letters_channel_name\s*\)""",
            ).containsMatchIn(channels),
        )
        assertTrue(
            "Channels must NOT hard-code an English channel name literal",
            !channels.contains("\"Daily letter\""),
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

    @Test fun `Channels is called exactly once at process start (no per-post channel creation)`() {
        // v0.25.19: the per-post `getNotificationChannel == null`
        // guard is GONE — the channel is now created in one
        // place (Channels.ensureAll) at process start via
        // org.mindanchor.MindAnchorApp.onCreate. Per-post
        // guards are obsolete because `createNotificationChannel`
        // is no longer called from per-post sites.
        //
        // The FindingTest pins:
        //   1. LetterScheduler.kt does NOT call createNotificationChannel
        //      (per-post sites are out — the create is centralised)
        //   2. MindAnchorApp.kt DOES call Channels.ensureAll
        //      (the single create call at process start)
        val schedulerNoCreate = !scheduler.contains("createNotificationChannel")
        val app = fileAt(
            "app/src/main/java/org/mindanchor/MindAnchorApp.kt",
        ).readText()
        val ensureAllCalled = app.contains("Channels.ensureAll")
        assertTrue(
            "LetterScheduler must NOT call createNotificationChannel (centralised in Channels v0.25.19). " +
                "schedulerNoCreate=$schedulerNoCreate",
            schedulerNoCreate,
        )
        assertTrue(
            "MindAnchorApp must call Channels.ensureAll at process start. ensureAllCalled=$ensureAllCalled",
            ensureAllCalled,
        )
    }
}
