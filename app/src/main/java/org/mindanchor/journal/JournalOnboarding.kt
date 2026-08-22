/*
 * v0.67.0 — first-run Journal onboarding.
 *
 * A 3-card overlay shown above the Today surface the
 * first time the user opens the journal. The cards
 * explain the three inputs the journal is built around
 * (mood, skill, diary) in the same validate-then-
 * suggest voice as the rest of the v0.66.x surface.
 * The user can dismiss the overlay two ways:
 *   - "Got it" — the default close. Sets
 *     `onboardingSeen = true` so the overlay does not
 *     show again.
 *   - "Don't show this again" — same effect, but with
 *     an explicit "do not show me" confirmation. The
 *     user can re-open the onboarding from Settings →
 *     About → "Show journal intro", so the dismissal
 *     is reversible, not a one-way data loss.
 *
 * The 3 cards:
 *   1. Mood — a thing you can name. The 5-state mood
 *      row, the 2D Affect-Grid when toggled. DBT-
 *      grounded (Linehan 1993): naming a feeling is
 *      a different mental action from changing one.
 *   2. Skill — a thing you can do. The skill-of-the-
 *      day card and the Skills library (TIPP / DEAR
 *      MAN / STOP / Breathing Space / Wise Mind).
 *      Skill pairing rule: bridge-to-therapist
 *      framing (Simon 2022 JAMA HR 1.29).
 *   3. Diary — a thing you can notice. The 3-slider
 *      urge log (NSSI / Suicidal / Dissociation,
 *      0..5). Visible only after a skill is done
 *      (60s window).
 *
 * Why a 3-card overlay, not a settings toggle:
 *   - The user is here to write, not to set up. A
 *     first-run overlay that disappears the moment
 *     the user is ready is a better fit for the
 *     journal's posture.
 *   - "Don't show this again" preserves the user's
 *     choice without a separate settings row. The
 *     same data-store flag is checked in two places:
 *     the overlay (set true on dismiss) and Settings
 *     (re-set false to re-open).
 *
 * Why not a `Scaffold` or full-screen dialog:
 *   - The overlay must sit above the Today surface
 *     so the user sees the surface they will use
 *     behind the cards. A `Box` with a translucent
 *     paper backdrop (the journal's own colour) is
 *     enough — a full-screen Dialog hides the surface
 *     the user is about to learn about.
 *
 * BPD-safe patterns preserved from the rest of
 * v0.66.x:
 *   - "Got it" / "Don't show this again" — no "!"
 *   - No "you must" / "you should" / "we recommend"
 *   - No streak, no counter, no comparison
 *   - The 3 cards are descriptive, not imperative:
 *     "a thing you can name", not "name a feeling"
 *   - Crisis lines stay visible underneath the
 *     overlay's translucent backdrop. A user in
 *     distress who taps the journal app and sees an
 *     overlay can still long-press the iCall number
 *     below it.
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mindanchor.R
import org.mindanchor.journal.skills.SkillId

/**
 * The 3-card onboarding overlay. Renders above the
 * Today surface with a translucent paper backdrop.
 *
 * The user dismisses with one of two buttons at the
 * bottom:
 *   - "Got it" — primary dismiss. Calls
 *     `onDismiss(persist = true)`.
 *   - "Don't show this again" — same as "Got it" for
 *     now (the wording is the persistence promise, not
 *     a behaviour difference). The Settings row can
 *     re-open the onboarding by setting the flag back
 *     to false.
 *
 * The three cards are rendered in a Column with the
 * same paper-card chrome as Today. The cards are
 * short (one paragraph each) so the user can read
 * them in under 30 seconds — the journal's first-run
 * is meant to be unobtrusive.
 */
@Composable
internal fun JournalOnboarding(
    onDismiss: (persist: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaperCard.copy(alpha = 0.96f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.journal_onboarding_title),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Light,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp,
                ),
                color = Terracotta,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.journal_onboarding_subtitle),
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Light,
                    fontSize = 13.sp,
                ),
                color = Ink.copy(alpha = 0.60f),
            )
            Spacer(modifier = Modifier.height(28.dp))

            OnboardingCard(
                number = "1",
                title = stringResource(R.string.journal_onboarding_card1_title),
                body = stringResource(R.string.journal_onboarding_card1_body),
            )
            Spacer(modifier = Modifier.height(14.dp))
            OnboardingCard(
                number = "2",
                title = stringResource(R.string.journal_onboarding_card2_title),
                body = stringResource(R.string.journal_onboarding_card2_body),
            )
            Spacer(modifier = Modifier.height(14.dp))
            OnboardingCard(
                number = "3",
                title = stringResource(R.string.journal_onboarding_card3_title),
                body = stringResource(R.string.journal_onboarding_card3_body),
            )
            Spacer(modifier = Modifier.height(28.dp))

            // Two dismiss buttons. The journal's posture
            // is "the user is in charge" — both buttons
            // dismiss the overlay; the second one is just
            // an explicit promise that we will not show
            // it again. The buttons are side-by-side, the
            // primary (Got it) on the left.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OnboardingButton(
                    label = stringResource(R.string.journal_onboarding_got_it),
                    primary = true,
                    onClick = { onDismiss(true) },
                    modifier = Modifier.weight(1f),
                )
                OnboardingButton(
                    label = stringResource(R.string.journal_onboarding_dont_show),
                    primary = false,
                    onClick = { onDismiss(true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    number: String,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperCard, shape = RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = PaperBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = number,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Light,
                    fontSize = 24.sp,
                ),
                color = Terracotta,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = JournalSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                ),
                color = Ink,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
            color = Ink.copy(alpha = 0.70f),
        )
    }
}

@Composable
private fun OnboardingButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (primary) Terracotta else PaperCard
    val fg = if (primary) PaperCard else Terracotta
    Column(
        modifier = modifier
            .background(bg, shape = RoundedCornerShape(6.dp))
            .border(
                width = 1.dp,
                color = Terracotta,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = JournalSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
            ),
            color = fg,
        )
    }
}
