package org.mindanchor.ui

import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * v0.72.x: applies a saturation-0 [ColorMatrixColorFilter]
 * to the subtree when [enabled] is true.
 *
 * This is the in-app fallback for the
 * `accessibility_display_daltonizer` write that used
 * to drive the greyscale effect. On Android 14+ the
 * daltonizer constants are no longer in the public
 * SDK and the system no longer applies the write to
 * non-accessibility-service apps, so the only greyscale
 * the user can actually see is the one this composable
 * applies to MindAnchor's own windows. Third-party
 * apps still render in full colour; for system-wide
 * greyscale on a modern Android the only path is an
 * accessibility service, which is out of scope for
 * this fix.
 *
 * The composable is a no-op on API < 31 because
 * [RenderEffect.createColorFilterEffect] needs Android
 * 12; the launcher minSdk is 33 so this is always
 * available, but the `if` guard documents the floor and
 * means the build still works if the floor is ever
 * lowered.
 */
@Composable
fun GreyscaleRoot(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }
    // Platform [ColorMatrix] (not Compose's) — the
    // graphics layer consumes the platform type via
    // [ColorMatrixColorFilter]. A saturation=0 matrix
    // collapses every channel to its luminance, giving
    // a full greyscale without touching the alpha
    // channel.
    val greyscaleEffect: ComposeRenderEffect? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val matrix = AndroidColorMatrix().apply { setSaturation(0f) }
            val filter = ColorMatrixColorFilter(matrix)
            val android = RenderEffect.createColorFilterEffect(filter)
            android.asComposeRenderEffect()
        } else {
            null
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (greyscaleEffect != null) {
                    renderEffect = greyscaleEffect
                }
            },
    ) {
        content()
    }
}

