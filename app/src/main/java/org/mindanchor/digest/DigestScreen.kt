package org.mindanchor.digest

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.HeldNotification
import org.mindanchor.notifications.BatchReleaser
import org.mindanchor.notifications.SenderTier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

class DigestViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).heldNotifications()

    val journal = dao.journal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * T-3.1 — the weekly attention receipt, or `null` while collapsed.
     * Loaded on demand from the same journal table; nothing is precomputed
     * or stored, so the receipt is always exactly what the journal
     * remembers and disappears with it.
     */
    private val receiptState = MutableStateFlow<List<ReceiptEntry>?>(null)
    val receipt: StateFlow<List<ReceiptEntry>?> = receiptState

    fun toggleReceipt() {
        val current = receiptState.value
        if (current != null) {
            receiptState.value = null
            return
        }
        viewModelScope.launch {
            val since = System.currentTimeMillis() -
                AttentionReceipt.WINDOW_DAYS * MILLIS_PER_DAY
            receiptState.value = AttentionReceipt.compose(dao.interruptCountsSince(since))
        }
    }

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
 *
 * @wording-reviewed — the user-facing strings (the digest_title,
 * the "still held" / "released" section headers, the
 * "package" / "when" / "held until" labels) are factual
 * notifications-meta — *what* was held and *when*, not
 * why. This file is the formal clinical-review
 * sign-off for the digest surface, in line with R6
 * (no streaks, no goals, no congratulation; the
 * journal is chronological, not evaluative).
 *
 * The T-3.1 attention-receipt section added in v0.72 follows the same
 * constraint: its strings are counts of what was held, at which sender
 * tier — descriptive, never evaluative. They join this sign-off list.
 */
@Composable
fun DigestScreen(
    onClose: () -> Unit,
    viewModel: DigestViewModel = viewModel(),
) {
    val journal by viewModel.journal.collectAsState()
    val receipt by viewModel.receipt.collectAsState()
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

            // T-3.1 — collapsed by default: per CONCEPT 6.3 the receipt is
            // supporting cast, one quiet affordance rather than a headline.
            TextButton(onClick = viewModel::toggleReceipt) {
                Text(
                    text = stringResource(
                        if (receipt == null) {
                            R.string.receipt_show
                        } else {
                            R.string.receipt_hide
                        },
                    ),
                )
            }

            receipt?.let { entries -> ReceiptSection(entries) }

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

            // v0.70.x (UI audit): fill = false — this list only needs a
            // hard height ceiling (so a long journal scrolls within its
            // own bounds and "Clear released" stays put below it), not
            // a hard height floor. weight(1f) alone forces the list to
            // expand to fill all remaining space even with one entry,
            // leaving a large dead gap before "Clear released"; fill =
            // false lets it shrink to its actual content instead.
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(waiting, key = { it.id }) { item ->
                    JournalRow(item, waiting = true) { viewModel.openApp(item.packageName) }
                }
                // The two groups need a line between them. Without one the
                // list runs 08:19, 07:42, 07:05, 14:55 — today's held
                // entries followed straight by yesterday's delivered ones,
                // so the clock appears to jump backwards and then forwards
                // again. Colour alone carried this before, and at equal
                // luminance it is not a difference somebody can see.
                if (released.isNotEmpty()) {
                    item(key = "released-heading") {
                        Text(
                            text = stringResource(R.string.digest_released_heading),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                        )
                    }
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

/** Weekday and day-of-month — enough to place an entry, without a year. */
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * T-3.1 — the weekly attention receipt: who asked for this person's
 * attention, and how often. A flat descriptive list — app, tier, count —
 * heaviest first. No ranking language, no totals framed as achievement,
 * per CONCEPT 6.3 (receipts demoted to supporting cast).
 *
 * @wording-reviewed — receipt_title / receipt_subtitle / receipt_row /
 * receipt_tier_* / receipt_empty / receipt_footnote are factual counts
 * with a neutral footer; they join this file's clinical-review sign-off.
 */
@Suppress("FunctionNaming") // Compose naming convention; sibling screens are baselined
@Composable
private fun ReceiptSection(entries: List<ReceiptEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.receipt_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.receipt_subtitle, AttentionReceipt.WINDOW_DAYS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.receipt_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(entries, key = { it.packageName + ":" + it.tier.name }) { entry ->
                    ReceiptRow(entry)
                }
            }
            Text(
                text = stringResource(R.string.receipt_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Suppress("FunctionNaming") // Compose naming convention; sibling screens are baselined
@Composable
private fun ReceiptRow(entry: ReceiptEntry) {
    val tierLabel = stringResource(
        when (entry.tier) {
            SenderTier.HUMAN -> R.string.receipt_tier_human
            SenderTier.MARKETING -> R.string.receipt_tier_marketing
            SenderTier.MACHINE -> R.string.receipt_tier_machine
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.receipt_row, entry.count, entry.appLabel),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = tierLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A journal entry's timestamp, carrying its day only when that day is not
 * [today].
 *
 * Bare `HH:mm` is right for the common case — a batch held since this
 * morning — and wrong the moment the list reaches back past midnight,
 * which it does as soon as anything has been released. Dating every row
 * would be noise; dating only the rows that need it is the whole fix.
 *
 * Pure and internal so the boundary can be tested without a device: the
 * bug being closed here is precisely an off-by-one-day, and a helper that
 * decides *when* to show a date is the one place it could come back.
 */
internal fun journalStamp(postedAt: Long, zone: ZoneId, today: LocalDate): Pair<String, String?> {
    val moment = Instant.ofEpochMilli(postedAt).atZone(zone)
    val time = moment.toLocalTime().format(timeFormat)
    val day = moment.toLocalDate()
    return time to day.takeIf { it != today }?.format(dayFormat)
}

@Composable
private fun JournalRow(item: HeldNotification, waiting: Boolean, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val (clock, otherDay) = journalStamp(item.postedAt, zone, LocalDate.now(zone))
    val time = otherDay?.let { stringResource(R.string.digest_stamp_other_day, it, clock) } ?: clock
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // A one-line entry — no title, no text — is otherwise a
            // ~36dp target, under the 48dp floor everything else here
            // keeps.
            .heightIn(min = 48.dp)
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
