package org.mindanchor.report

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsTest {

    private val start = LocalDate.of(2026, 1, 1)
    private fun days(n: Int) = (0 until n).map { start.plusDays(it.toLong()) }

    @Test
    fun `each label is paired with the day before's signal, not the same day's`() {
        // The whole difference between a diary and something worth
        // reading before the day starts.
        val signal = mapOf(start to 10.0, start.plusDays(1) to 20.0, start.plusDays(2) to 30.0)
        val label = mapOf(start to 1.0, start.plusDays(1) to 2.0, start.plusDays(2) to 3.0)
        val pairs = PatternFinder.pairsFor(signal, label, days(3))
        assertEquals(2, pairs.size)
        assertEquals(10.0, pairs[0].signal, 1e-9)
        assertEquals(2.0, pairs[0].label, 1e-9)
        assertEquals(20.0, pairs[1].signal, 1e-9)
        assertEquals(3.0, pairs[1].label, 1e-9)
    }

    @Test
    fun `a day missing either half is skipped, never filled in`() {
        // Inventing a value to close a gap would put a made-up number
        // into the only part of this system that claims to find
        // relationships.
        val signal = mapOf(start to 10.0, start.plusDays(2) to 30.0)
        val label = mapOf(start.plusDays(1) to 2.0, start.plusDays(3) to 4.0)
        val pairs = PatternFinder.pairsFor(signal, label, days(4))
        assertEquals(2, pairs.size)
        assertEquals(listOf(10.0, 30.0), pairs.map { it.signal })
    }

    @Test
    fun `the order of the record is preserved`() {
        // Block permutation depends on the sequence being the real one.
        // This is the single place in the app where reordering a list
        // silently changes an answer.
        val signal = (0 until 10).associate { start.plusDays(it.toLong()) to it.toDouble() }
        val label = (0 until 10).associate { start.plusDays(it.toLong()) to (10 - it).toDouble() }
        val pairs = PatternFinder.pairsFor(signal, label, days(10))
        assertEquals(pairs.map { it.signal }, pairs.map { it.signal }.sorted())
    }

    @Test
    fun `nothing is claimed from a short record, and it says it is still learning`() {
        val n = 10
        val signals = mapOf(Signal.HRV to (0 until n).associate { start.plusDays(it.toLong()) to it.toDouble() })
        val labels = mapOf(Label.VALENCE to (0 until n).associate { start.plusDays(it.toLong()) to (it % 5).toDouble() })
        assertTrue(PatternFinder.stillLearning(signals, labels, days(n)))
        assertTrue(
            PatternFinder.find(signals, labels, days(n), mapOf(Signal.HRV to 1.0), seed = 1).isEmpty(),
        )
    }

    @Test
    fun `a long enough record is no longer still learning`() {
        val n = 60
        val signals = mapOf(Signal.HRV to (0 until n).associate { start.plusDays(it.toLong()) to it.toDouble() })
        val labels = mapOf(Label.VALENCE to (0 until n).associate { start.plusDays(it.toLong()) to (it % 5).toDouble() })
        assertFalse(PatternFinder.stillLearning(signals, labels, days(n)))
    }

    @Test
    fun `a real lagged link is found and pointed the right way`() {
        // Sleep runs in fortnight-long stretches, and the morning after a
        // short stretch is reported worse. Built so the link is genuine
        // and strong rather than borderline, because a borderline one is
        // exactly what this is designed not to report.
        val n = 90
        val sleepByDay = (0 until n).associate { day ->
            start.plusDays(day.toLong()) to if ((day / 14) % 2 == 0) 380.0 else 300.0
        }
        val moodByDay = (0 until n).associate { day ->
            // Depends on the PREVIOUS day's sleep, which is what makes
            // this a lagged link rather than a same-day one.
            val previous = sleepByDay[start.plusDays((day - 1).toLong())] ?: 380.0
            start.plusDays(day.toLong()) to if (previous >= 380.0) 4.0 else 2.0
        }
        val patterns = PatternFinder.find(
            signalsByDay = mapOf(Signal.SLEEP_MINUTES to sleepByDay),
            labelsByDay = mapOf(Label.VALENCE to moodByDay),
            days = days(n),
            todaysSignals = mapOf(Signal.SLEEP_MINUTES to 300.0),
            seed = 5,
        )
        assertEquals(1, patterns.size)
        val pattern = patterns.first()
        assertEquals(Signal.SLEEP_MINUTES, pattern.signal)
        assertEquals(Label.VALENCE, pattern.label)
        assertTrue("a short night should read as the lower side", pattern.lower)
    }

    @Test
    fun `pure noise across the whole grid yields nothing`() {
        val rng = org.mindanchor.model.LinkFinder.Xorshift(4242)
        val n = 90
        val signals = Signal.entries.associateWith { _ ->
            (0 until n).associate { start.plusDays(it.toLong()) to rng.nextInt(100).toDouble() }
        }
        val labels = Label.entries.associateWith { _ ->
            (0 until n).associate { start.plusDays(it.toLong()) to (rng.nextInt(5) + 1).toDouble() }
        }
        val patterns = PatternFinder.find(
            signals,
            labels,
            days(n),
            Signal.entries.associateWith { 50.0 },
            seed = 21,
        )
        assertTrue("noise must produce no patterns, got $patterns", patterns.isEmpty())
    }

    @Test
    fun `no more than two patterns are ever shown`() {
        // A list of these reads as a diagnosis however each line is
        // worded.
        //
        // Every one of the fourteen cells carries the same real link
        // here, and all fourteen clear the bar — checked by removing the
        // cap in simulation, where this returns fourteen. So the cap is
        // genuinely doing the work rather than the assertion passing
        // because nothing was found, which is how the first version of
        // this test managed to prove nothing at all.
        val n = 90
        val sleepByDay = (0 until n).associate { day ->
            start.plusDays(day.toLong()) to if ((day / 14) % 2 == 0) 380.0 else 300.0
        }
        val moodByDay = (0 until n).associate { day ->
            val previous = sleepByDay[start.plusDays((day - 1).toLong())] ?: 380.0
            start.plusDays(day.toLong()) to if (previous >= 380.0) 4.0 else 2.0
        }
        val patterns = PatternFinder.find(
            signalsByDay = Signal.entries.associateWith { sleepByDay },
            labelsByDay = Label.entries.associateWith { moodByDay },
            days = days(n),
            todaysSignals = Signal.entries.associateWith { 300.0 },
            seed = 9,
        )
        assertEquals(PatternFinder.MAX_PATTERNS, patterns.size)
    }

    @Test
    fun `an identical history produces an identical answer`() {
        val n = 90
        val sleep = (0 until n).associate { start.plusDays(it.toLong()) to ((it / 14) % 2 * 80 + 300).toDouble() }
        val mood = (0 until n).associate { day ->
            val previous = sleep[start.plusDays((day - 1).toLong())] ?: 380.0
            start.plusDays(day.toLong()) to if (previous >= 380.0) 4.0 else 2.0
        }
        val signals = mapOf(Signal.SLEEP_MINUTES to sleep)
        val labels = mapOf(Label.VALENCE to mood)
        val today = mapOf(Signal.SLEEP_MINUTES to 300.0)
        assertEquals(
            PatternFinder.find(signals, labels, days(n), today, seed = 3),
            PatternFinder.find(signals, labels, days(n), today, seed = 3),
        )
    }

    @Test
    fun `a signal not measured last night yields no pattern about today`() {
        val n = 90
        val sleep = (0 until n).associate { start.plusDays(it.toLong()) to ((it / 14) % 2 * 80 + 300).toDouble() }
        val mood = (0 until n).associate { day ->
            val previous = sleep[start.plusDays((day - 1).toLong())] ?: 380.0
            start.plusDays(day.toLong()) to if (previous >= 380.0) 4.0 else 2.0
        }
        val patterns = PatternFinder.find(
            mapOf(Signal.SLEEP_MINUTES to sleep),
            mapOf(Label.VALENCE to mood),
            days(n),
            todaysSignals = emptyMap(),
            seed = 5,
        )
        assertTrue("nothing was measured to compare today against", patterns.isEmpty())
    }

    @Test
    fun `the grid key is spelled out so a future field cannot move every p-value`() {
        assertEquals("HRV:VALENCE", SignalLabel(Signal.HRV, Label.VALENCE).toString())
    }
}
