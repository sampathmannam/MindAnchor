package org.mindanchor.support

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.atomic.AtomicInteger

class SupportHarnessActivity : SupportActivity() {
    override fun supportViewModelFactory(): ViewModelProvider.Factory =
        checkNotNull(factoryProvider)(application)

    override fun closeSupport() {
        closeCounter?.incrementAndGet()
        super.closeSupport()
    }

    companion object {
        internal var factoryProvider: ((Application) -> ViewModelProvider.Factory)? = null
        internal var closeCounter: AtomicInteger? = null
    }
}
