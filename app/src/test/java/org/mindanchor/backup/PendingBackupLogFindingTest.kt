package org.mindanchor.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * v0.25.5 WP-H: WorkManager offline retry for the Drive backup.
 *
 * The on-write trigger is best-effort: a [BackupTarget.append]
 * that returns [AppendResult.NetworkError] (or any non-Ok
 * result) on a momentarily-offline device used to cost the
 * user the entry — the only retry was the "Back up now" button.
 * The fix queues the encrypted payload in [BackupPrefs] for the
 * [BackupRetryWorker]'s next NetworkType.CONNECTED run.
 *
 * The five tests below pin the data layer + the on-write call
 * site. The WorkManager Worker class itself is a follow-up (the
 * data layer is the contract that matters for the on-write
 * path; the worker just drains it).
 */
class PendingBackupLogFindingTest {

    @Test
    fun `PendingBackup equals compares payload by content, not reference`() {
        // [data class] equals on a ByteArray is reference-based
        // by default; [PendingBackup] overrides to compare by
        // content. A regression that dropped the override would
        // break queue dedup in [BackupPrefs.removePending] and in
        // the round-trip tests — two queues with the same
        // payload would never match.
        val now = Instant.parse("2026-03-10T08:00:00Z")
        val a = PendingBackup(
            type = ContentType.Notes,
            payload = byteArrayOf(1, 2, 3),
            queuedAt = now,
        )
        val b = PendingBackup(
            type = ContentType.Notes,
            payload = byteArrayOf(1, 2, 3),
            queuedAt = now,
        )
        val c = PendingBackup(
            type = ContentType.Notes,
            payload = byteArrayOf(1, 2, 4),
            queuedAt = now,
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue("different payload should not be equal", a != c)
    }

    @Test
    fun `PendingBackupLog round-trips a queue through encode then decode`() {
        // The wire format is the on-disk format. A regression
        // that changed the field order (or used a different
        // separator) would silently orphan every previously-
        // queued entry.
        val now = Instant.parse("2026-03-10T08:00:00Z")
        val original = listOf(
            PendingBackup(ContentType.Notes, byteArrayOf(1, 2, 3), now),
            PendingBackup(ContentType.Letters, byteArrayOf(4, 5, 6, 7), now.plusSeconds(60)),
        )
        val encoded = PendingBackupLog.encode(original)
        val decoded = PendingBackupLog.decode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `PendingBackupLog decode drops corrupt lines without throwing`() {
        // Same fail-closed rule as MeasuredLedger: a corrupted
        // line costs one entry, never the file. A regression
        // that threw would turn a single bad write into a crash
        // on the next read.
        val mixed = """
            MindAnchor-Notes.txt	2026-03-10T08:00:00Z	AQID	3
            BOGUS	2026-03-10T08:00:00Z	AQID	3
            MindAnchor-Notes.txt	not-a-date	AQID	3
            MindAnchor-Notes.txt	2026-03-10T08:00:00Z	!!!not-base64!!!	3
            MindAnchor-Notes.txt	2026-03-10T08:00:00Z	AQID	99
            MindAnchor-Letters.txt	2026-03-10T08:01:00Z	BAUGBw==	4
        """.trimIndent()
        val decoded = PendingBackupLog.decode(mixed)
        // Two of the six lines are valid; the corrupt ones are
        // dropped, never thrown.
        assertEquals(2, decoded.size)
        assertEquals(ContentType.Notes, decoded[0].type)
        assertEquals(ContentType.Letters, decoded[1].type)
    }

    @Test
    fun `BackupScheduler encryptAndAppend enqueues on a non-Ok result`() {
        // The on-write call site. A regression that dropped the
        // enqueue (back to the v0.25.4 "the entry is lost" path)
        // would re-introduce the silent-failure mode. The file-
        // shape pin is the cheapest way to keep the enqueue
        // there.
        val source = readSource("BackupScheduler.kt")
        assertNotNull(source)
        assertTrue(
            "BackupScheduler.encryptAndAppend enqueues on a non-Ok result",
            source!!.contains("backupPrefs.enqueuePending(") &&
                source.contains("PendingBackup(") &&
                source.contains("result !is AppendResult.Ok"),
        )
    }

    @Test
    fun `BackupPrefs MAX_PENDING bounds the queue at 100 entries`() {
        // The bound is the contract. A regression that removed
        // the cap would let a long offline stretch grow the
        // file unbounded; a regression that lowered it to
        // something silly (e.g. 5) would drop too many entries
        // when the device reconnects.
        assertEquals(100, BackupPrefs.MAX_PENDING)
    }

    private fun readSource(filename: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/backup/$filename",
            "../app/src/main/java/org/mindanchor/backup/$filename",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
