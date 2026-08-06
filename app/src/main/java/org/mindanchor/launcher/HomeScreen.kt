package org.mindanchor.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.mindanchor.R
import org.mindanchor.settings.SettingsScreen
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class LauncherSurface { Home, Drawer, Settings }

/**
 * Root of the launcher UI. Three surfaces: the calm home (clock, greeting,
 * favorites), the search-first app drawer, and settings. No grid, no icons,
 * no badges — text only (CONCEPT.md §3.2).
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var surface by remember { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }

    BackHandler(enabled = surface != LauncherSurface.Home) {
        surface = LauncherSurface.Home
        viewModel.onQueryChange("")
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (surface) {
            LauncherSurface.Home -> HomeSurface(
                favorites = state.favorites,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                onLaunch = viewModel::launch,
                onLongPress = { actionsFor = it },
            )

            LauncherSurface.Drawer -> DrawerSurface(
                viewModel = viewModel,
                state = state,
                onLaunch = {
                    viewModel.launch(it)
                    surface = LauncherSurface.Home
                },
                onLongPress = { actionsFor = it },
            )

            LauncherSurface.Settings -> SettingsScreen(
                hiddenApps = state.allApps.filter { it.isHidden },
                onUnhide = { viewModel.setHidden(it, false) },
                onBack = { surface = LauncherSurface.Home },
            )
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            onDismiss = { actionsFor = null },
            onToggleFavorite = { viewModel.toggleFavorite(app); actionsFor = null },
            onToggleHidden = { viewModel.setHidden(app, !app.isHidden); actionsFor = null },
            onRename = { label -> viewModel.rename(app, label); actionsFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSurface(
    favorites: List<DisplayApp>,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = greetingFor(
                    now.hour,
                    stringResource(R.string.greeting_morning),
                    stringResource(R.string.greeting_day),
                    stringResource(R.string.greeting_evening),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                favorites.forEach { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                            )
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }

        TextButton(
            onClick = onOpenDrawer,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text(
                text = stringResource(R.string.open_drawer),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSurface(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
) {
    val query by viewModel.searchQuery.collectAsState()
    val results = viewModel.searchResults(state)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { results.firstOrNull()?.let(onLaunch) },
            ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(results, key = { it.component }) { app ->
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                        )
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}

internal fun greetingFor(hour: Int, morning: String, day: String, evening: String): String =
    when (hour) {
        in 5..11 -> morning
        in 12..17 -> day
        else -> evening
    }
