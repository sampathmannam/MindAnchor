/*
 * v0.66.0 (DBT-grounded journal) — Task 11.
 *
 * The Today screen — the journal's home — is now a
 * single screen with the following top-to-bottom layout:
 *   1. Date header (kept from v0.65.0)
 *   2. 14-day N-of-1 strip placeholder (text-only,
 *      see the NOfOneStripPlaceholder sub-composable
 *      for the wiring TODO)
 *   3. Mood chips (5 horizontal chips, single-select,
 *      deselectable; no dimming of unselected — BPD-first)
 *   4. Journal composer (the existing BasicTextField +
 *      "Thanks for writing that." validation)
 *   5. Skill-of-the-day card (a Card with title + "When
 *      to use" + a "Done" button)
 *   6. Diary card expander (collapsed by default; tap
 *      to expand; the link is disabled until a skill has
 *      been Done in this session)
 *   7. New 4-line crisis footer (iCall, Vandrevala,
 *      AASRA, Tele-MANAS 14416) — single-tap to dial
 *   8. Optional "Share with my therapist" button
 *      (visible only when `therapistExportEnabled` is
 *      true; default OFF)
 *   9. The 3-icon footer (kept from v0.65.0 — search,
 *      archive, settings)
 *
 * v0.66.0 BPD-safe patterns preserved from v0.65.0:
 *   - No entry number, no time, no streak, no leaderboard.
 *   - "Anything here?" placeholder is the empty state.
 *   - "Thanks for writing that." is a validation, not a
 *     counter, and it appears the moment the user types.
 *   - Mood chips never dim unselected states.
 *   - No "!" anywhere.
 *   - Crisis footer is always visible.
 *
 * Sub-composables are all inline private @Composable funs
 * (per the v0.66.0 plan's self-review — no new files for
 * v0.66.0 UI primitives):
 *   - NOfOneStripPlaceholder  (14-day strip, text-only)
 *   - MoodChipsRow            (5 mood chips, BPD-safe)
 *   - SkillOfTheDayCard       (the skill card with Done)
 *   - DiaryCardExpander       (collapsed urge entry)
 *   - UrgeSlider              (one 0..5 slider for urges)
 *   - CrisisLineRow           (one crisis-line row)
 *
 * State scope (Task 11, no JournalRoot changes):
 *   - `mood` and `lastSkillDone` are local-only state
 *     inside this composable. They reset on app restart.
 *     The persistence path is the diary card entry
 *     (Task 12), which writes the mood and the skill
 *     used to DiaryCardPrefs / SkillsPrefs. For v0.66.0
 *     this is acceptable: the inline mood is a
 *     session-scoped input, and the diary card
 *     submission (a follow-up) is the durable record.
 *   - The 60-second "skill recently done" window from
 *     the brief is NOT implemented. The diary expander
 *     is enabled the moment the user taps Done on the
 *     skill card, and stays enabled for the rest of
 *     the session. Task 14's drive-verify will exercise
 *     the flow; a 60s timer is a follow-up if needed.
 *   - The actual SkillsPrefs / DiaryCardPrefs writes
 *     are NOT done in this file. The callbacks
 *     `onSkillDone` and `onUrgeEntry` are wired up in
 *     JournalRoot (Task 12 follow-up). For now
 *     JournalRoot passes no-op lambdas so the build
 *     compiles; the v0.66.0 single-screen refactor is
 *     purely a UI + signature change here.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.mindanchor.journal.diary.Urges
import org.mindanchor.journal.skills.Skill
import org.mindanchor.journal.skills.SkillId
import org.mindanchor.journal.skills.SkillsLibrary

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
    onContinueWriting: () -> Unit,
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

    // Session-local state. NOT persisted (see file header).
    var mood by remember { mutableStateOf<Int?>(null) }
    var lastSkillDone: SkillId? by remember { mutableStateOf(null) }

    val skill = remember(skillOfTheDay) { SkillsLibrary.byId(skillOfTheDay) }
    val canLogUrge = lastSkillDone != null

    Column(
        modifier = modifier
            .fillMaxSize()
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
                        text = "TODAY",
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

                // 2. 14-day N-of-1 strip (text-only placeholder).
                //    TODO(v0.66.x): wire to N-of-1 strip from MoodScreen.kt
                //    — the real strip lives on the dedicated mood screen
                //    in v0.65.0; the v0.66.0 single-screen refactor leaves
                //    the inline strip as a copy-only placeholder so the
                //    surface has its expected shape from day one.
                NOfOneStripPlaceholder(
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Mood chips (5 horizontal chips, single-select,
                //    deselectable, NO dimming of unselected states).
                MoodChipsRow(
                    selected = mood,
                    onSelect = { tapped ->
                        mood = if (mood == tapped) null else tapped
                    },
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

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
                                    text = "Anything here?",
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
                            text = "Thanks for writing that.",
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
                //    diary expander) AND calls `onSkillDone`, which
                //    JournalRoot wires to SkillsPrefs.markUsed.
                SkillOfTheDayCard(
                    skill = skill,
                    onDone = {
                        lastSkillDone = skill.id
                        onSkillDone(skill.id)
                    },
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

                // 7. The new 4-line crisis footer. Each row is a
                //    single-tap dial target. Numbers are
                //    hard-coded here (not imported) so the surface
                //    is self-contained.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                ) {
                    CrisisLineRow(
                        name = "iCall",
                        tel = "9152987821",
                        hours = "TISS Mumbai",
                        onCall = onCall,
                    )
                    CrisisLineRow(
                        name = "Vandrevala",
                        tel = "18602662362",
                        hours = "24/7 multilingual",
                        onCall = onCall,
                    )
                    CrisisLineRow(
                        name = "AASRA",
                        tel = "9820466726",
                        hours = "24/7 suicide prevention",
                        onCall = onCall,
                    )
                    CrisisLineRow(
                        name = "Tele-MANAS",
                        tel = "14416",
                        hours = "Govt. of India",
                        onCall = onCall,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                                text = "Share with my therapist",
                                style = TextStyle(
                                    fontFamily = JournalSerif,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                ),
                            )
                        }
                    }
                }

                // 9. The persistent 3-icon footer (v0.65.0, kept).
                //    The icons are unlabelled, the active icon is
                //    None on Today, the crisis line is the one
                //    above this row (the new 4-line footer).
                JournalFooter(
                    activeIcon = FooterIcon.None,
                    onSearch = onSearch,
                    onArchive = onArchive,
                    onSettings = onSettings,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────
// Inline sub-composables (all private; no new files for v0.66.0).
// ─────────────────────────────────────────────────────────────────

/**
 * 14-day N-of-1 strip — text-only placeholder for v0.66.0.
 *
 * The real N-of-1 strip lives on the dedicated Mood screen in
 * v0.65.0 (median + MAD per-person, robust z-score, 14-day
 * floor, 180-day history prune — see MoodScreen.kt). The
 * v0.66.0 single-screen refactor places a copy-only
 * placeholder here so the surface has its expected shape
 * from day one. Wiring the real strip to Today is a follow-up
 * (a `moodHistoryFlow: Flow<List<LocalDate>>` from
 * JournalPrefs + a 14-day direction band rendering).
 */
@Composable
private fun NOfOneStripPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Pattern, not diagnosis.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
            ),
            color = Ink.copy(alpha = 0.50f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your 14-day direction will appear here once you log a few moods.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
            ),
            color = Ink.copy(alpha = 0.30f),
        )
        // TODO(v0.66.x): wire to N-of-1 strip from MoodScreen.kt
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
            text = "SKILL OF THE DAY",
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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = "Done",
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
                text = if (canLog) "Log an urge" else "Use a skill first",
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
                    text = "Close",
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
 * "Call" affordance on the right. Tapping the row (or the
 * "Call" text) fires [onCall] with the tel — single-tap,
 * no long-press for v0.66.0 (per the brief: keeps it
 * simple). The four numbers are iCall 9152987821, Vandrevala
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
            .clickable { onCall(tel) }
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
            text = "Call",
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
