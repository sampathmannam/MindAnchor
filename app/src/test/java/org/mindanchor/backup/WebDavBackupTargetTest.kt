package org.mindanchor.backup

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [WebDavBackupTarget].
 *
 * The class is a thin HTTP wrapper around a small set
 * of WebDAV verbs. The tests stand up a MockWebServer
 * (plain HTTP — the test constructor bypasses the
 * production HTTPS-only check), dispatch canned
 * responses, and assert the class parses them, encodes
 * the right request, and returns the right verdict.
 *
 * The HTTPS-only contract is exercised by the
 * [WebDavBackupFindingTest] and the
 * `testConnection returns Insecure for an http URL`
 * case below. The plain-HTTP path is exercised here.
 */
class WebDavBackupTargetTest {

    private lateinit var server: MockWebServer
    private lateinit var target: WebDavBackupTarget

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // The test-only constructor bypasses the
        // production HTTPS-only check, so the test
        // can hit the MockWebServer's plain HTTP
        // endpoint. The HTTPS-only contract itself
        // is verified by `testConnection returns
        // Insecure for an http URL` and the
        // WebDavBackupFindingTest.
        target = WebDavBackupTarget(
            OkHttpClient(),
            WebDavBackupTarget.AllowInsecureForTest.INSTANCE,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/MindAnchor/").toString()

    // --- testConnection ----------------------------------------------------

    @Test
    fun `testConnection returns Ok on 207 multistatus`() {
        server.enqueue(MockResponse().setResponseCode(207))
        val result = target.testConnection(baseUrl(), "alice", "secret")
        assertEquals(WebDavBackupTarget.TestResult.Ok, result)
        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("0", recorded.getHeader("Depth"))
        assertEquals("Basic YWxpY2U6c2VjcmV0", recorded.getHeader("Authorization"))
    }

    @Test
    fun `testConnection returns Unauthorized on 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(
            WebDavBackupTarget.TestResult.Unauthorized,
            target.testConnection(baseUrl(), "alice", "wrong"),
        )
    }

    @Test
    fun `testConnection returns NotFound on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(
            WebDavBackupTarget.TestResult.NotFound,
            target.testConnection(baseUrl(), "alice", "secret"),
        )
    }

    @Test
    fun `testConnection returns Insecure for an http URL`() {
        // The test target bypasses the production
        // HTTPS check, so we cannot use the bypass
        // to test the Insecure verdict. Build a
        // production-shaped target here, with the
        // default client, and check that an http://
        // URL still returns Insecure.
        val productionTarget = WebDavBackupTarget()
        val httpUrl = baseUrl()  // MockWebServer's URL is http://
        assertEquals(
            WebDavBackupTarget.TestResult.Insecure,
            productionTarget.testConnection(httpUrl, "alice", "secret"),
        )
    }

    @Test
    fun `testConnection returns NetworkError on an unreachable host`() {
        // The server is shut down before the call; the URL points at it,
        // so the connection refuses.
        server.shutdown()
        val result = target.testConnection(baseUrl(), "alice", "secret")
        assertTrue(result is WebDavBackupTarget.TestResult.NetworkError)
    }

    // --- listBackups -------------------------------------------------------

    @Test
    fun `listBackups parses a multistatus response and filters by suffix`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/MindAnchor/mindanchor-backup-2026-08-10.enc</D:href>
                <D:propstat><D:prop>
                  <D:getcontentlength>12345</D:getcontentlength>
                  <D:getlastmodified>Sun, 10 Aug 2026 14:32:11 GMT</D:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/MindAnchor/random-file.txt</D:href>
              </D:response>
              <D:response>
                <D:href>/MindAnchor/mindanchor-backup-2026-08-09.enc</D:href>
                <D:propstat><D:prop>
                  <D:getcontentlength>22222</D:getcontentlength>
                  <D:getlastmodified>Mon, 09 Aug 2026 09:01:00 GMT</D:getlastmodified>
                </D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(207).setBody(xml))
        val result = target.listBackups(baseUrl(), "alice", "secret")
        assertNotNull(result)
        val files = result!!
        // Two .enc files, sorted newest first; the .txt is filtered out.
        assertEquals(2, files.size)
        assertEquals("mindanchor-backup-2026-08-10.enc", files[0].name)
        assertEquals(12345L, files[0].size)
        assertEquals("mindanchor-backup-2026-08-09.enc", files[1].name)
    }

    @Test
    fun `listBackups returns null on a non-207 response`() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(target.listBackups(baseUrl(), "alice", "wrong"))
    }

    @Test
    fun `listBackups returns null for an http URL`() {
        // Build a production-shaped target. The
        // bypass-target uses the bypass; we want to
        // check the production code path.
        val productionTarget = WebDavBackupTarget()
        val httpUrl = baseUrl()
        assertNull(productionTarget.listBackups(httpUrl, "alice", "secret"))
    }

    // --- put ---------------------------------------------------------------

    @Test
    fun `put returns true on 201 Created`() {
        server.enqueue(MockResponse().setResponseCode(201))
        val ok = target.put(baseUrl(), "alice", "secret", "test.enc", byteArrayOf(1, 2, 3))
        assertTrue(ok)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertTrue(recorded.path?.endsWith("/test.enc") == true)
        assertEquals(3, recorded.bodySize)
    }

    @Test
    fun `put returns false on 5xx`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertFalse(target.put(baseUrl(), "alice", "secret", "test.enc", byteArrayOf(1)))
    }

    @Test
    fun `put refuses an http URL`() {
        val productionTarget = WebDavBackupTarget()
        val httpUrl = baseUrl()
        assertFalse(productionTarget.put(httpUrl, "alice", "secret", "test.enc", byteArrayOf(1)))
    }

    // --- get ---------------------------------------------------------------

    @Test
    fun `get returns the response body bytes on 200`() {
        val body = okio.Buffer().write(byteArrayOf(0x10, 0x20, 0x30, 0x40))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(body),
        )
        val bytes = target.get(baseUrl(), "alice", "secret", "test.enc")
        assertNotNull(bytes)
        assertEquals(4, bytes!!.size)
        assertEquals(0x10.toByte(), bytes[0])
    }

    @Test
    fun `get returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(target.get(baseUrl(), "alice", "secret", "missing.enc"))
    }

    // --- shape -------------------------------------------------------------

    @Test
    fun `joinUrl trims a trailing slash and concatenates the name`() {
        server.enqueue(MockResponse().setResponseCode(201))
        val urlWithSlash = if (baseUrl().endsWith("/")) baseUrl() else baseUrl() + "/"
        assertTrue(target.put(urlWithSlash, "alice", "secret", "x.enc", byteArrayOf(0)))
        val recorded = server.takeRequest()
        assertTrue(
            "Path should not have a double slash, got: ${recorded.path}",
            recorded.path?.contains("//x.enc") != true,
        )
    }
}
