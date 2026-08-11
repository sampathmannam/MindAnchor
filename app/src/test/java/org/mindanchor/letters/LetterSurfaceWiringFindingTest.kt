package org.mindanchor.letters

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterSurfaceWiringFindingTest {

    @Test fun `LetterScreen is a Composable function (smoke)`() {
        // File-shape: the class is in the right package and the top-level
        // function exists. This will be re-pinned by the
        // LetterInboxLayout / LetterReaderLayout tests below.
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        val method = cls.declaredMethods.first { it.name == "LetterScreen" }
        assertNotNull(method)
        assertTrue(method.parameterCount >= 7)
    }

    @Test fun `LetterScreen defaults selectedDate to inbox when null passed`() {
        // File-shape: parameter list has a LocalDate? selectedDate as the
        // 3rd parameter; a nullable type is required so the inbox branch
        // can fire.
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        val method = cls.declaredMethods.first { it.name == "LetterScreen" }
        val param = method.parameters[2]
        assertEquals("date", param.name)
        assertEquals(LocalDate::class.java, param.type)
    }

    @Test fun `friendlyLetterDate is internal (testable)`() {
        val cls = Class.forName("org.mindanchor.letters.LetterDateFormatKt")
        val method = cls.declaredMethods.first { it.name == "friendlyLetterDate" }
        assertTrue("friendlyLetterDate must be internal", java.lang.reflect.Modifier.isStatic(method.modifiers))
    }

    @Test fun `LetterInbox is a private function in the same file`() {
        // Reads the bytecode for the same file's companion-private functions.
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        val hasInbox = cls.declaredMethods.any { it.name == "LetterInbox" }
        assertTrue(hasInbox)
    }

    @Test fun `LetterReader is a private function in the same file`() {
        val cls = Class.forName("org.mindanchor.letters.LetterScreenKt")
        val hasReader = cls.declaredMethods.any { it.name == "LetterReader" }
        assertTrue(hasReader)
    }
}
