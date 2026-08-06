package org.mindanchor.digest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.ui.MindAnchorTheme

/** Hosts the notification journal, opened from the digest notification or home. */
class DigestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindAnchorTheme {
                DigestScreen(onClose = { finish() })
            }
        }
    }
}
