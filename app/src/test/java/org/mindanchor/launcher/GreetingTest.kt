package org.mindanchor.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun `morning hours greet with morning`() {
        assertEquals("m", greetingFor(5, "m", "d", "e"))
        assertEquals("m", greetingFor(11, "m", "d", "e"))
    }

    @Test
    fun `midday hours greet with day`() {
        assertEquals("d", greetingFor(12, "m", "d", "e"))
        assertEquals("d", greetingFor(17, "m", "d", "e"))
    }

    @Test
    fun `night hours greet with evening`() {
        assertEquals("e", greetingFor(18, "m", "d", "e"))
        assertEquals("e", greetingFor(23, "m", "d", "e"))
        assertEquals("e", greetingFor(0, "m", "d", "e"))
        assertEquals("e", greetingFor(4, "m", "d", "e"))
    }
}
