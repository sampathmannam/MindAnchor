/*
 * v0.66.0 (DBT-grounded journal) — Task 12.
 *
 * Stub for the dedicated DBT skills-library screen. The
 * v0.66.0 plan wires the navigation route (`JournalRoute.Skills`)
 * and the in-app plumbing (callback signatures, crisis-line
 * `onCall`, back-stack pop) in this task, but the actual skills
 * library UI is a follow-up. The five `SkillId` entries
 * (TIPP, DEAR_MAN, STOP, BREATHING_SPACE, WISE_MIND) are
 * already enumerated in `SkillsLibrary.kt`; the picker UI
 * that lets a user browse them, see "when to use", and start
 * one lands in a follow-up task.
 *
 * For v0.66.0 this stub renders the title + "coming soon"
 * copy + a back affordance so the route is reachable from
 * `JournalRoot` and the user can leave without using the
 * system back button. The footer is deliberately absent —
 * this is a deep-destination screen, not a full navigation
 * surface.
 *
 * The `onCall: (String) -> Unit` parameter is plumbed in
 * for the follow-up — a future skill-composable may want
 * to dial a crisis line, and threading the parameter now
 * means the call site in `JournalRoot` does not change.
 *
 * Public (no `internal`) to match the other journal
 * top-level composables (`JournalCrisis`, `JournalToday`).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Skills library stub. v0.66.0 wires the route so a
 * forward nav from the Crisis surface (and from a future
 * picker) reaches this screen; the actual skills library
 * UI lands in v0.66.x.
 *
 * The stub renders a small back arrow at the top (calling
 * [onBack]) and a one-line placeholder body. The system
 * back button is also handled by `BackHandler` in
 * `JournalRoot`, so either path returns the user to the
 * previous screen.
 */
@Composable
fun JournalSkills(
    onBack: () -> Unit,
    onCall: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // Top-left back affordance. The same 40dp tappable
        // Box pattern JournalSettings.kt uses for its back
        // arrow. The system back button also works (via
        // BackHandler in JournalRoot), so the user has two
        // paths out of the screen.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            ChevronLeftGlyph(color = Ink.copy(alpha = 0.30f))
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "SKILLS LIBRARY",
            style = JournalSmallCaps,
            color = Terracotta,
        )
        Text(
            text = "Coming in v0.66.1",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 16.sp,
            ),
            color = Ink.copy(alpha = 0.55f),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        // `onCall` is plumbed in for the follow-up task
        // that builds the real picker. The reference
        // expression below silences the unused-parameter
        // warning without changing the call site in
        // JournalRoot.
        @Suppress("UNUSED_EXPRESSION")
        onCall
    }
}
