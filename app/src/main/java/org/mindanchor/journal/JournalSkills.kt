/*
 * v0.66.1: the Skills library — real picker UI.
 *
 * v0.66.0 had a stub (see prior history of this file).
 * v0.66.1 ships the real picker: 5 cards, one per
 * SkillId in SkillsLibrary, each rendering the
 * title / when-to-use / how-to-do-it verbatim from the
 * library metadata, with a Done button per skill that
 * routes back through the parent (JournalRoot) so the
 * 60-second diary-expander enablement window in Today
 * (URGE_LOG_WINDOW_MS) gets armed.
 *
 * v0.66.0 BPD-safety invariants preserved:
 *   - No streak, no leaderboard, no public metric.
 *   - "How to do it" copy is descriptive ("Cold water
 *     on your face, hands, or back of neck") not
 *     imperative ("You should do cold water..."). The
 *     Wise Mind test pin (no "you should") is in
 *     SkillsLibraryTest.
 *   - Crisis resources are surfaced via the existing
 *     `onCall` callback. ACTION_DIAL is the only place
 *     the user can end up dialling a crisis line from
 *     this screen — the call site is JournalRoot's
 *     `dial` closure.
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mindanchor.journal.skills.Skill
import org.mindanchor.journal.skills.SkillId
import org.mindanchor.journal.skills.SkillsLibrary

/**
 * The Skills library.
 *
 * The user can browse all 5 DBT/ACT/grounding skills, read
 * "when to use" + "how to do it" verbatim, and tap "Done"
 * on a skill to log it. The Done tap flows back to JournalRoot
 * which writes to SkillsPrefs.markUsed(skill, today), and
 * (if the user is on Today) arms the 60-second diary-expander
 * window in JournalToday. The system back button returns to
 * the previous screen.
 */
@Composable
fun JournalSkills(
    onBack: () -> Unit,
    onCall: (String) -> Unit = {},
    onSkillDone: (SkillId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                ChevronLeftGlyph(color = Ink.copy(alpha = 0.30f))
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "SKILLS LIBRARY",
                style = JournalSmallCaps,
                color = Terracotta,
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Five DBT, ACT, and grounding skills. " +
                "Pick one when the day asks for it.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 14.sp,
            ),
            color = Ink.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        Spacer(modifier = Modifier.size(16.dp))

        // 5 skill cards. SkillsLibrary is the single source of
        // truth for the order, the copy, and the metadata. The
        // UI shape (SkillCard) is inline; no new files for v0.66.1.
        SkillsLibrary.all.forEach { skill ->
            SkillCard(
                skill = skill,
                onDone = { onSkillDone(skill.id) },
            )
            Spacer(modifier = Modifier.size(12.dp))
        }

        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "MindAnchor is a personal R&D tool, not a substitute for therapy.",
            style = TextStyle(
                fontFamily = JournalSerif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Light,
                fontSize = 11.sp,
            ),
            color = Ink.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

/**
 * One skill card: title + "when to use" + "how to do it"
 * + Done. The how-to text preserves the verbatim protocol
 * language from SkillsLibrary (no rewriting, no "you should").
 */
@Composable
private fun SkillCard(
    skill: Skill,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.03f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = skill.title,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
            ),
            color = Ink.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "When to use",
            style = TextStyle(
                fontFamily = JournalSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = Terracotta.copy(alpha = 0.70f),
        )
        Text(
            text = skill.whenToUse,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = Ink.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "How to do it",
            style = TextStyle(
                fontFamily = JournalSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
            ),
            color = Terracotta.copy(alpha = 0.70f),
        )
        Text(
            text = skill.howToDoIt,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
            ),
            color = Ink.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .background(Terracotta.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                    .clickable(onClick = onDone)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Done",
                    style = TextStyle(
                        fontFamily = JournalSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = Terracotta,
                )
            }
        }
    }
}
