package org.mindanchor.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The on-device log file. The pure-function tests
 * here pin the format and the rotation policy.
 *
 * @see docs/research/24 for the rationale.
 */
class LogFileTest {

    private fun tmpDir(): File {
        val dir = Files.createTempDirectory("mindanchor-logtest").toFile()
        dir.deleteOnExit()
        return File(dir, "logs").also { it.mkdirs() }
    }

    @Test
    fun `one line per record, four tab-separated fields`() {
        val dir = tmpDir()
        val log = LogFile(dir)
        log.append(LogFile.Level.INFO, "test", "first message", now = 1L)
        log.append(LogFile.Level.WARN, "test", "second message", now = 2L)
        val content = log.current.readText()
        val lines = content.split("\n").filter { it.isNotEmpty() }
        assertEquals(2, lines.size)
        // Each line: timestamp<TAB>level<TAB>tag<TAB>message
        val parts0 = lines[0].split('\t')
        assertEquals(4, parts0.size)
        assertEquals("1", parts0[0])
        assertEquals("INFO", parts0[1])
        assertEquals("test", parts0[2])
        assertEquals("first message", parts0[3])
    }

    @Test
    fun `the four fields are in the documented order`() {
        val dir = tmpDir()
        val log = LogFile(dir)
        log.append(LogFile.Level.ERROR, "FrictionGate", "gate fired", now = 100L)
        val parts = log.current.readText().trim().split('\t')
        assertEquals("100", parts[0])          // timestamp
        assertEquals("ERROR", parts[1])        // level
        assertEquals("FrictionGate", parts[2]) // tag
        assertEquals("gate fired", parts[3])   // message
    }

    @Test
    fun `control characters in the message are sanitized`() {
        val dir = tmpDir()
        val log = LogFile(dir)
        log.append(LogFile.Level.INFO, "test", "a\tb\nc\rd", now = 1L)
        val content = log.current.readText()
        val line = content.trim()
        // The line must not contain tab or newline
        // (other than the trailing one we just trimmed).
        assertTrue("Line should not contain raw tab: '$line'", !line.contains('\t'))
        assertTrue("Line should not contain raw newline: '$line'", !line.contains('\n'))
        assertTrue("Line should not contain raw CR: '$line'", !line.contains('\r'))
        // The sanitized message is "a b c d" with spaces
        assertTrue("Line should contain the sanitized message: '$line'",
            line.endsWith("a b c d"))
    }

    @Test
    fun `UTF-8 multi-byte characters survive the round-trip`() {
        val dir = tmpDir()
        val log = LogFile(dir)
        log.append(LogFile.Level.INFO, "test", "हिंदी · 日本語 · 한국어", now = 1L)
        val content = log.current.readText()
        assertTrue(
            "Multi-byte characters must be preserved: '$content'",
            content.contains("हिंदी · 日本語 · 한국어"),
        )
    }

    @Test
    fun `MAX_FILE_BYTES is 1 MB`() {
        // The rotation policy is documented; the
        // constant is a load-bearing number and the
        // test pins it.
        assertEquals(1L * 1024 * 1024, LogFile.MAX_FILE_BYTES)
    }

    @Test
    fun `MAX_FILES is 5`() {
        // The rotation policy keeps 5 files; the
        // test pins the constant.
        assertEquals(5, LogFile.MAX_FILES)
    }
}
