package org.mindanchor.model

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {

    // --- the rank correlation itself ---

    @Test
    fun `a perfect ordering correlates perfectly, either way round`() {
        assertEquals(1.0, LinkFinder.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(1.0, 2.0, 3.0, 4.0))!!, 1e-9)
        assertEquals(-1.0, LinkFinder.spearman(listOf(1.0, 2.0, 3.0, 4.0), listOf(4.0, 3.0, 2.0, 1.0))!!, 1e-9)
    }

    @Test
    fun `it is the ordering that matters, not the distance`() {
        // The point of ranks: one enormous value must not create a
        // correlation by itself, the way it would with Pearson.
        val gentle = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val extreme = listOf(1.0, 2.0, 3.0, 4.0, 1_000_000.0)
        assertEquals(
            LinkFinder.spearman(gentle, gentle)!!,
            LinkFinder.spearman(gentle, extreme)!!,
            1e-9,
        )
    }

    @Test
    fun `ties are ranked by average, because these scales are full of them`() {
        // Valence is an integer one to five. Ties are the norm, and the
        // familiar 6-sigma-d-squared shortcut is simply wrong on them.
        val rho = LinkFinder.spearman(
            listOf(1.0, 1.0, 2.0, 2.0, 3.0),
            listOf(1.0, 2.0, 2.0, 3.0, 3.0),
        )
        assertNotNull(rho)
        // Worked by hand: average ranks are [1.5,1.5,3.5,3.5,5] and
        // [1,2.5,2.5,4.5,4.5], so Sxy = 7.25 and Sxx = Syy = 9, giving
        // 7.25/9. The tie-free shortcut formula gets this wrong.
        assertEquals(7.25 / 9.0, rho!!, 1e-9)
    }

    @Test
    fun `a person who answered the same thing every day has no correlation to find`() {
        // Not an error. It is a real fact about them, and it is not a
        // correlation.
        assertNull(LinkFinder.spearman(listOf(3.0, 3.0, 3.0, 3.0, 3.0), listOf(1.0, 2.0, 3.0, 4.0, 5.0)))
    }

    @Test
    fun `too few days is refused outright`() {
        assertNull(LinkFinder.spearman(listOf(1.0, 2.0), listOf(1.0, 2.0)))
    }

    // --- the permutation test ---

    @Test
    fun `a permutation p-value is never zero`() {
        // Phipson & Smyth 2010. The observed arrangement is one of the
        // arrangements, so p = 0 is not a thing that can be true, and
        // reporting it invites exactly the certainty this design refuses.
        val xs = (1..40).map { it.toDouble() }
        val rho = LinkFinder.spearman(xs, xs)!!
        val p = LinkFinder.blockPermutationP(xs, xs, rho, seed = 1)
        assertTrue("p must be strictly positive, was $p", p > 0.0)
        assertTrue("a perfect link should sit at the floor, was $p", p < 0.01)
    }

    @Test
    fun `an obvious link is found`() {
        val xs = (1..40).map { it.toDouble() }
        val ys = xs.map { it * 2 }
        val rho = LinkFinder.spearman(xs, ys)!!
        assertTrue(LinkFinder.blockPermutationP(xs, ys, rho, seed = 7) < 0.01)
    }

    @Test
    fun `the same history gives the same answer twice`() {
        // A report that reshuffles overnight on unchanged data looks like
        // it found something new. Every p-value here has to be a pure
        // function of the history and the seed.
        val xs = (1..40).map { (it % 7).toDouble() }
        val ys = (1..40).map { ((it * 3) % 5).toDouble() }
        val rho = LinkFinder.spearman(xs, ys)!!
        assertEquals(
            LinkFinder.blockPermutationP(xs, ys, rho, seed = 11),
            LinkFinder.blockPermutationP(xs, ys, rho, seed = 11),
            0.0,
        )
    }

    // --- correction for testing everything at once ---

    @Test
    fun `Holm leaves a lone test alone and multiplies the rest`() {
        assertEquals(listOf(0.04), LinkFinder.holm(listOf(0.04)))
        val adjusted = LinkFinder.holm(listOf(0.001, 0.02, 0.04, 0.3))
        assertEquals(0.004, adjusted[0], 1e-9)
        assertEquals(0.06, adjusted[1], 1e-9)
        assertEquals(0.08, adjusted[2], 1e-9)
        assertEquals(0.3, adjusted[3], 1e-9)
    }

    @Test
    fun `Holm never lets a weaker result overtake a stronger one`() {
        val ps = listOf(0.049, 0.001, 0.5, 0.02, 0.2, 0.03)
        val adjusted = LinkFinder.holm(ps)
        val byStrength = ps.indices.sortedBy { ps[it] }
        var previous = 0.0
        byStrength.forEach { index ->
            assertTrue("adjustment inverted the ordering", adjusted[index] >= previous - 1e-12)
            previous = adjusted[index]
        }
    }

    @Test
    fun `Holm is capped at certainty`() {
        assertTrue(LinkFinder.holm(listOf(0.4, 0.5, 0.6)).all { it <= 1.0 })
    }

    @Test
    fun `borderline findings do not survive being one of ten questions`() {
        // A p of 0.03 is a finding on its own and is not one when it is
        // one of ten things asked at once. This is the whole reason for
        // the correction.
        val ten = listOf(0.03) + List(9) { 0.5 }
        assertFalse(LinkFinder.holm(ten)[0] <= LinkFinder.ALPHA)
    }

    // --- the grid ---

    @Test
    fun `a signal with too little history is not tested and says so`() {
        val short = List(LinkFinder.MIN_PAIRED_DAYS - 1) { Paired(it.toDouble(), it.toDouble()) }
        val byKey = mapOf("hrv" to short)
        assertTrue(LinkFinder.find(byKey, seed = 1).isEmpty())
        assertEquals(listOf("hrv"), LinkFinder.notYetTestable(byKey))
    }

    @Test
    fun `not yet tested and tested-and-found-nothing are different answers`() {
        // Conflating them would report "nothing is going on" from data
        // that could never have shown anything.
        val enough = List(LinkFinder.MIN_PAIRED_DAYS) { Paired(it.toDouble(), (it % 5).toDouble()) }
        assertTrue(LinkFinder.notYetTestable(mapOf("hrv" to enough)).isEmpty())
        assertTrue(LinkFinder.notYetTestable(mapOf("hrv" to emptyList<Paired>())).isEmpty())
    }

    @Test
    fun `a real link in a long enough record is found and pointed the right way`() {
        val n = 60
        val rising = List(n) { Paired(signal = it.toDouble(), label = it.toDouble()) }
        val falling = List(n) { Paired(signal = it.toDouble(), label = (n - it).toDouble()) }
        val found = LinkFinder.find(mapOf("up" to rising, "down" to falling), seed = 3)
        assertEquals(LinkDirection.SAME, found.getValue("up").direction)
        assertEquals(LinkDirection.OPPOSITE, found.getValue("down").direction)
        assertTrue(found.getValue("up").holds)
        assertTrue(found.getValue("down").holds)
    }

    @Test
    fun `the whole grid gives the same answer on the same data`() {
        val a = List(40) { Paired((it % 9).toDouble(), (it % 4).toDouble()) }
        val b = List(40) { Paired((it % 5).toDouble(), (it % 3).toDouble()) }
        val one = LinkFinder.find(mapOf("a" to a, "b" to b), seed = 5)
        val two = LinkFinder.find(mapOf("a" to a, "b" to b), seed = 5)
        assertEquals(one, two)
    }

    @Test
    fun `two signals with identical histories are not given identical permutations`() {
        // The seed is offset per key. Without that, a coincidence in one
        // signal would be reproduced exactly in another and read as
        // corroboration.
        val same = List(40) { Paired((it % 7).toDouble(), (it % 5).toDouble()) }
        val found = LinkFinder.find(mapOf("a" to same, "b" to same), seed = 2)
        assertEquals(found.getValue("a").rho, found.getValue("b").rho, 1e-12)
        assertTrue(
            "identical permutation streams would make one signal corroborate itself",
            found.getValue("a").rawP != found.getValue("b").rawP,
        )
    }

    @Test
    fun `pure noise with no link in it is not reported as a link`() {
        // Deterministic pseudo-noise rather than a random source, so this
        // test cannot pass or fail by luck of the run.
        val rng = LinkFinder.Xorshift(99)
        val noise = List(60) {
            Paired(signal = rng.nextInt(100).toDouble(), label = rng.nextInt(5).toDouble())
        }
        val found = LinkFinder.find(mapOf("noise" to noise), seed = 13)
        assertFalse("noise must not clear the bar", found.getValue("noise").holds)
    }

    @Test
    fun `the generator is deterministic and stays in range`() {
        val first = LinkFinder.Xorshift(42).let { r -> List(20) { r.nextInt(10) } }
        val second = LinkFinder.Xorshift(42).let { r -> List(20) { r.nextInt(10) } }
        assertEquals(first, second)
        assertTrue(first.all { it in 0..9 })
        // A generator that returns one value forever would silently make
        // every permutation identical and every p-value meaningless.
        assertTrue("the stream is degenerate", first.toSet().size > 1)
    }

    @Test
    fun `a zero seed does not collapse the generator`() {
        // Xorshift is stuck at zero forever if it ever reaches it.
        val values = LinkFinder.Xorshift(0).let { r -> List(20) { r.nextInt(100) } }
        assertTrue(values.toSet().size > 1)
    }

    @Test
    fun `rho and direction agree`() {
        listOf(0.7, 0.0, -0.7).forEach { rho ->
            val link = Link(n = 30, rho = rho, rawP = 0.01, adjustedP = 0.01)
            val expected = if (rho >= 0) LinkDirection.SAME else LinkDirection.OPPOSITE
            assertEquals(expected, link.direction)
        }
    }

    @Test
    fun `holds tracks the adjusted p, never the raw one`() {
        // The raw p is what a single test would have said. Acting on it
        // would defeat the whole correction.
        val borderline = Link(n = 30, rho = 0.5, rawP = 0.01, adjustedP = 0.09)
        assertFalse(borderline.holds)
        assertTrue(borderline.copy(adjustedP = LinkFinder.ALPHA).holds)
    }

    @Test
    fun permutationFloorIsReachable() {
        // The trap this pins: a permutation test cannot report a p below
        // 1/(permutations+1), and Holm multiplies the smallest by the
        // number of tests. If that floor sits above ALPHA, no link can
        // ever be found however real it is — and nothing would say so.
        // The feature would go silent forever and look like it was
        // working. At 2000 permutations and ten tests the floor is 0.005,
        // which is ALPHA exactly; one more testable signal would have
        // broken it silently.
        val grid = 14
        val floor = grid.toDouble() / (LinkFinder.PERMUTATIONS + 1.0)
        assertTrue(
            "a grid of $grid could never produce a finding: floor $floor vs alpha ${LinkFinder.ALPHA}",
            floor < LinkFinder.ALPHA,
        )
        // And with real headroom, not by a hair.
        assertTrue("no margin left for another signal", floor < LinkFinder.ALPHA / 2)
    }

    @Test
    fun `the bar is set where the false-positive rate was measured, not at habit`() {
        // 0.05 was measured on this exact grid against pure autocorrelated
        // noise and produced false links in up to 21.7% of runs, because
        // Holm assumes valid p-values and block permutation's are still
        // optimistic in the far tail. See LinkFinder.ALPHA for the table.
        assertTrue("0.05 is the number that did not work", LinkFinder.ALPHA <= 0.005)
    }

    @Test
    fun `week-long blocks divide the minimum record evenly`() {
        // Both numbers are chosen, and a change to either that left a
        // ragged final block would quietly bias the last few days.
        assertEquals(0, LinkFinder.MIN_PAIRED_DAYS % LinkFinder.BLOCK_DAYS)
        assertTrue(LinkFinder.MIN_PAIRED_DAYS / LinkFinder.BLOCK_DAYS >= 4)
    }

    @Test
    fun `an obvious link survives even a strongly autocorrelated record`() {
        // The block permutation is there to stop stickiness manufacturing
        // links. It must not be so blunt that it also erases real ones.
        val n = 56
        val xs = List(n) { (it / 7).toDouble() }
        val history = xs.mapIndexed { index, x -> Paired(x, x + (index % 2) * 0.1) }
        val found = LinkFinder.find(mapOf("real" to history), seed = 17)
        assertTrue(found.getValue("real").holds)
    }

    @Test
    fun `a p-value is a probability`() {
        val rng = LinkFinder.Xorshift(5)
        repeat(20) {
            val xs = List(40) { rng.nextInt(50).toDouble() }
            val ys = List(40) { rng.nextInt(50).toDouble() }
            val rho = LinkFinder.spearman(xs, ys) ?: return@repeat
            val p = LinkFinder.blockPermutationP(xs, ys, rho, seed = it.toLong(), permutations = 200)
            assertTrue("p out of range: $p", p > 0.0 && p <= 1.0)
        }
    }

    @Test
    fun `a stronger correlation never scores a weaker p-value`() {
        val xs = (1..42).map { it.toDouble() }
        val strong = xs
        val weak = xs.mapIndexed { index, value -> if (index % 3 == 0) 100.0 - value else value }
        val strongP = LinkFinder.blockPermutationP(xs, strong, LinkFinder.spearman(xs, strong)!!, seed = 4)
        val weakP = LinkFinder.blockPermutationP(xs, weak, LinkFinder.spearman(xs, weak)!!, seed = 4)
        assertTrue(
            "stronger evidence must not read as weaker: $strongP vs $weakP",
            strongP <= weakP + 1e-12,
        )
        assertTrue(abs(LinkFinder.spearman(xs, strong)!!) > abs(LinkFinder.spearman(xs, weak)!!))
    }
}
