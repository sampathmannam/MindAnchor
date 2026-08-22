/*
 * v0.66.0 (DBT-grounded journal) — Task 12.
 *
 * Two related surfaces live in this file:
 *
 *   1. The existing v0.65.0 `JournalCrisisLine` — a small, two-line,
 *      equal-weight text band that sits above the footer on every
 *      screen in the journal. iCall / Vandrevala / AASRA, each a
 *      long-press target that fires `onCall(tel)`. Unchanged from
 *      v0.65.0 — still used by JournalSettings, JournalArchive,
 *      JournalMood, and JournalQuickNote.
 *
 *   2. The new v0.66.0 `JournalCrisis` — a dedicated full-screen
 *      "if you're in crisis" surface, the centrepiece of the v0.66.0
 *      DBT layer. Top-to-bottom:
 *         a. "I need to ground" panic button (FilledTonalButton,
 *            prominent, terracotta-tinted). Tapping it calls
 *            `onSkillStart(SkillId.STOP)`. A small caption underneath
 *            names the skill so the user knows what tapping does
 *            ("60 seconds. S.T.O.P." — Linehan 1993).
 *         b. The user's Stanley-Brown Safety Plan, editable inline.
 *            Six `OutlinedTextField`s, one per step. Labels come
 *            from `SafetyPlanEntry.LABELS` (the pinned order from
 *            Task 3). Every keystroke calls `onPlanChange` with the
 *            updated plan; the parent persists to DataStore.
 *         c. The 4 India crisis lines — iCall, Vandrevala, AASRA,
 *            Tele-MANAS 14416. Each is a long-press target. A
 *            single-tap does NOT dial (a single tap on a crisis
 *            number is too easy to fire by accident — the v0.65.0
 *            BPD-first pattern). Hours-of-availability is the small
 *            "·" line under the number.
 *         d. "MindAnchor is a personal R&D tool, not a substitute
 *            for therapy." — the disclosure that the rest of the
 *            journal also carries in spirit but says explicitly
 *            here, on the crisis surface.
 *
 * The new `JournalCrisis` is a `public` composable (no `internal`
 * modifier) so `JournalRoot` can call it from its dispatch `when`
 * without crossing visibility. The screen has no header / back arrow
 * / footer in the v0.66.0 MVP — `BackHandler` in `JournalRoot` is
 * the only back path, which matches the v0.66.0 plan's "deep
 * destination" shape (the screen is reached from Today; the system
 * back returns the user to Today; forward navigation is via
 * `onNavigateToSkills` to the skills library, which has its own
 * back).
 *
 * The four crisis-line numbers are hard-coded strings, not imported
 * from a shared module — the surface is self-contained, the
 * numbers are not going to change weekly, and the tests for the
 * v0.66.0 plan pin them as literals.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mindanchor.journal.crisis.SafetyPlanEntry
import org.mindanchor.journal.skills.SkillId

// ─────────────────────────────────────────────────────────────────
// v0.65.0 (kept): the persistent crisis-line text band.
// ─────────────────────────────────────────────────────────────────

/**
 * The crisis line. Two short lines:
 *   "Need to talk?  iCall 9152987821"
 *   "Vandrevala 1860-2662-362  ·  AASRA 9820466726"
 *
 * The 11sp Plus Jakarta Sans at the same alpha as other
 * secondary text keeps the line equal-weight.
 *
 * v0.65.0: each number is a long-press target.
 *   onCall("9152987821")         → iCall
 *   onCall("18602662362")        → Vandrevala
 *   onCall("9820466726")         → AASRA
 *
 * [onCall] is invoked with the phone number to dial, no
 * prefix. The caller (JournalRoot) is responsible for
 * translating that to an ACTION_DIAL intent. The line
 * itself does no I/O — keep the journal composable
 * unit-testable without a Context.
 */
@Composable
internal fun JournalCrisisLine(
    modifier: Modifier = Modifier,
    onCall: (String) -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // First line: "Need to talk?  iCall 9152987821"
            // Only the iCall number is tappable here, not the
            // "Need to talk?" prefix. The whole-line tap is
            // reserved for the iCall number.
            Text(
                text = "Need to talk?  iCall 9152987821",
                style = JournalCrisisLineStyle(),
                color = CrisisLine,
                textAlign = TextAlign.Center,
                modifier = Modifier.pointerInput("iCall") {
                    detectTapGestures(onLongPress = { onCall("9152987821") })
                },
            )
            // Second line: Vandrevala ... · AASRA ...
            // Two independent long-press targets.
            RowCrisisSecondLine(onCall = onCall)
        }
    }
}

/**
 * The second crisis line, with two distinct long-press
 * targets (Vandrevala, AASRA). Layout-wise they sit on
 * one Row with a "·" separator; for the gesture layer
 * each number is its own Box so the long-press hit
 * areas don't overlap.
 */
@Composable
private fun RowCrisisSecondLine(onCall: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Vandrevala 1860-2662-362",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
            modifier = Modifier.pointerInput("Vandrevala") {
                detectTapGestures(onLongPress = { onCall("18602662362") })
            },
        )
        Text(
            text = "  ·  ",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "AASRA 9820466726",
            style = JournalCrisisLineStyle(),
            color = CrisisLine,
            textAlign = TextAlign.Center,
            modifier = Modifier.pointerInput("AASRA") {
                detectTapGestures(onLongPress = { onCall("9820466726") })
            },
        )
    }
}

/**
 * The crisis-line text style. 11sp Plus Jakarta Sans,
 * normal weight, 0.5 letter-spacing. Plus Jakarta Sans
 * is the journal's sans face (the serif is Crimson Pro
 * for body text only).
 */
internal fun JournalCrisisLineStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp,
)

// ─────────────────────────────────────────────────────────────────
// v0.66.0 (Task 12): the dedicated "if you're in crisis" full screen.
// ─────────────────────────────────────────────────────────────────

/**
 * The v0.66.0 crisis surface — a full-screen DBT card with
 * the panic button at the top, the user's Stanley-Brown
 * Safety Plan in the middle, the 4 India crisis lines, and
 * the "not a substitute for therapy" disclosure at the
 * bottom. Reached from Today via `JournalRoute.Crisis`;
 * back is via `BackHandler` in `JournalRoot` (system back)
 * or by tapping the "Open skills library" link, which
 * navigates forward to `JournalRoute.Skills`.
 *
 * The screen is BPD-first in three ways:
 *   - The panic button is the only high-emphasis visual
 *     element. Everything else is paper-and-ink, no badges,
 *     no "!" affordance, no streak, no completion copy.
 *   - Each crisis line is a long-press target, not a
 *     single-tap. A single tap on a crisis number is too
 *     impulsive; long-press is a deliberate, slow gesture.
 *   - The plan editor is plain text fields, no rating, no
 *     "your plan is N% complete" copy, no leaderboard. The
 *     plan is the user's own words; the app does not score
 *     it.
 *
 * [onPlanChange] is called on every keystroke with the
 * full updated [SafetyPlanEntry]. The caller persists the
 * whole plan to DataStore; the cost is six preference
 * writes per edit, which DataStore handles async on a
 * background dispatcher (see `SafetyPlanPrefs` for the
 * rationale on per-field keys vs. a single blob).
 *
 * [onSkillStart] is fired by the panic button with
 * `SkillId.STOP`. The actual skill-composable surface
 * lands in a follow-up task; for v0.66.0 the parent wires
 * this to a no-op so the build compiles.
 *
 * [onNavigateToSkills] is the cross-link to the skills
 * library — a forward nav, not a back button.
 *
 * [onCall] is the dial helper from `JournalRoot`; the
 * surface never calls `Context.startActivity` directly.
 * The signature is `(String) -> Unit = {}` so the composable
 * stays unit-testable without a Context.
 */
@Composable
fun JournalCrisis(
    plan: SafetyPlanEntry,
    onPlanChange: (SafetyPlanEntry) -> Unit,
    onSkillStart: (SkillId) -> Unit,
    onNavigateToSkills: () -> Unit,
    onCall: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. The "I need to ground" panic button. FilledTonalButton
        //    is the M3 "prominent but not loud" emphasis — it has
        //    a tinted fill (terracotta at low alpha in the
        //    journal's light theme) and a clear label. A solid
        //    FilledButton with the project's full-strength
        //    terracotta would be too loud; an OutlinedButton
        //    would not be visible enough for a user in crisis.
        //    The caption underneath ("60 seconds. S.T.O.P.")
        //    names the skill so the user knows what tapping does
        //    (Linehan 1993, McKay/Wood/Brantley 2007).
        FilledTonalButton(
            onClick = { onSkillStart(SkillId.STOP) },
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Text(
                text = "I need to ground",
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                ),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "60 seconds. S.T.O.P. — Stop, Take a breath, Observe, Proceed.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = Ink.copy(alpha = 0.55f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 2. The Stanley-Brown Safety Plan editor. Six text
        //    fields, labels in pinned order (SafetyPlanEntry.LABELS).
        //    Each field is a `OutlinedTextField` (M3); the label
        //    text is the journal's sans for system text. The
        //    brief's `when (i)` dispatch is kept verbatim — the
        //    6 fields in 6 cases is the cleanest shape, and a
        //    typed `Map<Int, (SafetyPlanEntry, String) -> SafetyPlanEntry>`
        //    would be a code smell.
        Text(
            text = "YOUR SAFETY PLAN",
            style = JournalSmallCaps,
            color = Terracotta,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Written by you. On this device. Yours to revise.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = Ink.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        SafetyPlanEntry.LABELS.forEachIndexed { i, label ->
            val value: String = when (i) {
                0 -> plan.warningSigns
                1 -> plan.internalCoping
                2 -> plan.socialDistractions
                3 -> plan.people
                4 -> plan.professionals
                else -> plan.meansRestriction
            }
            val onValueChange: (String) -> Unit = { newValue ->
                onPlanChange(when (i) {
                    0 -> plan.copy(warningSigns = newValue)
                    1 -> plan.copy(internalCoping = newValue)
                    2 -> plan.copy(socialDistractions = newValue)
                    3 -> plan.copy(people = newValue)
                    4 -> plan.copy(professionals = newValue)
                    else -> plan.copy(meansRestriction = newValue)
                })
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = {
                    Text(
                        text = label,
                        style = TextStyle(
                            fontFamily = JournalSans,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                        ),
                    )
                },
                textStyle = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Step 5: when in doubt, call. The numbers below are hard-coded.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
            ),
            color = Ink.copy(alpha = 0.45f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. The 4 India crisis lines. Each row is a
        //    long-press target. The hours-of-availability sits
        //    as a small "·" line under the number so the line
        //    stays at the journal's secondary-text alpha — equal
        //    weight to the rest of the page, not a banner.
        Text(
            text = "CRISIS LINES (INDIA)",
            style = JournalSmallCaps,
            color = Terracotta,
        )
        Spacer(modifier = Modifier.height(8.dp))
        CrisisLineRow(name = "iCall", tel = "9152987821", hours = "TISS Mumbai · Mon-Sat 8am-10pm", onCall = onCall)
        CrisisLineRow(name = "Vandrevala", tel = "18602662362", hours = "24/7 multilingual", onCall = onCall)
        CrisisLineRow(name = "AASRA", tel = "9820466726", hours = "24/7 suicide prevention", onCall = onCall)
        CrisisLineRow(name = "Tele-MANAS", tel = "14416", hours = "24/7 · 20 languages", onCall = onCall)

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Cross-link to the skills library — a forward nav,
        //    not a back. The link is rendered as a small
        //    terracotta text button so it does not compete with
        //    the panic button at the top.
        Text(
            text = "Open the skills library",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
            color = Terracotta,
            modifier = Modifier
                .pointerInput("SkillsLib") {
                    detectTapGestures(onLongPress = { onNavigateToSkills() })
                }
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 5. The disclosure. Validate-then-suggest, not a
        //    disclaimer. BPD-first: the line is honest about
        //    what the app is (a personal R&D tool), without
        //    scaring the user. The colour is at the same
        //    low alpha as the rest of the secondary text.
        Text(
            text = "MindAnchor is a personal R&D tool, not a substitute for therapy.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
            ),
            color = Ink.copy(alpha = 0.40f),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One crisis-line row on the dedicated `JournalCrisis`
 * surface (different from the v0.65.0 `JournalCrisisLine`
 * band on every other screen). Three stacked text lines:
 * the name (prominent), the number, and the hours of
 * availability as a small "·" joined line. The whole row
 * is a long-press target — long-press fires
 * [onCall] with [tel]. Single-tap does NOT dial (a
 * single tap on a crisis number is too easy to fire by
 * accident; the v0.65.0 BPD-first pattern).
 *
 * The row sits in a thin `PaperBorder` outline at low
 * alpha so it reads as "a list entry, not a button bar".
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
            .padding(vertical = 4.dp)
            .border(
                width = 1.dp,
                color = PaperBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                color = PaperCard.copy(alpha = 0.50f),
                shape = RoundedCornerShape(8.dp),
            )
            .pointerInput(tel) {
                detectTapGestures(onLongPress = { onCall(tel) })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = Ink.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tel,
                style = TextStyle(
                    fontFamily = JournalSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = Ink.copy(alpha = 0.70f),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = hours,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontWeight = FontWeight.Light,
                    fontSize = 12.sp,
                ),
                color = Ink.copy(alpha = 0.45f),
            )
        }
    }
}
