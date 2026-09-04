package org.mindanchor.continuity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mindanchor.ui.MindAnchorTheme

/**
 * Hosts the Task 11 replacement-phone restore flow.
 *
 * A standalone activity, `android:exported="false"`, following the exact
 * style of [org.mindanchor.journal.JournalActivity] — restore is its own
 * full-screen, single-purpose surface, not folded into `HomeActivity`.
 * Back is left to the system default; nothing here overrides it.
 */
class RestoreActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = RestoreViewModel(context = applicationContext)

        setContent {
            MindAnchorTheme {
                RestoreScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}
