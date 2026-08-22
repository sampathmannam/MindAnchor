@file:Suppress("MaxLineLength", "FunctionNaming", "ReturnCount", "UnusedParameter", "MagicNumber")
package org.mindanchor.friction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mindanchor.R
import org.mindanchor.data.BpdProfile
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent

@Composable
fun BeforeYouSendInterstitial(context: BeforeYouSendContext, profile: BpdProfile, onDismiss: () -> Unit) {
    val template = pickTemplate(context)
    val a11y = stringResource(R.string.bys_a11y, template.label)
    CalmBackground { sky ->
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)
                .semantics(mergeDescendants = false) { contentDescription = a11y },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(stringResource(R.string.bys_title), style = MaterialTheme.typography.titleLarge, color = sky.textPrimary)
            Text(stringResource(R.string.bys_intro), style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary)
            TemplateCard(template, sky)
            Spacer(Modifier.heightIn(min = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.bys_send_anyway), color = sky.textSecondary)
                }
                Surface(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                ) {
                    // v0.25.12 fix: changed `Modifier.fillMaxSize()` to
                    // `Modifier.fillMaxWidth()`. The Surface sits inside a Row
                    // inside a Column with `fillMaxSize`. A Box with
                    // `fillMaxSize` requests the full parent column height, so
                    // the "Send" Surface grew to 1126 px (half the screen).
                    // With `fillMaxWidth`, the Box fills the Surface horizontally
                    // but sizes vertically to the Text content.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.bys_send), color = sky.textPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable private fun TemplateCard(template: BeforeYouSendTemplate, sky: SkyContent) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(template.label, style = MaterialTheme.typography.titleMedium, color = sky.textPrimary)
            template.lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, color = sky.textPrimary) }
        }
    }
}

data class BeforeYouSendContext(val messageLength: Int, val isAllCaps: Boolean, val sentAfter23: Boolean, val closeContact: Boolean)

private fun pickTemplate(context: BeforeYouSendContext): BeforeYouSendTemplate = when {
    context.isAllCaps -> BeforeYouSendTemplate.FAST
    context.closeContact -> BeforeYouSendTemplate.GIVE
    else -> BeforeYouSendTemplate.DEAR_MAN
}

private enum class BeforeYouSendTemplate(val label: String, val lines: List<String>) {
    DEAR_MAN(label = "DEAR MAN", lines = listOf("Describe the facts, briefly.", "Express how you feel, in one line.", "Assert what you want, without softening.", "Reinforce what changes for them if they do.")),
    GIVE(label = "GIVE", lines = listOf("Gentle — no barbs, no score-keeping.", "Interested — listen for what they are saying back.", "Validate — name what is real for them.", "Easy manner — a face they can stay near.")),
    FAST(label = "FAST", lines = listOf("Fair — to you, and to them.", "No unnecessary Apologies.", "Stick to your values, not the heat of the moment.", "Truthful — say the true thing, not the loud thing.")),
}

object BeforeYouSendHeuristic {
    const val LONG_MESSAGE_CHARS = 280
    const val ALL_CAPS_RATIO = 0.5f
    fun shouldIntervene(profile: BpdProfile, length: Int, allCapsRatio: Float, after23: Boolean, closeContact: Boolean): Boolean {
        val anyFlag = profile.longMessagesIRegret || profile.lateNightImpulses || profile.sometimesISplit
        if (!anyFlag) return false
        if (length >= LONG_MESSAGE_CHARS && profile.longMessagesIRegret) return true
        if (after23 && closeContact && profile.lateNightImpulses) return true
        if (allCapsRatio >= ALL_CAPS_RATIO && (profile.sometimesISplit || profile.longMessagesIRegret)) return true
        return false
    }
    fun contextFor(length: Int, allCapsRatio: Float, after23: Boolean, closeContact: Boolean): BeforeYouSendContext =
        BeforeYouSendContext(length, allCapsRatio >= ALL_CAPS_RATIO, after23, closeContact)
}
