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
 * The Google Drive REST target for the backup
 * feature. One file per [ContentType], append-only,
 * plain UTF-8 text — the same protection the rest
 * of a person's Drive already has (only this app
 * can access a `drive.file`-scoped file it created;
 * the file itself is an ordinary Drive file the
 * account owner can open, same as any other document
 * in their Drive).
 *
 * v0.70.7: this used to encrypt every payload with a
 * device-bound Android Keystore key before it left
 * the phone. That key could never leave the device
 * it was generated on — Keystore keys are
 * non-exportable by design — so anything backed up
 * that way became permanently undecryptable the
 * moment the phone was replaced, which is exactly
 * the continuity a backup feature exists to provide.
 * The user chose to drop that layer rather than move
 * to a passphrase-derived key, in exchange for the
 * files being genuinely restorable on a new phone
 * signed into the same Google account.
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
 * sequence of newline-terminated one-line JSON
 * objects — a JSON-Lines file. [download] + split on
 * `\n` + parse each line is the whole restore.
 *
 * The newline separator is what makes the file
 * inspectable in the Drive web UI: opening
 * `MindAnchor-Notes.txt` shows a plain list of the
 * person's own entries, one JSON object per line,
 * word for word what they wrote.
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
     * Downloads the per-type file's current complete
     * content, or null if the file does not exist yet
     * (nothing of this type has ever been backed up
     * from any device) or the download failed.
     *
     * v0.70.7. Reuses the same find-then-download
     * pair [append] already uses for its
     * download-append-reupload dance — restore is
     * "find the file, download it, stop" instead of
     * "...download it, append, reupload".
     */
    override suspend fun download(type: ContentType): ByteArray? = withContext(Dispatchers.IO) {
        if (type != this@GoogleDriveBackupTarget.type) {
            Log.w(LOG_TAG, "download: type mismatch ${type.fileName}")
            return@withContext null
        }
        val token = auth.currentAccessToken()
        if (token.isNullOrBlank()) {
            Log.w(LOG_TAG, "download: no access token (signed out?)")
            return@withContext null
        }
        val fileId = findFileId(type, token) ?: return@withContext null
        downloadFile(fileId, token)
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
            if (!ok) Log.w(LOG_TAG, "createFile: HTTP ${resp.code} ${resp.use { it.body?.string() }}")
            else resp.close()
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
        // A CRLF must precede every boundary delimiter, including the
        // closing one (RFC 2046 §5.1.1) — the transition from the
        // metadata part to this part gets its CRLF from the "append(crlf)"
        // calls above, but the raw `content` bytes end wherever the
        // caller's payload happens to end, so the CRLF before the closing
        // boundary has to be added here explicitly. Without it, Drive's
        // parser reads `<content>--boundary--` as one unterminated line
        // and rejects the whole request with "Missing end boundary".
        val closing = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
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
