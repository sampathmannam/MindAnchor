@file:Suppress("MaxLineLength")
package org.mindanchor.chain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.26.1 §3.4: hosts the chain-capture screen.
 *
 * Owns the [ChainCapturePrefs] for the lifetime of the activity;
 * the screen takes the prefs as a parameter rather than reading
 * the application context itself, so the activity can be replaced
 * by a test-time fake without touching the composable.
 *
 * No deep-link wiring: Agent 1 owns the home-surface entry
 * points. The activity is `exported="false"` and is launched
 * either from a notification (added in a follow-up) or via an
 * explicit `Intent` from a future entry point.
 */
class ChainCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = ChainCapturePrefs(applicationContext)
        setContent {
            MindAnchorTheme {
                ChainCaptureScreen(
                    prefs = prefs,
                    onSaved = { finish() },
                    onClose = { finish() },
                )
            }
        }
    }
}
