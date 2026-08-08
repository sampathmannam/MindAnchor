@file:wording-reviewed

package org.mindanchor.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.mindanchor.R

/**
 * The v0.20.1 check-in screen. One question, five
 * large answers, then a free-text reflection. The
 * back button is the *only* reject path.
 *
 * ## Why one rating, not two scales
 *
 * The Mood EMA uses valence + arousal (two scales).
 * The check-in uses a *single* 1-5 global rating.
 * Hays 2009 (PROMIS Global Health) and Robins 2001
 * (single-item self-esteem) both support a single-
 * item global rating as a low-friction signal.
 * Two scales double the time-to-answer; a
 * single-item rating is a "N-of-1 within-person
 * signal" the user can chart over time without
 * interpretation.
 *
 * ## Why the rating is the active ingredient
 *
 * The 1-5 rating is the *response* — the user
 * reports where they are right now. The launcher
 * does not interpret it; it captures the response
 * and stores it. There is no cut-off, no screen-
 * positive interpretation, no threshold for
 * "concerning." The project rule is "no mood
 * inference."
 *
 * ## Why the reflection is optional
 *
 * Brief §B5: the reflection is "optional 1-3
 * sentence free-text." It is not required to save.
 * The user can submit a rating-only check-in by
 * pressing the "Save" button with the reflection
 * field empty.
 *
 * ## Why there is no "Not now" button
 *
 * Brief §B3: "no differing of check in, just a
 * simple back button to reject." The system back
 * button is the entire reject affordance. The
 * launcher does not draw a "Not now" / "Skip" /
 * "Maybe later" button; the user does not need to
 * choose between "engaging" and "snoozing" — they
 * either engage now or back out.
 *
 * @wording-reviewed — the question text, the rating
 * anchors, and the reflection placeholder are
 * launcher-authored wording and clinical-review-
 * gated. See docs/CLINICAL_REVIEW.md.
 */
@Composable
fun CheckInScreen(
    /**
     * Called exactly once, with the rating (1-5)
     * and the (possibly empty) reflection. The
     * activity persists the check-in and dismisses
     * the screen.
     */
    onSave: (rating: Int, reflection: String) -> Unit,
    /**
     * v0.20.1 round 5 follow-up: while the activity
     * is in the middle of saving (after the user
     * tapped Save, before the activity finishes),
     * the Save button is disabled. Without this
     * flag, a fast double-tap can fire onSave
     * twice and create two check-ins for one
     * prompt. The flag is a UI hint; the activity
     * also guards with a `saved` boolean (defense
     * in depth).
     */
    saving: Boolean = false,
) {
    var rating by remember { mutableStateOf<Int?>(null) }
    var reflection by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.check_in_question),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (CheckIn.MIN_RATING..CheckIn.MAX_RATING).forEach { value ->
                        OutlinedButton(
                            onClick = { rating = value },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp)
                                .semantics {
                                    // v0.20.1 round 5
                                    // follow-up: a screen
                                    // reader otherwise
                                    // just reads "1"
                                    // "2" "3" with no
                                    // context. The
                                    // contentDescription
                                    // anchors the number
                                    // to the user-
                                    // language labels
                                    // (rough / ok /
                                    // bright) so the
                                    // button is
                                    // meaningful
                                    // without sight.
                                    contentDescription =
                                        "Rating $value of 5"
                                },
                        ) {
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.check_in_rating_low),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.check_in_rating_high),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.padding(top = 32.dp))

                OutlinedTextField(
                    value = reflection,
                    onValueChange = {
                        if (it.length <= CheckIn.MAX_REFLECTION) {
                            reflection = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text(stringResource(R.string.check_in_reflection_label)) },
                    placeholder = { Text(stringResource(R.string.check_in_reflection_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    supportingText = {
                        Text(
                            text = "${reflection.length} / ${CheckIn.MAX_REFLECTION}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )

                Spacer(modifier = Modifier.padding(top = 24.dp))

                Button(
                    onClick = { onSave(rating!!, reflection) },
                    enabled = rating != null && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.check_in_save),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
