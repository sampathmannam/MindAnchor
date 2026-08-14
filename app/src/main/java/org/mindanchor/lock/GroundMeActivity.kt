@file:Suppress("MaxLineLength")
package org.mindanchor.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.launcher.GroundMeScreen
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.26.1 §3.4: the lock-screen / Quick Settings entry point
 * for the grounding exercises.
 *
 * The activity is a *thin host* for the existing
 * [org.mindanchor.launcher.GroundMeScreen] (v0.25.11/v0.25.13).
 * It is its own activity, separate from the in-app
 * [org.mindanchor.launcher.LauncherSurface.GroundMe] surface,
 * because the Quick Settings tile has to launch *something*
 * exported, and the launcher's internal surfaces are not
 * separately addressable as components.
 *
 * `exported="false"`: the tile service is in the same app, so
 * the activity is started via an explicit component name with
 * no need for the system to resolve it.
 */
class GroundMeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindAnchorTheme {
                GroundMeScreen(onClose = { finish() })
            }
        }
    }
}
