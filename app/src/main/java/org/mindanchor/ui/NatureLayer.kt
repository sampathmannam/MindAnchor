package org.mindanchor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws the landscape for a scene.
 *
 * Everything is tinted with the sky's own haze colour, which means the
 * landscape can only ever push the background further from the text colour,
 * never closer — so SkyMath's contrast guarantee still holds even though it
 * reasons about the bare gradient. In practice that renders as dark
 * silhouettes under a bright sky and pale, misted shapes at dawn and dusk.
 */
fun DrawScope.drawNature(scene: NatureScene, tint: Color) {
    when (scene) {
        NatureScene.MEADOW -> drawMeadow(tint)
        NatureScene.WATER -> drawWater(tint)
        NatureScene.FOREST -> drawForest(tint)
        NatureScene.OFF, NatureScene.ROTATE -> Unit
    }
}

/** Ridge layer opacities, far to near: distance reads as atmosphere. */
private val RIDGE_ALPHA = listOf(0.07f, 0.11f, 0.17f)

private fun DrawScope.drawMeadow(tint: Color) {
    // Three receding ridges, the nearest sitting lowest and darkest.
    val baselines = listOf(0.74f, 0.83f, 0.92f)
    val amplitudes = listOf(0.055f, 0.045f, 0.035f)
    baselines.forEachIndexed { layer, baseline ->
        drawRidge(
            tint = tint,
            alpha = RIDGE_ALPHA[layer],
            baseline = baseline,
            amplitude = amplitudes[layer],
            layer = layer,
        )
    }
    // A few grass strokes on the nearest ridge — sparse, never a texture.
    val bladeAlpha = 0.16f
    NatureMath.spread(7, inset = 0.1).forEachIndexed { index, x ->
        val jitter = NatureMath.jitter(index, salt = 3)
        val startX = (x + 0.03 * jitter).toFloat() * size.width
        val height = (0.028f + 0.012f * jitter.toFloat()) * size.height
        val baseY = 0.93f * size.height
        drawLine(
            color = tint.copy(alpha = bladeAlpha),
            start = Offset(startX, baseY),
            end = Offset(startX + 0.012f * jitter.toFloat() * size.width, baseY - height),
            strokeWidth = size.width * 0.004f,
        )
    }
}

private fun DrawScope.drawWater(tint: Color) {
    // A far shore, then the lake: horizontal bands that thin toward the
    // horizon, reading as reflected light without any movement.
    drawRidge(tint = tint, alpha = 0.09f, baseline = 0.72f, amplitude = 0.03f, layer = 1)

    val horizon = 0.74f
    drawRect(
        color = tint.copy(alpha = 0.06f),
        topLeft = Offset(0f, horizon * size.height),
        size = Size(size.width, size.height * (1f - horizon)),
    )

    val bands = 6
    for (i in 0 until bands) {
        val progress = (i + 1f) / bands
        val y = (horizon + (1f - horizon) * progress * 0.95f) * size.height
        val jitter = NatureMath.jitter(i, salt = 11).toFloat()
        val width = size.width * (0.22f + 0.5f * progress) * (1f + 0.15f * jitter)
        val left = size.width * (0.5f - 0.5f * (0.22f + 0.5f * progress)) +
            jitter * size.width * 0.06f
        drawRoundRectBand(
            color = tint.copy(alpha = 0.05f + 0.05f * progress),
            left = left,
            top = y,
            width = width,
            height = size.height * (0.006f + 0.006f * progress),
        )
    }
}

private fun DrawScope.drawForest(tint: Color) {
    // Two stands of conifers: a pale distant line, then a nearer, taller one.
    drawRidge(tint = tint, alpha = 0.07f, baseline = 0.78f, amplitude = 0.03f, layer = 2)

    drawConiferRow(
        tint = tint,
        alpha = 0.10f,
        count = 11,
        baseline = 0.84f,
        height = 0.085f,
        salt = 5,
    )
    drawConiferRow(
        tint = tint,
        alpha = 0.18f,
        count = 7,
        baseline = 0.96f,
        height = 0.135f,
        salt = 9,
    )
}

// --- primitives -----------------------------------------------------------

private fun DrawScope.drawRidge(
    tint: Color,
    alpha: Float,
    baseline: Float,
    amplitude: Float,
    layer: Int,
) {
    val steps = 48
    val path = Path().apply {
        moveTo(0f, size.height)
        for (step in 0..steps) {
            val xFraction = step.toDouble() / steps
            val offset = NatureMath.ridgeOffset(xFraction, layer)
            val y = (baseline - amplitude * offset.toFloat()) * size.height
            if (step == 0) lineTo(0f, y) else lineTo(xFraction.toFloat() * size.width, y)
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color = tint.copy(alpha = alpha))
}

private fun DrawScope.drawConiferRow(
    tint: Color,
    alpha: Float,
    count: Int,
    baseline: Float,
    height: Float,
    salt: Int,
) {
    NatureMath.spread(count).forEachIndexed { index, x ->
        val jitter = NatureMath.jitter(index, salt).toFloat()
        val centerX = (x.toFloat() + 0.02f * jitter) * size.width
        val treeHeight = height * (1f + 0.22f * jitter) * size.height
        val halfWidth = treeHeight * 0.3f
        val baseY = baseline * size.height
        val path = Path().apply {
            moveTo(centerX, baseY - treeHeight)
            // Slightly stepped sides read as branches rather than a cone.
            lineTo(centerX + halfWidth * 0.55f, baseY - treeHeight * 0.55f)
            lineTo(centerX + halfWidth * 0.4f, baseY - treeHeight * 0.5f)
            lineTo(centerX + halfWidth, baseY)
            lineTo(centerX - halfWidth, baseY)
            lineTo(centerX - halfWidth * 0.4f, baseY - treeHeight * 0.5f)
            lineTo(centerX - halfWidth * 0.55f, baseY - treeHeight * 0.55f)
            close()
        }
        drawPath(path, color = tint.copy(alpha = alpha))
    }
}

private fun DrawScope.drawRoundRectBand(
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2f, height / 2f),
    )
}
