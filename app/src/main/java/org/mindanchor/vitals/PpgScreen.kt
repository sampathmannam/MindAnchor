package org.mindanchor.vitals

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import java.time.LocalDate
import org.mindanchor.R
import org.mindanchor.report.Signal
import kotlin.math.roundToInt

/**
 * Camera-PPG capture: rest a finger over the rear camera and flash for
 * about a minute and a half, and get resting HRV and heart rate back — or
 * a plain statement that the recording did not work.
 *
 * [lastReadingSummary] is how a caller who has persisted a prior reading
 * shows it here; this screen itself never stores anything; see
 * [PpgCapture] and [Hrv] for the pipeline that decides what to trust, and
 * for why a shaky reading is refused rather than shown with a caveat.
 *
 * Five states, and only one of them can show a number: needing the camera
 * permission, idle, counting down while measuring, a trusted result, and a
 * refusal. [PpgCapture.state] tells this screen which one it is in; the
 * screen's own job is to never let a refusal and a number appear together.
 */
@Composable
fun PpgScreen(
    onBack: () -> Unit,
    lastReadingSummary: String? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val capture = remember { PpgCapture(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { capture.release() }
    }

    // Camera permission is granted outside this composition — from the
    // system dialog the launcher below opens, or from app settings — so,
    // as with every other permission this app checks, the answer is
    // re-read on resume rather than trusted to stay whatever it was when
    // the screen first appeared.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasCameraPermission = remember(permissionEpoch) {
        context.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionEpoch++ }

    val captureState by capture.state.collectAsState()
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<PpgCaptureState?>(null) }

    // The whole reason to hold the screen open is to hold a finger still
    // against the lens; letting it sleep mid-recording would end the
    // measurement without saying so.
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    fun beginMeasurement() {
        finished = null
        running = true
        scope.launch {
            val outcome = capture.start(lifecycleOwner)
            running = false
            finished = outcome
            // The reading's whole purpose is to feed the nightly look —
            // until this line existed it was shown once and discarded,
            // and the report read HRV only from Health Connect, which
            // this person's watch never writes. A storage failure is not
            // worth disturbing a measurement that succeeded, so it fails
            // silently; the coverage screen is where absence shows up.
            (outcome as? PpgCaptureState.Done)?.hrv?.metrics?.let { metrics ->
                runCatching {
                    MeasuredStore(context.applicationContext)
                        .record(LocalDate.now(), Signal.HRV.name, metrics.rmssdMillis)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }

        Text(
            text = stringResource(R.string.ppg_section),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Text(
            text = stringResource(R.string.ppg_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.ppg_how),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.ppg_feeds),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        when {
            !hasCameraPermission -> {
                // needs-permission
                TextButton(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.ppg_grant))
                }
            }

            running -> {
                // measuring, with a countdown driven by PpgCapture's own flow
                val remaining = (captureState as? PpgCaptureState.Measuring)?.secondsRemaining
                    ?: PpgCapture.MEASUREMENT_SECONDS
                Text(
                    text = stringResource(R.string.ppg_measuring),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.ppg_seconds_left, remaining),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(
                    onClick = { capture.stop() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.ppg_cancel))
                }
            }

            else -> {
                val outcome = finished
                val metrics = (outcome as? PpgCaptureState.Done)?.hrv?.metrics
                val bpm = (outcome as? PpgCaptureState.Done)?.heartRateBpm
                // A cancelled recording is not a refusal — the person chose
                // to stop, and the quality gate never got a say — so it
                // falls through to the ordinary idle state below rather
                // than showing ppg_refused.
                val wasCancelled = (outcome as? PpgCaptureState.Failed)?.reason == PpgCapture.REASON_CANCELLED

                when {
                    metrics != null && bpm != null -> {
                        // result — the only state allowed to show a number
                        Text(
                            text = stringResource(R.string.ppg_result, metrics.rmssdMillis.roundToInt(), bpm),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }

                    outcome != null && !wasCancelled -> {
                        // refused — a Done with no metrics, or any other
                        // Failed reason. Deliberately the same copy either
                        // way: the person needs "try again, hold still",
                        // not a taxonomy of camera failure modes.
                        Text(
                            text = stringResource(R.string.ppg_refused),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }

                    else -> {
                        // idle
                        Text(
                            text = if (lastReadingSummary != null) {
                                stringResource(R.string.ppg_last, lastReadingSummary)
                            } else {
                                stringResource(R.string.ppg_none_yet)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }

                TextButton(
                    onClick = { beginMeasurement() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.ppg_start))
                }
            }
        }
    }
}
