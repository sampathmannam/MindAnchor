package org.mindanchor.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * The optional nature layer (docs/research/06 §2).
 *
 * A 40-second glance at a green-roof photograph measurably restored
 * sustained attention (Lee et al. 2015, n=150), and across ~175 experiments
 * simulated nature reliably reduces negative affect even where it falls
 * short of the real thing (Browning et al. 2020). Vegetation, water and sky
 * are the restorative content; static imagery is enough.
 *
 * Scenes are drawn procedurally rather than shipped as photographs: it
 * keeps the app free of binary assets and third-party image licences, lets
 * every scene inherit the time-of-day palette, and costs kilobytes.
 */
enum class NatureScene {
    /** No landscape — the bare sky. */
    OFF,
    MEADOW,
    WATER,
    FOREST,

    /** A different scene each day; nothing moves within a day. */
    ROTATE,
    ;

    companion object {

        /** Concrete scenes, in rotation order. */
        val DRAWABLE = listOf(MEADOW, WATER, FOREST)

        fun fromKey(key: String?): NatureScene =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: ROTATE

        /**
         * The scene to draw today, or null when the landscape is off.
         * Rotation is keyed to the day, so the picture is stable from wake
         * to sleep and simply differs tomorrow — variety without motion.
         */
        fun resolve(setting: NatureScene, epochDay: Long): NatureScene? = when (setting) {
            OFF -> null
            ROTATE -> DRAWABLE[Math.floorMod(epochDay, DRAWABLE.size.toLong()).toInt()]
            else -> setting
        }
    }
}

/** Pure geometry for the landscape, kept free of Android types. */
object NatureMath {

    /**
     * Height of a ridge at [xFraction] across the screen, as a fraction of
     * the layer's amplitude (0..1). Two summed sines of different
     * frequencies give the gently irregular profile of real terrain rather
     * than a mechanical wave.
     */
    fun ridgeOffset(xFraction: Double, layer: Int): Double {
        val phase = 0.7 * layer
        val slow = sin(2 * PI * (0.6 + 0.15 * layer) * xFraction + phase)
        val fast = sin(2 * PI * (1.7 + 0.4 * layer) * xFraction + phase * 1.6)
        val combined = 0.72 * slow + 0.28 * fast // slow terrain dominates
        return ((combined + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Evenly spaced positions for [count] items across the width, inset
     * from both edges so nothing is clipped awkwardly.
     */
    fun spread(count: Int, inset: Double = 0.06): List<Double> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(0.5)
        val span = 1.0 - 2 * inset
        return (0 until count).map { inset + span * it / (count - 1) }
    }

    /**
     * Deterministic jitter in -1..1 for item [index] of [salt], so a scene
     * looks hand-placed but is identical on every recomposition.
     */
    fun jitter(index: Int, salt: Int): Double {
        val hashed = sin((index + 1) * 12.9898 + salt * 78.233) * 43758.5453
        return 2.0 * (hashed - kotlin.math.floor(hashed)) - 1.0
    }
}
