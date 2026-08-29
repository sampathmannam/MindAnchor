package org.mindanchor.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The Task 9 replacement for [GoogleDriveBackupTarget]'s append-only
 * per-type file. Where the legacy target grows one newline-delimited
 * file forever, this class treats Drive as plain named-object storage:
 * [put] fully replaces an object's content, [get] downloads it back
 * byte-for-byte, [list] enumerates objects by name prefix. See
 * [RemoteBackupStore] for the contract and [ContinuityFiles] for the
 * naming scheme this class is meant to be called with.
 *
 * ## Why kotlinx.serialization, not substring search
 *
 * [GoogleDriveBackupTarget.parseFirstFileId] extracts a Drive file id
 * with a `"id":"` substring search — correct only by luck, and fragile
 * against any whitespace or field-ordering change in Drive's response.
 * Every Drive response this class parses goes through a real
 * `kotlinx.serialization.json.Json` decoder and a typed
 * `@Serializable` shape instead.
 *
 * ## Authentication
 *
 * The current access token is supplied by [currentAccessToken], a
 * narrow `suspend () -> String?` function rather than a concrete
 * [GoogleDriveAuth] dependency — this is what lets the "no token, zero
 * HTTP calls" case be tested as a pure JVM unit test, no Android
 * [android.content.Context] or Robolectric required. Production
 * callers pass `auth::currentAccessToken`. A null/blank token returns
 * [RemoteResult.AuthExpired] before any request is built.
 *
 * ## Protocol
 *
 * Three Drive REST endpoints, the same ones [GoogleDriveBackupTarget]
 * already uses, called with different semantics:
 *
 *  - `GET /drive/v3/files?q=...&fields=files(id,name,size,modifiedTime)`
 *    — exact-name lookup (`put`/`get`) or prefix lookup (`list`).
 *  - `POST /upload/drive/v3/files?uploadType=multipart` — create,
 *    when [put]'s lookup finds no existing object.
 *  - `PATCH /upload/drive/v3/files/{id}?uploadType=media` — replace,
 *    when [put]'s lookup finds an existing object. The whole body is
 *    the new [put] `bytes` argument; there is no append, no download-
 *    then-reupload.
 *  - `GET /drive/v3/files/{id}?alt=media` — [get]'s download, once the
 *    lookup has resolved a name to an id.
 *
 * ## HTTP error mapping
 *
 * 401 → [RemoteResult.AuthExpired] (the caller should re-prompt
 * sign-in). 429 and 5xx → [RemoteResult.Retryable] (transient; worth
 * retrying later). Any other non-2xx → [RemoteResult.Permanent] (a
 * genuinely bad request; retrying will not help). A thrown
 * [IOException] (DNS failure, connection refused, socket timeout) is
 * also [RemoteResult.Retryable] — a network glitch is not a permanent
 * failure.
 */
class GoogleDriveObjectStore(
    private val client: OkHttpClient = defaultClient(),
    private val currentAccessToken: suspend () -> String?,
) : RemoteBackupStore {

    override suspend fun put(name: String, bytes: ByteArray): RemoteResult<RemoteObject> =
        withContext(Dispatchers.IO) {
            val token = currentAccessToken()
            if (token.isNullOrBlank()) {
                Log.w(LOG_TAG, "put: no access token (signed out?)")
                return@withContext RemoteResult.AuthExpired
            }
            val found = findExact(name, token)
            val existing = when (found) {
                is RemoteResult.Ok -> found.value
                is RemoteResult.AuthExpired -> return@withContext found
                is RemoteResult.Retryable -> return@withContext found
                is RemoteResult.Permanent -> return@withContext found
            }
            if (existing == null) create(name, bytes, token) else update(existing.id, bytes, token)
        }

    override suspend fun get(name: String): RemoteResult<ByteArray?> =
        withContext(Dispatchers.IO) {
            val token = currentAccessToken()
            if (token.isNullOrBlank()) {
                Log.w(LOG_TAG, "get: no access token (signed out?)")
                return@withContext RemoteResult.AuthExpired
            }
            val found = findExact(name, token)
            val existing = when (found) {
                is RemoteResult.Ok -> found.value
                is RemoteResult.AuthExpired -> return@withContext found
                is RemoteResult.Retryable -> return@withContext found
                is RemoteResult.Permanent -> return@withContext found
            } ?: return@withContext RemoteResult.Ok(null)
            when (val downloaded = download(existing.id, token)) {
                is RemoteResult.Ok -> RemoteResult.Ok(downloaded.value)
                is RemoteResult.AuthExpired -> downloaded
                is RemoteResult.Retryable -> downloaded
                is RemoteResult.Permanent -> downloaded
            }
        }

    override suspend fun list(prefix: String): RemoteResult<List<RemoteObject>> =
        withContext(Dispatchers.IO) {
            val token = currentAccessToken()
            if (token.isNullOrBlank()) {
                Log.w(LOG_TAG, "list: no access token (signed out?)")
                return@withContext RemoteResult.AuthExpired
            }
            // Drive has no native "starts with" query operator; `contains`
            // is the closest available and is what real-world Drive
            // integrations use for prefix-style matching.
            val query = "name contains '${prefix.escapeForDriveQuery()}' and trashed=false"
            when (val result = listQuery(query, token)) {
                is RemoteResult.Ok -> RemoteResult.Ok(result.value.map { it.toRemoteObject() })
                is RemoteResult.AuthExpired -> result
                is RemoteResult.Retryable -> result
                is RemoteResult.Permanent -> result
            }
        }

    /**
     * Exact-name lookup, used by both [put] and [get]. Returns the
     * matching [DriveFile] (or null — no error — when nothing
     * matches).
     */
    private fun findExact(name: String, token: String): RemoteResult<DriveFile?> {
        val query = "name='${name.escapeForDriveQuery()}' and trashed=false"
        return when (val result = listQuery(query, token)) {
            is RemoteResult.Ok -> RemoteResult.Ok(result.value.firstOrNull())
            is RemoteResult.AuthExpired -> result
            is RemoteResult.Retryable -> result
            is RemoteResult.Permanent -> result
        }
    }

    private fun listQuery(query: String, token: String): RemoteResult<List<DriveFile>> {
        val url = "$DRIVE_API/files?spaces=drive&q=${query.urlEncode()}" +
            "&fields=files(id,name,size,modifiedTime)"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return executeForResult(req, "listQuery") { resp ->
            json.decodeFromString<DriveFilesListResponse>(resp.body?.string().orEmpty()).files
        }
    }

    /**
     * Creates a new Drive object named [name] with [bytes] as its
     * full content. The metadata part of the multipart body is built
     * via [Json.encodeToString] on [DriveCreateMetadata] — never raw
     * string interpolation — so a name containing a `"` or `\`
     * character cannot corrupt the JSON.
     */
    private fun create(name: String, bytes: ByteArray, token: String): RemoteResult<RemoteObject> {
        val metadataJson = json.encodeToString(DriveCreateMetadata(name))
        val body = buildMultipartBody(metadataJson, bytes)
        val url = "$DRIVE_UPLOAD/files?uploadType=multipart&fields=id,name,size,modifiedTime"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return executeForResult(req, "create") { resp ->
            json.decodeFromString<DriveFile>(resp.body?.string().orEmpty()).toRemoteObject()
        }
    }

    /**
     * Replaces [fileId]'s content with [bytes] in full — a plain
     * media PATCH, no download-then-reupload. This is the "verified
     * object store" half of Task 9: no append-only growth, no risk of
     * a naive restore parser choking on an embedded `0x0A` byte inside
     * AES-GCM ciphertext (see [GoogleDriveBackupTarget]'s KDoc).
     */
    private fun update(fileId: String, bytes: ByteArray, token: String): RemoteResult<RemoteObject> {
        val url = "$DRIVE_UPLOAD/files/$fileId?uploadType=media&fields=id,name,size,modifiedTime"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .patch(bytes.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
            .build()
        return executeForResult(req, "update") { resp ->
            json.decodeFromString<DriveFile>(resp.body?.string().orEmpty()).toRemoteObject()
        }
    }

    private fun download(fileId: String, token: String): RemoteResult<ByteArray> {
        val req = Request.Builder()
            .url("$DRIVE_API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return executeForResult(req, "download") { resp -> resp.body?.bytes() ?: ByteArray(0) }
    }

    /**
     * Runs [req] and maps the outcome to a [RemoteResult]: a 2xx
     * response is decoded via [onSuccess] (called while the response
     * body is still open); 401 → [RemoteResult.AuthExpired]; 429 or
     * 5xx → [RemoteResult.Retryable]; any other non-2xx →
     * [RemoteResult.Permanent]; a thrown [IOException] (the request
     * never got an HTTP response at all) → [RemoteResult.Retryable].
     *
     * [opName] is logged on failure for diagnosis; it is a fixed
     * literal at every call site (`"create"`, `"download"`, ...),
     * never [req]'s body or the caller's `name`/`bytes` arguments.
     */
    private fun <T> executeForResult(req: Request, opName: String, onSuccess: (Response) -> T): RemoteResult<T> {
        val response = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            Log.w(LOG_TAG, "$opName failed: ${e.javaClass.simpleName}")
            return RemoteResult.Retryable("network_error")
        }
        response.use { resp ->
            return when {
                resp.code == HTTP_UNAUTHORIZED -> RemoteResult.AuthExpired
                resp.code == HTTP_TOO_MANY_REQUESTS || resp.code in HTTP_SERVER_ERROR_RANGE -> {
                    Log.w(LOG_TAG, "$opName: HTTP ${resp.code}")
                    RemoteResult.Retryable("http_${resp.code}")
                }
                resp.code in HTTP_OK_RANGE -> RemoteResult.Ok(onSuccess(resp))
                else -> {
                    // Temporary diagnostic (Program 0 real-device debugging
                    // session, 2026-08-29) — the status code alone doesn't
                    // say why Drive rejected the request; the response body
                    // for a non-2xx Drive API error is a JSON object with a
                    // human-readable `error.message`. Remove once root-caused.
                    Log.w(LOG_TAG, "$opName: HTTP ${resp.code} body=${resp.body?.string().orEmpty().take(500)}")
                    RemoteResult.Permanent("http_${resp.code}")
                }
            }
        }
    }

    private fun DriveFile.toRemoteObject(): RemoteObject = RemoteObject(
        id = id,
        name = name,
        size = size?.toLongOrNull() ?: 0L,
        modifiedTime = modifiedTime?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
    )

    private fun buildMultipartBody(metadataJson: String, content: ByteArray): RequestBody {
        val boundary = "MindAnchorObjectBoundary${System.nanoTime()}"
        val crlf = "\r\n"
        val header = buildString {
            append("--").append(boundary).append(crlf)
            append("Content-Type: application/json; charset=UTF-8").append(crlf).append(crlf)
            append(metadataJson).append(crlf)
            append("--").append(boundary).append(crlf)
            append("Content-Type: application/octet-stream").append(crlf).append(crlf)
        }.toByteArray(Charsets.UTF_8)
        val closing = "--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val combined = header + content + closing
        val mediaType = "multipart/related; boundary=$boundary".toMediaType()
        return combined.toRequestBody(mediaType)
    }

    /**
     * Escapes a value for embedding inside a Drive `q=` query literal
     * (the `'...'` part) per the Drive API's query-string rules:
     * backslash-escape backslashes and single quotes. This is separate
     * from, and applied before, the [urlEncode] that escapes the
     * *whole* query parameter for the URL itself.
     */
    private fun String.escapeForDriveQuery(): String = replace("\\", "\\\\").replace("'", "\\'")

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val LOG_TAG = "MindAnchor/DriveObjectStore"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_OK_RANGE = 200..299
        private val HTTP_SERVER_ERROR_RANGE = 500..599
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_WRITE_TIMEOUT_SECONDS = 60L

        private val json = Json { ignoreUnknownKeys = true }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * The `files.list` response shape, restricted by the `fields=` query
 * param to just the fields this class needs.
 */
@Serializable
private data class DriveFilesListResponse(val files: List<DriveFile> = emptyList())

/**
 * A single Drive file entry. Drive's REST API returns [size] as a
 * JSON string, not a number, and [modifiedTime] as an RFC3339 string —
 * both parsed into [RemoteObject]'s typed `Long`/`Instant` fields by
 * [GoogleDriveObjectStore.toRemoteObject].
 */
@Serializable
private data class DriveFile(
    val id: String,
    val name: String,
    val size: String? = null,
    val modifiedTime: String? = null,
)

/** The JSON metadata part of a Drive multipart create request. */
@Serializable
private data class DriveCreateMetadata(val name: String)
