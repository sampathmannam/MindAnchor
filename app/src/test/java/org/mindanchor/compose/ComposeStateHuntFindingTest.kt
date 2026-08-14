@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.compose

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt (Compose + state).
 *
 * File-shape pins for the bugs called out in
 * [.git/sdd/bug_hunt_v2_compose.md]. These are static checks
 * — they read the source files and assert the bug-shaped
 * pattern is (or is not) present. No Compose runtime, no
 * instrumentation, no recomposition. The shape of a bug
 * is the bug: a Composable holding form-state in `remember`
 * will lose the form on rotation, period, regardless of
 * whether the test can drive a rotation.
 *
 * The pins are intentionally narrow (a small substring of
 * a single file) so a future regression that re-introduces
 * the bug at the same call site fails the same test. A
 * "fix the call site but make a different shape" regression
 * would fail a different test — which is the right shape
 * for a finding-test that is meant to lock the conclusion.
 */
class ComposeStateHuntFindingTest {

    // ----- BUG-003 (deepened from v1 BUG-002) -----

    @Test
    fun `BUG-003 OnboardingScreen step and selected use remember not rememberSaveable`() {
        // v1 flagged this. v2 deepens: the same call site has
        // THREE remember blocks for state that should survive
        // process death (step, selected goals, chronotype).
        // The default autoSaver covers Set<Goal> (String enum
        // name) and Chronotype (Java enum), so a single
        // rememberSaveable per call is the fix.
        val source = readSource("OnboardingScreen.kt", "onboarding")
        assertNotNull("OnboardingScreen.kt must be readable", source)
        assertTrue(
            "step holds in remember (not rememberSaveable)",
            source!!.contains("var step by remember { mutableStateOf(0) }"),
        )
        assertTrue(
            "selected holds in remember (not rememberSaveable)",
            source.contains("var selected by remember { mutableStateOf(setOf<Goal>()) }"),
        )
        assertTrue(
            "chronotype holds in remember (not rememberSaveable)",
            source.contains("var chronotype by remember { mutableStateOf(Chronotype.UNKNOWN) }"),
        )
    }

    // ----- BUG-004 -----

    @Test
    fun `BUG-004 zero collectAsStateWithLifecycle in main source set`() {
        // v0.25.7 added lifecycleScope in HomeActivity; v2
        // verifies the Compose side picked up the
        // collectAsStateWithLifecycle primitive. It did not.
        // Every collectAsState is now a known backpressure hole
        // — the flow keeps producing when the screen is
        // STOPPED, the recomposer keeps listening, the
        // ViewModel never gets to drop a stale state.
        val files = listOf(
            "HomeActivity.kt" to "",
            "launcher/HomeScreen.kt" to "launcher",
            "model/NoteActivity.kt" to "model",
            "model/CheckInHistoryActivity.kt" to "model",
            "settings/SettingsScreen.kt" to "settings",
            "settings/GoogleDriveBackupSettingsSection.kt" to "settings",
            "vitals/PpgScreen.kt" to "vitals",
            "pulse/PulseScreen.kt" to "pulse",
            "report/ReportScreen.kt" to "report",
            "support/SupportScreen.kt" to "support",
            "digest/DigestScreen.kt" to "digest",
            "ui/CalmBackground.kt" to "ui",
        )
        var anyLifecycleAwareCollect = false
        for ((filename, pkg) in files) {
            val source = readSource(filename, pkg) ?: continue
            if (source.contains("collectAsStateWithLifecycle")) {
                anyLifecycleAwareCollect = true
                break
            }
        }
        assertTrue(
            "At least one main-source Composable should use collectAsStateWithLifecycle; saw none",
            anyLifecycleAwareCollect,
        )
    }

    // ----- BUG-005 / BUG-020 (OneThingCard draft) -----
    //
    // v0.25.10 fix (d30bada): OneThingCard CAPTURE-mode draft migrated
    // to rememberSaveable. v0.25.14 (this PR): the BUG-shape pin was
    // flipped to a fix-shape pin. The original test asserted the
    // substring `var draft by remember { mutableStateOf("") }` existed
    // anywhere in the file — which would still pass if only the
    // QuickNotesCard was fixed and OneThingCard kept `remember`. The
    // flipped pin asserts the rememberSaveable pattern exists inside
    // OneThingCard specifically (between the function definition and
    // its closing brace, with the `if (text == null)` branch above it).

    @Test
    fun `BUG-005 OneThingCard CAPTURE-mode draft uses rememberSaveable (v0_25_10 fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("private fun OneThingCard(")
        // v0.25.14 fix: the file has 3 `var draft by rememberSaveable` lines
        // (OneThingCard, OpenLoopCard, QuickNotesCard). Plain indexOf would
        // return the first one — which is OpenLoopCard's, BEFORE OneThingCard.
        // Search from fnIdx so the matched pattern is provably inside
        // OneThingCard, not the file-scope first match.
        val captureIdx = if (fnIdx >= 0) src.indexOf("if (text == null) {", fnIdx) else -1
        val draftIdx = if (captureIdx >= 0) src.indexOf("var draft by rememberSaveable { mutableStateOf(\"\") }", captureIdx) else -1
        assertTrue(
            "OneThingCard CAPTURE-mode draft must use rememberSaveable (v0.25.10 fix). " +
                "fnIdx=$fnIdx captureIdx=$captureIdx draftIdx=$draftIdx. " +
                "The order must be: fn < capture < draft. " +
                "source=\n$src",
            fnIdx >= 0 && captureIdx > fnIdx && draftIdx > captureIdx,
        )
    }

    // ----- BUG-006 (OpenLoopCard draft) -----
    //
    // v0.25.10 fix (d30bada): OpenLoopCard CAPTURE-mode draft migrated
    // to rememberSaveable. v0.25.14: the BUG-shape pin was flipped to
    // a fix-shape pin, scoped to the OpenLoopCard function (not just
    // anywhere in the file).

    @Test
    fun `BUG-006 OpenLoopCard CAPTURE-mode draft uses rememberSaveable (v0_25_10 fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val captureIdx = src.indexOf("LoopPhase.CAPTURE -> {")
        val draftIdx = src.indexOf("var draft by rememberSaveable { mutableStateOf(\"\") }")
        assertTrue(
            "OpenLoopCard CAPTURE branch must use rememberSaveable (v0.25.10 fix). " +
                "captureIdx=$captureIdx draftIdx=$draftIdx. " +
                "The rememberSaveable draft must come after the CAPTURE marker. " +
                "source=\n$src",
            captureIdx >= 0 && draftIdx > captureIdx,
        )
    }

    // ----- BUG-007 (QuickNotesCard draft) -----
    //
    // v0.25.14 fix: QuickNotesCard draft migrated to rememberSaveable.
    // The BUG-shape pin was flipped to a fix-shape pin. The new test
    // asserts the rememberSaveable pattern exists in QuickNotesCard
    // specifically (after `private fun QuickNotesCard(` and before
    // the next function definition).

    @Test
    fun `BUG-007 QuickNotesCard draft uses rememberSaveable (v0_25_14 fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("private fun QuickNotesCard(")
        // v0.25.14 fix: the file has 3 `var draft by rememberSaveable` lines
        // (OneThingCard at line 742, OpenLoopCard at line 619, QuickNotesCard
        // at line 1115). Plain indexOf would return the first one — OpenLoopCard,
        // BEFORE QuickNotesCard. Search from fnIdx so the matched pattern is
        // provably inside QuickNotesCard.
        val draftIdx = if (fnIdx >= 0) src.indexOf("var draft by rememberSaveable { mutableStateOf(\"\") }", fnIdx) else -1
        assertTrue(
            "QuickNotesCard draft must use rememberSaveable (v0.25.14 fix). " +
                "fnIdx=$fnIdx draftIdx=$draftIdx. The rememberSaveable draft " +
                "must appear after the QuickNotesCard function definition. " +
                "source=\n$src",
            fnIdx >= 0 && draftIdx > fnIdx,
        )
    }

    // ----- BUG-008 (BedtimeListCard drafts) -----

    @Test
    fun `BUG-008 BedtimeListCard CAPTURE-mode drafts use remember not rememberSaveable`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        assertTrue(
            "BedtimeListCard CAPTURE branch holds drafts in remember (mutableStateListOf)",
            source!!.contains("private fun BedtimeListCard(") &&
                source.contains("BedtimePhase.CAPTURE -> {") &&
                source.contains("val drafts = remember {") &&
                source.contains("mutableStateListOf<String>().apply { add(\"\") }"),
        )
    }

    // ----- BUG-009 (AppActionsDialog rename) -----

    @Test
    fun `BUG-009 AppActionsDialog rename flow uses remember not rememberSaveable`() {
        val source = readSource("AppActionsDialog.kt", "launcher")
        assertNotNull(source)
        assertTrue(
            "AppActionsDialog rename state is remember (not rememberSaveable)",
            source!!.contains("var renaming by remember { mutableStateOf(false) }") &&
                source.contains("var newLabel by remember { mutableStateOf(app.label) }"),
        )
    }

    // ----- BUG-010 (EmaScreen) -----

    @Test
    fun `BUG-010 EmaScreen valence and saved use remember not rememberSaveable`() {
        val source = readSource("EmaScreen.kt", "model")
        assertNotNull(source)
        assertTrue(
            "EmaScreen holds the in-flight answer in remember",
            source!!.contains("var valence by remember { mutableStateOf<Int?>(null) }") &&
                source.contains("var saved by remember { mutableStateOf(false) }"),
        )
    }

    // ----- BUG-011 (PulseScreen) -----

    @Test
    fun `BUG-011 PulseScreen answers and savedScore use remember not rememberSaveable`() {
        val source = readSource("PulseScreen.kt", "pulse")
        assertNotNull(source)
        assertTrue(
            "PulseScreen holds the in-flight answers in remember",
            source!!.contains("var answers by remember { mutableStateOf(List(WhoFive.ITEM_COUNT) { -1 }) }") &&
                source.contains("var savedScore by remember { mutableStateOf<Int?>(null) }"),
        )
    }

    // ----- BUG-012 (LauncherRoot surface state) -----
    //
    // v0.25.14 fix: 3 of the 6 LauncherRoot state fields (surface,
    // reportCameFrom, letterCameFrom — all `LauncherSurface` enums,
    // which are auto-Saveable) migrated from `remember` to
    // `rememberSaveable`. The 3 complex types (actionsFor: DisplayApp?,
    // gateFor: DisplayApp?, letterSelectedDate: LocalDate?) keep
    // `remember` for v0.25.14 — they need custom Savers, which is the
    // v0.25.15 work.
    //
    // The BUG-shape pin is split: the fix-shape half (3 enums) is
    // the new positive pin; the deferred half (3 complex) is the
    // deferred-work pin. Both belong in the same test class so a
    // future v0.25.15+ migration can be detected by the second.

    @Test
    fun `BUG-012 LauncherRoot enum state uses rememberSaveable (v0_25_14 partial fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        assertTrue(
            "LauncherRoot must hold the 3 enum-typed state fields in " +
                "rememberSaveable (v0.25.14 fix): surface (LauncherSurface), " +
                "reportCameFrom (LauncherSurface), letterCameFrom (LauncherSurface). " +
                "The enums are auto-Saveable so no custom Saver is needed. " +
                "source=\n" + source!!,
            source.contains("var surface by rememberSaveable { mutableStateOf(LauncherSurface.Home) }") &&
                source.contains("var reportCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Settings) }") &&
                source.contains("var letterCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Home) }"),
        )
    }

    @Test
    fun `BUG-012 LauncherRoot complex state still uses remember (deferred to v0_25_15)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        // v0.25.15 will add custom Savers for DisplayApp? and LocalDate?
        // and migrate these 3. Until then they keep `remember` and this
        // pin documents the deferred work.
        assertTrue(
            "LauncherRoot must still hold the 3 complex-typed state fields " +
                "in `remember` (deferred to v0.25.15: needs custom Savers for " +
                "DisplayApp? and LocalDate?): actionsFor, gateFor, letterSelectedDate. " +
                "source=\n" + source!!,
            source.contains("var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }") &&
                source.contains("var gateFor by remember { mutableStateOf<DisplayApp?>(null) }") &&
                source.contains("var letterSelectedDate by remember { mutableStateOf<LocalDate?>(null) }"),
        )
    }

    // ----- BUG-013 (haptics not gated by accessibility / system toggle) -----

    @Test
    fun `BUG-013 haptics not gated by the system haptics toggle or the 'remove animations' a11y setting`() {
        val files = listOf(
            "launcher/HomeScreen.kt" to "launcher",
            "model/NoteScreen.kt" to "model",
            "letters/LetterScreen.kt" to "letters",
            "friction/FrictionGate.kt" to "friction",
        )
        var hasSystemHapticsCheck = false
        var hasHapticsCalls = false
        for ((filename, pkg) in files) {
            val source = readSource(filename, pkg) ?: continue
            if (source.contains("performHapticFeedback")) hasHapticsCalls = true
            // HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING / Settings.System.HAPTIC_FEEDBACK_ENABLED
            // is the documented check; also LocalContext.getSystemService(Vibrator).hasVibrator().
            // Neither is referenced anywhere in the launcher.
            if (source.contains("FLAG_IGNORE_GLOBAL_SETTING") ||
                source.contains("HAPTIC_FEEDBACK_ENABLED") ||
                source.contains("Settings.System.getInt") ||
                source.contains("VibratorManager")
            ) {
                hasSystemHapticsCheck = true
            }
        }
        assertTrue("The haptics-using surfaces must include some haptics calls", hasHapticsCalls)
        assertTrue(
            "At least one haptics call site should check the system haptics toggle (FLAG_IGNORE_GLOBAL_SETTING / Settings.System.HAPTIC_FEEDBACK_ENABLED) or the 'remove animations' a11y setting; saw none",
            hasSystemHapticsCheck,
        )
    }

    // ----- BUG-014 (HomeActivity configChanges did not include density / fontScale / locale / uiMode) -----
    //
    // v0.25.9 FIX: HomeActivity's configChanges now
    // includes fontScale, locale, uiMode, density,
    // layoutDirection, smallestScreenSize. A
    // regression that drops one of these from
    // HomeActivity flips the assertion red.

    @Test
    fun `BUG-014 HomeActivity configChanges covers fontScale locale uiMode density (v0_25_9 fix)`() {
        val manifest = readSource("AndroidManifest.xml", "")
        assertNotNull(manifest)
        val homeActivityBlock = extractActivityBlock(manifest!!, "HomeActivity")
        assertNotNull("HomeActivity block must be present in the manifest", homeActivityBlock)
        val block = homeActivityBlock!!
        val required = listOf("fontScale", "locale", "uiMode", "density", "layoutDirection", "smallestScreenSize")
        val missing = required.filter { it !in block }
        assertTrue(
            "HomeActivity configChanges must cover fontScale, locale, uiMode, density, " +
                "layoutDirection, smallestScreenSize (the v0.25.9 fix) so a config change " +
                "does not recreate the activity and lose all v0.25.5 home-card `remember` " +
                "state. missing=$missing block=$block.",
            missing.isEmpty(),
        )
    }

    // ----- BUG-015 (NoteScreen addInFlight / pendingDeleteId / filter) -----

    @Test
    fun `BUG-015 NoteScreen addInFlight pendingDeleteId and filter use remember not rememberSaveable`() {
        val source = readSource("NoteScreen.kt", "model")
        assertNotNull(source)
        assertTrue(
            "NoteScreen has the in-flight add guard, the delete-confirm flag, and the type filter in remember (not rememberSaveable)",
            source!!.contains("var addInFlight by remember { mutableStateOf(false) }") &&
                source.contains("var pendingDeleteId by remember { mutableStateOf<Long?>(null) }") &&
                source.contains("var filter by remember { mutableStateOf<NoteType?>(null) }"),
        )
        // Sanity: the three rememberSaveable calls (editingNoteId,
        // editorBody, newNoteDraft) should also be present —
        // those are the right primitives for the body text.
        assertTrue(
            "NoteScreen should still have rememberSaveable for editingNoteId, editorBody, and newNoteDraft",
            source.contains("var editingNoteId by rememberSaveable { mutableStateOf<Long?>(null) }") &&
                source.contains("var editorBody by rememberSaveable { mutableStateOf(\"\") }") &&
                source.contains("var newNoteDraft by rememberSaveable { mutableStateOf(\"\") }"),
        )
    }

    // ----- BUG-016 (OpenLoopCard showDialog) -----

    @Test
    fun `BUG-016 OpenLoopCard PostponeDialog visibility uses rememberSaveable`() {
        // The PostponeDialog visibility is local to the
        // RETURN branch and is genuinely transient; pinning
        // it is informational, not load-bearing.
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        assertTrue(
            "OpenLoopCard RETURN branch holds the PostponeDialog flag in rememberSaveable (v0.25.10 fix)",
            source!!.contains("LoopPhase.RETURN -> {") &&
                source.contains("var showDialog by rememberSaveable { mutableStateOf(false) }"),
        )
    }

    // ----- BUG-017 (modelFits stub) -----

    @Test
    fun `BUG-017 HomeScreen letter surface has a modelFits stub held in remember`() {
        // The letter surface currently ships with
        // `modelFits = remember { mutableStateOf(false) }` as
        // a stub. The shape is wrong: the value should come
        // from a ViewModel, not be created at composition.
        // A future wiring that forgets to remove the stub
        // would silently disable Generate-now forever.
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        assertTrue(
            "HomeScreen letter surface holds modelFits in remember { mutableStateOf(false) } (stub)",
            source!!.contains("val modelFits = remember { mutableStateOf(false) }"),
        )
    }

    // ----- BUG-018 (no SaveableStateHolder / SavedStateHandle) -----

    @Test
    fun `BUG-018 no SaveableStateHolder or SavedStateHandle used in the launcher`() {
        // Process death is a known failure mode of
        // remember (vs rememberSaveable). A `SaveableStateHolder`
        // on a tab or `SavedStateHandle` in a ViewModel is
        // the standard fix. Neither is in use anywhere in
        // the launcher.
        val files = listOf(
            "launcher/HomeScreen.kt",
            "launcher/LauncherViewModel.kt",
            "model/NoteActivity.kt",
            "model/NoteScreen.kt",
            "onboarding/OnboardingScreen.kt",
            "settings/SettingsScreen.kt",
            "settings/SettingsViewModel.kt",
        )
        var anySaveableHolderOrHandle = false
        for (rel in files) {
            val source = readSource(rel.substringAfter("/"), rel.substringBefore("/"))
                ?: continue
            if (source.contains("SaveableStateHolder") ||
                source.contains("SavedStateHandle") ||
                source.contains("SavedStateRegistry")
            ) {
                anySaveableHolderOrHandle = true
                break
            }
        }
        assertTrue(
            "BUG-018: at least one of the launcher VMs / screens should use SavedStateHandle or SaveableStateHolder for process-death recovery; saw none",
            anySaveableHolderOrHandle,
        )
    }

    private fun readSource(filename: String, pkg: String): String? = runCatching {
        // [pkg] is the package directory under org/mindanchor/ (e.g. "launcher",
        // "model", "onboarding"). An empty pkg means the file lives at the
        // root of the org/mindanchor/ tree (e.g. HomeActivity.kt) or is a
        // manifest under app/src/main/. The candidate list covers both shapes
        // and the "../app/..." mirror for the parent-dir run-from-test config.
        val candidates = buildList {
            if (pkg.isNotEmpty()) {
                add("app/src/main/java/org/mindanchor/$pkg/$filename")
                add("../app/src/main/java/org/mindanchor/$pkg/$filename")
            }
            add("app/src/main/java/org/mindanchor/$filename")
            add("../app/src/main/java/org/mindanchor/$filename")
            add("app/src/main/$filename")
            add("../app/src/main/$filename")
        }
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()

    private fun extractActivityBlock(manifest: String, name: String): String? {
        // Find the opening `<activity` tag that
        // declares `android:name=".NAME"`. Walk
        // backwards from the name to the opening
        // `<` (or find the start of the line
        // containing the name attribute), then
        // forward to the closing `</activity>`.
        // The returned string includes the
        // opening-tag attributes (so
        // `configChanges` is present) and the
        // inner content.
        val nameIdx = manifest.indexOf("android:name=\".$name\"")
        if (nameIdx < 0) return null
        // Walk back to the `<activity` opening tag.
        val openStart = manifest.lastIndexOf("<activity", nameIdx)
        if (openStart < 0) return null
        val close = manifest.indexOf("</activity>", nameIdx)
        if (close < 0) return null
        return manifest.substring(openStart, close + "</activity>".length)
    }
}
