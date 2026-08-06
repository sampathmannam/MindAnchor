package org.mindanchor.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.mindanchor.R
import org.mindanchor.digest.DigestActivity
import org.mindanchor.friction.FrictionGate
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.support.SupportActivity
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick
import java.time.format.DateTimeFormatter

private enum class LauncherSurface { Home, Drawer, Settings }

/**
 * Root of the launcher UI. Three surfaces: the calm home (clock, greeting,
 * favorites), the search-first app drawer, and settings. No grid, no icons,
 * no badges — text only (CONCEPT.md §3.2).
 */
@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel = viewModel(),
    /** Bumped whenever the home button is pressed; see HomeActivity. */
    goHomeSignal: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    var surface by remember { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by remember { mutableStateOf<DisplayApp?>(null) }
    var gateFor by remember { mutableStateOf<DisplayApp?>(null) }

    // Pressing home while deep in the drawer or settings must land on the
    // home surface — otherwise the launcher "sticks" wherever you left it.
    LaunchedEffect(goHomeSignal) {
        if (goHomeSignal > 0) {
            gateFor = null
            actionsFor = null
            surface = LauncherSurface.Home
            viewModel.onQueryChange("")
        }
    }

    BackHandler(enabled = surface != LauncherSurface.Home || gateFor != null) {
        gateFor = null
        surface = LauncherSurface.Home
        viewModel.onQueryChange("")
    }

    fun attemptLaunch(app: DisplayApp) {
        val packageName = app.component.substringBefore('/')
        if (packageName in state.frictionPackages) {
            gateFor = app
        } else {
            viewModel.launch(app)
            surface = LauncherSurface.Home
        }
    }

    gateFor?.let { app ->
        FrictionGate(
            appLabel = app.label,
            onOpen = { minutes ->
                viewModel.launchTimed(app, minutes)
                gateFor = null
                surface = LauncherSurface.Home
            },
            onNeverMind = {
                gateFor = null
                surface = LauncherSurface.Home
            },
        )
        return
    }

    when (surface) {
        LauncherSurface.Home -> CalmBackground { sky ->
            HomeSurface(
                sky = sky,
                favorites = state.favorites,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
            )
        }

        LauncherSurface.Drawer -> Surface(modifier = Modifier.fillMaxSize()) {
            DrawerSurface(
                viewModel = viewModel,
                state = state,
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
            )
        }

        LauncherSurface.Settings -> Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                allApps = state.allApps,
                hiddenApps = state.allApps.filter { it.isHidden },
                onUnhide = { viewModel.setHidden(it, false) },
                onBack = { surface = LauncherSurface.Home },
            )
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isFrictioned = app.component.substringBefore('/') in state.frictionPackages,
            onDismiss = { actionsFor = null },
            onToggleFavorite = { viewModel.toggleFavorite(app); actionsFor = null },
            onToggleHidden = { viewModel.setHidden(app, !app.isHidden); actionsFor = null },
            onToggleFriction = { viewModel.toggleFriction(app); actionsFor = null },
            onRename = { label -> viewModel.rename(app, label); actionsFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeSurface(
    sky: SkyContent,
    favorites: List<DisplayApp>,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
) {
    val now = rememberMinuteTick()
    val clockFormat = rememberClockFormat()

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(clockFormat)),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                ),
                color = sky.textPrimary,
            )
            Text(
                text = greetingFor(
                    now.hour,
                    stringResource(R.string.greeting_morning),
                    stringResource(R.string.greeting_day),
                    stringResource(R.string.greeting_evening),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )

            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                favorites.forEach { app ->
                    // The target is the full width of the row, not the width
                    // of the word. A favourite named "X" used to offer a
                    // sliver to hit; anyone with a tremor, large fingers or
                    // shaking hands was aiming at almost nothing. 48dp is
                    // the documented minimum and the floor here.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = sky.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
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
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        val context = LocalContext.current
        // Support is one tap from the home screen and never buried: during
        // acute distress or dissociation, three taps and a scroll is too far.
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(context, SupportActivity::class.java))
                }
            },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Text(
                text = stringResource(R.string.support_shortcut),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = {
                runCatching { context.startActivity(Intent(context, DigestActivity::class.java)) }
            },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Text(
                text = stringResource(R.string.digest_screen_title),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
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
