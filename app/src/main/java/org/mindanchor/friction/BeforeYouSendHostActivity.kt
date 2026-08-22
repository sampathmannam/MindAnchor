@file:Suppress("MaxLineLength")
package org.mindanchor.friction

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.mindanchor.data.BpdProfilePrefs
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.26.1 §3.3: a host activity for [BeforeYouSendInterstitial]
 * that the AppWatchService tone-check notification can deep-link
 * to.
 *
 * The interstitial is a `Composable` and was previously only
 * reachable from a debug-demo surface inside HomeScreen
 * (see `BeforeYouSendDemo`). This activity is the
 * "clicked-the-notification" path: it reads the SMS context
 * from the intent extras, builds a [BeforeYouSendContext] from
 * the body length + a caps ratio, reads the user's
 * [org.mindanchor.data.BpdProfile], and hosts the interstitial.
 *
 * `exported="false"`: the activity is started via an explicit
 * `Intent` from a `PendingIntent` built inside the same
 * application, so the system does not need to resolve it across
 * app boundaries.
 */
class BeforeYouSendHostActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sender = intent.getStringExtra(EXTRA_SENDER).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val length = body.length
        val allCapsRatio = if (body.isEmpty()) 0f else {
            body.count { it.isUpperCase() }.toFloat() / body.count { it.isLetter() }.coerceAtLeast(1).toFloat()
        }
        // The "after 23" check is the v0.26.0 §3.3 heuristic's
        // gating signal. For an SMS that just arrived, "now"
        // is the moment, so the wall-clock hour is what we
        // pass. The interstitial itself does not gate — it
        // is the user's own BpdProfile that does.
        // The "after 23" check is the v0.26.0 §3.3 heuristic's
        // gating signal. For an SMS that just arrived, "now"
        // is the moment, so the wall-clock hour is what we
        // pass. The interstitial itself does not gate — it
        // is the user's own BpdProfile that does. The
        // 23 is the v0.26.0 §3.3 spec's "late-night" wall
        // boundary, not an arbitrary number.
        val after23 = java.time.LocalTime.now().hour >= LATE_NIGHT_HOUR_BOUNDARY
        // A close-contact tag is the dialer-app's most-recently-called
        // identity in the broader launcher, but the v0.26.1
        // AppWatchService does not have that read. The
        // heuristic's "close contact" branch is opt-in by the
        // user (BpdProfile.sometimesISplit), so the default
        // is false here.
        val closeContact = false
        val context = BeforeYouSendHeuristic.contextFor(length, allCapsRatio, after23, closeContact)
        val profilePrefs = BpdProfilePrefs(applicationContext)
        setContent {
            MindAnchorTheme {
                val profile by profilePrefs.profile.collectAsState(initial = org.mindanchor.data.BpdProfile())
                BeforeYouSendInterstitial(
                    context = context,
                    profile = profile,
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_SENDER = "bys_sender"
        const val EXTRA_BODY = "bys_body"

        /**
         * The v0.26.0 §3.3 spec's "late-night" wall-clock
         * boundary: 23:00. Past this hour the
         * BeforeYouSendHeuristic gates on
         * `lateNightImpulses`. Named here so the magic
         * number does not appear inline in the
         * `onCreate` body.
         */
        const val LATE_NIGHT_HOUR_BOUNDARY = 23

        /**
         * Build the launch intent for the notification
         * [android.app.PendingIntent]. Carries the SMS
         * context as extras. Used by
         * [org.mindanchor.watch.AppWatchService] when it
         * builds the "Tone check" notification.
         */
        fun intent(context: Context, sender: String, body: String): Intent =
            Intent(context, BeforeYouSendHostActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_BODY, body)
            }
    }
}
