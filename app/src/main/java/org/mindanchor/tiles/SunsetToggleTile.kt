package org.mindanchor.tiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.sunset.SunsetController

/**
 * Quick Settings tile: "Sunset now" — toggles quiet-hours priority-only mode
 * without going through the Settings surface.
 *
 * State mirrors [SunsetPrefs.enabled] (the same source of truth the home
 * surface reads). The tile shows ACTIVE when Sunset mode is on, INACTIVE
 * otherwise. The OS-level filter is read at click time so the user always
 * sees what they are about to change.
 *
 * Wiring:
 *  - Read: [SunsetPrefs.enabled] (a Flow — first() inside the listener
 *    and the click handler).
 *  - Toggle: [SunsetController.onToggled] — re-arms the start/end alarms
 *    and applies (or lifts) the priority filter.
 *
 * v0.70+ (Phase 1 T-1.4). No new permissions; the tile uses the existing
 * ACCESS_NOTIFICATION_POLICY grant that the Sunset setup already requires.
 */
class SunsetToggleTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val current = SunsetPrefs(applicationContext).enabled.first()
            renderState(current)
        }
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        scope.launch {
            val current = SunsetPrefs(ctx).enabled.first()
            val next = !current
            // Persist first so a crash mid-toggle does not leave the
            // filter in one state and the prefs in another. Both
            // setEnabled and onToggled are suspend because they read
            // and write the same DataStore-backed store.
            SunsetPrefs(ctx).setEnabled(next)
            SunsetController.onToggled(ctx, next)
            renderState(next)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderState(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_sunset_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_sunset)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
