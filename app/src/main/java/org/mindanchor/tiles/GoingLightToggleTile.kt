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
import org.mindanchor.data.FrictionPrefs
import org.mindanchor.friction.GoingLightSchedule
import org.mindanchor.goinglight.GoingLightScheduler

/**
 * Quick Settings tile: "Going Light" — toggles the scheduled mobile-internet
 * gate without going through Settings.
 *
 * Going Light (docs/research/15 §1; Castelo 2025) is the project name for
 * the brief: a daily or weekly window during which the launcher and a
 * companion VpnService cut the *mobile-internet* connection (browser,
 * social, YouTube) while leaving SMS, voice, and offline apps untouched.
 *
 * Wiring:
 *  - Read: [FrictionPrefs.goingLightSchedule] (a Flow).
 *  - Toggle: persists the inverted [GoingLightSchedule], then calls
 *    [GoingLightScheduler.enable] (arms the next transition alarm and, if
 *    the new state is in-window right now, starts the VpnService) or
 *    [GoingLightScheduler.disable] (stops the VpnService and clears the
 *    alarm). The persist-then-arm order matches the existing Settings
 *    UI — the BroadcastReceiver reads the schedule from FrictionPrefs
 *    when the alarm fires, so the persisted value must be in place
 *    before the alarm is armed.
 *
 * v0.70+ (Phase 1 T-1.4). The tile does not request the VPN consent
 * itself; the user must have already granted it via Settings. If they
 * have not, the OS will surface the consent dialog when the scheduler
 * tries to start the service, which is the same first-time UX as the
 * Settings toggle.
 */
class GoingLightToggleTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val current = FrictionPrefs(applicationContext).goingLightSchedule.first()
            renderState(current.enabled)
        }
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        scope.launch {
            val prefs = FrictionPrefs(ctx)
            val current = prefs.goingLightSchedule.first()
            val next = current.copy(enabled = !current.enabled)
            if (next.enabled) {
                GoingLightScheduler.enable(ctx, next)
            } else {
                GoingLightScheduler.disable(ctx)
            }
            renderState(next.enabled)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderState(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_going_light_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_going_light)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
