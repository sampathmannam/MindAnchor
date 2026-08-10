package org.mindanchor.sim.personas

import kotlin.random.Random

/**
 * A deterministic RNG seeded from a [Long] and a per-persona salt.
 *
 * Two different [salt]s with the same [seed] produce different streams.
 * The persona's [id] is the natural salt — so two personas on the same
 * day never see the same noise even when the seed is identical.
 *
 * The [next] family returns values in [from, until) (Random's convention)
 * so callers never have to defend against a "max" edge case.
 */
internal class PersonaRng(seed: Long, salt: String) {
    private val rng: Random = Random(seed * 1_000_003L xor salt.hashCode().toLong())

    fun nextDouble(): Double = rng.nextDouble()

    /** Uniform double in [from, until). */
    fun nextDouble(from: Double, until: Double): Double =
        from + rng.nextDouble() * (until - from)

    /** Gaussian noise, mean 0, sd 1. Box-Muller. */
    fun nextGaussian(): Double {
        var u1: Double
        do { u1 = rng.nextDouble() } while (u1 <= 1e-12)
        val u2 = rng.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) *
            kotlin.math.cos(2.0 * Math.PI * u2)
    }

    /** Gaussian noise, mean [mean], sd [sd]. */
    fun nextGaussian(mean: Double, sd: Double): Double = mean + nextGaussian() * sd

    /** Long in [from, until). */
    fun nextLong(from: Long, until: Long): Long = rng.nextLong(from, until)
}
