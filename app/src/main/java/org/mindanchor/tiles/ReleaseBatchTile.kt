package org.mindanchor.tiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.mindanchor.R
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.notifications.BatchReleaser

/**
 * Quick Settings tile: "Release my batch now" — fires the held notification
 * batch on demand, outside the scheduled release times.
 *
 * This is the manual override for the moment when the user has decided
 * they are ready to look at the held batch (a "now is the right time"
 * override of the schedule). It always shows INACTIVE: it is an action,
 * not a state.
 *
 * Wiring:
 *  - [BatchReleaser.releaseNow] does the actual fan-out and persists the
 *    "released at" timestamp so the next scheduled release knows to
 *    skip-or-arm correctly.
 *  - [BatchAlarms.ensureScheduled] re-arms the next release from the
 *    current schedule, so a manual release does not leave a stale alarm
 *    pointing at the next slot.
 *
 * v0.70+ (Phase 1 T-1.4). No new permissions. Failures are reported via
 * a Toast so the user has feedback even when the QS panel is closing.
 */
class ReleaseBatchTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        renderState()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        // Quick visual feedback first; the actual work runs off-thread.
        // This is a "fire and forget" action — the QS panel is closing
        // as the user lifts their finger, and we want the click to feel
        // instant rather than waiting on DataStore.
        Toast.makeText(ctx, R.string.tile_release_batch_toast, Toast.LENGTH_SHORT).show()
        scope.launch {
            val result = runCatching {
                BatchReleaser.releaseNow(ctx)
                BatchAlarms.ensureScheduled(ctx)
            }
            result.onFailure {
                Toast.makeText(
                    ctx,
                    R.string.tile_release_batch_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_release_batch_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_release_batch)
        // The tile is an action, not a state. INACTIVE here means
        // "nothing is queued" in the QS UX language — the user reads
        // the label, not the state. ACTIVE would imply the batch is
        // currently being released, which would be a misleading
        // hint about transient state.
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
