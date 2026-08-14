@file:Suppress("MaxLineLength")
package org.mindanchor.ifs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.ui.MindAnchorTheme

/**
 * v0.26.1 §3.4: hosts the IFS picker screen.
 *
 * Owns the [IfsPickerPrefs] for the lifetime of the activity;
 * the screen takes the prefs as a parameter rather than reading
 * the application context itself, so the activity can be replaced
 * by a test-time fake without touching the composable.
 *
 * `exported="false"`: Agent 1 owns the home-surface entry points.
 */
class IfsPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = IfsPickerPrefs(applicationContext)
        setContent {
            MindAnchorTheme {
                IfsPickerScreen(
                    prefs = prefs,
                    onPicked = { finish() },
                    onClose = { finish() },
                )
            }
        }
    }
}
