package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.vitals.WellnessStats
import java.time.LocalDate

class AnchorCoreTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 26)

    // Median + MAD = 100 ± 10 over 14 days: reportable, MAD non-zero.
    private fun baselineFor(signal: WellnessSignal) =
        WellnessStats.baseline(signal, List(7) { 90.0 } + List(7) { 110.0 })

    @Test
    fun `observed days counts union of rhythm days and vital-only days`() {
        val rhythm = mapOf(
            today.minusDays(1) to 1380,
            today.minusDays(2) to null,
        )
        val vitals = setOf(today.minusDays(2))
        assertEquals(2, AnchorCore.observedDays(rhythm, vitals))
    }

    @Test
    fun `cluster fires when three of seven onsets run ninety past usual`() {
        // Onsets as minutes-after-18:00. Usual (median of 7) = 300 (23:00).
        // Three nights at 480 (02:00) are >= 390, so laterThanUsual == 3 —
        // the maximum a 7-night median allows.
        val onsets = listOf(300, 300, 300, 300, 480, 480, 480)
        val fact = AnchorCore.lateNightCluster(onsets, today)
        assertNotNull(fact)
        assertEquals("3|300", fact!!.detail)
    }

    @Test
    fun `cluster stays silent under five nights`() {
        assertNull(AnchorCore.lateNightCluster(listOf(480, 480, 480, 480), today))
    }

    @Test
    fun `cluster stays silent when no night ran late`() {
        assertNull(AnchorCore.lateNightCluster(List(7) { 300 }, today))
    }

    @Test
    fun `sleep irregular fires on an eighteen point drop`() {
        val fact = AnchorCore.sleepIrregular(thisWeekSri = 60, lastWeekSri = 78, today = today)
        assertNotNull(fact)
        assertEquals("18", fact!!.detail)
    }

    @Test
    fun `sleep irregular silent on a rise, a small drop, or missing weeks`() {
        assertNull(AnchorCore.sleepIrregular(80, 70, today))
        assertNull(AnchorCore.sleepIrregular(64, 70, today))
        assertNull(AnchorCore.sleepIrregular(null, 70, today))
        assertNull(AnchorCore.sleepIrregular(60, null, today))
    }

    @Test
    fun `steps far below baseline fire MOVEMENT_LOW`() {
        // z = 0.6745 * (20 - 100) / 10 = -5.4
        val reading = WellnessStats.reading(
            WellnessSignal.STEPS,
            today = 20.0,
            baseline = baselineFor(WellnessSignal.STEPS),
        )
        val facts = AnchorCore.vitalFacts(listOf(reading), today)
        assertEquals(1, facts.size)
        assertEquals(FactKind.MOVEMENT_LOW, facts[0].kind)
    }

    @Test
    fun `resting heart rate far above baseline fires RHR_HIGH`() {
        // z = 0.6745 * (150 - 100) / 10 = +3.4
        val reading = WellnessStats.reading(
            WellnessSignal.RESTING_HEART_RATE,
            today = 150.0,
            baseline = baselineFor(WellnessSignal.RESTING_HEART_RATE),
        )
        val facts = AnchorCore.vitalFacts(listOf(reading), today)
        assertEquals(1, facts.size)
        assertEquals(FactKind.RHR_HIGH, facts[0].kind)
    }

    @Test
    fun `vital facts stay silent inside the bands`() {
        // z = 0.6745 * (101 - 100) / 10 = +0.07
        val reading = WellnessStats.reading(
            WellnessSignal.HRV,
            today = 101.0,
            baseline = baselineFor(WellnessSignal.HRV),
        )
        assertTrue(AnchorCore.vitalFacts(listOf(reading), today).isEmpty())
    }

    @Test
    fun `vital facts respect the fourteen-day baseline floor`() {
        // Only 10 days of history: robustZ is computable (-5.4) but the
        // baseline is not reportable, so no fact may fire from it.
        val thin = WellnessStats.baseline(WellnessSignal.STEPS, List(5) { 90.0 } + List(5) { 110.0 })
        val reading = WellnessStats.reading(WellnessSignal.STEPS, today = 20.0, baseline = thin)
        assertTrue(AnchorCore.vitalFacts(listOf(reading), today).isEmpty())
    }
}
