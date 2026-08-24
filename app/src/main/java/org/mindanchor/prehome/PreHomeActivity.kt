package org.mindanchor.prehome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.mindanchor.HomeActivity
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.26+ (spec Phase 1) — the moment-of-pause
 * activity. The launcher is set as the user's
 * default home; this activity is the HOME intent
 * target. The system shows it after unlock, before
 * the existing [HomeActivity]. The activity
 * shows:
 *
 *  1. The morning intention (if one is set for
 *     today; otherwise a one-tap field with a
 *     "Save" button).
 *  2. A 3-second breath phase (visual breath
 *     pacing; no breathing-rate instruction, the
 *     existing pattern from the friction gate).
 *  3. App icons (the same drawer's apps; a
 *     future commit routes through the launcher
 *     surface).
 *  4. A "Skip to home" affordance (one tap, no
 *     judgment, no "are you sure?").
 *
 * If the user taps a doomscroll package, the
 * [DoomscrollPromptDialog] shows before the launch
 * intent fires.
 *
 * ## Why a separate Activity
 *
 * The existing [HomeActivity] is the "after
 * PreHome" home — the place the user lands when
 * the moment-of-pause is done. The spec is clear
 * that PreHome and Home are different surfaces
 * (PreHome is the moment-of-pause; Home is the
 * always-on home), and the existing HomeActivity's
 * Compose root is calibrated for the always-on
 * home, not the moment-of-pause. A separate
 * activity is the cleanest separation.
 *
 * ## Why the manifest change is staged
 *
 * The PreHome opt-in is a Settings toggle
 * (`prehome_enabled`, default OFF). When OFF, this
 * activity is not the HOME; the existing
 * [HomeActivity] is. When the user opts in, the
 * settings flip the HOME intent to this activity.
 * The default-OFF is the project's
 * opt-out-by-silence rule.
 */
class PreHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val intentionRepo = MorningIntentionRepository(applicationContext)
        val doomscrollList = DoomscrollList(applicationContext)
        setContent {
            MindAnchorTheme {
                CalmBackground { _ ->
                    PreHomeSurface(
                        intentions = intentionRepo,
                        doomscrollList = doomscrollList,
                        onSkipToHome = ::launchHome,
                    )
                }
            }
        }
    }

    private fun launchHome() {
        val home = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(home)
        finish()
    }
}

@Composable
private fun PreHomeSurface(
    intentions: MorningIntentionRepository,
    doomscrollList: DoomscrollList,
    onSkipToHome: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var intention by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf(false) }
    var breathDone by remember { mutableStateOf(false) }
    var doomscrollPackages by remember { mutableStateOf(DoomscrollList.DEFAULT_DOOMSCROLL) }
    var pendingLaunch by remember { mutableStateOf<PendingLaunch?>(null) }

    // Load today's intention (or the most recent fallback)
    // and the doomscroll set on first composition.
    LaunchedEffect(Unit) {
        launch {
            intention = intentions.read(LocalDate.now())
        }
        launch {
            doomscrollList.packages.collectLatest { doomscrollPackages = it }
        }
        launch {
            intentions.asked.collectLatest { asked = it }
        }
    }

    // The 3-second breath phase. The launcher does
    // not instruct a rate; the visual breath is a
    // timing aid, not a breath-coach.
    LaunchedEffect(Unit) {
        delay(3_000L)
        breathDone = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "A moment before the home",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        IntentionCard(
            current = intention,
            draft = draft,
            onDraftChange = { draft = it },
            onSave = {
                scope.launch {
                    intentions.write(LocalDate.now(), draft)
                    intention = draft.trim()
                    draft = ""
                    intentions.markAsked(LocalDate.now())
                    asked = true
                }
            },
            asked = asked,
            breathDone = breathDone,
        )
        Spacer8()
        if (breathDone) {
            TextButton(
                onClick = onSkipToHome,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip to home")
            }
        }
    }

    // The doomscroll prompt. Shown when the user
    // taps a flagged package; the launch intent is
    // held until the user picks.
    pendingLaunch?.let { launch ->
        DoomscrollPromptDialog(
            appLabel = launch.label,
            onOpen = {
                pendingLaunch = null
                ctx.startActivity(launch.intent)
                launchHomeAfter(ctx, onSkipToHome)
            },
            onHold = { pendingLaunch = null },
            onPickDifferent = { pendingLaunch = null },
        )
    }
}

@Composable
private fun IntentionCard(
    current: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    asked: Boolean,
    breathDone: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Today's intention",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (current != null) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else if (!breathDone) {
                Text(
                    text = "Take a moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text("What is one thing you want to be present for today?")
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = onSave,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (asked) "Save" else "Save intention")
                }
            }
        }
    }
}

@Composable
private fun Spacer8() {
    Box(modifier = Modifier.heightIn(min = 8.dp))
}

private fun launchHomeAfter(ctx: Context, onSkipToHome: () -> Unit) {
    // After a doomscroll launch the launcher
    // follows the user to the opened app; the
    // "back" button returns them to the home
    // screen. The spec is explicit: there is no
    // second pre-home prompt.
    onSkipToHome()
}

private data class PendingLaunch(
    val label: String,
    val intent: Intent,
)
