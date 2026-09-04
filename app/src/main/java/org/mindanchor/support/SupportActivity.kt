package org.mindanchor.support

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mindanchor.ui.MindAnchorTheme

/**
 * Support lives in its own activity so it can be opened from anywhere —
 * the home screen, a notification, or a shortcut — without first passing
 * through the launcher's other surfaces.
 */
open class SupportActivity : ComponentActivity() {

    protected open fun supportViewModelFactory(): ViewModelProvider.Factory =
        defaultViewModelProviderFactory

    protected open fun closeSupport() = finish()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val factory = supportViewModelFactory()
        setContent {
            MindAnchorTheme {
                val supportViewModel: SupportViewModel = viewModel(factory = factory)
                SupportScreen(onClose = ::closeSupport, viewModel = supportViewModel)
            }
        }
    }
}
