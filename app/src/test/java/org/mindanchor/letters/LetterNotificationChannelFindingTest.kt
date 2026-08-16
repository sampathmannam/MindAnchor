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
 * name (so the resource resolver picks the right value at
 * runtime).
 *
 * v0.25.19: the channel creation moved from LetterScheduler.kt
 * to org.mindanchor.notifications.Channels (centralised for
 * all 6 channels). The per-post `getNotificationChannel == null`
 * guard is GONE — `Channels.ensureAll(...)` is called once at
 * process start by `org.mindanchor.MindAnchorApp.onCreate`,
 * and `createNotificationChannel` is no longer called from
 * per-post sites.
 *
 * v0.30.0: removed the `values-ta strings has a Tamil
 * letters_channel_name localization` test (the Tamil
 * placeholder was deleted per the "no tamil needed"
 * directive). The English-only test
 * `values default strings has a letters_channel_name`
 * is the source-of-truth pin.
 *
 * The FindingTest pins each leg in turn. A future refactor
 * that bumps importance to `IMPORTANCE_HIGH` (loud alert,
 * wrong shape for a morning letter), or hard-codes the
 * channel name in a Kotlin literal, or moves the create call
 * back into LetterScheduler (scattering channel creation
 * again) flips the test red.
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
        // picks the right resource entry at runtime. A
        // regression to a hard-coded "Daily letter" literal
        // would freeze the channel name across all locales
        // and would not surface in any test that does not
        // exercise the system locale.
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
        // The English default lives in values/strings.xml.
        // v0.30.0: dropped the values-ta/ shadow reference
        // (the Tamil placeholder was deleted per the
        // "no tamil needed" directive). The default-key
        // test pins that the resource is declared, not
        // just referenced.
        assertTrue(
            "values/strings.xml must define <string name=\"letters_channel_name\">",
            stringsDefault.contains("name=\"letters_channel_name\""),
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
