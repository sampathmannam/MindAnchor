package org.mindanchor.friction

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.MindAnchorTheme
import org.mindanchor.ui.HapticFeedbackGateProvider

/**
 * The pause, shown over an app that was opened from somewhere other than
 * the launcher.
 *
 * Same gate, same tones, same wording as the launcher's own — this is a
 * second doorway into [FrictionGate], not a second design. Anything else
 * would mean a person meets two different versions of the same moment
 * depending on how they happened to arrive.
 */
class GateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_PACKAGE)
        if (target.isNullOrBlank()) {
            finish()
            return
        }
        enableEdgeToEdge()

        val prefs = FrictionPrefs(applicationContext)
        val sunsetPrefs = SunsetPrefs(applicationContext)
        val label = labelFor(target)

        setContent {
            MindAnchorTheme {
                // v0.25.16 BUG-013: wrap in HapticFeedbackGateProvider
                // so the breath-pause haptics consult the system
                // haptics toggle / "remove animations" preference.
                // GateActivity is the second doorway into the gate
                // (LauncherRoot is the first), and it also mounts
                // the FrictionGate Composable.
                HapticFeedbackGateProvider {
                    var gate by remember { mutableStateOf<GateContext?>(null) }
                LaunchedEffect(target) {
                    gate = withContext(Dispatchers.IO) {
                        val prior = prefs.recordReach(
                            target,
                            System.currentTimeMillis(),
                            FrictionContext.RECENT_WINDOW_MILLIS,
                        )
                        prefs.recordGateShown(target)
                        val quiet = sunsetPrefs.isQuietHour()
                        val tone = FrictionContext.toneFor(
                            recentOpens = prior,
                            insideSleepWindow = quiet,
                        )
                        val offer = SmallThings.offer(
                            things = prefs.smallThings.first(),
                            nthReach = prior,
                            tone = tone,
                            quietHours = quiet,
                        )
                        val plan = prefs.ifThenPlans.first()[target]?.takeIf { it.isComplete }
                        val compassion = CompassionStore.rotate(
                            prefs.compassionMoments.first(),
                            prior,
                        )?.phrase
                        GateContext(
                            tone = tone,
                            smallThing = offer,
                            ifThenPlan = plan,
                            compassionMoment = compassion,
                        )
                    }
                }

                // Back is a way out, not a way through. Letting it dismiss
                // the gate to reveal the app underneath would make the whole
                // thing a formality that one gesture defeats.
                BackHandler { goHome() }

                val resolved = gate
                when {
                    resolved == null -> CalmBackground { }
                    else -> FrictionGate(
                        tone = resolved.tone,
                        appLabel = label,
                        smallThing = resolved.smallThing,
                        ifThenPlan = resolved.ifThenPlan,
                        compassionMoment = resolved.compassionMoment,
                        // Taking the small thing is leaving, not entering,
                        // and counts as backing out for the same reason
                        // "never mind" does.
                        onSmallThingTaken = {
                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                prefs.recordGateAbandoned(target)
                            }
                            goHome()
                        },
                        onOpen = { minutes -> allow(target, label, minutes) },
                        onNeverMind = {
                            // Counted before leaving. This is the outcome
                            // the pause exists to make possible, and a
                            // pause nobody ever takes is one worth telling
                            // the person about.
                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                prefs.recordGateAbandoned(target)
                            }
                            goHome()
                        },
                    )
                }
                }
            }
        }
    }

    /**
     * Lets the person through, then gets out of the way.
     *
     * Finishing reveals whatever was underneath — the app they were already
     * heading for. Nothing needs to be launched, which matters: re-launching
     * it would drop them at its home screen rather than at the message or
     * the link they actually tapped.
     */
    private fun allow(target: String, label: String, minutes: Long?) {
        AppWatchService.allow(target, minutes)
        if (minutes != null) {
            SessionManager.startSession(applicationContext, target, label, minutes)
        }
        finish()
    }

    private fun goHome() {
        // Straight to the launcher rather than back into the app. "Never
        // mind" has to actually mean it.
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }

    private fun labelFor(packageName: String): String = runCatching {
        val manager = packageManager
        manager.getApplicationLabel(
            manager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)),
        ).toString()
    }.getOrDefault(packageName)

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
