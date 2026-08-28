package org.mindanchor.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.mindanchor.R
import org.mindanchor.support.SupportActivity

/**
 * Quick Settings tile: "Support" — one-tap jump to the support surface
 * (safety plan + chosen people).
 *
 * The brief (CONCEPT 3.5 — "zero friction to coping tools") is the
 * reason this tile exists. A person reaching for the QS panel has
 * already opened the shade; the home surface is one tap further; the
 * support surface is several taps further. The tile collapses the
 * distance to one.
 *
 * SupportActivity is `exported="false"` in the manifest. Because the
 * tile runs in the same process as the launcher, the same-UID access
 * is sufficient — the explicit `Intent` resolves without an exported
 * flag, and there is no risk of a third-party app piggy-backing the
 * start.
 *
 * v0.70+ (Phase 1 T-1.4). No new permissions, no new components
 * outside the app's UID. STATE_INACTIVE: this is a launcher, not a
 * toggle.
 */
class SupportTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        renderState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, SupportActivity::class.java).apply {
            // The tile runs in the SystemUI process's view of the
            // activity stack; FLAG_ACTIVITY_NEW_TASK is required
            // so the start does not get rejected with
            // "android.util.AndroidRuntimeException: Calling
            // startActivity() from outside of an Activity context
            // requires the FLAG_ACTIVITY_NEW_TASK flag".
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun renderState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_support_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_support)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
