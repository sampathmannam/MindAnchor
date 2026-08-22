/*
 * v0.66.0 (DBT-grounded journal) — Task 11.
 *
 * The Today screen — the journal's home — is now a
 * single screen with the following top-to-bottom layout:
 *   1. Date header (kept from v0.65.0)
 *   2. 14-day N-of-1 strip (real, v0.67.0; backed by
 *      diary card mood history with own-median + MAD
 *      robust z-score; under 14 days, shows still-
 *      learning copy)
 *   3. Mood chips (5 horizontal chips, single-select,
 *      deselectable; no dimming of unselected — BPD-first)
 *      OR 2D Affect-Grid (2x2; gated by `affectGridEnabled`)
 *   4. Journal composer (the existing BasicTextField +
 *      "Thanks for writing that." validation)
 *   5. Skill-of-the-day card (a Card with title + "When
 *      to use" + a "Read aloud" / "Done" row; voice-first
 *      toggleable via `voiceFirstEnabled`)
 *   6. Diary card expander (collapsed by default; tap
 *      to expand; the link is disabled until a skill has
 *      been Done in this session or via Skills library)
 *   7. Optional "Share with my therapist" button
 *      (visible only when `therapistExportEnabled` is
 *      true; default OFF)
 *   8. NavTextButtonRow — Skills / Crisis text buttons
 *   ─── sticky area (NOT in scroll) ───
 *   9. v0.67.0: 4-line crisis bar (iCall, Vandrevala,
 *      AASRA, Tele-MANAS 14416) — long-press to dial
 *  10. The 3-icon footer (kept from v0.65.0 — search,
 *      archive, settings)
 *
 * v0.66.0 BPD-safe patterns preserved from v0.65.0:
 *   - No entry number, no time, no streak, no leaderboard.
 *   - "Anything here?" placeholder is the empty state.
 *   - "Thanks for writing that." is a validation, not a
 *     counter, and it appears the moment the user types.
 *   - Mood chips never dim unselected states.
 *   - No "!" anywhere.
 *   - Crisis bar is always visible (v0.67.0: pinned,
 *     not in the scroll).
 *
 * Sub-composables are all inline private @Composable funs
 * (per the v0.66.0 plan's self-review — no new files for
 * v0.66.0 UI primitives):
 *   - NOfOneStrip             (14-day strip, real)
 *   - MoodChipsRow            (5 mood chips, BPD-safe)
 *   - AffectGrid              (2D 2x2 mood grid, optional)
 *   - AffectQuadrant          (one cell of the 2D grid)
 *   - SkillOfTheDayCard       (the skill card with Done)
 *   - DiaryCardExpander       (collapsed urge entry)
 *   - UrgeSlider              (one 0..5 slider for urges)
 *   - CrisisLineRow           (one crisis-line row)
 *   - NavTextButtonRow        (text buttons for Skills/Crisis)
 *
 * State scope (Task 11, no JournalRoot changes):
 *   - `lastSkillDone` is local-only state inside this
 *     composable. The persistence path is the diary card
 *     entry, which writes the mood and the skill used to
 *     DiaryCardPrefs / SkillsPrefs. The 60-second "skill
 *     recently done" window from the brief IS implemented.
 *     Tapping Done on the skill card sets `lastSkillDone`
 *     and arms a 60-second timer; when the timer fires,
 *     `lastSkillDone` is reset to null. Tapping Done a
 *     second time cancels the previous timer and re-arms.
 *     The Skills library's Done (v0.66.2) sets the same
 *     flag via `pendingArm` and pops back to Today.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mindanchor.journal.diary.Urges
import org.mindanchor.journal.skills.Skill
import org.mindanchor.journal.skills.SkillId
import org.mindanchor.journal.skills.SkillsLibrary
import org.mindanchor.R

/**
 * The diary-expander enablement window after a skill is
 * marked Done. The brief pins this at 60 seconds — long
 * enough for a DBT "use the skill, then notice the urge"
 * sequence, short enough that the diary expander does
 * not stay armed across the rest of the user's day.
 */
private const val URGE_LOG_WINDOW_MS: Long = 60_000L

/**
 * The Today body. v0.66.0 = single screen with mood,
 * journal, skill-of-the-day, diary card expander,
 * 14-day strip, and a 4-line crisis footer. The journal
 * entry (BasicTextField) is the centre of the screen;
 * the mood chips above it are a session-scoped input;
 * the skill card and diary expander are DBT-shaped
 * optional flows. The crisis footer is always visible
 * above the 3-icon footer.
 *
 * The hard-coded crisis numbers (iCall / Vandrevala /
 * AASRA / Tele-MANAS) are the same four numbers the
 * v0.66.0 plan pins for the 4-line crisis footer. They
 * are NOT imported from a shared module — they live
 * here, in the v0.66.0 crisis footer, so the surface
 * is self-contained and unit-testable.
 */
@Composable
internal fun JournalToday(
    entryBody: String,
    onEntryBodyChange: (String) -> Unit,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onSettings: () -> Unit,
    onCall: (String) -> Unit = {},
    onSkillDone: (SkillId) -> Unit = {},
    onUrgeEntry: (Urges) -> Unit = {},
    onExportRequest: () -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
    onNavigateToCrisis: () -> Unit = {},
    voiceFirstEnabled: Boolean = false,
    therapistExportEnabled: Boolean = false,
    skillOfTheDay: SkillId = SkillId.BREATHING_SPACE,
    // v0.66.2: mood is now persisted to today's diary card.
    // `currentMood` is the index into `Mood.entries` for the
    // first emotion in the card (0..4), or null when the card
    // has no emotions. `onMoodChange` writes the new chip
    // selection back to the card. The previous session-local
    // `var mood` was lost on app restart, which left the
    // diary card's `emotions` empty even when the user had
    // clearly picked a mood for the day.
    currentMood: Int? = null,
    onMoodChange: (Int?) -> Unit = {},
    // v0.66.2: one-shot signal from JournalRoot. When the
    // user marks a skill Done in the Skills library, Root
    // sets `pendingArm = true` and pops the Skills screen.
    // When Today recomposes and sees `pendingArm = true`,
    // it arms the 60s diary expander window the same way a
    // tap on the Skill of the Day's Done button would have
    // — the user just did a skill, the right next affordance
    // is to log any urges it surfaced. `onPendingArmConsumed`
    // flips the flag back to false (one-shot).
    pendingArm: Boolean = false,
    onPendingArmConsumed: () -> Unit = {},
    // v0.66.2: 2D Affect-Grid toggle. When ON, the mood row
    // renders a 2x2 grid (valence × arousal) instead of the
    // 1D 5-chip row. The grid's quadrant-to-Mood mapping is
    // preserved in `AffectGrid` below — Drained→CRUSHED,
    // Tense→HEAVY, Calm→STEADY, Energized→BRIGHT. LIGHT is
    // the 5th state but not in the 2D grid (it sits between
    // Calm and Energized in the original 1D — the 2D drops
    // it for a cleaner quadrant tap target). The
    // `onMoodChange` callback is unchanged: tapping a grid
    // quadrant still writes the mapped Mood index to
    // DiaryCardPrefs.
    affectGridEnabled: Boolean = false,
    // v0.67.0: the 14-day N-of-1 mood history. The list
    // contains exactly 14 entries, oldest first; the int is
    // the day-by-day mood ordinal (0..4) or null when the
    // day was not logged. The list is computed in
    // JournalRoot from `diaryCardPrefs.entriesInRange`. The
    // 14-day floor is from the v0.65.0 mood screen pattern
    // (median + MAD per person, 14-day floor, 180-day
    // history prune — see MindAnchor user-memory entry
    // "N-of-1 framing for wearable surfaces (MindAnchor)").
    // The strip renders the user's own median + MAD, not a
    // population comparison.
    moodHistory: List<Pair<LocalDate, Int?>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    // BPD-first: the date is shown, the time is not.
    // v0.64.0 keeps the date because dates help orient
    // ("Monday" vs "Tuesday" matters) but omits the
    // hour/minute (which would be a counter). The format
    // "EEEE  d MMMM" produces "Thursday 21 August" with
    // proper title-case — we do NOT lowercase + only
    // title-case the first character.
    val today = remember { LocalDate.now() }
    val dateText = today.format(DateTimeFormatter.ofPattern("EEEE  d MMMM", Locale.US))

    // `lastSkillDone` is enabled the moment the user taps
    // Done on the skill card, and a 60-second timer
    // (URGE_LOG_WINDOW_MS, per the brief) automatically
    // disables it again. Tapping Done a second time
    // cancels the previous timer and re-arms with the
    // new skill — the user can change their mind and
    // re-record a different skill within the window.
    var lastSkillDone: SkillId? by remember { mutableStateOf(null) }
    val lastSkillDoneJob = remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // v0.66.2: voice-first read-side. The `voiceFirstEnabled`
    // toggle was plumbed in v0.66.1 but unread — the actual
    // TTS surface landed in v0.66.2. The TTS instance is
    // initialised once at composition (async, with a status
    // callback that sets `ttsReady` to true on success) and
    // released at dispose. The "speaking" flag drives the
    // button label swap (Read aloud → Stop). The user can
    // tap a skill's "Read aloud" to hear its how-to-do-it
    // text via the system TTS engine. The TTS engine is the
    // system default — on the test emulator it is Google's
    // speech engine, no network call from MindAnchor.
    val ttsContext = LocalContext.current
    val ttsState = remember { mutableStateOf<TextToSpeech?>(null) }
    val ttsReady = remember { mutableStateOf(false) }
    val ttsSpeaking = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        ttsState.value = TextToSpeech(ttsContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady.value = true
            }
        }
        onDispose {
            ttsState.value?.stop()
            ttsState.value?.shutdown()
            ttsState.value = null
            ttsReady.value = false
        }
    }
    val speakSkill: (Skill) -> Unit = speakSkillImpl@{ sk ->
        val tts = ttsState.value
        // v0.67.0: TTS spam dedupe. A user in distress who
        // taps "Read aloud" 20 times in 2 seconds should
        // not queue 20 utterances. The TTS engine's
        // QUEUE_FLUSH replaces the queue on each speak(),
        // which mostly handles the spam — but it also
        // interrupts itself mid-utterance, which is its
        // own bad UX. We gate here: if we're already
        // speaking, ignore the re-tap. The user can stop
        // the current utterance with the "Stop" button.
        if (tts == null || !ttsReady.value) return@speakSkillImpl
        if (ttsSpeaking.value) return@speakSkillImpl
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { ttsSpeaking.value = true }
            override fun onDone(utteranceId: String?) { ttsSpeaking.value = false }
            @Deprecated("Required by API")
            override fun onError(utteranceId: String?) { ttsSpeaking.value = false }
            @Deprecated("Required by API")
            override fun onError(utteranceId: String?, errorCode: Int) { ttsSpeaking.value = false }
        })
        // Read the title + when-to-use + how-to-do-it, with a
        // short pause-like phrase between sections so the
        // listener can follow the structure. TTS engines
        // honour SSML breaks; this stays plain text to keep
        // the dependency surface to the engine default.
        val text = buildString {
            append(sk.title).append(". ")
            append(sk.whenToUse).append(". ")
            append("How to do it. ").append(sk.howToDoIt)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mindanchor-skill-${sk.id.name}")
    }
    val stopSpeaking: () -> Unit = {
        ttsState.value?.stop()
        ttsSpeaking.value = false
    }

    // v0.66.2: arm the 60s diary expander window when the
    // user just did a skill via the Skills library. Without
    // this, the only way to enable the urge expander was
    // the Skill of the Day card on Today, which is not
    // the natural follow-up after "I just did TIPP from
    // the Crisis screen". The arming is identical to the
    // Skill of the Day card's onDone — same window, same
    // skill, same `delay(URGE_LOG_WINDOW_MS)`. Then we
    // consume the flag so a later re-composition does not
    // re-arm (one-shot).
    LaunchedEffect(pendingArm) {
        if (pendingArm) {
            lastSkillDoneJob.value?.cancel()
            lastSkillDone = skillOfTheDay
            lastSkillDoneJob.value = scope.launch {
                delay(URGE_LOG_WINDOW_MS)
                lastSkillDone = null
            }
            onPendingArmConsumed()
        }
    }

    val skill = remember(skillOfTheDay) { SkillsLibrary.byId(skillOfTheDay) }
    val canLogUrge = lastSkillDone != null

    Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        // v0.66.2: the 3-icon footer is now sticky (always
        // pinned to the bottom of the viewport, visible without
        // scrolling). The rest of the surface — the paper
        // card with header, mood, composer, skill, diary
        // expander, crisis lines, export button, and the
        // v0.66.1 nav row — scrolls inside a `weight(1f)`
        // Column above the footer. Previously the footer
        // lived at the end of the scroll, which meant a
        // user on the Crisis / Skills routes (v0.66.1 nav
        // row) had to scroll past the entire card to reach
        // Search / Archive / Settings.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            JournalPaperCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                // 1. Header — TODAY + divider + date. (v0.65.0, kept)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = stringResource(R.string.journal_today_header),
                        style = TextStyle(
                            fontFamily = JournalSerif,
                            fontWeight = FontWeight.Light,
                            fontSize = 32.sp,
                            letterSpacing = 5.sp,
                        ),
                        color = Terracotta,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Terracotta.copy(alpha = 0.30f)),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = dateText,
                        style = JournalSmallCaps,
                        color = Ink.copy(alpha = 0.40f),
                    )
                }

                // 2. 14-day N-of-1 strip. v0.67.0 replaces the
                //    v0.66.0/v0.66.1 text-only placeholder with
                //    a real strip backed by the diary card
                //    history. The strip's median + MAD is the
                //    user's OWN, not a population comparison
                //    (N-of-1 framing — per-person, robust
                //    z-score). A 14-day floor is preserved per
                //    the v0.65.0 mood screen pattern; under
                //    14 days of data, the strip shows the
                //    "still learning" copy from the placeholder
                //    rather than fake "z-score" colouring.
                NOfOneStrip(
                    history = moodHistory,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Mood input. Two shapes, gated by
                //    `affectGridEnabled`:
                //    - OFF (default): the 1D 5-chip row.
                //    - ON: the 2D Affect-Grid (valence × arousal,
                //      2x2). Same `onMoodChange` callback — the
                //      grid maps each quadrant to a Mood index.
                //    v0.66.2: both shapes are driven by the
                //    persisted diary card (passed in as
                //    `currentMood` + `onMoodChange` from
                //    JournalRoot), not by session-local state.
                if (affectGridEnabled) {
                    // v0.67.0: a one-line label sits above the
                    // 2D grid. The semantic of the 1D ↔ 2D
                    // swap is "same mood, different lens" —
                    // the user's persisted mood carries over,
                    // but the labels (Steady vs Calm) are
                    // different. Without the label, the swap
                    // can read as "my mood disappeared". The
                    // label is always visible (not a one-time
                    // tooltip) because the toggle is sticky
                    // and a user may toggle back and forth.
                    Text(
                        text = stringResource(R.string.journal_mood_swap_label),
                        style = TextStyle(
                            fontFamily = JournalSerif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Light,
                            fontSize = 11.sp,
                        ),
                        color = Ink.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                    AffectGrid(
                        selected = currentMood,
                        onSelect = { tapped ->
                            onMoodChange(if (currentMood == tapped) null else tapped)
                        },
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                } else {
                    MoodChipsRow(
                        selected = currentMood,
                        onSelect = { tapped ->
                            onMoodChange(if (currentMood == tapped) null else tapped)
                        },
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Journal composer (v0.65.0 pattern, kept verbatim).
                //    - Single Column (no fixed-height Box)
                //    - BasicTextField always present, with the
                //      "Anything here?" placeholder as the default
                //      empty state.
                //    - "Thanks for writing that." validation line
                //      appears the moment the user types, directly
                //      below the body.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    BasicTextField(
                        value = entryBody,
                        onValueChange = onEntryBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = JournalSerif,
                            fontWeight = FontWeight.Light,
                            fontSize = 20.sp,
                            lineHeight = 32.sp,
                            color = Ink.copy(alpha = 0.80f),
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Terracotta),
                        decorationBox = { innerTextField ->
                            if (entryBody.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.journal_anything_here),
                                    style = TextStyle(
                                        fontFamily = JournalSerif,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 20.sp,
                                        lineHeight = 32.sp,
                                    ),
                                    color = Ink.copy(alpha = 0.20f),
                                )
                            } else {
                                innerTextField()
                            }
                        },
                    )
                    if (entryBody.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Terracotta.copy(alpha = 0.20f)),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.journal_thanks_for_writing),
                            style = TextStyle(
                                fontFamily = JournalSerif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                            color = AcknowledgeTeal,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 5. Skill-of-the-day card. Tapping "Done" both
                //    sets the local `lastSkillDone` (enables the
                //    diary expander for URGE_LOG_WINDOW_MS) AND
                //    calls `onSkillDone`, which JournalRoot wires
                //    to SkillsPrefs.markUsed. A second Done tap
                //    within the window cancels the previous timer
                //    and re-arms with the new skill.
                SkillOfTheDayCard(
                    skill = skill,
                    onDone = {
                        lastSkillDoneJob.value?.cancel()
                        lastSkillDone = skill.id
                        onSkillDone(skill.id)
                        lastSkillDoneJob.value = scope.launch {
                            delay(URGE_LOG_WINDOW_MS)
                            lastSkillDone = null
                        }
                    },
                    // v0.66.2: voice-first read-side. The
                    // "Read aloud" / "Stop" button is rendered
                    // only when `voiceFirstEnabled` is true
                    // (default OFF — the button is opt-in via
                    // Settings → Voice-first for crisis, check-
                    // in, skills). When speaking, the button
                    // becomes "Stop" and stops the TTS engine.
                    voiceFirstEnabled = voiceFirstEnabled,
                    isSpeaking = ttsSpeaking.value,
                    onSpeak = { speakSkill(skill) },
                    onStop = stopSpeaking,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Diary card expander. Collapsed by default.
                //    The "Log an urge" label becomes the disabled
                //    "Use a skill first" until the user has tapped
                //    Done on the skill card. When expanded, three
                //    sliders (NSSI / Suicidal / Dissociation, 0..5)
                //    feed `onUrgeEntry` on every change. The actual
                //    DiaryCardPrefs write happens in JournalRoot
                //    (Task 12 follow-up); for now this is a callback
                //    only.
                DiaryCardExpander(
                    canLog = canLogUrge,
                    onUrgeEntry = onUrgeEntry,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 8. Optional export button. Visible only when
                //    `therapistExportEnabled` is true (default OFF
                //    per Task 10). The onExportRequest callback is
                //    wired in JournalRoot to the therapist-export
                //    intent (Task 12 follow-up).
                if (therapistExportEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        OutlinedButton(onClick = onExportRequest) {
                            Text(
                                text = stringResource(R.string.journal_share_with_therapist),
                                style = TextStyle(
                                    fontFamily = JournalSerif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                    }
                }

                // 8b. v0.66.1 nav row — Skills and Crisis reach
                //     the two new DBT-shaped routes that the
                //     v0.66.0 plan added but the v0.66.0 Today
                //     could not navigate to. The 3-icon footer
                //     below keeps v0.65.0's search/archive/settings
                //     for unchanged nav to the legacy 5 routes.
                //     The text-button shape is BPD-safe (no "!"
                //     affordance, validate-then-suggest copy).
                NavTextButtonRow(
                    buttons = listOf(
                        NavTextButton(stringResource(R.string.journal_nav_skills), onNavigateToSkills),
                        NavTextButton(stringResource(R.string.journal_nav_crisis), onNavigateToCrisis),
                    ),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )

                // 9. The persistent 3-icon footer (v0.65.0, kept)
                //    was here in v0.66.1, but it lived at the end
                //    of the scroll, which meant the user had to
                //    scroll past the entire paper card to reach
                //    Search / Archive / Settings. v0.66.2 pulls
                //    the footer out of the scroll so it is sticky
                //    (see the outer Column above) and accessible
                //    without scrolling. The 4-line crisis block
                //    stays inside the card (item 7) — that is the
                //    surface's promise, not a nav chrome.
                //
                // JournalFooter(...) is rendered as a sibling
                // of the scrollable Column, after this block.
            }
        }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // v0.67.0: the 4-line crisis bar is now sticky
        // (pinned between the scrollable card and the
        // 3-icon footer). v0.66.0/v0.66.1/v0.66.2 had the
        // crisis lines inside the paper card, which meant
        // a user who had scrolled to read their own
        // journal entry could not see the iCall number
        // without scrolling back up. The brief for a
        // crisis line is "always visible" — that is the
        // safety floor, not a UX preference. v0.66.2
        // made the 3-icon footer sticky; v0.67.0 makes
        // the crisis bar sticky too. The bar uses the
        // same four hard-coded numbers (iCall /
        // Vandrevala / AASRA / Tele-MANAS), long-press
        // to dial (BPD-first — deliberate, not
        // impulsive), and the same CrisisLineRow
        // composable. A 1dp terracotta hairline above
        // the bar separates it from the paper card
        // without a hard card edge.
        //
        // v0.67.0: the names and the hours-of-
        // availability text are pulled from
        // `res/values*/strings.xml` so the chrome
        // translates to Tamil / Hindi / Kannada. The
        // NUMBERS stay in code (the v0.67.0 D-6
        // localisation brief is explicit: "crisis line
        // numbers stay in code, hard-coded"). The
        // numbers are the public-safety digits and must
        // be exact in every locale.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaperCard)
                .border(
                    width = 1.dp,
                    color = Terracotta.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(0.dp),
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            CrisisLineRow(
                name = stringResource(R.string.crisis_line_icall_name),
                tel = "9152987821",
                hours = stringResource(R.string.crisis_line_icall_hours),
                onCall = onCall,
            )
            CrisisLineRow(
                name = stringResource(R.string.crisis_line_vandrevala_name),
                tel = "18602662362",
                hours = stringResource(R.string.crisis_line_vandrevala_hours),
                onCall = onCall,
            )
            CrisisLineRow(
                name = stringResource(R.string.crisis_line_aasra_name),
                tel = "9820466726",
                hours = stringResource(R.string.crisis_line_aasra_hours),
                onCall = onCall,
            )
            CrisisLineRow(
                name = stringResource(R.string.crisis_line_telemanas_name),
                tel = "14416",
                hours = stringResource(R.string.crisis_line_telemanas_hours),
                onCall = onCall,
            )
        }

        // v0.66.2: the sticky 3-icon footer. Pinned to the
        // bottom of the viewport. Same visual chrome as the
        // v0.65.0 footer (translucent paper + 1dp hairline),
        // just no longer inside the scroll content. Active
        // icon is `None` on Today — the legacy 5-route icons
        // (Search / Archive / Settings) are all the nav
        // surface this footer offers; the v0.66.1 nav row
        // (Skills / Crisis) is the v0.66.x nav surface.
        JournalFooter(
            activeIcon = FooterIcon.None,
            onSearch = onSearch,
            onArchive = onArchive,
            onSettings = onSettings,
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Inline sub-composables (all private; no new files for v0.66.0).
// ─────────────────────────────────────────────────────────────────

/**
 * 14-day N-of-1 strip. v0.67.0: the v0.66.0/v0.66.1
 * text-only placeholder is replaced with a real strip
 * backed by the user's diary-card mood history.
 *
 * The strip's median + MAD is the user's OWN — a per-
 * person robust baseline, not a population comparison
 * (N-of-1 framing). The cell colour is the absolute
 * deviation from the user's own median, expressed in
 * robust z-score units (|x - median| / MAD). The colour
 * does NOT say "good" or "bad" — only direction. A
 * cell at the user's own median is the same tint as the
 * empty (unlogged) cell, so the strip's resting state is
 * the user's normal.
 *
 * The 14-day floor follows the v0.65.0 mood screen
 * pattern (median + MAD requires >= 14 paired days to
 * be stable). Under 14 days of data, the strip shows
 * the "still learning" copy and no cells. Empty days
 * (no mood logged) render as a thin hairline cell so
 * the user can see where the data is missing.
 *
 * Design intent (BPD-safe): no "!" anywhere, no
 * "you missed a day" copy, no streaks, no
 * "abnormal/below" labels. The cells say "this is your
 * day-to-day" and nothing more.
 */
@Composable
private fun NOfOneStrip(
    history: List<Pair<LocalDate, Int?>>,
    modifier: Modifier = Modifier,
) {
    val validOrdinals: List<Int> = history.mapNotNull { it.second }
    val hasEnoughData = validOrdinals.size >= 14
    val median: Int? = if (hasEnoughData) {
        val sorted = validOrdinals.sorted()
        val mid = sorted.size / 2
        if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    } else {
        null
    }
    val mad: Int? = if (hasEnoughData) {
        val med = median!!
        val absDev = validOrdinals.map { kotlin.math.abs(it - med) }.sorted()
        val mid = absDev.size / 2
        if (absDev.size % 2 == 0) (absDev[mid - 1] + absDev[mid]) / 2 else absDev[mid]
    } else {
        null
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.journal_strip_pattern),
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
            ),
            color = Ink.copy(alpha = 0.50f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (!hasEnoughData) {
            // v0.67.0: still-learning copy. The
            // 14-day floor is a research-backed
            // minimum for a stable median + MAD.
            // Under that floor, no cells are drawn —
            // a partially-shaded strip would be
            // dishonest because the baseline itself
            // is not yet stable.
            val logged = validOrdinals.size
            Text(
                text = stringResource(R.string.journal_strip_still_learning, logged),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                ),
                color = Ink.copy(alpha = 0.30f),
            )
        } else {
            // v0.67.0: render 14 cells, oldest
            // first. The cell colour is the robust
            // z-score of that day's mood against
            // the user's own median + MAD. Empty
            // days (null ordinal) render as a
            // hairline cell (same colour as the
            // paper) so the user can see where the
            // data is missing. The strip does NOT
            // say "good" or "bad" — only "this is
            // your day-to-day".
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val med = median!!
                val madValue = mad!!
                history.takeLast(14).forEach { (_, ordinal) ->
                    val cellColor = if (ordinal == null) {
                        // Missing day — hairline cell,
                        // ink at 8% so it sits on the
                        // paper without being read as
                        // a data point.
                        Ink.copy(alpha = 0.08f)
                    } else {
                        val z = if (madValue == 0) 0f else kotlin.math.abs(ordinal - med).toFloat() / madValue
                        val mood = Mood.entries.getOrNull(ordinal) ?: Mood.STEADY
                        when {
                            z == 0f -> mood.bg
                            z < 0.5f -> mood.bg
                            z < 1f -> mood.fg.copy(alpha = 0.30f)
                            else -> mood.fg.copy(alpha = 0.55f)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .background(cellColor, shape = RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

/**
 * Five mood chips in a single horizontal row. Single-select,
 * deselectable (tapping the selected chip clears it). NO
 * dimming of unselected chips — BPD-first: don't rank the
 * states; "Steady" is not less than "Bright". The chip's
 * tint uses the existing `Mood.bg` / `Mood.fg` palette; the
 * selected chip is outlined in terracotta instead of the
 * mood-fg hairline so the selection state is unambiguous
 * without the dimming the v0.65.0 mood screen dropped.
 */
@Composable
private fun MoodChipsRow(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Mood.values().forEach { mood ->
            val isSelected = selected == mood.ordinal
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(mood.bg, shape = RoundedCornerShape(8.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Terracotta else mood.fg.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(mood.ordinal) }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = mood.displayName,
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Light,
                        fontSize = 12.sp,
                    ),
                    color = mood.fg,
                )
            }
        }
    }
}

/**
 * 2D Affect-Grid (Russell circumplex, v0.66.2). When the
 * "2D mood grid (Affect-Grid)" toggle is ON in Settings,
 * Today's mood row renders this 2x2 grid instead of the
 * 1D 5-chip [MoodChipsRow].
 *
 * The 2D UI exposes 4 of the 5 Mood states (LIGHT, the
 * in-between state in the 1D row, has no 2D equivalent and
 * is dropped). The quadrant → Mood mapping is:
 *   - top-left  (Drained, low energy + sad) → CRUSHED
 *   - top-right (Tense,   high energy + sad) → HEAVY
 *   - bot-left  (Calm,    low energy + happy) → STEADY
 *   - bot-right (Energized, high energy + happy) → BRIGHT
 *
 * The grid uses the existing `Mood.bg` / `Mood.fg` palette
 * for the selected cell's tint, with the same terracotta
 * border used by [MoodChipsRow] for the selected state.
 * This keeps the visual language consistent across the
 * 1D and 2D mood input shapes. The Y axis (energy) is
 * vertical, the X axis (valence) is horizontal — the
 * standard Russell (1980) layout.
 */
@Composable
private fun AffectGrid(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top row — sad (low valence).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AffectQuadrant(
                label = "Drained",
                mood = Mood.CRUSHED,
                isSelected = selected == Mood.CRUSHED.ordinal,
                onClick = { onSelect(Mood.CRUSHED.ordinal) },
                modifier = Modifier.weight(1f),
            )
            AffectQuadrant(
                label = "Tense",
                mood = Mood.HEAVY,
                isSelected = selected == Mood.HEAVY.ordinal,
                onClick = { onSelect(Mood.HEAVY.ordinal) },
                modifier = Modifier.weight(1f),
            )
        }
        // Bottom row — happy (high valence).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AffectQuadrant(
                label = "Calm",
                mood = Mood.STEADY,
                isSelected = selected == Mood.STEADY.ordinal,
                onClick = { onSelect(Mood.STEADY.ordinal) },
                modifier = Modifier.weight(1f),
            )
            AffectQuadrant(
                label = "Energized",
                mood = Mood.BRIGHT,
                isSelected = selected == Mood.BRIGHT.ordinal,
                onClick = { onSelect(Mood.BRIGHT.ordinal) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One 2D Affect-Grid cell. The 2D label ("Drained" / "Tense"
 * / "Calm" / "Energized") is shown on the cell; the cell's
 * background tint uses the mapped `Mood.bg` colour. The
 * 2D label is the only visible text in the cell — the
 * 5-state Mood name (e.g. "Crushed") is the internal
 * selection target, not shown to the user in 2D mode.
 */
@Composable
private fun AffectQuadrant(
    label: String,
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .background(mood.bg, shape = RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Terracotta else mood.fg.copy(alpha = 0.20f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = mood.fg,
        )
    }
}

/**
 * The skill-of-the-day card. The skill is read from
 * `SkillsLibrary.byId(skillOfTheDay)`; tapping "Done" calls
 * [onDone], which both updates the local `lastSkillDone` in
 * [JournalToday] AND fires the `onSkillDone` callback that
 * JournalRoot wires to `SkillsPrefs.markUsed`. The card
 * itself is a small tint over AcknowledgeTeal at low alpha
 * (validate-then-suggest — the skill is offered, not
 * commanded), with the title, the "When to use" line, and
 * the Done text-button.
 */
@Composable
private fun SkillOfTheDayCard(
    skill: Skill,
    onDone: () -> Unit,
    // v0.66.2: voice-first affordance. The "Read aloud" /
    // "Stop" button is rendered only when `voiceFirstEnabled`
    // is true. The button text swaps to "Stop" while the TTS
    // engine is speaking the skill's how-to-do-it text.
    voiceFirstEnabled: Boolean = false,
    isSpeaking: Boolean = false,
    onSpeak: () -> Unit = {},
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AcknowledgeTeal.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            )
            .border(
                width = 1.dp,
                color = AcknowledgeTeal.copy(alpha = 0.30f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.journal_skill_of_day_label),
            style = JournalSmallCaps,
            color = AcknowledgeTeal,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = skill.title,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
            ),
            color = Ink,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = skill.whenToUse,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = Ink.copy(alpha = 0.60f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // v0.66.2: voice-first read-side button. Only
            // rendered when the toggle is ON. The "Stop"
            // label is the same widget — tapping it while
            // speaking halts the TTS engine (does not re-
            // start it). BPD-safe copy: "Read aloud" not
            // "Listen now" — validate-then-suggest.
            // v0.67.0: a small speaker / stop glyph is rendered
            // to the left of the text. The button was
            // v0.66.2-discoverable (text-only) but for a
            // feature that exists to be *found* in
            // distress, the text-only affordance was too
            // quiet. The icon makes the button look like
            // an audio control and not just a label.
            if (voiceFirstEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clickable(onClick = { if (isSpeaking) onStop() else onSpeak() })
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (isSpeaking) {
                        StopGlyph(color = AcknowledgeTeal)
                    } else {
                        SpeakerGlyph(color = AcknowledgeTeal)
                    }
                    Text(
                        text = if (isSpeaking) stringResource(R.string.journal_stop) else stringResource(R.string.journal_read_aloud),
                        style = TextStyle(
                            fontFamily = JournalSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = AcknowledgeTeal,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(1.dp))
            }
            Text(
                text = stringResource(R.string.journal_done_button),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = AcknowledgeTeal,
                modifier = Modifier
                    .clickable(onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The diary card expander. Collapsed by default — a single
 * row that says "Use a skill first" until the user has
 * tapped Done on the skill card, then it becomes
 * "Log an urge" and is tappable. Tapping expands three
 * sliders (NSSI / Suicidal / Dissociation, 0..5). Every
 * slider change calls [onUrgeEntry] with the current
 * `Urges(nssi, sui, dis)`. The actual DiaryCardPrefs write
 * is wired in JournalRoot (Task 12 follow-up); for now
 * this is a callback only.
 *
 * The 0..5 range matches the DBT diary card scale
 * (Linehan 1993, McKay/Wood/Brantley 2007) and the
 * `Urges` data class's `require(0..5)`. A `.toInt()` on
 * the slider value (continuous 0f..5f) is always in range
 * — 4.99 truncates to 4, 5.0 truncates to 5.
 */
@Composable
private fun DiaryCardExpander(
    canLog: Boolean,
    onUrgeEntry: (Urges) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var nssi by remember { mutableStateOf(0f) }
    var sui by remember { mutableStateOf(0f) }
    var dis by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (canLog) stringResource(R.string.journal_log_urge) else stringResource(R.string.journal_use_skill_first),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontStyle = if (canLog) FontStyle.Normal else FontStyle.Italic,
                    fontWeight = if (canLog) FontWeight.Normal else FontWeight.Light,
                    fontSize = 14.sp,
                ),
                color = if (canLog) Terracotta else Ink.copy(alpha = 0.30f),
                modifier = if (canLog) {
                    Modifier.clickable {
                        expanded = !expanded
                    }
                } else {
                    Modifier
                },
            )
            if (canLog && expanded) {
                Text(
                    text = stringResource(R.string.journal_close),
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 12.sp,
                    ),
                    color = Ink.copy(alpha = 0.40f),
                    modifier = Modifier.clickable { expanded = false },
                )
            }
        }
        if (expanded && canLog) {
            Spacer(modifier = Modifier.height(12.dp))
            UrgeSlider(
                label = "NSSI",
                value = nssi,
                onValueChange = {
                    nssi = it
                    onUrgeEntry(Urges(nssi.toInt(), sui.toInt(), dis.toInt()))
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            UrgeSlider(
                label = "Suicidal",
                value = sui,
                onValueChange = {
                    sui = it
                    onUrgeEntry(Urges(nssi.toInt(), sui.toInt(), dis.toInt()))
                },
            )
            Spacer(modifier = Modifier.height(4.dp))
            UrgeSlider(
                label = "Dissociation",
                value = dis,
                onValueChange = {
                    dis = it
                    onUrgeEntry(Urges(nssi.toInt(), sui.toInt(), dis.toInt()))
                },
            )
        }
    }
}

/**
 * A single 0..5 urge slider with a label and a live numeric
 * readout. Used by [DiaryCardExpander]. The value is
 * continuous (no discrete `steps`) so the user can land on
 * any 0..5 value, and `.toInt()` truncates to the integer
 * the [Urges] data class requires.
 */
@Composable
private fun UrgeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Light,
                    fontSize = 13.sp,
                ),
                color = Ink.copy(alpha = 0.60f),
            )
            Text(
                text = value.toInt().toString(),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                ),
                color = Ink.copy(alpha = 0.80f),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..5f,
            colors = SliderDefaults.colors(
                thumbColor = Terracotta,
                activeTrackColor = Terracotta.copy(alpha = 0.60f),
                inactiveTrackColor = Ink.copy(alpha = 0.10f),
            ),
        )
    }
}

/**
 * One crisis-line row. The name and the number (with the
 * hours-of-availability line) sit on the left; a small
 * "Call" affordance on the right. Long-press anywhere on
 * the row fires [onCall] with the tel — long-press is
 * the BPD-first shape for a crisis line: a deliberate,
 * slow gesture rather than an impulsive single tap. The
 * single-tap does NOT dial (a single tap on a crisis
 * number would be too easy to fire by accident). The
 * four numbers are iCall 9152987821, Vandrevala
 * 18602662362, AASRA 9820466726, Tele-MANAS 14416.
 */
@Composable
private fun CrisisLineRow(
    name: String,
    tel: String,
    hours: String,
    onCall: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(tel) {
                detectTapGestures(onLongPress = { onCall(tel) })
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                ),
                color = Ink.copy(alpha = 0.70f),
            )
            Text(
                text = "$tel · $hours",
                style = TextStyle(
                    fontFamily = JournalSans,
                    fontWeight = FontWeight.Light,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = Ink.copy(alpha = 0.40f),
            )
        }
        Text(
            text = stringResource(R.string.crisis_line_call),
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = Terracotta,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// v0.66.1: NavTextButtonRow — a row of text buttons
// used to reach the new Skills / Crisis routes from Today.
// The 3-icon footer below (Search · Archive · Settings)
// still routes to the v0.65.0 surfaces, so the new buttons
// are additive — they appear above the footer and only
// call the `onNavigateToSkills` / `onNavigateToCrisis`
// callbacks that v0.66.0 wired but the v0.66.0 Today
// could not surface.
// ─────────────────────────────────────────────────────────────────

private data class NavTextButton(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun NavTextButtonRow(
    buttons: List<NavTextButton>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        buttons.forEach { btn ->
            Text(
                text = btn.label,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = Terracotta,
                modifier = Modifier
                    .clickable(onClick = btn.onClick)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}
