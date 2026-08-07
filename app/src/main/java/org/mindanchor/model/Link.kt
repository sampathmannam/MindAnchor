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
data class Paired(
    val signal: Double,
    val label: Double,
    /**
     * Weekday stratum of the label's day, 0..6. Defaults to a single
     * stratum, under which the weekday adjustment reduces to subtracting
     * one constant — invisible to a rank correlation — so callers that
     * carry no dates behave exactly as they always did.
     */
    val weekday: Int = 0,
)

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
     * This number is not free to choose. A permutation test cannot report
     * a p below `1 / (permutations + 1)`, and Holm multiplies the
     * smallest p by the number of tests, so the smallest **adjusted** p
     * the whole grid can ever produce is `m / (permutations + 1)`. If
     * that floor sits above [ALPHA], no link can be found however real it
     * is, and nothing anywhere would say so — the feature would simply
     * be silent forever and look like it was working.
     *
     * At [ALPHA] = 0.005 and ten tests, two thousand permutations puts
     * the floor at 0.005 exactly: it passes, barely, and one more
     * testable signal would push it over and silently break everything.
     * Twenty thousand leaves the floor at 0.0005 for a ten-test grid and
     * 0.0007 for fourteen, so the margin survives adding signals. It
     * costs a couple of seconds at three in the morning on a phone that
     * is already awake and charging, which is the cheapest thing here.
     *
     * `permutationFloorIsReachable` in the tests pins this.
     */
    const val PERMUTATIONS = 20_000

    /**
     * The threshold a Holm-adjusted p must clear.
     *
     * ## Why not 0.05
     *
     * Because 0.05 was measured and it does not work. Simulated on the
     * real grid — five signals against two labels, every series pure
     * autocorrelated noise, so every "finding" is false by construction:
     *
     * | days | φ    | α = 0.05 | α = 0.01 | α = 0.005 |
     * |------|------|----------|----------|-----------|
     * | 28   | 0.60 | 8.3%     | 0.0%     | 0.0%      |
     * | 28   | 0.85 | 8.3%     | 1.7%     | 0.0%      |
     * | 56   | 0.60 | 15.0%    | 3.3%     | 1.7%      |
     * | 56   | 0.85 | **21.7%**| 8.3%     | 3.3%      |
     * | 84   | 0.60 | 0.0%     | 0.0%     | 0.0%      |
     * | 84   | 0.85 | 15.0%    | 3.3%     | 1.7%      |
     *
     * Holm is supposed to hold the family-wise rate at α, and at 0.05 it
     * does not, because it assumes valid p-values and block permutation's
     * are still optimistic in the far tail — a week-long block cannot
     * represent dependence that outlives a week, and twenty-eight days is
     * only four blocks to shuffle. Only 0.005 keeps the worst case under
     * the nominal 5% everywhere tested.
     *
     * ## What that costs, stated plainly
     *
     * Detection of a genuinely linked signal, same grid, one real link
     * among ten tests:
     *
     * | days | true ρ | α = 0.05 | α = 0.005 |
     * |------|--------|----------|-----------|
     * | 28   | 0.3    | 8.3%     | 1.7%      |
     * | 28   | 0.7    | 66.7%    | 36.7%     |
     * | 56   | 0.7    | 96.7%    | 86.7%     |
     * | 84   | 0.5    | 70.0%    | 46.7%     |
     * | 84   | 0.7    | 100%     | 96.7%     |
     *
     * So: a strong link takes about eight weeks to become reliably
     * findable, a moderate one takes twelve and is still a coin flip, and
     * a weak one is never found at all. That is the deal, and it is the
     * right way round for this particular app. Finding nothing is its
     * ordinary, stated good outcome and costs a person nothing. Telling
     * somebody their sleep predicts their mood when it does not is a
     * machine inventing a fact about their mind, and they may well
     * rearrange their life around it.
     *
     * ## One caveat on those tables
     *
     * Both were measured at two thousand permutations rather than the
     * twenty thousand [PERMUTATIONS] now sets, because twenty thousand
     * takes hours to simulate and seconds to run. The direction of that
     * difference is knowable without re-measuring, and it is favourable
     * on both axes. At two thousand, clearing α = 0.005 across ten tests
     * required *zero* of two thousand shuffles to match the observation,
     * and a signal whose true tail probability is 0.001 gets zero by luck
     * about 13% of the time. At twenty thousand the same bar allows nine
     * of twenty thousand, and that same signal passes about 0.5% of the
     * time. Less Monte Carlo luck means fewer false links **and** more
     * real ones, so the rates above are pessimistic about power and
     * optimistic about false positives — the safe way round for a table
     * being used to justify a threshold.
     */
    const val ALPHA = 0.005

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
            // The weekly schedule is a common cause of both series: short
            // Sunday sleep and a low Monday sit next to each other in a
            // lag-one pairing whether or not the one drives the other.
            // Measured before this adjustment existed, that shape alone
            // produced false positives at ten times the declared alpha
            // (0.053 against 0.005); removing each weekday's own median
            // from both series restored calibration (0.007) at no
            // measured cost in power on a genuine link. The trade is
            // stated: a real link that lives entirely in the weekly
            // schedule is silenced along with the artefact, and silence
            // over false alarm is this file's standing law.
            val xs = demedianByStratum(
                pairs.map { it.signal },
                // The signal belongs to the day before its label.
                pairs.map { (it.weekday + 6) % 7 },
            )
            val ys = demedianByStratum(pairs.map { it.label }, pairs.map { it.weekday })
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
     * Subtracts each stratum's own median from its members.
     *
     * With a single stratum this is a constant shift, which ranks ignore —
     * the property that keeps every dateless caller's behaviour
     * bit-identical. With seven, it removes the weekly cycle from both
     * series before anything is correlated, which is the entire defence
     * against the schedule masquerading as a link.
     */
    internal fun demedianByStratum(values: List<Double>, strata: List<Int>): List<Double> {
        val medians = strata.distinct().associateWith { stratum ->
            val members = values.filterIndexed { i, _ -> strata[i] == stratum }.sorted()
            val middle = members.size / 2
            if (members.size % 2 == 1) members[middle]
            else (members[middle - 1] + members[middle]) / 2.0
        }
        return values.mapIndexed { i, v -> v - medians.getValue(strata[i]) }
    }

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
