/*
 * v0.64.0 (BPD-first): the journal root.
 *
 * State-based navigation across the 5 journal screens
 * (Today, Archive, Settings, Mood, QuickNote). Holds
 * the current screen + a small back-stack so the back
 * gesture / back button returns to the previous screen
 * rather than exiting the journal.
 *
 * v0.64.0 changes:
 *   - The 5 screens are the BPD-first variants. The
 *     Today/QuickNote bodies are connected: a single
 *     `todayEntry` string lives in this root and is
 *     shared by both screens. Typing in QuickNote
 *     updates the same string Today shows.
 *   - The footer is no longer a fixed parameter on
 *     every screen — every surface has its own
 *     [JournalFooter] call with the right active
 *     icon, and the 3 icons are NOT labelled (BPD-first:
 *     no labels in the footer).
 *
 * v0.65.0 changes:
 *   - Crisis numbers (iCall / Vandrevala / AASRA) wired
 *     to ACTION_DIAL via long-press. The single-tap
 *     stays text; the long-press fires the dial intent.
 *   - The single `onCall` callback is supplied here from
 *     `LocalContext` so each surface composable stays
 *     Context-free and unit-testable.
 *   - The journal entry now persists via a JournalPrefs
 *     DataStore wrapper. DataStore is the single source
 *     of truth: `todayEntry` collects from
 *     `prefs.todayEntry` Flow, and the only path that
 *     mutates it is `updateEntry` (which writes to
 *     DataStore, re-emits, and `todayEntry` updates).
 *     A process kill no longer erases the prose.
 *
 * v0.66.0 changes (Task 9 + Task 12):
 *   - Three new DataStore wrappers are now constructed
 *     here: DiaryCardPrefs (per-day DBT diary card),
 *     SkillsPrefs (which skill the user logged when),
 *     SafetyPlanPrefs (the Stanley-Brown safety plan).
 *     None are consumed by a screen yet — Tasks 11/12
 *     will refactor the screen dispatch and pass the
 *     needed callbacks down. The wrappers are wired in
 *     now so the refactor is a wiring change, not a
 *     state-holder introduction.
 *   - `skillOfTheDay` is computed once at composition
 *     (not `mutableStateOf` — it does not need to change
 *     after the root composes). Mood override is `null`
 *     because Today is not the surface that owns mood
 *     input; the journal mood screen feeds `skillOfTheDay`
 *     in v0.66.0 task 11.
 *   - `plan` is collected from SafetyPlanPrefs so the
 *     safety-plan value is available to the v0.66.0
 *     JournalCrisis refactor (Task 12) without that
 *     refactor having to re-introduce the Flow plumbing.
 *   - The existing `dial` closure remains the single
 *     ACTION_DIAL helper. The brief's proposed
 *     `crisisDial(tel:)` would have duplicated the same
 *     five lines; one helper is enough.
 *   - Screen signatures are unchanged in this task. The
 *     `onSkillDone` and `onUrgeEntry` callbacks belong
 *     to Tasks 11/12's refactor, not to this wiring
 *     pass.
 *   - Task 12: two new `JournalRoute` entries
 *     (`Skills`, `Crisis`) are appended to the enum
 *     (additive — the first 5 are still addressable for
 *     rollback). The `onNavigateToSkills` and
 *     `onNavigateToCrisis` callbacks from the Today
 *     surface are wired to `stack.add(...)`; the new
 *     dispatch branches render `JournalSkills` (a
 *     stub — see JournalSkills.kt) and `JournalCrisis`
 *     (the new full-screen DBT card — see
 *     JournalCrisis.kt). The plan's `onPlanChange`
 *     writes through `scope.launch { safetyPlanPrefs
 *     .set(newPlan) }`, reusing the Task 9 plumbing.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.Activity
import java.time.LocalDate
import java.time.LocalTime
import org.mindanchor.journal.crisis.SafetyPlanEntry
import org.mindanchor.journal.crisis.SafetyPlanPrefs
import org.mindanchor.journal.crisis.TherapistExport
import org.mindanchor.journal.diary.DiaryCardEntry
import org.mindanchor.journal.diary.DiaryCardPrefs
import org.mindanchor.journal.diary.Urges
import org.mindanchor.journal.skills.SkillId
import org.mindanchor.journal.skills.SkillOfTheDay
import org.mindanchor.journal.skills.SkillsPrefs

/**
 * The 5 v0.65.0 journal screens + the 2 v0.66.0
 * DBT-shaped destinations. The first 5 entries are
 * "kept for rollback, NOT in v0.66.0 nav" per the
 * v0.66.0 plan's deprecated list — they remain
 * addressable so the existing 5-screen nav still
 * works (the Today v0.66.0 surface is a wide
 * signature, not a new screen), but the only new
 * routes are `Skills` and `Crisis`. The enum order
 * is the canonical navigation order for the v0.65.0
 * surfaces; the v0.66.0 destinations sit at the end
 * so existing code that switches on the first 5 is
 * unchanged.
 */
enum class JournalRoute { Today, Archive, Settings, Mood, QuickNote, Skills, Crisis, Privacy }

@Composable
fun JournalRoot(
    onExitRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val stack: SnapshotStateList<JournalRoute> = remember { mutableStateListOf(JournalRoute.Today) }
    val current: JournalRoute = stack.last()

    // v0.65.0: persist the journal entry to DataStore.
    //   - prefs is a JournalPrefs wrapping the host
    //     Context (HomeActivity, via LocalContext).
    //   - todayEntry is the single source of truth: the
    //     value the screens render is whatever the
    //     DataStore Flow currently emits. On first
    //     composition it shows DEFAULT_FIXTURE; a
    //     microtask later DataStore's first real value
    //     arrives and the screen re-renders. On a fresh
    //     install DEFAULT_FIXTURE IS the saved value, so
    //     there is no visible flicker.
    //   - updateEntry is the only path that mutates
    //     todayEntry. It pushes to DataStore, which
    //     re-emits the new value, and todayEntry updates
    //     from the Flow. No local mutableStateOf mirror
    //     — keeping one would mean two competing sources
    //     of truth, which is the bug this rewrite fixes.
    val context = LocalContext.current
    val prefs = remember { JournalPrefs(context) }
    val todayEntry by prefs.todayEntry.collectAsStateWithLifecycle(
        initialValue = JournalPrefs.DEFAULT_FIXTURE
    )
    val scope = rememberCoroutineScope()
    val updateEntry: (String) -> Unit = { newValue ->
        scope.launch { prefs.setTodayEntry(newValue) }
    }

    // v0.66.0 (Task 9): the DBT-shaped state holders.
    //   - DiaryCardPrefs backs the per-day diary card
    //     (read by the diary list surface, written by
    //     the diary card surface — both land in Task 11).
    //   - SkillsPrefs backs the "which skill did the user
    //     use on which date" log. Today will write a
    //     SkillId when the user marks a skill done; the
    //     nudge and the PDF export read it.
    //   - SafetyPlanPrefs holds the Stanley-Brown
    //     safety plan. Collected as a Flow here so
    //     JournalCrisis (Task 12) can read `plan`
    //     without re-introducing the plumbing.
    //   - skillOfTheDay is computed once at composition
    //     (not `mutableStateOf` — it does not need to
    //     change after the root composes). Mood is
    //     `null` here because Today does not own mood
    //     input; the mood screen is the right place to
    //     call SkillOfTheDay.suggest with the user's
    //     actual mood, and that lands in Task 11.
    val diaryCardPrefs = remember { DiaryCardPrefs(context) }
    val skillsPrefs = remember { SkillsPrefs(context) }
    val safetyPlanPrefs = remember { SafetyPlanPrefs(context) }
    val skillOfTheDay: SkillId = remember {
        SkillOfTheDay.suggest(LocalTime.now(), mood = null)
    }
    val plan by safetyPlanPrefs.plan.collectAsStateWithLifecycle(
        initialValue = SafetyPlanEntry.empty()
    )

    // v0.66.2: the today diary card is the durable record for
    // today's mood (per the v0.66.0 spec, the diary card is the
    // canonical DBT self-monitoring record). Read the current
    // card as a Flow so the mood chips on Today reflect the
    // persisted selection across recompositions and process
    // restarts. `currentMood` is the index into `Mood.entries`
    // (0..4) for the first emotion in the list, or null when
    // the card has no emotions. Single-select chip ↔
    // single-emotion list — the diary card's `emotions` is a
    // `List<Mood>` for forward compatibility (the DBT workbook
    // allows multiple per day) but Today only logs one at a
    // time. Mood-logged-once-per-day is the right shape — the
    // user is recording "how is it right now", not free-associating.
    val today: LocalDate = remember { LocalDate.now() }
    val todayCard by diaryCardPrefs.entryFor(today)
        .collectAsStateWithLifecycle(initialValue = null)
    val currentMood: Int? = todayCard?.emotions?.firstOrNull()?.ordinal

    // v0.67.0: 14-day N-of-1 mood history for the
    // strip on Today. The list contains 14 entries
    // (oldest first, today last) of (date, moodOrdinal?).
    // The history is read with a coroutine on the
    // first composition and re-read every time
    // `todayCard` changes (which happens whenever the
    // user picks a mood chip — the diary card writes
    // a new entry, the Flow re-emits, and we refresh
    // the strip). The cost is one full-store scan per
    // write; for a v0.66.0 user with a year of cards
    // that is 365 string decodes. The v0.66.0 PDF
    // export also does this scan, so the cost is
    // already on the hot path.
    val moodHistory = remember { mutableStateOf<List<Pair<LocalDate, Int?>>>(emptyList()) }
    LaunchedEffect(todayCard) {
        val from = today.minusDays(13)
        val entries = diaryCardPrefs.entriesInRange(from, today)
        val byDate = entries.associateBy { it.date }
        moodHistory.value = (0..13).map { offset ->
            val date = from.plusDays(offset.toLong())
            val ordinal = byDate[date]?.emotions?.firstOrNull()?.ordinal
            date to ordinal
        }
    }

    // The back gesture pops the stack. If the stack has
    // only Today, the back gesture forwards to
    // HomeActivity (which will move the launcher to the
    // background — standard launcher behaviour).
    BackHandler(enabled = stack.size > 1) {
        stack.removeAt(stack.lastIndex)
    }

    // v0.65.0: wire the crisis-line long-press to an
    // ACTION_DIAL intent. LocalContext returns the host
    // Activity (HomeActivity) so the dial opens in the
    // system dialer, not in-app. ACTION_DIAL (not
    // ACTION_CALL) — the user still has to press the
    // green button to connect, which is the right shape
    // for a crisis line (deliberate, not impulsive).
    val dial: (String) -> Unit = { phone ->
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // v0.66.1: read the 3 new v0.66.0 Settings toggles from
    // JournalSettingsPrefs. All default OFF. Default-false
    // in the `?:` matches the DataStore convention used in
    // every other v0.66.0 wrapper. The first read of the
    // prefs Flow is collected with `false` as the initial
    // value, so the UI is not blocked on the first disk read.
    val settingsPrefs = remember { JournalSettingsPrefs(context) }
    val voiceFirstEnabled by settingsPrefs.voiceFirstEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val therapistExportEnabled by settingsPrefs.therapistExportEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    // v0.67.0: the user's display name for the export file.
    // Side-effect into the static field on `TherapistExport`
    // so the export class does not need a prefs dependency
    // at the call site. The set is keyed off the latest
    // Flow value (the name is also displayed in the privacy
    // policy screen and the Settings row).
    val displayName by settingsPrefs.displayName
        .collectAsStateWithLifecycle(initialValue = "")
    SideEffect {
        TherapistExport.therapistDisplayName = displayName
    }
    // v0.67.0: the first-run onboarding overlay. Read
    // the dismissal flag; while false, the overlay is
    // shown above the Today surface. Tapping either
    // dismiss button flips the flag to true (the
    // "Got it" / "Don't show this again" distinction
    // is in the wording only — both persist). The
    // Settings row "Show journal intro" sets the flag
    // back to false to re-open the overlay.
    val onboardingSeen by settingsPrefs.onboardingSeen
        .collectAsStateWithLifecycle(initialValue = true)
    val onDismissOnboarding: (Boolean) -> Unit = { _ ->
        scope.launch { settingsPrefs.setOnboardingSeen(true) }
    }
    // v0.66.2: read the 2D mood input toggle. v0.66.0 stored
    // the flag in the prefs but never read it into a
    // composable — JournalSettings shows the toggle, but no
    // surface responds to it. v0.66.2 wires the flag into
    // Today so the 1D mood chips switch to a 2x2 Affect-Grid
    // (Russell circumplex — valence × arousal) when ON.
    val affectGridEnabled by settingsPrefs.affectGridEnabled
        .collectAsStateWithLifecycle(initialValue = false)

    // v0.66.1: real wires for the v0.66.0 no-op callbacks.
    //   - onSkillDone writes the SkillId to SkillsPrefs for
    //     `today` so the diary nudge and the PDF export can
    //     see what skill the user logged when. The day key
    //     is LocalDate.now() in the system zone — same zone
    //     the rest of the app uses.
    //   - onUrgeEntry writes a DiaryCardEntry to DiaryCardPrefs
    //     for `today` with the 0..5 Urges triple. The Mood
    //     list comes from the user's current chip selection
    //     (v0.66.2: mood is persisted; onUrgeEntry no longer
    //     clobbers the existing emotions list). The
    //     `exportedToTherapist` flag is `false` at write time
    //     and flips to `true` when the user actually exports
    //     the PDF (TherapistExport's own gate).
    //   - onExportRequest reads the last 14 days of diary +
    //     skill entries, renders them via TherapistExport to
    //     a file under getExternalFilesDir(DOCUMENTS), and
    //     fires a share intent so the user can hand it to a
    //     therapist in whatever channel they use (Signal,
    //     email, paper print). The PDF is generated on this
    //     device only — no network call, no telemetry, no
    //     analytics.
    //   - onMoodChange (v0.66.2) writes the new mood chip
    //     selection to today's diary card. The other card
    //     fields (urges, skillUsed, exportedToTherapist) are
    //     read from the current card first so tapping a mood
    //     chip does not clobber an already-logged urge. If
    //     there is no card yet for today, a fresh one is
    //     written with the new mood and the other fields at
    //     their default (null urges, no skill, not exported).
    //     `idx` is the index into `Mood.entries` (0..4), or
    //     null when the user tapped the currently-selected
    //     chip again to deselect.
    val onSkillDone: (SkillId) -> Unit = { skillId ->
        scope.launch { skillsPrefs.markUsed(skillId, today) }
    }
    val onUrgeEntry: (Urges) -> Unit = { urges ->
        scope.launch {
            // Preserve the already-persisted mood when the user
            // logs urges — the diary card is the durable record
            // for both fields, and writing emptyList() here would
            // silently clear a mood the user just picked. (The
            // v0.66.0/v0.66.1 code did exactly that, which is
            // why v0.66.2 reads the current card first.)
            val current = diaryCardPrefs.entryFor(today).first()
            diaryCardPrefs.setEntry(
                DiaryCardEntry(
                    date = today,
                    urges = urges,
                    emotions = current?.emotions ?: emptyList(),
                    skillUsed = current?.skillUsed,
                    exportedToTherapist = current?.exportedToTherapist ?: false,
                ),
            )
        }
    }
    val onMoodChange: (Int?) -> Unit = { idx ->
        scope.launch {
            val current = diaryCardPrefs.entryFor(today).first()
            val emotions = if (idx == null) emptyList() else listOf(Mood.entries[idx])
            diaryCardPrefs.setEntry(
                DiaryCardEntry(
                    date = today,
                    urges = current?.urges,
                    emotions = emotions,
                    skillUsed = current?.skillUsed,
                    exportedToTherapist = current?.exportedToTherapist ?: false,
                ),
            )
        }
    }
    val onExportRequest: () -> Unit = {
        scope.launch {
            val to = today
            val from = to.minusDays(13)
            val diaryEntries = diaryCardPrefs.entriesInRange(from, to)
            val skillEntries = skillsPrefs.entriesInRange(from, to)
            val exporter = TherapistExport(context)
            val file = exporter.export(
                from = from,
                to = to,
                diaryEntries = diaryEntries,
                skillEntries = skillEntries,
                moodOwnMedian = 3, // session-local mood; v0.66.1 leaves the v0.66.0 text-only 14-day strip alone
                moodMad = 0,
            )
            // v0.67.0: a one-line summary that the share
            // chooser can show in its preview line. Without
            // this, the chooser shows the file name only —
            // which the recipient has to open to know what
            // it is. The summary is the same shape as the
            // export's own header, condensed. The
            // `EXTRA_TEXT` field is also picked up by some
            // targets (email subject, message body) for free.
            // v0.67.0: also set `ClipData` so the chooser
            // preview can render the file (the previous
            // absence of ClipData made the chooser log a
            // permission-denied warning even when the share
            // itself worked — see the v0.66.1 drive-verify
            // log capture).
            val summary = exporter.summary(from, to, diaryEntries, skillEntries)
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                putExtra(android.content.Intent.EXTRA_TEXT, summary)
                putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
                clipData = android.content.ClipData.newUri(
                    context.contentResolver,
                    file.name,
                    fileUri,
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                android.content.Intent.createChooser(share, "Share with your therapist")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    // v0.66.1: wire the Crisis "I need to ground" panic
    // button. The previous v0.66.0 no-op meant tapping the
    // largest, most prominent button on the Crisis surface
    // had zero feedback. Now it pushes the Skills route on
    // the back-stack so the system back button returns the
    // user to the Crisis surface. The Skills library has the
    // S.T.O.P. and TIPP instructions in full (per SkillsLibrary
    // — verbatim DBT/ACT/grounding protocol), so the user
    // gets actionable content within one tap.
    val onSkillStart: (SkillId) -> Unit = { _ ->
        stack.add(JournalRoute.Skills)
    }

    // v0.66.2: when the user marks a skill Done in the Skills
    // library, they have just done the skill — the right time to
    // arm the 60-second diary expander window in Today (so they
    // can log any urges the skill surfaced). JournalToday reads
    // `pendingArm` at composition and arms the window if true,
    // then calls `onPendingArmConsumed()` to clear the flag.
    // This is a one-shot signal: the next library Done re-arms
    // it, but a process restart or a re-composition of Today
    // without a library Done does not. A `mutableStateOf` (not
    // a `MutableStateFlow`) is the right shape here — only
    // JournalRoot and JournalToday ever read or write it, and
    // the value flips true→false→true in seconds, not in days.
    val pendingArm = remember { mutableStateOf(false) }
    val onPendingArmConsumed: () -> Unit = { pendingArm.value = false }

    Box(modifier = modifier.fillMaxSize()) {
        when (current) {
            // v0.67.0: the first-run onboarding overlay.
            // Rendered ONLY when `onboardingSeen` is false.
            // The overlay is full-screen, translucent-paper
            // backed, with a 3-card stack in the centre. The
            // dismiss buttons set the flag to true. The
            // overlay is checked here rather than at the
            // top of the `Box` so the entire journal is
            // gated by one flag, and any future entry point
            // (e.g. a "Show me the tour" button) can be
            // added without touching the route dispatch.
            // v0.66.0 (Task 11): the Today composable now takes a
            // wider signature — skill-of-the-day + diary card
            // callbacks + voice-first + therapist-export toggles +
            // two navigation callbacks.
            // v0.66.1: the no-op callbacks are now real. onSkillDone
            // writes to SkillsPrefs (per-day); onUrgeEntry writes
            // to DiaryCardPrefs (per-day); onExportRequest
            // generates the on-device PDF and fires a share
            // intent. The voice-first + therapist-export toggles
            // are wired from JournalSettingsPrefs (real reads,
            // not hard-coded false).
            JournalRoute.Today -> JournalToday(
                entryBody = todayEntry,
                onEntryBodyChange = updateEntry,
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onCall = dial,
                onSkillDone = onSkillDone,
                onUrgeEntry = onUrgeEntry,
                onExportRequest = onExportRequest,
                onNavigateToSkills = { stack.add(JournalRoute.Skills) },
                onNavigateToCrisis = { stack.add(JournalRoute.Crisis) },
                voiceFirstEnabled = voiceFirstEnabled,
                therapistExportEnabled = therapistExportEnabled,
                skillOfTheDay = skillOfTheDay,
                // v0.66.2: persisted mood (read from today's
                // diary card) + persistence callback (writes
                // the new chip selection back to the card).
                currentMood = currentMood,
                onMoodChange = onMoodChange,
                // v0.66.2: 2D Affect-Grid toggle. When ON,
                // Today's mood row renders a 2x2 grid
                // (valence × arousal) instead of the 1D
                // chips. Tapping a quadrant maps to one of
                // the 5-state Mood values (the 2D UI exposes
                // 4 of the 5; the centre, if needed, is
                // STEADY).
                affectGridEnabled = affectGridEnabled,
                // v0.66.2: one-shot signal so a library Done
                // arms the 60s diary expander window on the
                // way back to Today.
                pendingArm = pendingArm.value,
                onPendingArmConsumed = onPendingArmConsumed,
                // v0.67.0: 14-day N-of-1 mood history. Re-read
                // every time `todayCard` changes (mood logged,
                // urge entry, or skill done).
                moodHistory = moodHistory.value,
            )
            JournalRoute.Archive -> JournalArchive(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.QuickNote) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onCall = dial,
            )
            JournalRoute.Settings -> JournalSettings(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onCall = dial,
                // v0.67.0: the in-app privacy policy route
                // is pushed onto the back-stack. The user
                // returns to Settings on back.
                onPrivacy = { stack.add(JournalRoute.Privacy) },
            )
            JournalRoute.Mood -> JournalMood(
                onBack = { stack.removeAt(stack.lastIndex) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onCall = dial,
            )
            JournalRoute.QuickNote -> JournalQuickNote(
                text = todayEntry,
                onTextChange = updateEntry,
                onBack = { stack.removeAt(stack.lastIndex) },
                onCall = dial,
            )
            // v0.66.0 (Task 12): the two new DBT destinations.
            // v0.66.1: the Skills stub is replaced with a real
            // picker UI (5 skills, full content from SkillsLibrary).
            // The Crisis "I need to ground" panic button now
            // navigates to Skills (was a no-op).
            // v0.66.1: after the user marks a skill Done in the
            // library, we both persist the use (onSkillDone writes
            // to SkillsPrefs) and pop the Skills screen off the
            // back-stack so the user returns to wherever they
            // came from (Today for normal use, Crisis for the
            // "I need to ground" panic-button flow). Without the
            // pop, the user was stranded on the Skills page with
            // no visible feedback that the Done tap registered.
            JournalRoute.Skills -> JournalSkills(
                onBack = { stack.removeAt(stack.lastIndex) },
                onCall = dial,
                onSkillDone = { skillId ->
                    onSkillDone(skillId)
                    // v0.66.2: arm the 60s diary expander window
                    // in Today before popping. The flag is read
                    // on Today's next composition and consumed
                    // (flipped back to false) by JournalToday. The
                    // pop makes sure Today IS the next composition
                    // — if the user came from Crisis instead, the
                    // pop returns them to Crisis, not Today, and
                    // the flag is held until the next time Today
                    // is composed (which is still useful — they
                    // may navigate back to Today and find the
                    // window armed). The flag survives the pop
                    // because it lives in JournalRoot, not in
                    // the route's local state.
                    pendingArm.value = true
                    if (stack.isNotEmpty() && stack.last() == JournalRoute.Skills) {
                        stack.removeAt(stack.lastIndex)
                    }
                },
            )
            JournalRoute.Crisis -> JournalCrisis(
                plan = plan,
                onPlanChange = { newPlan -> scope.launch { safetyPlanPrefs.set(newPlan) } },
                onSkillStart = onSkillStart,
                onNavigateToSkills = { stack.add(JournalRoute.Skills) },
                onCall = dial,
            )
            // v0.67.0: the in-app privacy policy surface.
            // Reached from JournalSettings → About → Privacy
            // & data. The route is a back-stack entry so the
            // user returns to the previous surface on back.
            JournalRoute.Privacy -> JournalPrivacyPolicy(
                onBack = { stack.removeAt(stack.lastIndex) },
            )
        }
        // v0.67.0: first-run onboarding overlay. Sits
        // ON TOP of whatever route the user is on, with
        // a translucent-paper backdrop so the journal
        // surface is still visible behind it (the user
        // can see the Today they are about to learn).
        // The overlay is gated by `!onboardingSeen` and
        // dismissed by `onDismissOnboarding` (which sets
        // the flag to true). The Settings row "Show
        // journal intro" sets the flag back to false to
        // re-open the overlay; a fresh install sees it
        // because the default is false.
        if (!onboardingSeen) {
            JournalOnboarding(
                onDismiss = onDismissOnboarding,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
