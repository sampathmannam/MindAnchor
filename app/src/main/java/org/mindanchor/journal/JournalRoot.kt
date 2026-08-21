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
 * v0.66.0 changes (Task 9):
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
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import android.app.Activity
import java.time.LocalTime
import org.mindanchor.journal.crisis.SafetyPlanEntry
import org.mindanchor.journal.crisis.SafetyPlanPrefs
import org.mindanchor.journal.diary.DiaryCardPrefs
import org.mindanchor.journal.skills.SkillId
import org.mindanchor.journal.skills.SkillOfTheDay
import org.mindanchor.journal.skills.SkillsPrefs

/**
 * The 5 journal screens. The enum order is the
 * canonical navigation order (Today → Archive →
 * Settings → Mood → QuickNote).
 */
enum class JournalRoute { Today, Archive, Settings, Mood, QuickNote }

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

    Box(modifier = modifier.fillMaxSize()) {
        when (current) {
            // v0.66.0 (Task 11): the Today composable now takes a
            // wider signature — skill-of-the-day + diary card
            // callbacks + voice-first + therapist-export toggles +
            // two navigation callbacks. The actual SkillsPrefs and
            // DiaryCardPrefs writes are wired in Task 12 (the
            // follow-up to this refactor); for now the new
            // callbacks are no-op lambdas so the build compiles.
            // The voice-first and therapist-export toggles default
            // to false here — the real values are read from
            // JournalSettingsPrefs in Task 12.
            JournalRoute.Today -> JournalToday(
                entryBody = todayEntry,
                onEntryBodyChange = updateEntry,
                onContinueWriting = { stack.add(JournalRoute.QuickNote) },
                onSearch = { stack.add(JournalRoute.Archive) },
                onArchive = { stack.add(JournalRoute.Archive) },
                onSettings = { stack.add(JournalRoute.Settings) },
                onCall = dial,
                onSkillDone = { /* Task 12: skillsPrefs.markUsed(it, today) */ },
                onUrgeEntry = { /* Task 12: diaryCardPrefs.setEntry(...) */ },
                onExportRequest = { /* Task 12: therapist export intent */ },
                onNavigateToSkills = { /* Task 12: stack.add(JournalRoute.Skills) */ },
                onNavigateToCrisis = { /* Task 12: stack.add(JournalRoute.Crisis) */ },
                voiceFirstEnabled = false,
                therapistExportEnabled = false,
                skillOfTheDay = skillOfTheDay,
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
        }
    }
}
