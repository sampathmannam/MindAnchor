package org.mindanchor.model

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mindanchor.ui.MindAnchorTheme

/**
 * Hosts the check-in itself, opened from its own quiet notification.
 *
 * [EmaSchedule] (see Ema.kt) decides when to ask; [EmaScheduler] is the
 * Android glue that turns that into alarms and the notification that
 * opens this. This activity is only ever the last step — take the
 * answer, save it, get out of the way. See [EmaScreen] for why leaving
 * costs nothing at any point in the flow.
 */
class EmaActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = MomentStore(applicationContext)

        setContent {
            MindAnchorTheme {
                EmaScreen(
                    onSave = { moment ->
                        // Fire-and-forget, same as the pause's own
                        // record-keeping in GateActivity. Unlike the
                        // pause, though, this alarm can fire at any
                        // moment while the person is looking at their own
                        // home screen, so a failure here must cost this
                        // one check-in — never the launcher sitting
                        // behind it.
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            runCatching {
                                store.append(moment)
                                EmaScheduler.ensureScheduled(applicationContext)
                            }
                        }
                    },
                    onFinished = { finish() },
                )
            }
        }
    }
}
