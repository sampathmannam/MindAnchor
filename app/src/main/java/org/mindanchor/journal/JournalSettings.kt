/*
 * v0.63.0: the Settings screen — paper-card settings list.
 *
 * Locked from superdesign draft 5088ef9e. The drafts
 * render four sections (SOURCES, MODES, STORAGE, ABOUT)
 * with hairline-divided rows, terracotta-on toggle
 * switches, and the persistent 3-icon footer. Each row
 * is a serif-normal 17px label on the left, a control
 * on the right.
 *
 * v0.63.0 implements the same four sections, all rows
 * in-memory. No DataStore writes — every toggle and
 * selection resets to the v0.63.0 default on app
 * restart. v0.64.0 will wire the toggles to
 * [org.mindanchor.data.NotesPrefs] /
 * [org.mindanchor.data.AppearancePrefs] / etc.
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val CURRENT_VERSION = "v0.63.0"

@Composable
internal fun JournalSettings(
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onArchive: () -> Unit,
    onBangGround: () -> Unit,
    onBangBreathe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // v0.63.0 in-memory state. v0.64.0 will load these
    // from the existing NotesPrefs / AppearancePrefs.
    var manualJournal by remember { mutableStateOf(true) }
    var voiceTranscriptions by remember { mutableStateOf(false) }
    var dailyPrompt by remember { mutableStateOf(true) }
    var quietHours by remember { mutableStateOf(true) }
    var hideCounts by remember { mutableStateOf(false) }
    var localBackup by remember { mutableStateOf(true) }

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

                // Body — 4 sections.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                ) {
                    SectionHeader("Sources")
                    SettingsToggleRow(
                        label = "Manual Journal Entry",
                        checked = manualJournal,
                        onCheckedChange = { manualJournal = it },
                    )
                    SettingsHairline()
                    SettingsToggleRow(
                        label = "Voice Transcriptions",
                        checked = voiceTranscriptions,
                        onCheckedChange = { voiceTranscriptions = it },
                    )
                    SettingsHairline()
                    SettingsToggleRow(
                        label = "Daily Reflections Prompt",
                        checked = dailyPrompt,
                        onCheckedChange = { dailyPrompt = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("Modes")
                    SettingsToggleRow(
                        label = "Quiet Hours (22:00 - 06:00)",
                        checked = quietHours,
                        onCheckedChange = { quietHours = it },
                    )
                    SettingsHairline()
                    SettingsReadOnlyRow(
                        label = "Calm Interface Accent",
                        value = "Terracotta",
                    )
                    SettingsHairline()
                    SettingsToggleRow(
                        label = "Hide All Entry Counts",
                        checked = hideCounts,
                        onCheckedChange = { hideCounts = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("Storage")
                    SettingsReadOnlyRow(
                        label = "Local Encrypted Backup",
                        value = "Active",
                        valueColor = QuietTeal.copy(alpha = 0.50f),
                        isBoldValue = true,
                    )
                    SettingsHairline()
                    SettingsLinkRow(label = "Export Data (Markdown)")

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("About")
                    SettingsReadOnlyRow(
                        label = "MindAnchor Version",
                        value = CURRENT_VERSION,
                    )
                    SettingsHairline()
                    SettingsLinkRow(label = "The Philosophy of Slow")
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer.
                JournalFooter(
                    activeIcon = FooterIcon.Settings,
                    onSearch = onSearch,
                    onArchive = onArchive,
                    onSettings = onBack,
                    onBangGround = onBangGround,
                    onBangBreathe = onBangBreathe,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        JournalToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsReadOnlyRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = Ink.copy(alpha = 0.30f),
    isBoldValue: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        Text(
            text = value.uppercase(),
            style = JournalSmallCaps.copy(
                fontWeight = if (isBoldValue) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
            ),
            color = valueColor,
        )
    }
}

@Composable
private fun SettingsLinkRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = JournalSettingsLabel,
            color = Ink.copy(alpha = 0.80f),
        )
        ExternalLinkGlyph(color = Ink.copy(alpha = 0.20f))
    }
}

@Suppress("FunctionName")
private fun JournalSerifHeaderStyle() = androidx.compose.ui.text.TextStyle(
    fontFamily = JournalSerif,
    fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
    fontSize = 28.sp(),
    letterSpacing = 5.sp(),
)

@Suppress("FunctionName")
private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
