package org.mindanchor.letters

import androidx.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mindanchor.reader.ReadingSize

class LetterReaderTypographyFindingTest {

    // The reader-style helpers take a Typography parameter (not a runtime
    // MaterialTheme.typography read) so the size math is testable from
    // a regular JUnit test. A default Typography() is enough — the
    // assertions below check the .copy() overrides (fontSize, lineHeight,
    // fontWeight), not the inherited shape fields.
    private val typography = Typography()

    @Test fun `body fontSize at SMALL is 14sp`() {
        assertEquals(14, readerBodyStyle(typography, ReadingSize.SMALL).fontSize.value.toInt())
    }

    @Test fun `body fontSize at MEDIUM is 18sp`() {
        assertEquals(18, readerBodyStyle(typography, ReadingSize.MEDIUM).fontSize.value.toInt())
    }

    @Test fun `body fontSize at LARGE is 32sp (200 percent of 16)`() {
        assertEquals(32, readerBodyStyle(typography, ReadingSize.LARGE).fontSize.value.toInt())
    }

    @Test fun `lineHeight is 145 percent of fontSize at all three sizes`() {
        for (size in listOf(ReadingSize.SMALL, ReadingSize.MEDIUM, ReadingSize.LARGE)) {
            val style = readerBodyStyle(typography, size)
            assertEquals(
                "lineHeight must be 1.45 * fontSize for ${size.sp}sp",
                (size.sp * 1.45f).toInt(),
                style.lineHeight.value.toInt()
            )
        }
    }

    @Test fun `disclaimer fontSize is 85 percent of body fontSize at all three sizes`() {
        for (size in listOf(ReadingSize.SMALL, ReadingSize.MEDIUM, ReadingSize.LARGE)) {
            val body = readerBodyStyle(typography, size).fontSize.value
            val disclaimer = readerDisclaimerStyle(typography, size).fontSize.value
            assertEquals(
                "disclaimer must be 0.85 * body for ${size.sp}sp",
                (body * 0.85f).toInt(),
                disclaimer.toInt()
            )
        }
    }
}
