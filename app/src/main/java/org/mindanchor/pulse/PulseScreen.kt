package org.mindanchor.pulse

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * WHO-5 pulse: five items, each 0–5, standard 0–100 score. Honest framing:
 * a self-check over the last two weeks, not a diagnosis. Low scores show a
 * gentle suggestion to talk to someone — never an alarm.
 */
@Composable
fun PulseScreen(
    onClose: () -> Unit,
    viewModel: PulseViewModel = viewModel(),
) {
    val questions = stringArrayResource(R.array.who5_items)
    var answers by remember { mutableStateOf(List(WhoFive.ITEM_COUNT) { -1 }) }
    var savedScore by remember { mutableStateOf<Int?>(null) }
    val history by viewModel.history.collectAsState()

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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (0..WhoFive.MAX_ANSWER).forEach { value ->
                            val chosen = answers[index] == value
                            TextButton(
                                onClick = {
                                    answers = answers.toMutableList().also { it[index] = value }
                                },
                            ) {
                                Text(
                                    text = value.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (chosen) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(
                    text = stringResource(R.string.pulse_result, savedScore ?: 0),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                if ((savedScore ?: 100) <= 50) {
                    Text(
                        text = stringResource(R.string.pulse_low_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val context = LocalContext.current
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
                history.forEach { result ->
                    val date = Instant.ofEpochMilli(result.takenAt)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        text = "$date — ${result.score}/100",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
