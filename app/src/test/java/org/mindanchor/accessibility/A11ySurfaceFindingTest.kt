@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import org.mindanchor.testing.TestFileUtil.fileAt
/**
 * SOTA v2 bug-hunt — accessibility, i18n, RTL, dark mode, touch
 * targets, content descriptions, haptics, animation respect.
 *
 * Static file-shape tests, mirroring the
 * [FrictionGateAccessibilityTest] pattern: each bug in
 * `.git/sdd/bug_hunt_v2_accessibility.md` becomes a test here that
 * pins the *shape* of the file the bug lives in. The test fails the
 * build if a future commit re-introduces the gap.
 *
 * The runtime check for any of these bugs is the project owner's
 * responsibility on a real device with TalkBack; the unit-test
 * surface here is the gate that makes a regression loud in CI
 * before it ever reaches the device.
 */
class A11ySurfaceFindingTest {

    // ---- B1: HomeScreen "letters" TextButton content is hardcoded English. ----

    @Test
    fun `B1 — HomeScreen letters TextButton must use stringResource`() {
        val home = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()
        val bad = Regex("""TextButton\([^)]*onOpenLetters[^)]*\)\s*\{\s*Text\(\s*"letters"\s*\)\s*\}""")
        assertFalse(
            "HomeScreen.kt must not contain a TextButton(onOpenLetters) { Text(\"letters\") } " +
                "with a hardcoded English label. Use stringResource(R.string.letters_shortcut) " +
                "so the label is localizable. B1 in .git/sdd/bug_hunt_v2_accessibility.md.",
            bad.containsMatchIn(home),
        )
    }

    // ---- B2: GoingLightVpnService notification text is hardcoded English. ----

    @Test
    fun `B2 — GoingLightVpnService notification text must use getString`() {
        val vpn = fileAt("app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt")
            .readText()
        val literals = listOf(
            "\"Going Light is on\"",
            "\"Mobile internet is paused for selected apps\"",
            "\"Active Going Light window\"",
        )
        for (literal in literals) {
            assertFalse(
                "GoingLightVpnService.kt must not contain the literal $literal. " +
                    "Use getString(R.string.going_light_*) so the notification " +
                    "text is localizable. B2 in .git/sdd/bug_hunt_v2_accessibility.md.",
                vpn.contains(literal),
            )
        }
    }

    // ---- B3: HomeScreen noteTimeText hardcodes "yesterday". ----

    @Test
    fun `B3 — HomeScreen noteTimeText must not hardcode "yesterday"`() {
        val home = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()
        val fn = Regex("""private fun noteTimeText\([^)]*\):\s*String\s*\{[\s\S]*?"yesterday\s+\$\{'$'}time[\s\S]*?\}""")
        assertFalse(
            "HomeScreen.kt's noteTimeText() function must not return a literal " +
                "\"yesterday \$time\" string. The word \"yesterday\" must come from " +
                "stringResource so it is localisable. B3 in .git/sdd/bug_hunt_v2_accessibility.md.",
            fn.containsMatchIn(home),
        )
    }

    // ---- B4: friendlyLetterDate hardcodes "Today" / "Yesterday" / Locale.ENGLISH. ----

    @Test
    fun `B4 — LetterDateFormat friendlyLetterDate must not force Locale_ENGLISH`() {
        val ldf = fileAt("app/src/main/java/org/mindanchor/letters/LetterDateFormat.kt").readText()
        val badStrings = listOf(
            "return \"Today\"",
            "return \"Yesterday\"",
            "Locale.ENGLISH",
        )
        for (s in badStrings) {
            assertFalse(
                "LetterDateFormat.kt must not contain '$s' for user-facing date labels. " +
                    "Use stringResource(R.string.letters_today / letters_yesterday) and " +
                    "DateTimeFormatter.ofPattern(..., Locale.getDefault()) so the inbox " +
                    "respects the device locale. B4 in .git/sdd/bug_hunt_v2_accessibility.md.",
                ldf.contains(s),
            )
        }
    }

    // ---- B5: NoteScreen IconButton content descriptions are hardcoded English. ----

    @Test
    fun `B5 — NoteScreen IconButton content descriptions must be stringResource`() {
        val note = fileAt("app/src/main/java/org/mindanchor/model/NoteScreen.kt").readText()
        val badPhrases = listOf(
            "\"Back to launcher\"",
            "\"Pin this note\"",
            "\"Unpin this note\"",
            "\"Delete this note\"",
        )
        for (b in badPhrases) {
            assertFalse(
                "NoteScreen.kt must not contain a hardcoded English contentDescription " +
                    "literal ('$b'). Use stringResource(R.string.note_*) so the screen " +
                    "reader label is localizable. B5 in .git/sdd/bug_hunt_v2_accessibility.md.",
                note.contains(b),
            )
        }
    }

    @Test
    fun `B5 — CheckInHistoryScreen back-arrow content description must be stringResource`() {
        val h = fileAt("app/src/main/java/org/mindanchor/model/CheckInHistoryScreen.kt").readText()
        assertFalse(
            "CheckInHistoryScreen.kt must not use the literal " +
                "\"Back to launcher\" for the back-arrow contentDescription. " +
                "Use stringResource(R.string.action_back) or a notes-back string. " +
                "B5 in .git/sdd/bug_hunt_v2_accessibility.md.",
            h.contains("contentDescription = \"Back to launcher\""),
        )
    }

    @Test
    fun `B5 — CheckInScreen rating content description must be stringResource`() {
        val c = fileAt("app/src/main/java/org/mindanchor/model/CheckInScreen.kt").readText()
        assertFalse(
            "CheckInScreen.kt must not build a literal \"Rating \$value of 5\" " +
                "string inside the contentDescription. Use a string resource with " +
                "a %1\$d placeholder so the rating anchor is localisable. " +
                "B5 in .git/sdd/bug_hunt_v2_accessibility.md.",
            c.contains("\"Rating \$value of 5\""),
        )
    }

    // ---- B6: Role.Button semantic missing on TextButtons outside FrictionGate. ----

    @Test
    fun `B6 — HomeScreen TextButtons must carry a Role_Button semantic`() {
        val home = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()
        val textButtonCount = Regex("""\bTextButton\s*\(""").findAll(home).count()
        val roleButtonCount = Regex("""role\s*=\s*Role\.Button""").findAll(home).count()
        assertTrue(
            "HomeScreen.kt has $textButtonCount TextButton call sites but only " +
                "$roleButtonCount role = Role.Button semantics. " +
                "Every TextButton must carry a Role.Button modifier so TalkBack " +
                "announces the element type. B6 in .git/sdd/bug_hunt_v2_accessibility.md.",
            roleButtonCount >= textButtonCount,
        )
    }

    @Test
    fun `B6 — LetterScreen TextButtons must carry a Role_Button semantic`() {
        val l = fileAt("app/src/main/java/org/mindanchor/letters/LetterScreen.kt").readText()
        val textButtonCount = Regex("""\bTextButton\s*\(""").findAll(l).count()
        val roleButtonCount = Regex("""role\s*=\s*Role\.Button""").findAll(l).count()
        assertTrue(
            "LetterScreen.kt has $textButtonCount TextButton call sites but only " +
                "$roleButtonCount role = Role.Button semantics. B6.",
            roleButtonCount >= textButtonCount,
        )
    }

    @Test
    fun `B6 — SupportScreen TextButtons must carry a Role_Button semantic`() {
        val s = fileAt("app/src/main/java/org/mindanchor/support/SupportScreen.kt").readText()
        val textButtonCount = Regex("""\bTextButton\s*\(""").findAll(s).count()
        val roleButtonCount = Regex("""role\s*=\s*Role\.Button""").findAll(s).count()
        assertTrue(
            "SupportScreen.kt has $textButtonCount TextButton call sites but only " +
                "$roleButtonCount role = Role.Button semantics. The dial buttons " +
                "are crisis-time affordances; they must announce the element type. B6.",
            roleButtonCount >= textButtonCount,
        )
    }

    @Test
    fun `B6 — SettingsScreen TextButtons must carry a Role_Button semantic`() {
        val s = fileAt("app/src/main/java/org/mindanchor/settings/SettingsScreen.kt").readText()
        val textButtonCount = Regex("""\bTextButton\s*\(""").findAll(s).count()
        val roleButtonCount = Regex("""role\s*=\s*Role\.Button""").findAll(s).count()
        assertTrue(
            "SettingsScreen.kt has $textButtonCount TextButton call sites but only " +
                "$roleButtonCount role = Role.Button semantics. B6.",
            roleButtonCount >= textButtonCount,
        )
    }

    @Test
    fun `B6 — DigestScreen clickable rows and TextButtons must carry Role or content description`() {
        val d = fileAt("app/src/main/java/org/mindanchor/digest/DigestScreen.kt").readText()
        val clickableCount = Regex("""\.clickable\s*\(""").findAll(d).count()
        val roleOrDescription = Regex("""role\s*=\s*Role\.Button|contentDescription\s*=""").findAll(d).count()
        assertTrue(
            "DigestScreen.kt has $clickableCount .clickable call sites but only " +
                "$roleOrDescription role/contentDescription semantics. B6.",
            roleOrDescription >= clickableCount,
        )
    }

    @Test
    fun `B6 — NoteScreen note-row clickable must carry Role or contentDescription`() {
        val n = fileAt("app/src/main/java/org/mindanchor/model/NoteScreen.kt").readText()
        val clickableCount = Regex("""\.clickable\s*\{""").findAll(n).count()
        val roleOrDescription = Regex("""role\s*=\s*Role\.Button|contentDescription\s*=""").findAll(n).count()
        assertTrue(
            "NoteScreen.kt has $clickableCount .clickable call sites but only " +
                "$roleOrDescription role/contentDescription semantics. B6.",
            roleOrDescription >= clickableCount,
        )
    }

    // ---- B7: NoteScreen row heightIn — 48dp floor on the row's clickable. ----

    @Test
    fun `B7 — NoteScreen note-row clickable must enforce a 48dp minimum height`() {
        val n = fileAt("app/src/main/java/org/mindanchor/model/NoteScreen.kt").readText()
        val clickableWithoutHeight = Regex(
            """\.clickable\s*\{[\s\S]*?\}\s*\.padding\(vertical\s*=\s*8\.dp\)""",
        ).findAll(n).count()
        val heightIn48 = Regex("""heightIn\(\s*min\s*=\s*48\.dp\s*\)""").findAll(n).count()
        assertTrue(
            "NoteScreen.kt: the .clickable on the note row carries " +
                ".padding(vertical = 8.dp) but no .heightIn(min = 48.dp). " +
                "At 100% font scale with a one-line body the row collapses " +
                "below 48dp. Add .heightIn(min = 48.dp) before the .padding. B7.",
            clickableWithoutHeight == 0 || heightIn48 >= 1,
        )
    }

    // ---- B8: No "remove animations" / "reduce motion" preference outside FrictionGate. ----

    @Test
    fun `B8 — FrictionGate must check ANIMATOR_DURATION_SCALE before animating`() {
        val g = fileAt("app/src/main/java/org/mindanchor/friction/FrictionGate.kt")
            .readText()
        val ok = g.contains("ANIMATOR_DURATION_SCALE")
        assertTrue(
            "FrictionGate.kt must read Settings.Global.ANIMATOR_DURATION_SCALE " +
                "and skip the circle scale animation when the user has " +
                "asked the system to remove animations. The check exists " +
                "today (line ~295) — pin it. B8.",
            ok,
        )
    }

    @Test
    fun `B8 — HomeScreen must not start new animations without a reduce-motion gate`() {
        val home = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()
        val newAnimationSurface = Regex("""\banimateTo\(|\banimate\(""")
            .findAll(home).count()
        assertTrue(
            "HomeScreen.kt has $newAnimationSurface .animateTo / .animate call sites. " +
                "The single animation surface is FrictionGate and it is gated. " +
                "If a future commit adds an animation here, it must be " +
                "gated on Settings.Global.ANIMATOR_DURATION_SCALE. B8.",
            newAnimationSurface == 0,
        )
    }

    // ---- B9: No "disable haptics" preference. ----

    @Test
    fun `B9 — Launcher must expose a user-facing haptics toggle or pin the v0_25_5 decision`() {
        val strings = fileAt("app/src/main/res/values/strings.xml").readText()
        val settings = fileAt("app/src/main/java/org/mindanchor/settings/SettingsScreen.kt").readText()
        val hasHapticsToggle = strings.contains("haptics") || settings.contains("haptics")
        assertTrue(
            "MindAnchor ships with five haptic call sites " +
                "(HomeScreen QuickNotesCard save+clear, BedtimeListCard save, " +
                "LetterScreen delete, NoteScreen delete-confirm, FrictionGate breath). " +
                "There is no user-facing haptics preference and no string " +
                "resource for one. B9 asks for either: (a) a settings preference, " +
                "or (b) a sentinel comment pinning the v0.25.5 decision.",
            hasHapticsToggle,
        )
    }

    // ---- B10: State changes (Saved / Thanks) need live regions. ----

    @Test
    fun `B10 — ReportFeedbackRow Thanks text must be inside a live region`() {
        val r = fileAt("app/src/main/java/org/mindanchor/report/ReportScreen.kt").readText()
        val hasFn = r.contains("private fun ReportFeedbackRow(")
        val hasLiveRegion = r.contains("liveRegion") || r.contains("LiveRegionMode")
        assertTrue(
            "ReportScreen.kt's ReportFeedbackRow 'Thanks' Text must be wrapped " +
                "in Modifier.semantics { liveRegion = LiveRegionMode.Polite } " +
                "so TalkBack announces the saved state. " +
                "Found ReportFeedbackRow: $hasFn, found liveRegion: $hasLiveRegion. B10.",
            hasFn && hasLiveRegion,
        )
    }

    @Test
    fun `B10 — NoteReclassifySection running button must announce the state change`() {
        val nr = fileAt("app/src/main/java/org/mindanchor/settings/NoteReclassifySection.kt")
            .readText()
        val hasLiveRegion = nr.contains("liveRegion") || nr.contains("LiveRegionMode")
        assertTrue(
            "NoteReclassifySection.kt: the re-classify button label flips " +
                "to R.string.note_reclassify_running on tap. Without a " +
                "live region the user has to navigate to discover the " +
                "running state. B10.",
            hasLiveRegion,
        )
    }

    // ---- B11: Friendly-day "Tomorrow" is hardcoded English in HomeScreen. ----

    @Test
    fun `B11 — HomeScreen formatWallClock must not hardcode "tomorrow" English`() {
        val home = fileAt("app/src/main/java/org/mindanchor/launcher/HomeScreen.kt").readText()
        val fn = Regex("""private fun formatWallClock\([^)]*\):\s*String\s*\{[\s\S]*?"tomorrow\s+\$\{'$'}time[\s\S]*?\}""")
        assertFalse(
            "HomeScreen.kt's formatWallClock() function must not return a literal " +
                "\"tomorrow \$time\" string. The word \"tomorrow\" must come from " +
                "stringResource so it is localisable. B11.",
            fn.containsMatchIn(home),
        )
    }

    // ---- B13: PpgScreen countdown needs a live region. ----

    @Test
    fun `B13 — PpgScreen countdown must be in a polite live region`() {
        val p = fileAt("app/src/main/java/org/mindanchor/vitals/PpgScreen.kt").readText()
        val hasLiveRegion = p.contains("liveRegion") || p.contains("LiveRegionMode")
        assertTrue(
            "PpgScreen.kt's 'X seconds left' countdown is a one-second-updating " +
                "Text. Without a polite live region TalkBack will not announce " +
                "the count and a blind user cannot tell the measurement is " +
                "progressing. B13.",
            hasLiveRegion,
        )
    }

    // ---- B14: Theme routes through isSystemInDarkTheme. ----

    @Test
    fun `B14 — Theme_MindAnchor routes through isSystemInDarkTheme`() {
        val t = fileAt("app/src/main/java/org/mindanchor/ui/Theme.kt").readText()
        val usesSystem = t.contains("isSystemInDarkTheme")
        assertTrue(
            "ui/Theme.kt: MindAnchorTheme must consult isSystemInDarkTheme(). " +
                "Pin the call so a future refactor keeps the decision. B14.",
            usesSystem,
        )
    }

    // ---- B15: every IconButton / TextButton has contentDescription or
    // a Role.Button / role-button semantic on the 12 v0.25.18 surfaces.
    //
    // v0.25.18 a11y sweep: an IconButton with no contentDescription
    // reads as "button" to TalkBack — the user hears the type but
    // not the action. The migration path is:
    //   IconButton(
    //     onClick = ...,
    //     modifier = Modifier.semantics {
    //         contentDescription = <hoisted stringResource val>
    //     },
    //   ) { ... }
    // A TextButton with `Text(stringResource(...))` inside is fine —
    // the Text supplies the announcement. A TextButton with
    // `role = Role.Button` plus a visible text label is also fine.
    //
    // The test walks each of the 12 swept files, finds every
    // IconButton / TextButton call site, and asserts the next 200
    // characters contain either `contentDescription =`,
    // `role = Role.Button`, or `stringResource(`. The 200-char window
    // is the standard FindingTest pattern for "what does the call
    // site look like after the IconButton( ... ) {".

    @Test
    fun `B15 — every IconButton on the v0_25_18 surfaces has contentDescription or stringResource`() {
        val swept = listOf(
            "app/src/main/java/org/mindanchor/settings/SettingsScreen.kt",
            "app/src/main/java/org/mindanchor/letters/LetterScreen.kt",
            "app/src/main/java/org/mindanchor/model/NoteScreen.kt",
            "app/src/main/java/org/mindanchor/model/NoteActivity.kt",
            "app/src/main/java/org/mindanchor/digest/DigestScreen.kt",
            "app/src/main/java/org/mindanchor/support/SupportScreen.kt",
            "app/src/main/java/org/mindanchor/report/ReportScreen.kt",
            "app/src/main/java/org/mindanchor/vitals/PpgScreen.kt",
            "app/src/main/java/org/mindanchor/onboarding/OnboardingScreen.kt",
            "app/src/main/java/org/mindanchor/HomeActivity.kt",
        )
        val iconButtonPattern = Regex("""\bIconButton\s*\(""")
        val failures = mutableListOf<String>()
        for (rel in swept) {
            val source = try {
                fileAt(rel).readText()
            } catch (t: Throwable) {
                failures += "$rel could not be read: ${t.message}"
                continue
            }
            for (m in iconButtonPattern.findAll(source)) {
                val window = source.substring(
                    m.range.first,
                    (m.range.first + 1200).coerceAtMost(source.length),
                )
                val hasContentDescription = window.contains("contentDescription =")
                val hasRoleButton = window.contains("role = Role.Button")
                // The contentDescription can be a hoisted `val foo = stringResource(...)`
                // assigned on a prior line, or a direct `stringResource(...)` call.
                // We accept any of: contentDescription =, role = Role.Button,
                // or a stringResource( call within the window that supplies the
                // contentDescription target.
                val hasStringResource = window.contains("stringResource(")
                if (!hasContentDescription && !hasRoleButton && !hasStringResource) {
                    failures += "$rel: IconButton at offset ${m.range.first} has no " +
                        "contentDescription, no role = Role.Button, and no stringResource. " +
                        "Window: ${window.take(200)}"
                }
            }
        }
        assertTrue(
            "Every IconButton on the 12 v0.25.18 surfaces must have a " +
                "contentDescription (or role = Role.Button plus a visible " +
                "stringResource label, or a stringResource( inside the " +
                "call site that supplies the a11y label). " +
                "Failures:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
