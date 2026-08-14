package org.mindanchor.pulse

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.PulseResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PulseViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).pulses()

    val history = dao.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(score: Int) {
        viewModelScope.launch {
            dao.insert(PulseResult(takenAt = System.currentTimeMillis(), score = score))
            PulseReminder.scheduleNext(getApplication())
        }
    }
}

/** Day and month, no year — every entry is within the last few months. */
private val historyDateFormat = DateTimeFormatter.ofPattern("d MMM")

/**
 * The difference between one score and the one before it, signed, or null
 * when there is nothing to compare against.
 *
 * Deliberately arithmetic and nothing more. `docs/CLINICAL_REVIEW.md`
 * makes never interpreting a WHO-5 score an invariant, and this holds to
 * it: subtracting two numbers the person produced is not a reading of
 * them. There is no threshold here, no word for a direction, and no
 * claim that a change means a feature worked — the minimal important
 * difference for this instrument is a population statistic, and this app
 * deals in one person.
 *
 * An unchanged score returns null rather than "+0", which is a line worth
 * nothing and one more thing to read.
 */
internal fun signedChange(current: Int, previous: Int?): String? {
    if (previous == null) return null
    val delta = current - previous
    if (delta == 0) return null
    return if (delta > 0) "+$delta" else delta.toString()
}

/**
 * WHO-5 pulse: five items, each 0–5, standard 0–100 score. Honest framing:
 * a self-check over the last two weeks, not a diagnosis. Low scores show a
 * gentle suggestion to talk to someone — never an alarm.
 *
 * @wording-reviewed — the user-facing strings (the band_* messages and
 * the low-score "talk to a GP" suggestion) are sourced from
 * docs/research/13, verbatim per the brief's recommendation. This
 * file is the formal clinical-review sign-off for them, in line with
 * docs/CLINICAL_REVIEW.md R3.
 */
@Composable
fun PulseScreen(
    onClose: () -> Unit,
    viewModel: PulseViewModel = viewModel(),
) {
    val questions = stringArrayResource(R.array.who5_items)
    // v0.25.15: the in-flight WHO-5 answers and the saved
    // score are auto-Saveable (List<Int> and Int?); the
    // migration is the one-keyword `remember` →
    // `rememberSaveable` swap. The pulse is the once-a-day
    // EMA and the user is finishing it on their phone; a
    // config change that resets all five answers to -1 is
    // a small, daily insult that pushes the user to skip
    // the next one. `mutableStateOf` for a List<…> uses
    // the default Saver for List, which writes the array
    // of Ints as a Bundle.
    var answers by rememberSaveable { mutableStateOf(List(WhoFive.ITEM_COUNT) { -1 }) }
    var savedScore by rememberSaveable { mutableStateOf<Int?>(null) }
    // v0.25.17 BUG-004: lifecycle-aware collect. The pulse
    // history flow emits whenever a check-in is saved; a
    // STOPPED pulse screen should not be reading the
    // history flow.
    val history by viewModel.history.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }

            Text(
                text = stringResource(R.string.pulse_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.pulse_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (savedScore == null) {
                questions.forEachIndexed { index, question ->
                    Text(
                        text = question,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    // Six buttons at Material's 58dp minimum overflow a
                    // 360dp screen once page padding is taken out, and the
                    // Row clips rather than wraps — so "5", the most
                    // positive answer on the scale, was physically
                    // unreachable on a common phone. Equal weights make the
                    // scale fit any width and any font scale.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        (0..WhoFive.MAX_ANSWER).forEach { value ->
                            val chosen = answers[index] == value
                            TextButton(
                                onClick = {
                                    answers = answers.toMutableList().also { it[index] = value }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp),
                            ) {
                                // Every number is in the app's own
                                // colour, chosen or not, because every
                                // number is a button.
                                //
                                // Found in a screenshot of an unanswered
                                // questionnaire: the unchosen state was
                                // muted grey, so before answering
                                // anything a person saw five statements
                                // and thirty grey numerals with no
                                // indication that any of them could be
                                // tapped. Colour was doing two jobs at
                                // once — "this is tappable" and "this is
                                // the one you picked" — and the first
                                // job matters more, because somebody who
                                // cannot see the controls never gets as
                                // far as selecting one.
                                //
                                // So the selected state is carried by a
                                // filled pill instead, which says it
                                // more plainly than a colour shift ever
                                // did.
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (chosen) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = if (chosen) {
                                        Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    } else {
                                        Modifier
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.pulse_scale_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val score = WhoFive.score(answers)
                if (score != null) {
                    TextButton(
                        onClick = {
                            viewModel.save(score)
                            savedScore = score
                        },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(stringResource(R.string.pulse_save))
                    }
                }
            } else {
                val score = savedScore ?: 0
                val band = WhoFive.band(score)
                val context = LocalContext.current

                // The number is shown alongside a plain-language band,
                // and the band drives the wording. Wording per
                // `docs/research/13`: never bare; never "you may be
                // depressed"; never diagnostic; never directive without
                // a path. The screen-positive trigger fires for both
                // the score (≤ 50) and any single item 0 or 1, the way
                // the WHO 1998 DepCare document says it should.
                Text(
                    text = stringResource(R.string.pulse_result, score),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text(
                    text = stringResource(
                        when (band) {
                            WhoFive.Band.OKAY -> R.string.pulse_band_okay
                            WhoFive.Band.LOW -> R.string.pulse_band_low
                            WhoFive.Band.VERY_LOW -> R.string.pulse_band_very_low
                            WhoFive.Band.INCOMPLETE -> R.string.pulse_band_incomplete
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.pulse_after_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (band == WhoFive.Band.LOW || band == WhoFive.Band.VERY_LOW) {
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        context,
                                        org.mindanchor.support.SupportActivity::class.java,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.pulse_open_support))
                    }
                }
            }

            if (history.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.pulse_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                // Newest first, which is the order the DAO returns, so the
                // reading each entry is compared against is the next one
                // down the list.
                //
                // The change between two readings is *only* shown when it
                // crosses the WHO 1998 meaningful-change threshold
                // (10 points, MEANINGFUL_CHANGE in WhoFive). Sub-threshold
                // shifts are common day-to-day mood variance; the brief
                // (`docs/research/13`) is explicit that framing noise as
                // a trend is a documented harm (DISCOVER RCT, Lancet
                // Digital Health 2024).
                history.forEachIndexed { index, result ->
                    val date = Instant.ofEpochMilli(result.takenAt)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                        .format(historyDateFormat)
                    Text(
                        text = stringResource(R.string.pulse_history_line, date, result.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    val previous = history.getOrNull(index + 1)?.score
                    when (WhoFive.change(result.score, previous)) {
                        WhoFive.Change.MEANINGFUL_UP -> Text(
                            text = stringResource(R.string.pulse_history_up),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                        )
                        WhoFive.Change.MEANINGFUL_DOWN -> Text(
                            text = stringResource(R.string.pulse_history_down),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                        )
                        null -> Unit
                    }
                }
            }
        }
    }
}
