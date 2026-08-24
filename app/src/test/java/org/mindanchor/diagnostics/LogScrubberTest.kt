package org.mindanchor.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogScrubberTest {

    @Test
    fun `redacts phone numbers`() {
        val before = "call me at +1 555 123 4567 or (555) 123-4567"
        val after = LogScrubber.redact(before)
        assertTrue(after.contains("[PHONE REDACTED]"))
        assertTrue(!after.contains("555"))
    }

    @Test
    fun `redacts email addresses`() {
        val before = "contact sampath@example.com or foo.bar+baz@sub.example.org"
        val after = LogScrubber.redact(before)
        assertTrue(after.contains("[EMAIL REDACTED]"))
        assertTrue(!after.contains("@example.com"))
    }

    @Test
    fun `redacts held notification body but preserves metadata`() {
        val before = "HeldNotification{id=1, packageName=com.zhiliaoapp.musically, " +
            "appLabel=TikTok, title=Liked, text=This is a private message body, " +
            "postedAt=12345}"
        val after = LogScrubber.redact(before)
        assertTrue(after.contains("[NOTIFICATION REDACTED]"))
        assertTrue(after.contains("packageName=com.zhiliaoapp.musically"))
        assertTrue(after.contains("title=Liked"))
        assertTrue(!after.contains("This is a private message body"))
    }

    @Test
    fun `redacts held notification body that contains commas`() {
        // CodeRabbit review 2026-08-24 (PR #38):
        // the previous regex `([^,}]+)` stopped at the
        // first comma. A real notification body
        // (`Hey, are you free tonight?`) had only `Hey`
        // redacted. The capture now runs to the next
        // field terminator (`, <word>=` or `}`) so the
        // whole body is redacted in one pass.
        val before = "HeldNotification{id=2, packageName=com.example, " +
            "appLabel=Example, title=Hi, text=Hey, are you free tonight?, " +
            "postedAt=99}"
        val after = LogScrubber.redact(before)
        assertTrue(after.contains("[NOTIFICATION REDACTED]"))
        assertTrue(!after.contains("Hey, are you free tonight?"))
        assertTrue(!after.contains("are you free tonight"))
        assertTrue(after.contains("postedAt=99"))
    }

    @Test
    fun `passes through ordinary log lines`() {
        val before = "Gate shown for com.example at index 0"
        val after = LogScrubber.redact(before)
        assertEquals(before, after)
    }

    @Test
    fun `redacts multiple patterns in one line`() {
        val before = "user foo@bar.com called 555-123-4567 at 12:00"
        val after = LogScrubber.redact(before)
        assertTrue(after.contains("[EMAIL REDACTED]"))
        assertTrue(after.contains("[PHONE REDACTED]"))
        assertTrue(after.contains("at 12:00"))
    }
}
