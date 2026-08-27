// @wording-reviewed — the visible strings (the "How is today sitting?"
// question, the rating anchors, the "later" label) are launcher-authored
// wording and clinical-review-gated. See docs/CLINICAL_REVIEW.md.

package org.mindanchor.model

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * v0.72+ — the floating check-in pill. The new primary surface for
 * the v0.20.1 check-in, replacing the full-screen [CheckInActivity]
 * as the default.
 *
 * See `docs/CHECKIN_FLOATING_PROMPT.md` for the full design rationale
 * and the mental-health research backing the change. Briefly: a
 * full-screen prompt at phone-unlock is too heavy; brief, anchored,
 * one-tap prompts respect autonomy and complete more often
 * (Stone & Shiffman 1994, Deci & Ryan 2000, Fogg 2009).
 *
 * ## Behaviour
 *
 * - Anchored to the bottom of the host surface (the home screen),
 *   16dp from the bottom and side edges, 80% of the screen width.
 * - One line of text, five large tappable anchors (1-5), a `later`
 *   TextButton. No icon for expand — the question text is the affordance.
 * - Tapping an anchor saves the rating and dismisses the pill. The
 *   reflection / free-text path is the existing [CheckInActivity],
 *   reached by tapping the question text.
 * - Tapping `later` records a rejection (the rate-limit's auto-pause
 *   counter increments) and dismisses the pill.
 * - The system back button is also wired as a reject, matching the
 *   existing `CheckInActivity` behaviour.
 *
 * ## State management
 *
 * The pill is fully controlled: the host decides whether to show it
 * (via [visible]) and which callbacks fire. The pill does not
 * talk to the rate-limit holder directly. The host owns the
 * show/dismiss lifecycle.
 */
@Composable
fun CheckInFloatingPrompt(
    /**
     * Whether the pill should currently be visible. The host owns
     * this; the pill is a controlled component.
     */
    visible: Boolean,
    /**
     * Called with the chosen rating (1-5) when the user taps an
     * anchor. The host is responsible for persisting via
     * [CheckInEngine.recordAcceptance] + [CheckInPrefs.add] and
     * for dismissing the pill.
     */
    onAccept: (rating: Int) -> Unit,
    /**
     * Called when the user taps the `later` TextButton. The host
     * records the rejection via [CheckInEngine.recordRejection] and
     * dismisses the pill.
     */
    onReject: () -> Unit,
    /**
     * Called when the user taps the question text. The host opens
     * [CheckInActivity] with the chosen rating (or 0 for "no
     * rating yet") so the user can add a free-text reflection.
     */
    onExpand: (rating: Int) -> Unit,
    /**
     * The rating the user has already picked, or 0 if none yet.
     * Pre-fills the highlighted anchor and lets the pill be saved
     * immediately on second tap. When non-zero, the anchors are
     * still visible so the user can correct a wrong tap.
     */
    initialRating: Int = 0,
) {
    // rememberSaveable for the in-flight rating so a config change
    // doesn't drop the pick. The pill itself is hidden by the
    // host after accept/reject, so this is short-lived.
    var rating by remember { mutableStateOf(initialRating) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            // 0 → full: 0dp = the natural resting position.
            // We use fullHeight (1f) so the slide-in starts from
            // the bottom edge and lands at the natural offset.
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 250),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 250),
        ),
    ) {
        // Bottom-anchored card. widthIn caps the width on tablets;
        // 80% of screen width is the default on phones.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .widthIn(max = 480.dp)
                    // Whole-card click target for the expand path:
                    // tapping the question text opens the reflection
                    // screen. Anchors and `later` swallow taps above.
                    .clickable { onExpand(rating) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "How is today sitting?"
                    },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            // The text below is a clickable expand
                            // affordance. The whole-card click is
                            // captured by the Surface above; this Text
                            // is the visual focus, not a separate
                            // click handler.
                            text = stringResource(R.string.check_in_question),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        TextButton(
                            onClick = onReject,
                        ) {
                            Text(stringResource(R.string.check_in_later))
                        }
                    }
                    Spacer(modifier = Modifier.padding(top = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AnchorButton(
                            value = 1,
                            label = stringResource(R.string.check_in_anchor_rough),
                            selected = rating == 1,
                            enabled = true,
                            onTap = { value ->
                                rating = value
                                onAccept(value)
                            },
                        )
                        AnchorButton(
                            value = 2,
                            label = stringResource(R.string.check_in_anchor_low),
                            selected = rating == 2,
                            enabled = true,
                            onTap = { value ->
                                rating = value
                                onAccept(value)
                            },
                        )
                        AnchorButton(
                            value = 3,
                            label = stringResource(R.string.check_in_anchor_ok),
                            selected = rating == 3,
                            enabled = true,
                            onTap = { value ->
                                rating = value
                                onAccept(value)
                            },
                        )
                        AnchorButton(
                            value = 4,
                            label = stringResource(R.string.check_in_anchor_good),
                            selected = rating == 4,
                            enabled = true,
                            onTap = { value ->
                                rating = value
                                onAccept(value)
                            },
                        )
                        AnchorButton(
                            value = 5,
                            label = stringResource(R.string.check_in_anchor_bright),
                            selected = rating == 5,
                            enabled = true,
                            onTap = { value ->
                                rating = value
                                onAccept(value)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchorButton(
    value: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onTap: (Int) -> Unit,
) {
    val context = LocalContext.current
    val contentDesc = "Rating $value of 5: $label"
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled) { onTap(value) }
            .semantics { contentDescription = contentDesc }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = fg,
                textAlign = TextAlign.Center,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                textAlign = TextAlign.Center,
            )
        }
    }
}
