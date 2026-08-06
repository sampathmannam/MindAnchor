package org.mindanchor.digest

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.mindanchor.data.db.HeldNotification
import org.mindanchor.notifications.BatchReleaser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DigestViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).heldNotifications()

    val journal = dao.journal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun releaseNow() {
        viewModelScope.launch { BatchReleaser.releaseNow(getApplication()) }
    }

    fun clearReleased() {
        viewModelScope.launch { dao.clearReleased() }
    }

    fun openApp(packageName: String) {
        val app = getApplication<Application>()
        val intent = app.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }
}

/**
 * The journal: everything that was held, waiting or already released.
 * Plain chronology, no red, no urgency.
 */
@Composable
fun DigestScreen(
    onClose: () -> Unit,
    viewModel: DigestViewModel = viewModel(),
) {
    val journal by viewModel.journal.collectAsState()
    val waiting = journal.filter { it.releasedAt == null }
    val released = journal.filter { it.releasedAt != null }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
                Text(
                    text = stringResource(R.string.digest_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }

            if (waiting.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.digest_waiting_count, waiting.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::releaseNow) {
                        Text(stringResource(R.string.digest_release_now))
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(waiting, key = { it.id }) { item ->
                    JournalRow(item, waiting = true) { viewModel.openApp(item.packageName) }
                }
                items(released, key = { it.id }) { item ->
                    JournalRow(item, waiting = false) { viewModel.openApp(item.packageName) }
                }
            }

            if (released.isNotEmpty()) {
                TextButton(onClick = viewModel::clearReleased) {
                    Text(stringResource(R.string.digest_clear_released))
                }
            }
            if (journal.isEmpty()) {
                Text(
                    text = stringResource(R.string.digest_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun JournalRow(item: HeldNotification, waiting: Boolean, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(item.postedAt)
        .atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "$time · ${item.appLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = if (waiting) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (item.title.isNotBlank()) {
            Text(text = item.title, style = MaterialTheme.typography.titleSmall)
        }
        if (item.text.isNotBlank()) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
