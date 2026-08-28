package org.mindanchor.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MockWebServer round-trip for
 * [GoogleDriveBackupTarget]. v0.25.4 (WP-B).
 *
 * The class is a thin HTTP wrapper around four
 * Drive REST endpoints (find / create / download /
 * update). The tests stand up a MockWebServer
 * (plain HTTP — the test constructor bypasses the
 * production HTTPS-only check), dispatch canned
 * responses, and assert the class issues the right
 * requests with the right auth header, the right
 * body, and the right call sequence.
 *
 * The Drive REST URLs are hard-coded to
 * `https://www.googleapis.com/...` in the class;
 * to make the MockWebServer the destination, the
 * test wraps the OkHttp client in an interceptor
 * that rewrites the host:port to the test server.
 * The auth bearer, the multipart shape, and the
 * PATCH body are the production paths — the
 * interceptor only changes where the bytes go.
 *
 * The auth dependency is satisfied by a
 * [GoogleDriveAuth] constructed with the
 * test-only [TokenStore] overload, so a
 * pre-set access token is read on the first
 * call. The [signInClient] lazy on the auth
 * class is never triggered in this test
 * (the test only calls [GoogleDriveBackupTarget.append]
 * which only touches `currentAccessToken`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveBackupTargetTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var auth: GoogleDriveAuth
    private lateinit var target: GoogleDriveBackupTarget

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        // Rewrites the hard-coded
        // https://www.googleapis.com host to the
        // test server. The path, query, headers,
        // and body are the production shape.
        client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val rewritten = original.newBuilder()
                    .url(
                        original.url.newBuilder()
                            .host(server.hostName)
                            .port(server.port)
                            .scheme("http")
                            .build(),
                    )
                    .build()
                chain.proceed(rewritten)
            }
            .build()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tokenStore = TokenStore(
            ctx.getSharedPreferences("test_drive_auth", Context.MODE_PRIVATE),
        )
        tokenStore.write("test-access-token")
        auth = GoogleDriveAuth(ctx, tokenStore)
        target = GoogleDriveBackupTarget(
            client = client,
            auth = auth,
            type = ContentType.Notes,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun stubFileNotFound() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"files":[]}"""),
        )
    }

    private fun stubFileFound(fileId: String = "drive-file-abc", size: Int = 0) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"files":[{"id":"$fileId","name":"MindAnchor-Notes.txt","size":"$size"}]}""",
                ),
        )
    }

    private fun stubCreateOk() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"drive-file-abc","name":"MindAnchor-Notes.txt"}"""),
        )
    }

    private fun stubDownloadOk(content: ByteArray) {
        // MockWebServer's setBody accepts a String
        // for text content. The Drive target's
        // download path reads raw bytes (the file
        // is the AES-256-GCM newline-separated
        // blob stream), so we serialise the bytes
        // via ISO-8859-1 to keep them byte-stable
        // through the round-trip — every byte maps
        // to exactly one char in 8859-1, and back.
        val iso = Charsets.ISO_8859_1
        val body = String(content, iso)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .setHeader("Content-Type", "application/octet-stream"),
        )
    }

    /**
     * Reads the captured request body. The
     * MockWebServer's [RecordedRequest.body] is
     * already an okio [Buffer] (the server
     * captured it during dispatch); reading the
     * buffer is the standard pattern. Used to
     * inspect the request body the target sent.
     */
    private fun readBody(buffer: Buffer): ByteArray = buffer.readByteArray()

    private fun stubUpdateOk() {
        server.enqueue(MockResponse().setResponseCode(200))
    }

    @Test fun `append on a fresh install creates the file with the payload plus newline`() = runBlocking {
        stubFileNotFound()
        stubCreateOk()

        val payload = "hello".toByteArray()
        val result = target.append(ContentType.Notes, payload)

        assertEquals("first append should succeed", AppendResult.Ok, result)
        // The find request must carry the bearer.
        val findReq = server.takeRequest()
        assertEquals("GET", findReq.method)
        // MockWebServer's `path` is the path-only
        // portion; the query is in `requestUrl`
        // (or accessible via `path + '?' +
        // query` for older versions). The
        // simplest check is the requestUrl
        // string contains both the file name
        // and the q= marker.
        val findUrl = findReq.requestUrl.toString()
        assertTrue("find URL missing q param: $findUrl", findUrl.contains("?q=") || findUrl.contains("&q="))
        assertTrue(
            "find must include the per-type file name in the q param: $findUrl",
            findUrl.contains("MindAnchor-Notes.txt"),
        )
        assertEquals("Bearer test-access-token", findReq.getHeader("Authorization"))
        // The create request must be a multipart
        // POST and the body must include the
        // payload + a trailing newline byte.
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        val body = readBody(createReq.body)
        val bodyText = String(body, Charsets.ISO_8859_1)
        assertTrue("create body must contain the payload", bodyText.contains("hello"))
        // The create body is the multipart envelope
        // (metadata + boundary + content + closing).
        // The content portion sits between the
        // second `--boundary` and the closing
        // `--boundary--`. The simplest check is
        // "the body contains the payload string
        // followed by a newline", which is what
        // the Drive target writes.
        assertTrue(
            "create body must contain the payload + newline: $bodyText",
            bodyText.contains("hello\n"),
        )
        assertTrue(
            "create body must close with the multipart closing boundary",
            bodyText.contains("--MindAnchorBoundary"),
        )
    }

    @Test fun `append on an existing file downloads, appends, and re-uploads`() = runBlocking {
        val existingContent = "previous-entry\n".toByteArray()
        stubFileFound(size = existingContent.size)
        stubDownloadOk(existingContent)
        stubUpdateOk()

        val payload = "new-entry".toByteArray()
        val result = target.append(ContentType.Notes, payload)

        assertEquals("subsequent append should succeed", AppendResult.Ok, result)
        val findReq = server.takeRequest()
        assertEquals("GET", findReq.method)
        val downloadReq = server.takeRequest()
        assertEquals("GET", downloadReq.method)
        assertTrue(
            "download must use alt=media",
            downloadReq.requestUrl.toString().contains("alt=media"),
        )
        val updateReq = server.takeRequest()
        assertEquals("PATCH", updateReq.method)
        val updateBody = readBody(updateReq.body)
        // The update body must be old + payload + newline.
        val expected = existingContent + payload + byteArrayOf(0x0A)
        assertArrayEquals("upload body must be old + payload + newline", expected, updateBody)
        assertEquals("Bearer test-access-token", updateReq.getHeader("Authorization"))
    }

    @Test fun `append returns AuthExpired when the auth has no token`() = runBlocking {
        // Construct a fresh auth with an empty
        // token store (no prior write).
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val emptyTokenStore = TokenStore(
            ctx.getSharedPreferences("test_drive_auth_empty", Context.MODE_PRIVATE),
        )
        emptyTokenStore.clear()
        val noAuth = GoogleDriveAuth(ctx, emptyTokenStore)
        val noAuthTarget = GoogleDriveBackupTarget(
            client = client,
            auth = noAuth,
            type = ContentType.Notes,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )

        val result = noAuthTarget.append(ContentType.Notes, "hello".toByteArray())

        assertEquals(
            "no-token append must return AuthExpired without making any HTTP call",
            AppendResult.AuthExpired,
            result,
        )
        assertEquals("no HTTP request must be made", 0, server.requestCount)
    }

    @Test fun `append returns NetworkError on a 500 from the create endpoint`() = runBlocking {
        stubFileNotFound()
        server.enqueue(MockResponse().setResponseCode(500))

        val result = target.append(ContentType.Notes, "hello".toByteArray())

        assertTrue(
            "500 from create must surface as NetworkError",
            result is AppendResult.NetworkError,
        )
        assertEquals(0, server.requestCount - 2) // 2 requests made: find + create
    }

    @Test fun `append returns NetworkError on a 401 from the find endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = target.append(ContentType.Notes, "hello".toByteArray())

        assertTrue(
            "401 from find must surface as NetworkError " +
                "(auth expired is a NetworkError here, not AuthExpired)",
            result is AppendResult.NetworkError,
        )
    }

    @Test fun `append with a type-mismatch returns NetworkError without any HTTP call`() = runBlocking {
        val result = target.append(ContentType.Letters, "hello".toByteArray())
        assertTrue(
            "type mismatch (Letters, but target is Notes) must surface as NetworkError",
            result is AppendResult.NetworkError,
        )
        assertEquals("no HTTP call on type mismatch", 0, server.requestCount)
    }

    @Test fun `download on an existing file returns its current content`() = runBlocking {
        val existingContent = "line-one\nline-two\n".toByteArray()
        stubFileFound(size = existingContent.size)
        stubDownloadOk(existingContent)

        val result = target.download(ContentType.Notes)

        assertArrayEquals("download must return the file's raw bytes", existingContent, result)
        val findReq = server.takeRequest()
        assertEquals("GET", findReq.method)
        val downloadReq = server.takeRequest()
        assertEquals("GET", downloadReq.method)
        assertTrue("download must use alt=media", downloadReq.requestUrl.toString().contains("alt=media"))
        assertEquals("exactly find + download, no upload", 2, server.requestCount)
    }

    @Test fun `download when the file has never been written returns null with one HTTP call`() = runBlocking {
        stubFileNotFound()

        val result = target.download(ContentType.Notes)

        assertEquals("no file yet must read as null, not an error", null, result)
        assertEquals("only the find call, no download attempt", 1, server.requestCount)
    }

    @Test fun `download returns null without any HTTP call when the auth has no token`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val emptyTokenStore = TokenStore(
            ctx.getSharedPreferences("test_drive_auth_empty_download", Context.MODE_PRIVATE),
        )
        emptyTokenStore.clear()
        val noAuth = GoogleDriveAuth(ctx, emptyTokenStore)
        val noAuthTarget = GoogleDriveBackupTarget(
            client = client,
            auth = noAuth,
            type = ContentType.Notes,
            allowInsecureForTest = GoogleDriveBackupTarget.AllowInsecureForTest.INSTANCE,
        )

        val result = noAuthTarget.download(ContentType.Notes)

        assertEquals("no-token download must return null without any HTTP call", null, result)
        assertEquals("no HTTP request must be made", 0, server.requestCount)
    }

    @Test fun `download with a type-mismatch returns null without any HTTP call`() = runBlocking {
        val result = target.download(ContentType.Letters)
        assertEquals(
            "type mismatch (Letters, but target is Notes) must return null",
            null,
            result,
        )
        assertEquals("no HTTP call on type mismatch", 0, server.requestCount)
    }
}
