@file:Suppress("MaxLineLength")
package org.mindanchor.lock

import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.mindanchor.R

/**
 * v0.26.1 §3.4: a Quick Settings tile that opens
 * [GroundMeActivity] when tapped.
 *
 * The tile is a *single* affordance — one tap, one activity,
 * one screen ([org.mindanchor.launcher.GroundMeScreen]). The
 * user reaches the grounding exercises from the lock screen or
 * the shade without unlocking the phone or leaving whatever
 * they were doing, which is the whole point of the gesture at
 * the moment someone is in distress.
 *
 * The tile is always available; the launcher is not gating it
 * behind "show only in 2am hours" or similar heuristics. A
 * user who wants to ground mid-afternoon is just as welcome to
 * use it. The launcher is calm by default but never paternal.
 *
 * The service binds to no one; the `onClick` is an explicit
 * `startActivityAndCollapse` with `Intent.FLAG_ACTIVITY_NEW_TASK`
 * so the launch lands from the shade.
 */
class GroundMeTile : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        // Tiles are "active" by default; the label comes from
        // the manifest, and the icon from the manifest's tile
        // configuration. The launcher does not need any custom
        // state here.
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.label = getString(R.string.ground_me_tile_label)
    }

    override fun onStartListening() {
        super.onStartListening()
        // Re-assert the label on every listen callback so a
        // localisation change updates the tile without the
        // user having to re-add it.
        qsTile?.label = getString(R.string.ground_me_tile_label)
    }

    override fun onClick() {
        super.onClick()
        val launch = Intent().apply {
            component = ComponentName(this@GroundMeTile, GroundMeActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndCollapse(launch)
    }

    /** The onClick path does not bind, but the lifecycle requires this. */
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
