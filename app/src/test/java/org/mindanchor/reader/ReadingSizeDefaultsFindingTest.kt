package org.mindanchor.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadingSizeDefaultsFindingTest {

    @Test fun `SMALL is 14sp`() {
        assertEquals(14, ReadingSize.SMALL.sp)
    }

    @Test fun `MEDIUM is 18sp`() {
        assertEquals(18, ReadingSize.MEDIUM.sp)
    }

    @Test fun `LARGE is 32sp (200 percent of 16sp baseline)`() {
        assertEquals(32, ReadingSize.LARGE.sp)
        assertEquals(200, (ReadingSize.LARGE.sp * 100) / 16)
    }

    @Test fun `the three sizes are pairwise distinct`() {
        assertNotEquals(ReadingSize.SMALL, ReadingSize.MEDIUM)
        assertNotEquals(ReadingSize.MEDIUM, ReadingSize.LARGE)
        assertNotEquals(ReadingSize.SMALL, ReadingSize.LARGE)
    }

    @Test fun `LARGE is exactly 200 percent of the WCAG 1_4_4 reference body (16sp)`() {
        // Pin the bar: not "approximately" 200%, exactly.
        assertEquals(16 * 2, ReadingSize.LARGE.sp)
    }
}
