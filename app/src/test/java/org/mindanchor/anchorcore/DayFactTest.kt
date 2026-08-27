package org.mindanchor.anchorcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayFactTest {

    @Test
    fun `late night cluster renders nights and usual without interpreting`() {
        val line = DayFactRenderer.render(FactKind.LATE_NIGHT_CLUSTER, "4|75")
        // "4 nights this week ran well past your usual bedtime."
        assertTrue(line.contains("4"))
        assertTrue(line.contains("usual"))
    }

    @Test
    fun `sleep irregular renders the sri drop`() {
        val line = DayFactRenderer.render(FactKind.SLEEP_IRREGULAR, "18")
        assertTrue(line.contains("18"))
        assertTrue(line.contains("regularity", ignoreCase = true))
    }

    @Test
    fun `movement low renders the direction not a verdict`() {
        val line = DayFactRenderer.render(FactKind.MOVEMENT_LOW, "-2.1")
        assertTrue(line.contains("below", ignoreCase = true))
    }

    @Test
    fun `every kind has a renderer`() {
        for (kind in FactKind.entries) {
            val line = DayFactRenderer.render(kind, "1|x")
            assertTrue(line.isNotBlank())
        }
    }
}
