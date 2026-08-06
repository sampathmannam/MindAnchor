package org.mindanchor.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.ui.MindAnchorTheme

/**
 * Support lives in its own activity so it can be opened from anywhere —
 * the home screen, a notification, or a shortcut — without first passing
 * through the launcher's other surfaces.
 */
class SupportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindAnchorTheme {
                SupportScreen(onClose = { finish() })
            }
        }
    }
}
