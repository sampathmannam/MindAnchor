package org.mindanchor.backup

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The WebDAV upload and download surface for backup
 * files. v0.23.0.
 *
 * The launcher has one and only one opt-in outbound
 * channel: this class, gated on the user enabling
 * auto-backup in Settings. The class is intentionally
 * narrow: it knows how to PUT, GET, and list `.enc`
 * files. It does not know how to read or write the
 * plaintext JSON — that is [BackupCodec] and
 * [EncryptedBackupCodec]'s job. The class also does
 * not own the credential store; the caller passes in
 * the URL, username, and password on every call, so
 * no secret sits in this class's instance state.
 *
 * ## Protocol
 *
 * WebDAV is HTTP with a few extra verbs (PROPFIND,
 * MKCOL, COPY, MOVE) and an XML body. The launcher
 * uses three of them:
 *
 *  - **PROPFIND** with `Depth: 0` to test the
 *    connection (does the directory exist, do the
 *    credentials work, is WebDAV enabled on the
 *    server).
 *  - **PROPFIND** with `Depth: 1` to list existing
 *    `.enc` files for the restore picker.
 *  - **PUT** to upload a freshly wrapped file.
 *  - **GET** to download a remote file for restore.
 *
 * No MKCOL: the user is responsible for creating the
 * remote directory before arming the bridge. The
 * one-time confirmation screen tells the user how.
 *
 * ## Authentication
 *
 * WebDAV over HTTPS with HTTP Basic Auth. The
 * "app-password" most WebDAV providers (Nextcloud,
 * ownCloud) issue for third-party clients is exactly
 * what the launcher expects. The launcher never sends
 * the password in plain headers over plain HTTP —
 * [isHttps] refuses a non-https URL outright.
 *
 * ## Threading
 *
 * Every public method is `suspend`-free and blocks on
 * the OkHttp call. The caller wraps the call in
 * `withContext(Dispatchers.IO)`. The class is
 * stateless and the [client] is reusable across calls.
 */
class WebDavBackupTarget(
    private val client: OkHttpClient = defaultClient(),
) {

    /**
     * The internal constructor used by the test suite.
     *
     * Production code always goes through the public
     * constructor, which uses the default HTTPS-only
     * behaviour. The test constructor lets the unit
     * test exercise the same HTTP code path against a
     * plain MockWebServer without going through TLS.
     * Marked `internal` so the test (in the same
     * module) can use it, but the rest of the app and
     * any third-party callers cannot bypass the
     * HTTPS-only contract.
     */
    internal constructor(
        client: OkHttpClient,
        @Suppress("UNUSED_PARAMETER") allowInsecureForTest: AllowInsecureForTest,
    ) : this(client) {
        this.allowInsecureForTest = true
    }

    /**
     * Marker class for the test-only constructor. The
     * use of a dedicated type (rather than a Boolean
     * parameter) means the call site is self-documenting
     * and cannot be triggered by accident.
     */
    internal class AllowInsecureForTest private constructor() {
        companion object {
            val INSTANCE = AllowInsecureForTest()
        }
    }

    private var allowInsecureForTest: Boolean = false

    /**
     * The result of a test-connection PROPFIND.
     *
     *  - [Ok] means the directory exists, the
     *    credentials work, and WebDAV is enabled.
     *  - [Unauthorized] means the credentials are
     *    wrong (HTTP 401).
     *  - [NotFound] means the directory does not exist
     *    or the server is not WebDAV-enabled (HTTP
     *    404 / 405).
     *  - [Insecure] means the URL is not https:// —
     *    the launcher refuses to send the password.
     *  - [NetworkError] wraps everything else (DNS,
     *    TLS, IO).
     */
    sealed class TestResult {
        data object Ok : TestResult()
        data object Unauthorized : TestResult()
        data object NotFound : TestResult()
        data object Insecure : TestResult()
        data class NetworkError(val message: String) : TestResult()
    }

    /**
     * A remote backup file as listed by the
     * `Depth: 1` PROPFIND.
     *
     * @param name the basename (`mindanchor-backup-2026-08-10.enc`).
     * @param size the file size in bytes, or null if
     * the server did not report one.
     * @param lastModified the file's last-modified
     * timestamp in epoch millis, or null if the server
     * did not report one.
     */
    data class RemoteBackup(
        val name: String,
        val size: Long?,
        val lastModified: Long?,
    )

    /**
     * Tests the connection by issuing a `PROPFIND` with
     * `Depth: 0` against [baseUrl] using [username] /
     * [password]. Returns a [TestResult] indicating
     * the outcome. The caller surfaces the result to
     * the user.
     */
    fun testConnection(baseUrl: String, username: String, password: String): TestResult {
        if (!isHttps(baseUrl)) return TestResult.Insecure
        val req = propfindRequest(baseUrl, username, password, depth = DEPTH_ZERO, body = null)
        return runRequest(req) { resp -> verdictForStatus(resp.code) }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.w(LOG_TAG, "testConnection failed: $e")
                TestResult.NetworkError(e.message ?: "network error")
            },
        )
    }

    /**
     * Lists the existing `.enc` backups at [baseUrl],
     * newest first. The list is filtered to entries
     * whose basename starts with `mindanchor-backup-`
     * and ends with `.enc`, so a user's other WebDAV
     * files are not surfaced.
     *
     * @return the list, or null on a network /
     * protocol error. The caller shows the error to
     * the user.
     */
    fun listBackups(baseUrl: String, username: String, password: String): List<RemoteBackup>? {
        if (!isHttps(baseUrl)) return null
        val req = propfindRequest(
            baseUrl,
            username,
            password,
            depth = DEPTH_ONE,
            body = PROPFIND_LIST_BODY,
        )
        return runRequest(req) { resp ->
            if (resp.code != HTTP_MULTISTATUS) {
                Log.w(LOG_TAG, "listBackups: HTTP ${resp.code}")
                null
            } else {
                val text = resp.use { it.body?.string().orEmpty() }
                parseMultistatus(text)
                    .filter { isBackupFile(it.name) }
                    .sortedByDescending { it.lastModified ?: 0L }
            }
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.w(LOG_TAG, "listBackups failed: $e")
                null
            },
        )
    }

    /**
     * Uploads [data] as [name] under [baseUrl]. The
     * caller is expected to have already wrapped
     * [data] with [EncryptedBackupCodec.wrap].
     *
     * @return true on HTTP 2xx success,
     * false otherwise (including network errors).
     */
    fun put(baseUrl: String, username: String, password: String, name: String, data: ByteArray): Boolean {
        if (!isHttps(baseUrl)) return false
        val req = Request.Builder()
            .url(joinUrl(baseUrl, name))
            .header("Authorization", basicAuth(username, password))
            .put(data.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
            .build()
        return runRequest(req) { resp ->
            val ok = resp.code in HTTP_OK_RANGE
            if (!ok) Log.w(LOG_TAG, "put $name: HTTP ${resp.code}")
            resp.close()
            ok
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.w(LOG_TAG, "put $name failed: $e")
                false
            },
        )
    }

    /**
     * Downloads the file at [baseUrl] + "/" + [name].
     * Returns the raw bytes, or null on a network /
     * protocol error or a non-2xx response.
     *
     * The caller is expected to feed the result into
     * [EncryptedBackupCodec.unwrap] before
     * [BackupCodec.decode]. The class does not do
     * either; the two are independent transforms
     * composed by the caller.
     */
    fun get(baseUrl: String, username: String, password: String, name: String): ByteArray? {
        if (!isHttps(baseUrl)) return null
        val req = Request.Builder()
            .url(joinUrl(baseUrl, name))
            .header("Authorization", basicAuth(username, password))
            .get()
            .build()
        return runRequest(req) { resp ->
            if (resp.code !in HTTP_OK_RANGE) {
                Log.w(LOG_TAG, "get $name: HTTP ${resp.code}")
                resp.close()
                null
            } else {
                resp.use { it.body?.bytes() }
            }
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                Log.w(LOG_TAG, "get $name failed: $e")
                null
            },
        )
    }

    private fun propfindRequest(
        url: String,
        username: String,
        password: String,
        depth: String,
        body: String?,
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", basicAuth(username, password))
            .header("Depth", depth)
        val requestBody = body?.toRequestBody(XML_MEDIA_TYPE) ?: EMPTY_XML_BODY
        return builder.method("PROPFIND", requestBody).build()
    }

    private fun verdictForStatus(code: Int): TestResult = when (code) {
        HTTP_MULTISTATUS -> TestResult.Ok
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> TestResult.Unauthorized
        HTTP_NOT_FOUND, HTTP_METHOD_NOT_ALLOWED -> TestResult.NotFound
        else -> TestResult.NetworkError("HTTP $code")
    }

    private fun <T> runRequest(req: Request, block: (Response) -> T): Result<T> = runCatching {
        client.newCall(req).execute().use(block)
    }

    private fun basicAuth(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(Charsets.ISO_8859_1)
        // RFC 7617: the user-pass is base64-encoded.
        // Most server implementations tolerate padding,
        // but Nextcloud in particular rejects the `=`
        // on the URL-safe variant. Without-padding
        // matches OkHttp's `Credentials.basic` output
        // and is the form a curl-style user would
        // expect to see if they were watching.
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(raw)
        return "Basic $encoded"
    }

    private fun isHttps(url: String): Boolean = allowInsecureForTest || url.startsWith("https://", ignoreCase = true)

    private fun joinUrl(base: String, name: String): String {
        val trimmed = if (base.endsWith("/")) base.dropLast(1) else base
        return "$trimmed/$name"
    }

    private fun isBackupFile(name: String): Boolean =
        name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX)

    private fun parseMultistatus(xml: String): List<RemoteBackup> {
        if (xml.isBlank()) return emptyList()
        // The PROPFIND multistatus is a well-defined
        // shape: a series of `<D:response>` blocks
        // (or `<response>` without a prefix), each
        // containing an `<href>`, optionally a
        // `<getcontentlength>` and `<getlastmodified>`.
        // Regex is robust against the namespace quirks
        // that bit the DOM parser — both prefixed and
        // unprefixed shapes, both orderings of the
        // children, and any whitespace.
        return runCatching {
            val responsePattern = RESPONSE_PATTERN
            buildList {
                for (match in responsePattern.findAll(xml)) {
                    val body = match.groupValues[1]
                    parseResponseBody(body)?.let { add(it) }
                }
            }
        }.getOrElse { e ->
            Log.w(LOG_TAG, "parseMultistatus failed: $e")
            emptyList()
        }
    }

    private fun parseResponseBody(body: String): RemoteBackup? {
        val href = HREF_PATTERN.find(body)?.groupValues?.get(1) ?: return null
        val name = href.trimStart('/').substringAfterLast('/')
        if (name.isBlank()) return null
        val size = SIZE_PATTERN.find(body)?.groupValues?.get(1)?.toLongOrNull()
        val modified = MODIFIED_PATTERN.find(body)?.groupValues?.get(1)?.let { parseHttpDate(it) }
        return RemoteBackup(name = name, size = size, lastModified = modified)
    }

    private fun parseHttpDate(text: String): Long? {
        val cleaned = text.trim()
        return HTTP_DATE_FORMATS.firstNotNullOfOrNull { fmt ->
            runCatching {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(cleaned)?.time
            }.getOrNull()
        }
    }

    companion object {
        private const val LOG_TAG = "MindAnchor/WebDav"
        private const val BACKUP_PREFIX = "mindanchor-backup-"
        private const val BACKUP_SUFFIX = ".enc"
        private const val DEPTH_ZERO = "0"

        private val RESPONSE_PATTERN = Regex(
            "<(?:\\w+:)?response\\b[^>]*>(.*?)</(?:\\w+:)?response>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val HREF_PATTERN = Regex(
            "<(?:\\w+:)?href\\b[^>]*>([^<]+)</(?:\\w+:)?href>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val SIZE_PATTERN = Regex(
            "<(?:\\w+:)?getcontentlength\\b[^>]*>(\\d+)</(?:\\w+:)?getcontentlength>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val MODIFIED_PATTERN = Regex(
            "<(?:\\w+:)?getlastmodified\\b[^>]*>([^<]+)</(?:\\w+:)?getlastmodified>",
            RegexOption.DOT_MATCHES_ALL,
        )
        private const val DEPTH_ONE = "1"
        private const val HTTP_MULTISTATUS = 207
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_METHOD_NOT_ALLOWED = 405
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private val EMPTY_XML_BODY = "".toRequestBody("application/xml".toMediaType())
        private const val PROPFIND_LIST_BODY =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<D:propfind xmlns:D=\"DAV:\">\n" +
                "  <D:prop>\n" +
                "    <D:getcontentlength/>\n" +
                "    <D:getlastmodified/>\n" +
                "  </D:prop>\n" +
                "</D:propfind>\n"
        private val HTTP_DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss zzz",
        )

        private val HTTP_OK_RANGE = 200..299
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_WRITE_TIMEOUT_SECONDS = 60L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
