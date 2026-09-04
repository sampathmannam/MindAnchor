package org.mindanchor.journal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The top-level Journal surface: Today / Entries / Patterns behind a tab
 * row, hosted by [JournalActivity]. Dispatches to the three destination
 * composables based on [JournalViewModel.destination]; state itself lives
 * in the view-model, not here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    val entries by viewModel.entries.collectAsState(initial = emptyList())
    val todayMeasure by viewModel.morningMeasureForToday.collectAsState(initial = null)
    val measureHistory by viewModel.morningMeasureHistory.collectAsState(initial = emptyList())
    val researchLog by viewModel.researchLogForToday.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = viewModel.destination.ordinal) {
                Tab(
                    selected = viewModel.destination == JournalDestination.TODAY,
                    onClick = { viewModel.selectDestination(JournalDestination.TODAY) },
                    text = { Text("Today") },
                    modifier = Modifier.testTag("journal_tab_today"),
                )
                Tab(
                    selected = viewModel.destination == JournalDestination.ENTRIES,
                    onClick = { viewModel.selectDestination(JournalDestination.ENTRIES) },
                    text = { Text("Entries") },
                    modifier = Modifier.testTag("journal_tab_entries"),
                )
                Tab(
                    selected = viewModel.destination == JournalDestination.PATTERNS,
                    onClick = { viewModel.selectDestination(JournalDestination.PATTERNS) },
                    text = { Text("Patterns") },
                    modifier = Modifier.testTag("journal_tab_patterns"),
                )
            }
            when (viewModel.destination) {
                JournalDestination.TODAY -> JournalToday(
                    today = viewModel.todayDate,
                    title = viewModel.title,
                    body = viewModel.body,
                    onTitleChange = viewModel::onTitleChange,
                    onBodyChange = viewModel::onBodyChange,
                    onSave = viewModel::save,
                    savedConfirmation = viewModel.savedConfirmation,
                    saveError = viewModel.saveError,
                    morningMeasure = todayMeasure,
                    onSaveMorningMeasure = viewModel::saveMorningMeasure,
                    researchLog = researchLog,
                    onRecordResearchEvent = viewModel::recordResearchEvent,
                    researchLogError = viewModel.researchLogError,
                )
                JournalDestination.ENTRIES -> JournalEntries(
                    entries = entries,
                    searchQuery = viewModel.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                )
                JournalDestination.PATTERNS -> JournalPatterns(
                    entries = entries,
                    morningMeasureHistory = measureHistory,
                )
            }
        }
    }
}
