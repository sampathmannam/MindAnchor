package org.mindanchor.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhoFiveTest {

    @Test
    fun `all zeros scores 0`() {
        assertEquals(0, WhoFive.score(listOf(0, 0, 0, 0, 0)))
    }

    @Test
    fun `all fives scores 100`() {
        assertEquals(100, WhoFive.score(listOf(5, 5, 5, 5, 5)))
    }

    @Test
    fun `mixed answers multiply by four`() {
        assertEquals(52, WhoFive.score(listOf(3, 2, 3, 2, 3)))
    }

    @Test
    fun `unanswered items give no score`() {
        assertNull(WhoFive.score(listOf(3, -1, 3, 2, 3)))
    }

    @Test
    fun `wrong item count gives no score`() {
        assertNull(WhoFive.score(listOf(3, 3, 3)))
        assertNull(WhoFive.score(listOf(1, 1, 1, 1, 1, 1)))
    }

    @Test
    fun `out of range answers give no score`() {
        assertNull(WhoFive.score(listOf(6, 1, 1, 1, 1)))
    }
}
