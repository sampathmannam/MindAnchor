/*
 * v0.64.0 (BPD-first): the Settings screen.
 *
 * v0.63.0 had four sections: Sources, Modes, Storage,
 * About. Several rows in those sections are BPD-unsafe:
 *   - "Daily Reflections Prompt" — a scheduled nudge
 *     (counters + scheduling)
 *   - "Hide All Entry Counts" — the fact that we ship
 *     a row whose job is to hide counters means the
 *     app has counters. v0.64.0 has no counters, so
 *     the row is meaningless.
 *   - "Calm Interface Accent" — a personalization
 *     affordance that frames the app as a thing to
 *     configure. BPD-first: most things off by default;
 *     what stays on is the simple stuff.
 *
 * v0.64.0 changes:
 *   - "If you're in crisis" section is FIRST, with the
 *     three numbers tappable (in v0.64.0 they are
 *     visible but not yet wired to ACTION_DIAL —
 *     the line is just a line, on every surface).
 *   - Pause everything (single prominent toggle).
 *   - Quiet hours (pre-set 22:00 - 06:00).
 *   - Where this comes from (4 sources, collapsed by
 *     default — tapping expands).
 *   - About (version, build).
 *   - Crisis line above the footer (parity with every
 *     other surface).
 */
@file:Suppress("MagicNumber")
package org.mindanchor.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mindanchor.journal.JournalSettingsPrefs

private const val CURRENT_VERSION = "v0.66.0"

@Composable
internal fun JournalSettings(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onCall: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // v0.64.0 in-memory state. v0.65.0 will load these
    // from the existing NotesPrefs / AppearancePrefs.
    var pauseEverything by remember { mutableStateOf(false) }
    var sourcesExpanded by remember { mutableStateOf(false) }

    // v0.66.0 (Task 10): the three new optional
    // surface toggles, DataStore-backed. The reads
    // are `collectAsStateWithLifecycle(false)` —
    // `false` is the BPD-safe default, and on the
    // first composition the screen shows the
    // default until DataStore's first real value
    // arrives. Writes are wrapped in
    // `rememberCoroutineScope().launch` — the
    // `set*` functions are `suspend` (DataStore's
    // `edit` is suspending). Persistence file:
    // `journal_settings_v66` (see
    // `JournalSettingsPrefs.kt`).
    val context = LocalContext.current
    val settingsPrefs = remember { JournalSettingsPrefs(context) }
    val scope = rememberCoroutineScope()
    val voiceFirstEnabled by settingsPrefs.voiceFirstEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val affectGridEnabled by settingsPrefs.affectGridEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val therapistExportEnabled by settingsPrefs.therapistExportEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        JournalPaperCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(900.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header — back arrow + "SETTINGS" title.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(top = 32.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArrowLeftGlyph(color = Ink.copy(alpha = 0.30f))
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = "SETTINGS",
                        style = JournalSerifHeaderStyle(),
                        color = Terracotta,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .height(1.dp)
                        .background(Terracotta.copy(alpha = 0.20f)),
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    // v0.66.0 (Task 10): the three new
                    // optional surface toggles at the
                    // top of the list. All default OFF
                    // per v0.65.0 BPD-safe defaults.
                    // The visual style matches the
                    // existing `pauseEverything` toggle
                    // pattern below (SettingsToggleRow
                    // + sublabel). No "!" or "New!"
                    // affordance — the sublabels are
                    // validate-then-suggest.
                    SectionHeader("v0.66.0")
                    SettingsToggleRow(
                        label = "Voice-first for crisis, check-in, skills",
                        sublabel = "When this is on, the surfaces that have it read what's on screen out loud. You can turn it off any time.",
                        checked = voiceFirstEnabled,
                        onCheckedChange = { scope.launch { settingsPrefs.setVoiceFirstEnabled(it) } },
                    )
                    SettingsToggleRow(
                        label = "2D mood grid (Affect-Grid)",
                        sublabel = "When this is on, the Today mood input is a 2D grid instead of the 1D slider. Both are valid; pick what feels easier today.",
                        checked = affectGridEnabled,
                        onCheckedChange = { scope.launch { settingsPrefs.setAffectGridEnabled(it) } },
                    )
                    SettingsToggleRow(
                        label = "Share with therapist PDF",
                        sublabel = "When this is on, an export action appears on Today and in the Crisis surface. The export is generated on this device; nothing is sent.",
                        checked = therapistExportEnabled,
                        onCheckedChange = { scope.launch { settingsPrefs.setTherapistExportEnabled(it) } },
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // BPD-first: "If you're in crisis" is
                    // FIRST, not last. The numbers sit
                    // inside a paper card so they are
                    // visually equal to the other sections
                    // (not a banner, not highlighted).
                    SectionHeader("If you're in crisis")
                    CrisisCard()
                    Spacer(modifier = Modifier.height(24.dp))

                    // Pause everything — single prominent
                    // toggle. Default off (BPD-first: most
                    // things off by default).
                    SectionHeader("Pause")
                    SettingsToggleRow(
                        label = "Pause everything",
                        sublabel = "When this is on, the app holds what's here and doesn't ask.",
                        checked = pauseEverything,
                        onCheckedChange = { pauseEverything = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("Hours")
                    SettingsReadOnlyRow(
                        label = "Quiet hours",
                        value = "10:00 PM to 6:00 AM",
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("Where this comes from")
                    SettingsExpandableRow(
                        label = "4 sources",
                        expanded = sourcesExpanded,
                        onToggle = { sourcesExpanded = !sourcesExpanded },
                    )
                    if (sourcesExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Health Connect",
                                style = JournalSettingsLabel.copy(fontSize = 14.sp()),
                                color = Ink.copy(alpha = 0.60f),
                            )
                            Text(
                                text = "Bluetooth watch",
                                style = JournalSettingsLabel.copy(fontSize = 14.sp()),
                                color = Ink.copy(alpha = 0.60f),
                            )
                            Text(
                                text = "Polar account",
                                style = JournalSettingsLabel.copy(fontSize = 14.sp()),
                                color = Ink.copy(alpha = 0.60f),
                            )
                            Text(
                                text = "Camera baseline",
                                style = JournalSettingsLabel.copy(fontSize = 14.sp()),
                                color = Ink.copy(alpha = 0.60f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("About")
                    SettingsReadOnlyRow(
                        label = "MindAnchor version",
                        value = CURRENT_VERSION,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Crisis line.
                JournalCrisisLine(
                    modifier = Modifier.padding(vertical = 8.dp),
                    onCall = onCall,
                )

                // Footer.
                JournalFooter(
                    activeIcon = FooterIcon.Settings,
                    onSearch = onSearch,
                    onArchive = onArchive,
                    onSettings = onBack,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun CrisisCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = PaperBorder, shape = RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CrisisRow(name = "iCall", number = "9152987821")
            CrisisRow(name = "Vandrevala", number = "1860-2662-362")
            CrisisRow(name = "AASRA", number = "9820466726")
        }
    }
}

@Composable
private fun CrisisRow(name: String, number: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        Text(
            text = number,
            style = JournalSettingsLabel.copy(fontWeight = FontWeight.SemiBold),
            color = Ink.copy(alpha = 0.80f),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = JournalSmallCaps,
        color = Ink.copy(alpha = 0.40f),
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun SettingsHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Ink.copy(alpha = 0.05f)),
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    sublabel: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = JournalSettingsLabel,
                color = Ink.copy(alpha = 0.80f),
                modifier = Modifier.weight(1f),
            )
            JournalToggle(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (sublabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sublabel,
                style = JournalSettingsLabel.copy(fontSize = 12.sp()),
                color = Ink.copy(alpha = 0.50f),
            )
        }
    }
}

@Composable
private fun SettingsReadOnlyRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        Text(
            text = value,
            style = JournalSettingsLabel.copy(fontSize = 14.sp()),
            color = Ink.copy(alpha = 0.50f),
        )
    }
}

@Composable
private fun SettingsExpandableRow(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        // v0.64.0: a small chevron-down / chevron-up. We
        // use the existing ChevronLeftGlyph rotated 90°
        // down for "expanded" and 270° for "collapsed" to
        // avoid new icon assets.
        Box(
            modifier = Modifier
                .size(20.dp)
                .rotate(if (expanded) 90f else 270f),
            contentAlignment = Alignment.Center,
        ) {
            ChevronLeftGlyph(color = Ink.copy(alpha = 0.40f))
        }
    }
}

@Suppress("FunctionName")
private fun JournalSerifHeaderStyle() = androidx.compose.ui.text.TextStyle(
    fontFamily = JournalSerif,
    fontWeight = FontWeight.Light,
    fontSize = 28.sp(),
    letterSpacing = 5.sp(),
)

@Suppress("FunctionName")
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
