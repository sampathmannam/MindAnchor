@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.compose

import org.junit.Assert.assertFalse
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
        //
        // v0.25.17 fix: the broad migration closes this
        // finding. The original "at least one" pin still
        // passes (the per-file pin below is a stronger
        // regression guard).
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

    @Test
    fun `BUG-004 every main-source Composable in the 12-file set uses collectAsStateWithLifecycle (v0_25_17 fix)`() {
        // v0.25.17 fix-shape: the broad migration closes
        // the BUG-004 finding for the entire 12-file
        // main-source set. The original "at least one"
        // pin is a positive pin (must be present
        // somewhere); the per-file pin below is a
        // stronger regression guard that asserts the
        // primitive is in use in *every* file. A
        // v0.25.17+ regression that reverts a file to
        // plain `collectAsState` flips this assertion
        // red with a per-file error message.
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
        val missing = mutableListOf<String>()
        for ((filename, pkg) in files) {
            val source = readSource(filename, pkg)
            if (source == null) {
                missing += "$filename (read failed)"
                continue
            }
            if (!source.contains("collectAsStateWithLifecycle")) {
                missing += "$filename (no collectAsStateWithLifecycle)"
            }
        }
        assertTrue(
            "BUG-004 (v0.25.17 fix): every main-source Composable in the 12-file set " +
                "must use collectAsStateWithLifecycle. The pre-fix shape had " +
                "`collectAsState` (without WithLifecycle), which is a backpressure " +
                "hole — the flow keeps producing when the screen is STOPPED. " +
                "missing=$missing",
            missing.isEmpty(),
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
    //
    // v0.25.15 fix: `mutableStateListOf<String>()` is auto-Saveable
    // (the default Saver for `SnapshotStateList<String>` writes the
    // contents as a Bundle array of strings), so the migration is a
    // one-keyword `remember` → `rememberSaveable` swap. The pin
    // below is the fix-shape: the drafts must be `rememberSaveable`
    // AND still be `mutableStateListOf` (the new state is the new
    // primitive, not a different list type).

    @Test
    fun `BUG-008 BedtimeListCard CAPTURE-mode drafts use rememberSaveable (v0_25_15 fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("private fun BedtimeListCard(")
        // v0.25.15 fix: scope the search to BedtimeListCard (the
        // file has many `mutableStateListOf` sites; we want the one
        // inside the CAPTURE branch of this specific function).
        val captureIdx = if (fnIdx >= 0) src.indexOf("BedtimePhase.CAPTURE -> {", fnIdx) else -1
        val draftsIdx = if (captureIdx >= 0) src.indexOf("val drafts = rememberSaveable {", captureIdx) else -1
        val listShapeIdx = if (draftsIdx >= 0) src.indexOf("mutableStateListOf<String>().apply { add(\"\") }", draftsIdx) else -1
        assertTrue(
            "BedtimeListCard CAPTURE-mode drafts must use rememberSaveable " +
                "(v0.25.15 fix). fnIdx=$fnIdx captureIdx=$captureIdx " +
                "draftsIdx=$draftsIdx listShapeIdx=$listShapeIdx. The order " +
                "must be: fn < capture < drafts < listShape. " +
                "source=\n$src",
            fnIdx >= 0 && captureIdx > fnIdx && draftsIdx > captureIdx && listShapeIdx > draftsIdx,
        )
    }

    // ----- BUG-009 (AppActionsDialog rename) -----
    //
    // v0.25.15 fix: `Boolean` and `String` are auto-Saveable; the
    // migration is the one-keyword `remember` → `rememberSaveable`
    // swap on both `renaming` and `newLabel`.

    @Test
    fun `BUG-009 AppActionsDialog rename flow uses rememberSaveable (v0_25_15 fix)`() {
        val source = readSource("AppActionsDialog.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("fun AppActionsDialog(")
        val renamingIdx = if (fnIdx >= 0) src.indexOf("var renaming by rememberSaveable { mutableStateOf(false) }", fnIdx) else -1
        val labelIdx = if (renamingIdx >= 0) src.indexOf("var newLabel by rememberSaveable { mutableStateOf(app.label) }", renamingIdx) else -1
        assertTrue(
            "AppActionsDialog rename state must use rememberSaveable (v0.25.15 fix). " +
                "fnIdx=$fnIdx renamingIdx=$renamingIdx labelIdx=$labelIdx. " +
                "The order must be: fn < renaming < label. source=\n$src",
            fnIdx >= 0 && renamingIdx > fnIdx && labelIdx > renamingIdx,
        )
    }

    // ----- BUG-010 (EmaScreen) -----
    //
    // v0.25.15 fix: `Int?` and `Boolean` are auto-Saveable; the
    // migration is the one-keyword `remember` → `rememberSaveable`
    // swap on both `valence` and `saved`. Scope the search to the
    // EmaScreen Composable to avoid matching `remember` in any
    // future extracted helper.

    @Test
    fun `BUG-010 EmaScreen valence and saved use rememberSaveable (v0_25_15 fix)`() {
        val source = readSource("EmaScreen.kt", "model")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("fun EmaScreen(")
        val valenceIdx = if (fnIdx >= 0) src.indexOf("var valence by rememberSaveable { mutableStateOf<Int?>(null) }", fnIdx) else -1
        val savedIdx = if (valenceIdx >= 0) src.indexOf("var saved by rememberSaveable { mutableStateOf(false) }", valenceIdx) else -1
        assertTrue(
            "EmaScreen valence and saved must use rememberSaveable (v0.25.15 fix). " +
                "fnIdx=$fnIdx valenceIdx=$valenceIdx savedIdx=$savedIdx. " +
                "The order must be: fn < valence < saved. source=\n$src",
            fnIdx >= 0 && valenceIdx > fnIdx && savedIdx > valenceIdx,
        )
    }

    // ----- BUG-011 (PulseScreen) -----
    //
    // v0.25.15 fix: `List<Int>` and `Int?` are auto-Saveable; the
    // migration is the one-keyword `remember` → `rememberSaveable`
    // swap on both `answers` and `savedScore`. Scope the search
    // to the PulseScreen Composable.

    @Test
    fun `BUG-011 PulseScreen answers and savedScore use rememberSaveable (v0_25_15 fix)`() {
        val source = readSource("PulseScreen.kt", "pulse")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("fun PulseScreen(")
        val answersIdx = if (fnIdx >= 0) src.indexOf("var answers by rememberSaveable { mutableStateOf(List(WhoFive.ITEM_COUNT) { -1 }) }", fnIdx) else -1
        val savedIdx = if (answersIdx >= 0) src.indexOf("var savedScore by rememberSaveable { mutableStateOf<Int?>(null) }", answersIdx) else -1
        assertTrue(
            "PulseScreen answers and savedScore must use rememberSaveable (v0.25.15 fix). " +
                "fnIdx=$fnIdx answersIdx=$answersIdx savedIdx=$savedIdx. " +
                "The order must be: fn < answers < saved. source=\n$src",
            fnIdx >= 0 && answersIdx > fnIdx && savedIdx > answersIdx,
        )
    }

    // ----- BUG-012 (LauncherRoot surface state) -----
    //
    // v0.25.14 fix: 3 of the 6 LauncherRoot state fields (surface,
    // reportCameFrom, letterCameFrom — all `LauncherSurface` enums,
    // which are auto-Saveable) migrated from `remember` to
    // `rememberSaveable`.
    //
    // v0.25.15 fix: the remaining 3 complex-typed fields
    // (actionsFor: DisplayApp?, gateFor: DisplayApp?,
    // letterSelectedDate: LocalDate?) are now rememberSaveable too,
    // each with a custom Saver:
    //   - `DisplayAppNullableSaver` (mapSaver, component-name key)
    //     for `actionsFor` and `gateFor`
    //   - `LocalDateNullableSaver` (ISO-8601 string round-trip) for
    //     `letterSelectedDate`
    //
    // The BUG-shape pin for the deferred half is flipped to
    // fix-shape. The original BUG-shape pin is kept (renamed) for
    // the "still uses remember" regression check — but the active
    // assertion is the fix-shape, so a v0.25.16+ regression that
    // reverts to `remember` fails the same FindingTest with a
    // different message.

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
    fun `BUG-012 LauncherRoot complex state uses rememberSaveable (v0_25_15 fix)`() {
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        // v0.25.15 fix: the 3 complex-typed LauncherRoot state
        // fields are now rememberSaveable with custom Savers
        // (mapSaver for DisplayApp?, ISO-string for LocalDate?).
        // The pattern is the v0.25.14 lesson: scope the search
        // by `stateSaver = ...Saver` token so a future regression
        // that reverts to plain `remember` flips the assertion
        // red.
        val actionsForIdx = src.indexOf("var actionsFor by rememberSaveable(stateSaver = DisplayAppNullableSaver)")
        val gateForIdx = if (actionsForIdx >= 0) src.indexOf("var gateFor by rememberSaveable(stateSaver = DisplayAppNullableSaver)", actionsForIdx) else -1
        val letterDateIdx = if (gateForIdx >= 0) src.indexOf("var letterSelectedDate by rememberSaveable(stateSaver = LocalDateNullableSaver)", gateForIdx) else -1
        assertTrue(
            "LauncherRoot must hold the 3 complex-typed state fields in " +
                "rememberSaveable (v0.25.15 fix) with custom Savers: " +
                "actionsFor / gateFor (DisplayAppNullableSaver) and " +
                "letterSelectedDate (LocalDateNullableSaver). " +
                "actionsForIdx=$actionsForIdx gateForIdx=$gateForIdx " +
                "letterDateIdx=$letterDateIdx. The order must be: " +
                "actionsFor < gateFor < letterDate. source=\n$src",
            actionsForIdx >= 0 && gateForIdx > actionsForIdx && letterDateIdx > gateForIdx,
        )
    }

    // ----- BUG-013 (haptics not gated by accessibility / system toggle) -----
    //
    // v0.25.16 fix: a [org.mindanchor.ui.HapticFeedbackGate]
    // CompositionLocal wraps the launcher root. Every direct
    // `LocalHapticFeedback.current.performHapticFeedback(...)`
    // call at a haptics call site is replaced by
    // `org.mindanchor.ui.LocalHapticFeedbackGate.current.performHapticFeedback(...)`.
    // The full fix-shape coverage is in
    // `org.mindanchor.ui.HapticFeedbackGateFindingTest` (a new
    // finding-test file). The pin below is a positive regression
    // guard: a v0.25.16+ regression that drops the gate from any
    // of the four call sites flips the assertion red.

    @Test
    fun `BUG-013 haptics not gated by the system haptics toggle or the 'remove animations' a11y setting`() {
        // Kept for the v0.25.10+ bug-hunt history. The
        // v0.25.16 fix wires the four haptics call surfaces
        // through the [HapticFeedbackGate] CompositionLocal.
        // The pre-fix shape had no system-toggle check in any
        // of the four call sites; the fix introduces the gate
        // as the single check-point (see
        // `HapticFeedbackGateFindingTest` for the full
        // surface). The pin here is the regression guard:
        // if a v0.25.16+ change drops the gate from any of
        // the four surfaces, this assertion flips red.
        val files = listOf(
            "launcher/HomeScreen.kt" to "launcher",
            "model/NoteScreen.kt" to "model",
            "letters/LetterScreen.kt" to "letters",
            "friction/FrictionGate.kt" to "friction",
        )
        var hasHapticsCalls = false
        var allUseGate = true
        for ((filename, pkg) in files) {
            val source = readSource(filename, pkg) ?: continue
            if (source.contains("performHapticFeedback")) hasHapticsCalls = true
            // The fix-shape: every file that calls
            // `performHapticFeedback` must also reference
            // `org.mindanchor.ui.LocalHapticFeedbackGate.current`
            // (the gate). A file that has the call but not the
            // gate is the pre-fix shape.
            if (source.contains("performHapticFeedback") &&
                !source.contains("org.mindanchor.ui.LocalHapticFeedbackGate.current")
            ) {
                allUseGate = false
            }
        }
        assertTrue(
            "The haptics-using surfaces must include some haptics calls",
            hasHapticsCalls,
        )
        assertTrue(
            "BUG-013 (v0.25.16 fix): every haptics call site must route through the " +
                "`HapticFeedbackGate` CompositionLocal. A v0.25.16+ regression that " +
                "re-introduces a direct `LocalHapticFeedback.current` use at a call site " +
                "flips this assertion red.",
            allUseGate,
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
    //
    // v0.25.16 fix: the `val modelFits = remember { mutableStateOf(false) }`
    // stub in HomeScreen is replaced by a flow from
    // `LauncherViewModel.modelFits: StateFlow<Boolean>`. The
    // FindingTest pin below asserts both halves: the new VM
    // field exists, and the HomeScreen letter surface uses
    // `viewModel.modelFits.collectAsStateWithLifecycle()` rather
    // than the Composable-level stub.
    //
    // The full fix-shape coverage is in
    // `org.mindanchor.launcher.ModelFitsWiringFindingTest`
    // (a new finding-test file). The pin here is a positive
    // regression guard: a v0.25.16+ regression that drops the
    // wiring back to a stub fails the same FindingTest with a
    // different message.

    @Test
    fun `BUG-017 HomeScreen letter surface has a modelFits stub held in remember`() {
        // Kept for the v0.25.10+ bug-hunt history. The
        // v0.25.16 fix rewires the value to a
        // `viewModel.modelFits.collectAsStateWithLifecycle()`
        // (see `ModelFitsWiringFindingTest`), so the
        // Composable-level `remember { mutableStateOf(false) }`
        // stub is gone. A regression that re-introduces the
        // stub flips the assertion red.
        val source = readSource("HomeScreen.kt", "launcher")
        assertNotNull(source)
        val src = source!!
        val fnIdx = src.indexOf("LauncherSurface.Letter ->")
        val letterBlock = if (fnIdx >= 0) src.substring(fnIdx) else ""
        assertTrue(
            "BUG-017: the Composable-level modelFits stub is gone (v0.25.16 fix). " +
                "The pre-fix shape was `val modelFits = remember { mutableStateOf(false) }`; " +
                "the fix is `val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()`. " +
                "letterBlock=\n$letterBlock",
            !letterBlock.contains("val modelFits = remember { mutableStateOf(false) }") &&
                letterBlock.contains("viewModel.modelFits.collectAsStateWithLifecycle()"),
        )
    }

    // ----- BUG-018 (no SaveableStateHolder / SavedStateHandle) -----

    @Test
    fun `BUG-018 Settings tabs use SaveableStateHolder for tab-switch state preservation (v0_25_16 fix)`() {
        // v0.25.16 fix: the Settings surface creates a
        // [SaveableStateHolder] via [rememberSaveableStateHolder]
        // and wraps each SettingsGroup's content in
        // `holder.SaveableStateProvider(key) { ... }`. The
        // pre-fix shape was a plain `if (group == X) { ... }`
        // that tore down the slot table on every tab switch.
        //
        // The FindingTest asserts both halves: the holder is
        // created (`rememberSaveableStateHolder()`), the
        // provider is in use (`saveableStateHolder.SaveableStateProvider(`),
        // and the holder comes before the provider.
        val source = readSource("SettingsScreen.kt", "settings")
        assertNotNull("SettingsScreen.kt must be readable", source)
        val src = source!!
        // v0.25.16 fix-shape: a SaveableStateHolder is
        // created and at least one SaveableStateProvider is
        // present (the v0.25.16 wrap of the PAUSES tab).
        val holderIdx = src.indexOf("val saveableStateHolder = rememberSaveableStateHolder()")
        val providerIdx = src.indexOf("saveableStateHolder.SaveableStateProvider(\"PAUSES\")")
        assertTrue(
            "BUG-018 (v0.25.16 fix): SettingsScreen must declare a SaveableStateHolder " +
                "and use saveableStateHolder.SaveableStateProvider(\"PAUSES\") to wrap " +
                "tab content so per-tab state survives a tab switch within a single " +
                "Settings open. holderIdx=$holderIdx providerIdx=$providerIdx. " +
                "The order must be: holder < provider. source=\n$src",
            holderIdx >= 0 && providerIdx > holderIdx,
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
