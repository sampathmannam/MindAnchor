package org.mindanchor.backup

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mindanchor.continuity.ContinuityFiles
import java.time.Instant

/**
 * MockWebServer coverage for [GoogleDriveObjectStore], the Task 9
 * replacement for [GoogleDriveBackupTarget]'s fragile append-only path.
 *
 * Unlike [GoogleDriveBackupTargetTest], this suite is plain JUnit — no
 * Robolectric. [GoogleDriveObjectStore] takes a `suspend () -> String?`
 * token provider instead of a [GoogleDriveAuth] instance, so the
 * "auth missing" case is a pure function call with no Android [Context]
 * involved. The Drive REST URLs are hard-coded to
 * `https://www.googleapis.com/...` in the class under test, exactly like
 * [GoogleDriveBackupTarget]; the same host-rewriting interceptor trick
 * from [GoogleDriveBackupTargetTest] points the client at the local
 * [MockWebServer] instead.
 */
class GoogleDriveObjectStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
    }

    @After
    fun tearDown() {
        // A socket-failure test shuts the server down itself (to
        // simulate an unreachable host); shutting it down twice throws.
        runCatching { server.shutdown() }
    }

    private fun storeWithToken(token: String?): GoogleDriveObjectStore =
        GoogleDriveObjectStore(client = client) { token }

    // --- Auth missing: zero HTTP calls -------------------------------

    @Test
    fun `put returns AuthExpired without any HTTP call when the token provider returns null`() = runBlocking {
        val store = storeWithToken(null)

        val result = store.put(ContinuityFiles.LATEST, "hello".toByteArray())

        assertEquals(RemoteResult.AuthExpired, result)
        assertEquals("no HTTP call must be made when auth is missing", 0, server.requestCount)
    }

    @Test
    fun `get returns AuthExpired without any HTTP call when the token provider returns null`() = runBlocking {
        val store = storeWithToken(null)

        val result = store.get(ContinuityFiles.LATEST)

        assertEquals(RemoteResult.AuthExpired, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `list returns AuthExpired without any HTTP call when the token provider returns null`() = runBlocking {
        val store = storeWithToken(null)

        val result = store.list(ContinuityFiles.SNAPSHOT_PREFIX)

        assertEquals(RemoteResult.AuthExpired, result)
        assertEquals(0, server.requestCount)
    }

    // --- Exact-name lookup: a real JSON parser, not substring search --

    @Test
    fun `exact-name lookup parses a pretty-printed, whitespace-heavy files-list response`() = runBlocking {
        // Realistic, non-minified Drive `files.list` body: newlines,
        // indentation, and extra space around punctuation. A fragile
        // hand-rolled `"id":"` substring search (the exact bug Task 9
        // replaces) is sensitive to this kind of formatting; a real
        // kotlinx.serialization parse is not.
        val prettyBody = """
            {
              "files": [
                {
                  "id"   :   "drive-object-id-123"   ,
                  "name" : "${ContinuityFiles.LATEST}",
                  "size": "42",
                  "modifiedTime": "2026-08-20T10:15:30Z"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(prettyBody))
        server.enqueue(MockResponse().setResponseCode(200).setBody("downloaded-bytes"))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Ok, got $result", result is RemoteResult.Ok<*>)
        assertArrayEquals("downloaded-bytes".toByteArray(), (result as RemoteResult.Ok<*>).value as ByteArray)
        val findReq = server.takeRequest()
        assertTrue(findReq.requestUrl.toString().contains(ContinuityFiles.LATEST))
        val downloadReq = server.takeRequest()
        assertTrue(
            "the id parsed out of the whitespace-heavy JSON must be used for the download",
            downloadReq.requestUrl.toString().contains("drive-object-id-123"),
        )
        assertTrue(downloadReq.requestUrl.toString().contains("alt=media"))
    }

    @Test
    fun `get returns Ok(null), not an error, when no file exists yet`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertEquals(RemoteResult.Ok<ByteArray?>(null), result)
    }

    // --- put(): create vs replace --------------------------------------

    @Test
    fun `put creates the latest file when none exists yet`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"new-file-id","name":"${ContinuityFiles.LATEST}","size":"11","modifiedTime":"2026-08-20T10:15:30Z"}""",
            ),
        )

        val store = storeWithToken("tok")
        val result = store.put(ContinuityFiles.LATEST, "hello-world".toByteArray())

        assertTrue("expected Ok, got $result", result is RemoteResult.Ok<*>)
        val obj = (result as RemoteResult.Ok<*>).value as RemoteObject
        assertEquals("new-file-id", obj.id)
        assertEquals(ContinuityFiles.LATEST, obj.name)
        assertEquals(11L, obj.size)
        assertEquals(Instant.parse("2026-08-20T10:15:30Z"), obj.modifiedTime)

        val findReq = server.takeRequest()
        assertEquals("GET", findReq.method)
        val createReq = server.takeRequest()
        assertEquals("POST", createReq.method)
        val bodyText = String(createReq.body.readByteArray(), Charsets.ISO_8859_1)
        assertTrue("create body must carry the payload bytes", bodyText.contains("hello-world"))
    }

    @Test
    fun `put replaces the latest file with full-overwrite semantics when one already exists`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"existing-id","name":"${ContinuityFiles.LATEST}","size":"3","modifiedTime":"2026-08-19T00:00:00Z"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"existing-id","name":"${ContinuityFiles.LATEST}","size":"9","modifiedTime":"2026-08-20T00:00:00Z"}""",
            ),
        )

        val store = storeWithToken("tok")
        val result = store.put(ContinuityFiles.LATEST, "new-bytes".toByteArray())

        assertTrue("expected Ok, got $result", result is RemoteResult.Ok<*>)
        val obj = (result as RemoteResult.Ok<*>).value as RemoteObject
        assertEquals("existing-id", obj.id)
        assertEquals(9L, obj.size)

        server.takeRequest() // the find
        val updateReq = server.takeRequest()
        assertEquals("PATCH", updateReq.method)
        assertTrue(
            "update must target the existing file's id, not create a new one",
            updateReq.requestUrl.toString().contains("existing-id"),
        )
        // Replace semantics: the update body is the new bytes only,
        // no append of old content and no multipart wrapping.
        assertArrayEquals("new-bytes".toByteArray(), updateReq.body.readByteArray())
    }

    // --- get(): byte-for-byte download ----------------------------------

    @Test
    fun `get downloads bytes unchanged, including bytes that are not valid UTF-8`() = runBlocking {
        val fileId = "drive-object-id-456"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"$fileId","name":"${ContinuityFiles.LATEST}","size":"5","modifiedTime":"2026-08-20T10:15:30Z"}]}""",
            ),
        )
        val binaryContent = byteArrayOf(0x00, 0x0A, 0xFF.toByte(), 0x7F, 0x01)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(binaryContent))
                .setHeader("Content-Type", "application/octet-stream"),
        )

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Ok, got $result", result is RemoteResult.Ok<*>)
        assertArrayEquals(binaryContent, (result as RemoteResult.Ok<*>).value as ByteArray)
    }

    // --- list(): versioned snapshots -------------------------------------

    @Test
    fun `list returns every matching snapshot with id, name, size, and modifiedTime populated`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "files": [
                    {"id":"snap-1","name":"MindAnchor-Continuity-Snapshot-20260101T000000Z-abc.mab","size":"100","modifiedTime":"2026-01-01T00:00:00Z"},
                    {"id":"snap-2","name":"MindAnchor-Continuity-Snapshot-20260102T000000Z-def.mab","size":"200","modifiedTime":"2026-01-02T00:00:00Z"}
                  ]
                }
                """.trimIndent(),
            ),
        )

        val store = storeWithToken("tok")
        val result = store.list(ContinuityFiles.SNAPSHOT_PREFIX)

        assertTrue("expected Ok, got $result", result is RemoteResult.Ok<*>)
        @Suppress("UNCHECKED_CAST")
        val objs = (result as RemoteResult.Ok<*>).value as List<RemoteObject>
        assertEquals(2, objs.size)
        assertEquals("snap-1", objs[0].id)
        assertEquals("MindAnchor-Continuity-Snapshot-20260101T000000Z-abc.mab", objs[0].name)
        assertEquals(100L, objs[0].size)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), objs[0].modifiedTime)
        assertEquals("snap-2", objs[1].id)
        assertEquals(200L, objs[1].size)
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), objs[1].modifiedTime)

        val listReq = server.takeRequest()
        assertTrue(
            "list must query by the prefix",
            listReq.requestUrl.toString().contains(ContinuityFiles.SNAPSHOT_PREFIX),
        )
    }

    // --- HTTP error mapping ------------------------------------------------

    @Test
    fun `HTTP 401 maps to AuthExpired, not Retryable or Permanent`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertEquals(RemoteResult.AuthExpired, result)
    }

    @Test
    fun `HTTP 429 maps to Retryable with a descriptive code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Retryable, got $result", result is RemoteResult.Retryable)
        assertTrue((result as RemoteResult.Retryable).code.contains("429"))
    }

    @Test
    fun `HTTP 500 maps to Retryable with a descriptive code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Retryable, got $result", result is RemoteResult.Retryable)
        assertTrue((result as RemoteResult.Retryable).code.contains("500"))
    }

    @Test
    fun `a socket failure (server unreachable) maps to Retryable with a descriptive code`() = runBlocking {
        server.shutdown() // nothing is listening on this host:port now

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Retryable, got $result", result is RemoteResult.Retryable)
        assertEquals("network_error", (result as RemoteResult.Retryable).code)
    }

    @Test
    fun `HTTP 403 maps to Permanent, not Retryable`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))

        val store = storeWithToken("tok")
        val result = store.get(ContinuityFiles.LATEST)

        assertTrue("expected Permanent, got $result", result is RemoteResult.Permanent)
    }

    // --- No user text outside the intended fields -------------------------

    @Test
    fun `put never places the name argument or the bytes payload anywhere but their intended fields`() = runBlocking {
        val secretMarker = "JOURNAL-PLAINTEXT-DO-NOT-LEAK"
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"files":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"new-id","name":"${ContinuityFiles.LATEST}","size":"1","modifiedTime":"2026-08-20T00:00:00Z"}""",
            ),
        )

        val store = storeWithToken("tok")
        // `name` is always a filename (ContinuityFiles.LATEST), never
        // Journal text; the (would-be encrypted) envelope is what
        // carries user content, and it belongs in `bytes` only.
        val result = store.put(ContinuityFiles.LATEST, secretMarker.toByteArray())
        assertTrue(result is RemoteResult.Ok<*>)

        val findReq = server.takeRequest()
        val createReq = server.takeRequest()
        for (req in listOf(findReq, createReq)) {
            for (headerName in req.headers.names()) {
                assertFalse(
                    "header $headerName leaked the marker: ${req.getHeader(headerName)}",
                    req.getHeader(headerName)?.contains(secretMarker) == true,
                )
            }
            assertFalse("the request URL leaked the marker", req.requestUrl.toString().contains(secretMarker))
        }
        // The marker legitimately appears once: inside the create
        // request's multipart body, in the content part (`bytes`). It
        // must never appear in the JSON metadata part of that same
        // body (the file's `name` field) -- that would be exactly the
        // "description" / logging mistake this test guards against.
        val createBodyText = String(createReq.body.readByteArray(), Charsets.ISO_8859_1)
        val metadataPart = createBodyText.substringBefore("Content-Type: application/octet-stream")
        assertFalse(
            "the JSON metadata part must never contain the marker: $metadataPart",
            metadataPart.contains(secretMarker),
        )
        assertTrue(
            "the marker must appear in the body somewhere (the content part)",
            createBodyText.contains(secretMarker),
        )
    }
}
