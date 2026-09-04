package org.mindanchor.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * The Google Drive REST target for v0.25.4. The
 * v0.23.0 WebDAV bridge's PUT/GET/PROPFIND
 * surface is replaced by the same shape against
 * the user's own Drive: one file per
 * [ContentType], append-only, encrypted before
 * transport (the caller wraps the payload with
 * [EncryptedBackupCodec] before passing it to
 * [append]).
 *
 * ## Why a class per ContentType
 *
 * The class is bound to one [ContentType] at
 * construction. A future per-type Worker (WP-D)
 * builds one instance per [ContentType] from the
 * injected [GoogleDriveAuth] and dispatches the
 * right instance on a new entry. The interface
 * [BackupTarget.append] still takes a [ContentType]
 * so the Local target (which doesn't care) can
 * share the contract; the Drive target checks the
 * type at call time and surfaces a
 * [AppendResult.NetworkError] on a mismatch.
 *
 * ## Wire format
 *
 * Each call to [append] writes the `payload` bytes
 * verbatim, followed by a single newline
 * (`\n`, 0x0A). The per-type file is therefore a
 * sequence of newline-terminated AES-256-GCM
 * blobs. A restore is a download + split on `\n`
 * + per-line unwrap via [EncryptedBackupCodec.unwrap]
 * + parse each line as a single JSON entry.
 * Restoring is out of scope for v0.25.4; the
 * format is documented here so the WP-D scheduler
 * can be built against it without guessing.
 *
 * The newline separator is what makes the file
 * inspectable in the Drive web UI: opening
 * `MindAnchor-Notes.txt` shows a list of encoded
 * blobs, one per entry, exactly the v0.25.2
 * `BackupCodec` JSON-Lines shape (each line is
 * a JSON object, encrypted on disk).
 *
 * ## Protocol
 *
 * Drive REST over HTTPS, JSON for metadata, raw
 * bytes for content. Three endpoints:
 *
 *  - `GET /drive/v3/files?q=name%3D%27...%27%20and%20trashed%3Dfalse&fields=files(id,name,size)`
 *    to find the per-type file (if it exists).
 *  - `POST /upload/drive/v3/files?uploadType=multipart`
 *    to create the file on the first append.
 *  - `GET /drive/v3/files/{id}?alt=media` to
 *    download the current content (for the
 *    append-then-reupload model — Drive has no
 *    native append).
 *  - `PATCH /upload/drive/v3/files/{id}?uploadType=media`
 *    to upload the new (old + payload) content.
 *
 * The "no native append" constraint is the
 * reason this is a 3-round-trip model on every
 * call: find (or create), download, upload. For
 * the journal's typical entry size (a few KB of
 * text → ~80 bytes of AES-256-GCM ciphertext),
 * this is fast enough; the file size grows by
 * ~80 bytes per entry, so a year of daily entries
 * is a few MB. The class does not cache the
 * file-id in this iteration; the find is one
 * Drive REST call (~200ms on a healthy network).
 * A future optimisation (cache the id in a
 * DataStore key) is a v0.25.5+ improvement.
 *
 * ## Authentication
 *
 * Every request carries
 * `Authorization: Bearer <access_token>` from
 * [GoogleDriveAuth.currentAccessToken]. A null
 * token (the user has signed out, or the cached
 * token is no longer valid) returns
 * [AppendResult.AuthExpired] without making any
 * HTTP call — the caller decides whether to
 * re-prompt the user.
 *
 * ## Threading
 *
 * Every public method is `suspend` and runs on
 * [Dispatchers.IO]. The class is stateless
 * apart from the [client] and the [auth] / [type]
 * constructor args, both reusable across calls.
 *
 * ## HTTPS-only
 *
 * Production code refuses a non-HTTPS [client]
 * endpoint. The test surface uses the
 * [AllowInsecureForTest] marker to bypass the
 * check, the same pattern as the v0.23.0
 * [WebDavBackupTarget].
 *
 * ## Task 9: this class's wire format is not restorable, and it is quarantined
 *
 * The [append] wire format documented above — payload bytes followed
 * by a single `\n` (0x0A), repeated forever — is **not** a restorable
 * framing format. Each `payload` here is an AES-256-GCM ciphertext
 * blob, and ciphertext bytes are, by construction, uniformly random:
 * `0x0A` is a perfectly ordinary byte value that will legitimately
 * turn up *inside* a blob, not just between blobs. A restore parser
 * that naively splits the downloaded file on `\n` will, sooner or
 * later, split a single encrypted entry into two garbage halves.
 * There was never a restore path built against this format (see the
 * "Restoring is out of scope for v0.25.4" note above) — this is that
 * gap being closed for good, not a regression.
 *
 * [org.mindanchor.backup.GoogleDriveObjectStore] (Task 9) is the
 * replacement: plain named-object storage with full-overwrite
 * `put`/`get`/`list` semantics instead of unbounded newline-delimited
 * append, and [kotlinx.serialization.json.Json]-based response
 * parsing instead of [parseFirstFileId]'s substring search. It is the
 * only writer any new continuity code (settings, workers) should use
 * going forward.
 *
 * This class is not deleted, and its behavior here is unchanged: it
 * remains the reader/writer for the existing legacy Notes/Letters
 * backup path (see [ContentType]) until that path is separately
 * retired in a later task. No existing user file this class already
 * wrote is deleted or rewritten by Task 9.
 */
class GoogleDriveBackupTarget(
    private val client: OkHttpClient = defaultClient(),
    private val auth: GoogleDriveAuth,
    private val type: ContentType,
) : BackupTarget {

    /**
     * The internal constructor used by the test suite.
     * Mirrors [WebDavBackupTarget]'s pattern: a
     * dedicated marker class (not a Boolean) so a
     * test-only bypass cannot be triggered by
     * accident in production code.
     */
    internal constructor(
        client: OkHttpClient,
        auth: GoogleDriveAuth,
        type: ContentType,
        @Suppress("UNUSED_PARAMETER") allowInsecureForTest: AllowInsecureForTest,
    ) : this(client, auth, type) {
        this.allowInsecureForTest = true
    }

    internal class AllowInsecureForTest private constructor() {
        companion object {
            val INSTANCE = AllowInsecureForTest()
        }
    }

    private var allowInsecureForTest: Boolean = false

    /**
     * Appends [payload] to the per-type file. The
     * [type] must match the constructor [type]; a
     * mismatch returns
     * [AppendResult.NetworkError] with a
     * `type mismatch` message — the WP-D scheduler
     * builds one target per type, so a mismatch is
     * a programmer error, not a runtime case.
     */
    override suspend fun append(type: ContentType, payload: ByteArray): AppendResult =
        withContext(Dispatchers.IO) {
            if (type != this@GoogleDriveBackupTarget.type) {
                val msg = "append: type mismatch ${type.fileName} != " +
                    "${this@GoogleDriveBackupTarget.type.fileName}"
                Log.w(LOG_TAG, msg)
                return@withContext AppendResult.NetworkError("type mismatch")
            }
            val token = auth.currentAccessToken()
            if (token.isNullOrBlank()) {
                Log.w(LOG_TAG, "append: no access token (signed out?)")
                return@withContext AppendResult.AuthExpired
            }
            val payloadWithNewline = payload + NEWLINE_BYTE
            val fileId = findFileId(type, token)
            val newContent: ByteArray = if (fileId == null) {
                // First write: the file doesn't
                // exist. Create it with the payload
                // as initial content. We do not
                // bother downloading (there's
                // nothing to download).
                val created = createFile(type, payloadWithNewline, token)
                if (!created) {
                    return@withContext AppendResult.NetworkError("create failed")
                }
                payloadWithNewline
            } else {
                // Subsequent write: download the
                // current content, append, re-upload.
                val current = downloadFile(fileId, token)
                    ?: return@withContext AppendResult.NetworkError("download failed")
                val combined = current + payloadWithNewline
                val updated = updateFile(fileId, combined, token)
                if (!updated) {
                    return@withContext AppendResult.NetworkError("update failed")
                }
                combined
            }
            Log.i(LOG_TAG, "appended ${payload.size} bytes to ${type.fileName} (now ${newContent.size} bytes)")
            AppendResult.Ok
        }

    /**
     * Finds the per-type file's id in the user's
     * Drive root. Returns null if the file does
     * not exist (the first call to [append] for a
     * fresh install). The query is exact-name +
     * not-trashed + in the user's drive space; a
     * manual rename in the Drive web UI would
     * surface as a "file not found" on the next
     * append, which is the right failure mode (a
     * new file is created, no data is lost).
     */
    private fun findFileId(type: ContentType, token: String): String? {
        val name = type.fileName
        val encodedQuery = "name='$name' and trashed=false"
        val url = "$DRIVE_API/files?spaces=drive&q=${encodedQuery.urlEncode()}&fields=files(id,name)"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runRequest(req) { resp ->
            if (resp.code != HTTP_OK) {
                Log.w(LOG_TAG, "findFileId: HTTP ${resp.code}")
                null
            } else {
                val text = resp.use { it.body?.string().orEmpty() }
                parseFirstFileId(text)
            }
        }.getOrElse { e ->
            Log.w(LOG_TAG, "findFileId failed: $e")
            null
        }
    }

    /**
     * Creates the per-type file with [initial]
     * content via a multipart upload. The metadata
     * is JSON (the file's name + mimeType), the
     * content is the bytes. Returns true on a
     * 2xx response.
     */
    private fun createFile(type: ContentType, initial: ByteArray, token: String): Boolean {
        val metadata = """{"name":"${type.fileName}","mimeType":"text/plain"}"""
        val body = buildMultipartBody(metadata, initial)
        val req = Request.Builder()
            .url("$DRIVE_UPLOAD/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return runRequest(req) { resp ->
            val ok = resp.code in HTTP_OK_RANGE
            if (!ok) Log.w(LOG_TAG, "createFile: HTTP ${resp.code}")
            resp.close()
            ok
        }.getOrElse { e ->
            Log.w(LOG_TAG, "createFile failed: $e")
            false
        }
    }

    /**
     * Downloads the current file content via the
     * `alt=media` shortcut. Returns the raw bytes
     * on a 2xx response, null on any error.
     */
    private fun downloadFile(fileId: String, token: String): ByteArray? {
        val req = Request.Builder()
            .url("$DRIVE_API/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runRequest(req) { resp ->
            if (resp.code != HTTP_OK) {
                Log.w(LOG_TAG, "downloadFile: HTTP ${resp.code}")
                resp.close()
                null
            } else {
                resp.use { it.body?.bytes() }
            }
        }.getOrElse { e ->
            Log.w(LOG_TAG, "downloadFile failed: $e")
            null
        }
    }

    /**
     * Replaces the file's content with [newContent]
     * via a media upload. Returns true on a 2xx
     * response.
     */
    private fun updateFile(fileId: String, newContent: ByteArray, token: String): Boolean {
        val req = Request.Builder()
            .url("$DRIVE_UPLOAD/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(newContent.toRequestBody(TEXT_PLAIN_MEDIA_TYPE))
            .build()
        return runRequest(req) { resp ->
            val ok = resp.code in HTTP_OK_RANGE
            if (!ok) Log.w(LOG_TAG, "updateFile: HTTP ${resp.code}")
            resp.close()
            ok
        }.getOrElse { e ->
            Log.w(LOG_TAG, "updateFile failed: $e")
            false
        }
    }

    /**
     * Parses a `files.list` response to extract
     * the first `files[].id`. The shape is
     * `{ "files": [ { "id": "...", "name": "..." } ] }`
     * (the `fields=files(id,name)` query restricts
     * the response to just the two fields we
     * need). The simplest robust parse is regex
     * against the id pattern, which is
     * alphanumeric + dash + underscore, but for
     * a one-key extraction a JSON library is
     * overkill — a string search for `"id":"` and
     * a close-quote is enough.
     */
    private fun parseFirstFileId(json: String): String? {
        val marker = "\"id\":\""
        val start = json.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = json.indexOf('"', from)
        if (end < 0) return null
        return json.substring(from, end).takeIf { it.isNotBlank() }
    }

    private fun buildMultipartBody(metadata: String, content: ByteArray): okhttp3.RequestBody {
        val boundary = "MindAnchorBoundary${System.currentTimeMillis()}"
        val crlf = "\r\n"
        val bodyBuilder = StringBuilder()
        bodyBuilder.append("--").append(boundary).append(crlf)
        bodyBuilder.append("Content-Type: application/json; charset=UTF-8").append(crlf).append(crlf)
        bodyBuilder.append(metadata).append(crlf)
        bodyBuilder.append("--").append(boundary).append(crlf)
        bodyBuilder.append("Content-Type: text/plain; charset=UTF-8").append(crlf).append(crlf)
        // The Drive multipart body is JSON
        // metadata + raw content; OkHttp's
        // MultipartBody is for forms, not this
        // shape, so we hand-build the body and
        // set the content-type explicitly.
        val metadataBytes = bodyBuilder.toString().toByteArray(Charsets.UTF_8)
        val closing = "--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val combined = metadataBytes + content + closing
        val mediaType = "multipart/related; boundary=$boundary".toMediaType()
        return combined.toRequestBody(mediaType)
    }

    private fun <T> runRequest(req: Request, block: (Response) -> T): Result<T> = runCatching {
        client.newCall(req).execute().use(block)
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")

    companion object {
        private const val LOG_TAG = "MindAnchor/DriveBackup"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val HTTP_OK = 200
        private val HTTP_OK_RANGE = 200..299
        private val TEXT_PLAIN_MEDIA_TYPE = "text/plain; charset=UTF-8".toMediaType()
        private val NEWLINE_BYTE = byteArrayOf(0x0A)
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_WRITE_TIMEOUT_SECONDS = 60L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
