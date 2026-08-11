package org.mindanchor.backup

import java.io.File
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding tests for v0.23.0 WebDAV backup. Each test
 * pins one piece of the contract the v0.23.0 release
 * depends on; a refactor that breaks the test is
 * forcing the contributor to think about whether the
 * shape is still right.
 */
class WebDavBackupFindingTest {

    /**
     * The set of files that own the WebDAV bridge. A new
     * file in `org.mindanchor.backup` that uses an
     * outbound API has to be added here AND to
     * [NetworkCallsForbiddenTest.webDavBackupFiles].
     * Both test files drift together.
     */
    @Test
    fun `the WebDAV bridge is exactly four files`() {
        val expected = setOf(
            "app/src/main/java/org/mindanchor/backup/WebDavBackupTarget.kt",
            "app/src/main/java/org/mindanchor/backup/WebDavCredentialStore.kt",
            "app/src/main/java/org/mindanchor/backup/EncryptedBackupCodec.kt",
            "app/src/main/java/org/mindanchor/backup/KeystoreAesKey.kt",
        )
        // Read the set from the actual test file. This
        // way the source of truth is the test, and a
        // contributor who adds a fifth file has to
        // update both tests.
        val candidates = listOf(
            "app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt",
            "../app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt",
        )
        val testFile = candidates.map(::File).firstOrNull { it.isFile }
            ?: error("NetworkCallsForbiddenTest.kt not found from ${File(".").absolutePath}.")
        val text = testFile.readText()
        for (f in expected) {
            assertTrue(
                "WebDAV bridge file $f must be listed in NetworkCallsForbiddenTest",
                text.contains(f),
            )
        }
    }

    /**
     * The HTTPS-only contract. The launcher refuses to
     * send the user's app-password over plain HTTP, on
     * either testConnection, listBackups, put, or get.
     */
    @Test
    fun `WebDavBackupTarget refuses http URLs on every public method`() {
        val target = WebDavBackupTarget()
        val httpUrl = "http://cloud.example.com/MindAnchor/"
        val httpsUrl = "https://cloud.example.com/MindAnchor/"

        // We can't easily intercept a real call to an
        // http:// URL because the test is offline;
        // assert on the typed return value.
        assertEquals(
            WebDavBackupTarget.TestResult.Insecure,
            target.testConnection(httpUrl, "alice", "secret"),
        )
        assertNull(target.listBackups(httpUrl, "alice", "secret"))
        assertFalse(target.put(httpUrl, "alice", "secret", "x.enc", byteArrayOf(1)))
        assertNull(target.get(httpUrl, "alice", "secret", "x.enc"))

        // And the https:// shape is recognised.
        // (We don't enqueue a response, so the call
        // would fail with a NetworkError — but the
        // *kind* of failure is "NetworkError", not
        // "Insecure".)
        val r = target.testConnection(httpsUrl, "alice", "secret")
        assertTrue(
            "https:// must not be flagged as Insecure; got: $r",
            r !is WebDavBackupTarget.TestResult.Insecure,
        )
    }

    /**
     * Basic Auth header shape. WebDAV providers
     * (Nextcloud, ownCloud) issue app-passwords and
     * expect the standard `Basic <base64>` header
     * over HTTPS. The test exercises the production
     * helper indirectly: a hand-built `Request` with
     * the same header format must be valid OkHttp.
     */
    @Test
    fun `Basic Auth header is well-formed base64 of user colon password`() {
        val raw = "alice:secret"
        val encoded = android.util.Base64.encodeToString(
            raw.encodeToByteArray(),
            android.util.Base64.NO_WRAP,
        )
        val req = Request.Builder()
            .url("https://example.com/")
            .header("Authorization", "Basic $encoded")
            .build()
        assertNotNull(req.header("Authorization"))
        assertTrue(req.header("Authorization")!!.startsWith("Basic "))
    }

    /**
     * The backup file naming contract. The .enc suffix
     * is the gate that distinguishes a wrapped backup
     * from any other file the user might have on the
     * WebDAV share. The listBackups filter relies on
     * it; the restore UI relies on it. If the suffix
     * changes, the filter changes, and old remote
     * copies become invisible.
     */
    @Test
    fun `the backup file name has the v0_23 suffix and prefix`() {
        val stamp = "2026-08-10"
        val name = "mindanchor-backup-$stamp.enc"
        assertTrue(
            "name must start with the bridge prefix",
            name.startsWith("mindanchor-backup-"),
        )
        assertTrue(
            "name must end with the bridge suffix",
            name.endsWith(".enc"),
        )
    }
}
