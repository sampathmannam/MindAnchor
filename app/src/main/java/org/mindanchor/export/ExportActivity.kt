@file:Suppress("MaxLineLength", "FunctionNaming", "MagicNumber")
package org.mindanchor.export

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mindanchor.R
import org.mindanchor.chain.ChainCapture
import org.mindanchor.chain.ChainCapturePrefs
import org.mindanchor.data.BpdProfile
import org.mindanchor.data.BpdProfilePrefs
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.LauncherPrefs
import org.mindanchor.data.NotesPrefs
import org.mindanchor.ifs.IfsPick
import org.mindanchor.ifs.IfsPickerPrefs
import org.mindanchor.model.Moment
import org.mindanchor.model.MomentStore
import org.mindanchor.model.Note
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.MindAnchorTheme
import org.mindanchor.ui.SkyContent
import org.mindanchor.vitals.WellnessHistoryStore
import org.mindanchor.vitals.WellnessLedger
import org.mindanchor.vitals.WellnessSignal

/**
 * v0.26.1 §3.4: "Data export for my therapist."
 *
 * One button: build a JSON snapshot of everything the launcher
 * has on file, write it to the app's external-files `export/`
 * directory, and hand the system a `content://` URI to share
 * through `ACTION_SEND`.
 *
 * The export shape is `ExportPayload` below. The payload is
 * deliberately a flat object with named keys, not a class
 * hierarchy — a therapist (or any reader) does not have to
 * chase types. The wellness section is wrapped in an
 * `NOfOneWellnessBlock` that surfaces the median + MAD for
 * every signal, never a population threshold: the export is
 * the user's *own* history, in the N-of-1 framing the
 * launcher applies to it (see
 * `org.mindanchor.vitals.WellnessSignal`).
 *
 * Letter content is **never** in the export. The user prompt
 * for this feature is "share with my therapist" — letters
 * are a private channel and explicitly opt-in for share.
 * The [ExportSanityFindingTest] pins the absence.
 */
class ExportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindAnchorTheme {
                ExportScreen()
            }
        }
    }
}

@Composable
private fun ExportScreen() {
    val context = LocalContext.current
    val a11y = stringResource(R.string.export_a11y)
    CalmBackground { sky ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .semantics(mergeDescendants = false) { contentDescription = a11y },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Header(sky)
            Spacer(Modifier.heightIn(min = 4.dp))
            Text(
                stringResource(R.string.export_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = sky.textSecondary,
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.heightIn(min = 8.dp))
            var status by remember { mutableStateOf<ExportStatus>(ExportStatus.Idle) }
            val scope = rememberCoroutineScope()
            ExportButton(
                sky = sky,
                enabled = status !is ExportStatus.Building,
                onClick = {
                    scope.launch {
                        status = ExportStatus.Building
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching { buildAndShare(context) }
                        }
                        status = outcome.fold(
                            onSuccess = { ExportStatus.Shared(it) },
                            onFailure = { ExportStatus.Failed(it.message ?: "export failed") },
                        )
                    }
                },
            )
            StatusText(status, sky)
        }
    }
}

@Composable
private fun Header(sky: SkyContent) {
    Text(
        stringResource(R.string.export_title),
        style = MaterialTheme.typography.titleLarge,
        color = sky.textPrimary,
    )
}

@Composable
private fun ExportButton(
    sky: SkyContent,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.6f else 0.3f),
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.export_button), color = sky.textPrimary)
        }
    }
}

@Composable
private fun StatusText(status: ExportStatus, sky: SkyContent) {
    val text = when (status) {
        ExportStatus.Idle -> ""
        is ExportStatus.Building -> stringResource(R.string.export_building)
        is ExportStatus.Shared -> stringResource(R.string.export_shared)
        is ExportStatus.Failed -> stringResource(R.string.export_failed, status.reason)
    }
    if (text.isNotEmpty()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = sky.textSecondary)
    }
}

private sealed class ExportStatus {
    data object Idle : ExportStatus()
    data object Building : ExportStatus()
    data class Shared(val uri: Uri) : ExportStatus()
    data class Failed(val reason: String) : ExportStatus()
}

// ----- payload -----

@Serializable
internal data class ExportPayload(
    val exportedAt: String,
    val notes: List<NoteBlock>,
    val oneThing: OneThingBlock,
    val openLoop: OpenLoopBlock,
    val bedtimeList: BedtimeListBlock,
    val wellness: NOfOneWellnessBlock,
    val checkIns: List<CheckInBlock>,
    val bpdProfile: BpdProfileBlock,
    val chainCaptures: List<ChainCaptureBlock>,
    val ifsPicks: List<IfsPickBlock>,
    val note: String,
)

@Serializable
internal data class NoteBlock(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val type: String?,
    val pinned: Boolean,
    val body: String,
)

@Serializable
internal data class OneThingBlock(
    val text: String?,
)

@Serializable
internal data class OpenLoopBlock(
    val note: String?,
    val day: String?,
    val postponedAt: String?,
)

@Serializable
internal data class BedtimeListBlock(
    val day: String?,
    val items: List<String>,
)

@Serializable
internal data class NOfOneWellnessBlock(
    val framing: String,
    val signals: List<WellnessSignalBlock>,
)

@Serializable
internal data class WellnessSignalBlock(
    val signal: String,
    val days: Int,
    val median: Double?,
    val mad: Double?,
    val values: List<WellnessValueBlock>,
)

@Serializable
internal data class WellnessValueBlock(
    val day: String,
    val value: Double,
)

@Serializable
internal data class CheckInBlock(
    val valence: Int,
    val arousal: Int,
    val atMinuteOfDay: Int,
    val day: String,
)

@Serializable
internal data class BpdProfileBlock(
    val longMessagesIRegret: Boolean,
    val lateNightImpulses: Boolean,
    val sometimesISplit: Boolean,
    val namedPersonToCall: Boolean,
    val okAtNight: Boolean,
)

@Serializable
internal data class ChainCaptureBlock(
    val atMillis: Long,
    val event: String,
    val interpretation: String,
    val part: String,
    val want: String,
    val partToBring: String,
)

@Serializable
internal data class IfsPickBlock(
    val atMillis: Long,
    val partName: String,
)

private fun Note.toBlock() = NoteBlock(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    type = type?.name,
    pinned = pinned,
    body = body,
)

private fun ChainCapture.toBlock() = ChainCaptureBlock(
    atMillis = atMillis,
    event = event,
    interpretation = interpretation,
    part = part,
    want = want,
    partToBring = partToBring,
)

private fun IfsPick.toBlock() = IfsPickBlock(atMillis = atMillis, partName = partName)

private fun Moment.toBlock() = CheckInBlock(
    valence = valence,
    arousal = arousal,
    atMinuteOfDay = atMinuteOfDay,
    day = day,
)

private fun BpdProfile.toBlock() = BpdProfileBlock(
    longMessagesIRegret = longMessagesIRegret,
    lateNightImpulses = lateNightImpulses,
    sometimesISplit = sometimesISplit,
    namedPersonToCall = namedPersonToCall,
    okAtNight = okAtNight,
)

private fun WellnessLedger.Entry.toValueBlock() = WellnessValueBlock(day = day.toString(), value = value)

// ----- the build -----

private suspend fun buildAndShare(context: android.content.Context): Uri {
    val payload = buildPayload(context)
    val dir = File(context.getExternalFilesDir(null), "export").apply { mkdirs() }
    val stamp = Instant.now().toString().replace(":", "-")
    val file = File(dir, "mindanchor-export-$stamp.json")
    val json = Json { prettyPrint = true; encodeDefaults = true }
    file.writeText(json.encodeToString(ExportPayload.serializer(), payload), Charsets.UTF_8)
    val authority = context.packageName + ".fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "MindAnchor export")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(share, "Share MindAnchor export").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
    return uri
}

private suspend fun buildPayload(context: android.content.Context): ExportPayload {
    val notesPrefs = NotesPrefs(context)
    val launcherPrefs = LauncherPrefs(context)
    val frictionPrefs = FrictionPrefs(context)
    val momentStore = MomentStore(context)
    val bpdPrefs = BpdProfilePrefs(context)
    val chainPrefs = ChainCapturePrefs(context)
    val ifsPrefs = IfsPickerPrefs(context)
    val wellnessStore = WellnessHistoryStore(context)

    val notesState = notesPrefs.notes.first()
    val notes = notesState.notes.map { it.toBlock() }
    val oneThing = OneThingBlock(text = launcherPrefs.oneThing.first())
    val openLoop = OpenLoopBlock(
        note = frictionPrefs.openLoopNote.first(),
        day = frictionPrefs.openLoopDay.first(),
        postponedAt = frictionPrefs.openLoopPostponedAt.first()?.toString(),
    )
    val bedtime = BedtimeListBlock(
        day = frictionPrefs.bedtimeListDay.first(),
        items = frictionPrefs.bedtimeList.first(),
    )
    val wellness = wellnessSnapshot(wellnessStore)
    val checkIns = momentStore.moments.first().map { it.toBlock() }
    val bpdProfile = bpdPrefs.profile.first().toBlock()
    val chainCaptures = chainPrefs.captures.first().map { it.toBlock() }
    val ifsPicks = ifsPrefs.picks.first().map { it.toBlock() }
    return ExportPayload(
        exportedAt = Instant.now().toString(),
        notes = notes,
        oneThing = oneThing,
        openLoop = openLoop,
        bedtimeList = bedtime,
        wellness = wellness,
        checkIns = checkIns,
        bpdProfile = bpdProfile,
        chainCaptures = chainCaptures,
        ifsPicks = ifsPicks,
        // The "no letter content" promise is loud in the file
        // itself, not just in the spec. A future reader who
        // opens the export can see at a glance that letters
        // were deliberately excluded.
        note = "Letter content is never exported. " +
            "This file is the on-device view at the moment of export.",
    )
}

private suspend fun wellnessSnapshot(store: WellnessHistoryStore): NOfOneWellnessBlock {
    val all = store.all()
    val signals = WellnessSignal.ORDERED.map { signal ->
        val entries = all.filter { it.signal == signal }.sortedBy { it.day }
        val values = entries.map { it.value }
        val median = if (values.isEmpty()) null else org.mindanchor.vitals.WellnessStats.median(values)
        val mad = median?.let { org.mindanchor.vitals.WellnessStats.mad(values, it) }
        WellnessSignalBlock(
            signal = signal.name,
            days = entries.size,
            median = median,
            mad = mad,
            values = entries.map { it.toValueBlock() },
        )
    }
    return NOfOneWellnessBlock(
        framing = "N-of-1: each signal's median + MAD is over your own history, " +
            "never a population threshold.",
        signals = signals,
    )
}
