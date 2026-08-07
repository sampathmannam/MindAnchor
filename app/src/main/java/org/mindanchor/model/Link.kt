package org.mindanchor.model

import kotlin.math.abs
import kotlin.math.min

/**
 * One day's signal paired with the label that followed it.
 *
 * The pairing is deliberately lagged: [signal] is measured on the night
 * or day *before* [label]. A same-day pairing would describe — "you slept
 * badly and you feel bad" — where a lagged one can anticipate. That
 * difference is the entire point of this file.
 */
data class Paired(val signal: Double, val label: Double)

/** Which way a link runs. Direction only; see [Link] for why not size. */
enum class LinkDirection {
    /** Higher signal, higher label. */
    SAME,

    /** Higher signal, lower label. */
    OPPOSITE,
}

/**
 * A relationship between one signal and one label, found in one person's
 * own history and nowhere else.
 *
 * ## Why there is no coefficient here
 *
 * [rho] is kept because the ranking needs it, but nothing downstream may
 * present it as an effect size. On thirty-odd days, a correlation's
 * confidence interval is so wide that the point estimate is very nearly
 * uninformative — "ρ = 0.52" reads as precision that does not exist. What
 * survives at this sample size is a **direction** and the fact that it
 * held up against chance, which is what [direction] and [adjustedP]
 * carry. This is the same discipline as
 * [org.mindanchor.report.Observation], which refuses to carry its z-score
 * for the same reason.
 */
data class Link(
    val n: Int,
    val rho: Double,
    /** Block-permutation p-value, before correcting for how many were tested. */
    val rawP: Double,
    /** Holm-adjusted p-value across the whole grid of signals and labels. */
    val adjustedP: Double,
) {
    val direction: LinkDirection
        get() = if (rho >= 0) LinkDirection.SAME else LinkDirection.OPPOSITE

    /** Whether this survived correction and may be spoken about at all. */
    val holds: Boolean get() = adjustedP <= LinkFinder.ALPHA
}

/**
 * Looks for links between a person's signals and their own labels, and
 * is built mainly to avoid finding ones that are not there.
 *
 * ## What makes this hard, and why the obvious approach is wrong
 *
 * After a month there are perhaps thirty paired days. Fitting a
 * regression on five signals against thirty points would produce
 * coefficients, and every one of them would be noise dressed as a
 * finding. Three specific traps have to be handled or this reports
 * nonsense with a straight face:
 *
 * **Days are not independent.** Mood is sticky: a bad week is one long
 * event, not seven. An ordinary permutation test destroys that
 * autocorrelation and so understates how easily chance produces a
 * pattern — its p-values come out far too small. This uses **circular
 * block permutation** with week-long blocks, which shuffles whole weeks
 * and leaves the within-week structure intact. Week-long blocks also
 * happen to preserve day-of-week rhythm, which is real in both steps and
 * mood and would otherwise masquerade as a link between them.
 *
 * That is not a claim taken on faith. Simulated over 300 runs of two
 * *independent* AR(1) series of forty days each — so every "finding" is
 * by construction false — at a nominal 5%:
 *
 * | autocorrelation | ordinary shuffle | block shuffle |
 * |-----------------|------------------|---------------|
 * | none            | 4.0%             | 4.0%          |
 * | φ = 0.6         | **12.3%**        | 5.3%          |
 * | φ = 0.85        | **35.3%**        | 13.0%         |
 *
 * At the stickiness daily mood plausibly has, the ordinary test is wrong
 * by a factor of two and a half and the block test is right. The honest
 * caveat is the last row: at φ = 0.85 blocks reduce the inflation by
 * roughly two thirds but do not remove it, because a week-long block
 * cannot preserve dependence that outlives a week. Holm correction across
 * the grid is the second line of defence there, and the reason it is
 * family-wise rather than false-discovery.
 *
 * **Everything is being tested at once.** Five signals against two labels
 * is ten hypotheses, and at p < 0.05 chance alone yields one every other
 * time you look. Holm correction is applied across the whole grid. Holm
 * rather than Benjamini–Hochberg deliberately: BH controls the share of
 * findings that are false, which is the right choice when a false one is
 * cheap. Here a false one is a phone telling somebody something untrue
 * about their own mind, so the stricter family-wise control is worth the
 * findings it costs.
 *
 * **Rank, not value.** Spearman rather than Pearson: no normality
 * assumption, and one catastrophic night cannot drag a correlation into
 * existence by itself.
 *
 * ## What it still cannot do
 *
 * Establish cause. A link between short sleep and next-day low mood is
 * equally consistent with something else driving both, and this says so
 * by never using causal words. It is an observation about a person's own
 * record, which they are far better placed to interpret than this is.
 */
object LinkFinder {

    /**
     * Paired days needed before a link may even be tested.
     *
     * Four weeks. Below this the block permutation has too few blocks to
     * shuffle meaningfully — with week-long blocks, three weeks is three
     * blocks and only six distinct arrangements, so no p-value below
     * about 0.17 is even reachable. Reporting "nothing found" from data
     * that could not have found anything would be a lie of omission, so
     * below this the answer is explicitly "not yet".
     */
    const val MIN_PAIRED_DAYS = 28

    /** Days per permutation block: a week, to preserve day-of-week rhythm. */
    const val BLOCK_DAYS = 7

    /**
     * Permutations per test.
     *
     * Two thousand puts the resolution of a p-value at about 0.0005,
     * which is far finer than anything here acts on, and costs a few
     * milliseconds on thirty points. This runs once a night on a phone
     * that is already awake and charging.
     */
    const val PERMUTATIONS = 2_000

    /** The threshold a Holm-adjusted p must clear. */
    const val ALPHA = 0.05

    /**
     * Tests every signal against every label and returns what survived.
     *
     * [byKey] maps each signal-and-label combination to that person's
     * paired days, oldest first — **order matters here**, unlike
     * everywhere else in this app, because block permutation depends on
     * the sequence being the real one.
     *
     * A key with too few days is absent from the result rather than
     * present with a null: "not enough data yet" is [notYetTestable]'s
     * job, and conflating it with "tested and found nothing" is exactly
     * the confusion this is built to avoid.
     */
    fun <K> find(byKey: Map<K, List<Paired>>, seed: Long): Map<K, Link> {
        val testable = byKey.filterValues { it.size >= MIN_PAIRED_DAYS }
        if (testable.isEmpty()) return emptyMap()

        // Sorted by key string so the seed maps to the same key the same
        // way on every run: an identical history must produce an
        // identical answer, or a report reshuffles itself overnight and
        // looks like it found something new.
        val ordered = testable.entries.sortedBy { it.key.toString() }

        val raw = ordered.mapIndexed { index, (key, pairs) ->
            val xs = pairs.map { it.signal }
            val ys = pairs.map { it.label }
            val rho = spearman(xs, ys)
            if (rho == null) {
                key to null
            } else {
                // The seed varies per key so two signals with identical
                // histories do not get identical permutations, and is
                // derived rather than random so the whole thing stays
                // reproducible.
                key to (rho to blockPermutationP(xs, ys, rho, seed + index * PRIME))
            }
        }

        val tested = raw.mapNotNull { (key, value) -> value?.let { key to it } }
        if (tested.isEmpty()) return emptyMap()

        val adjusted = holm(tested.map { it.second.second })
        return tested.mapIndexed { index, (key, value) ->
            val (rho, p) = value
            key to Link(
                n = testable.getValue(key).size,
                rho = rho,
                rawP = p,
                adjustedP = adjusted[index],
            )
        }.toMap()
    }

    /** Keys that have data but not yet enough of it to say anything about. */
    fun <K> notYetTestable(byKey: Map<K, List<Paired>>): List<K> =
        byKey.filterValues { it.isNotEmpty() && it.size < MIN_PAIRED_DAYS }.keys.toList()

    private const val PRIME = 7919L

    /**
     * Spearman's rank correlation, with ties handled by average ranks.
     *
     * Returns null when either series is constant — no ranking exists to
     * correlate, and a person whose every answer was "3" has told us
     * something real about themselves that is not a correlation.
     */
    fun spearman(xs: List<Double>, ys: List<Double>): Double? {
        if (xs.size != ys.size || xs.size < 3) return null
        return pearson(rank(xs), rank(ys))
    }

    /**
     * Average ranks, which is what makes ties correct.
     *
     * The familiar `1 - 6Σd²/n(n²-1)` shortcut for Spearman is only valid
     * without ties, and these scales are one-to-five integers where ties
     * are the norm rather than the exception. Ranking properly and then
     * running Pearson over the ranks is the definition that survives them.
     */
    private fun rank(values: List<Double>): List<Double> {
        val order = values.indices.sortedBy { values[it] }
        val ranks = MutableList(values.size) { 0.0 }
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && values[order[j + 1]] == values[order[i]]) j++
            val average = (i + j) / 2.0 + 1.0
            for (k in i..j) ranks[order[k]] = average
            i = j + 1
        }
        return ranks
    }

    private fun pearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        val meanX = xs.average()
        val meanY = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxy += dx * dy
            sxx += dx * dx
            syy += dy * dy
        }
        if (sxx <= 0.0 || syy <= 0.0) return null
        return sxy / kotlin.math.sqrt(sxx * syy)
    }

    /**
     * How often chance alone produces a correlation this strong, when the
     * shuffling respects that days come in runs.
     *
     * The `+1` on both sides is Phipson & Smyth (2010): a permutation
     * test can never legitimately report p = 0, because the observed
     * arrangement is itself one of the arrangements. Without the
     * correction a strong link reports p = 0.000 and invites exactly the
     * certainty this whole design exists to avoid.
     */
    fun blockPermutationP(
        xs: List<Double>,
        ys: List<Double>,
        observedRho: Double,
        seed: Long,
        permutations: Int = PERMUTATIONS,
    ): Double {
        val random = Xorshift(seed)
        val target = abs(observedRho)
        var atLeastAsExtreme = 0
        repeat(permutations) {
            val shuffled = circularBlockShuffle(ys, random)
            val rho = spearman(xs, shuffled)
            if (rho != null && abs(rho) >= target) atLeastAsExtreme++
        }
        return (atLeastAsExtreme + 1.0) / (permutations + 1.0)
    }

    /**
     * Rebuilds the series out of week-long chunks taken from random
     * starting points, wrapping around the end.
     *
     * Wrapping matters: without it, blocks near the end of the history
     * could never be chosen as starting points, so the most recent days —
     * the ones a person actually cares about — would be systematically
     * under-represented in the null.
     */
    private fun circularBlockShuffle(values: List<Double>, random: Xorshift): List<Double> {
        val n = values.size
        val out = ArrayList<Double>(n)
        while (out.size < n) {
            val start = random.nextInt(n)
            for (offset in 0 until BLOCK_DAYS) {
                if (out.size >= n) break
                out += values[(start + offset) % n]
            }
        }
        return out
    }

    /**
     * Holm–Bonferroni step-down adjustment, in the input's own order.
     *
     * Monotonicity is enforced on the way up so an adjusted p can never
     * come out below one belonging to a smaller raw p, which would let a
     * weaker result outrank a stronger one purely through the correction.
     */
    fun holm(ps: List<Double>): List<Double> {
        val m = ps.size
        if (m == 0) return emptyList()
        val order = ps.indices.sortedBy { ps[it] }
        val adjusted = MutableList(m) { 0.0 }
        var running = 0.0
        order.forEachIndexed { rank, index ->
            val candidate = min(1.0, (m - rank) * ps[index])
            running = maxOf(running, candidate)
            adjusted[index] = running
        }
        return adjusted
    }

    /**
     * A small deterministic generator.
     *
     * Written out rather than taken from the platform so that the same
     * history produces the same p-values on every device and every
     * release, and so the whole thing can be checked against an
     * independent implementation. Xorshift64* is far more randomness than
     * shuffling thirty numbers needs.
     */
    internal class Xorshift(seed: Long) {
        private var state: Long = if (seed == 0L) -0x61c8864680b583ebL else seed

        fun nextLong(): Long {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 7)
            x = x xor (x shl 17)
            state = x
            return x * -0x61c8864680b583ebL
        }

        /** Uniform in `0 until bound`. */
        fun nextInt(bound: Int): Int {
            require(bound > 0)
            val value = nextLong() ushr 1
            return (value % bound).toInt()
        }
    }
}
