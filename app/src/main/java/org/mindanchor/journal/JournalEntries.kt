package org.mindanchor.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * The Entries destination: saved originals, most recent first (matches
 * [JournalRepository.entries]'s own `ORDER BY createdAt DESC`), with a
 * search field that filters by body OR title, case-insensitively, and a
 * tap-to-expand detail view. Program 0 deliberately has no calendar view,
 * media, sharing, or AI summaries here.
 */
@Composable
fun JournalEntries(
    entries: List<JournalEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    val filtered = if (searchQuery.isBlank()) {
        entries
    } else {
        entries.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("journal_search_field"),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { entry ->
                Card(
                    onClick = { selectedEntry = entry },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_entry_card"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = entry.title.ifBlank { entry.body.take(40) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("journal_entry_title"),
                        )
                        Text(
                            text = entry.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                        )
                    }
                }
            }
        }
    }

    val entryToShow = selectedEntry
    if (entryToShow != null) {
        Dialog(onDismissRequest = { selectedEntry = null }) {
            Card(modifier = Modifier.testTag("journal_entry_detail")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = entryToShow.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(text = entryToShow.body, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
