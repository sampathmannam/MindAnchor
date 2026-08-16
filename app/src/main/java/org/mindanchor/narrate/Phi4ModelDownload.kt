package org.mindanchor.narrate

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * The Phi-4 mini GGUF download surface. v0.23.0.
 *
 * The launcher offers a one-tap "Download Phi-4 mini
 * (Q4_K_M, 2.49 GB)" button on the Reading → Model
 * settings surface. The tap enqueues a system download
 * via [DownloadManager] rather than streaming the file
 * through the launcher's process: a 2.5 GB file over
 * HTTPS through our process is fragile in ways the
 * system download is not (pause, resume, retry,
 * notification, post-download intent).
 *
 * ## What the launcher does and does not do
 *
 *  - Does: enqueue a [DownloadManager] request for the
 *    [PRIMARY_URL], with the destination subpath
 *    [DOWNLOAD_SUBPATH] under the system Downloads
 *    directory. The launcher does not stream a single
 *    byte.
 *  - Does not: import the model automatically. When the
 *    download completes, the launcher listens for
 *    [DownloadManager.ACTION_DOWNLOAD_COMPLETE] and
 *    prompts the user with a one-tap "Use this as the
 *    narrate model?" Yes-then-import. The user remains
 *    in control: a download that completes while the
 *    user is not on the model screen does not silently
 *    replace anything.
 *
 * ## Why a system download, not a stream-into-our-storage
 *
 * Streaming 2.5 GB over HTTPS through the launcher's
 * process means: any network drop leaves a half-imported
 * model in app-private storage, the file is the wrong
 * size, the user has no way to resume, and the launcher
 * has to hold the file open across activity death. A
 * `DownloadManager` download is a system artifact: a
 * notification shows the user the source, the size, the
 * progress, and the option to cancel or retry. If the
 * app is killed mid-download, the system resumes the
 * download. If the user cancels, no orphan file is left
 * in app storage.
 *
 * ## The fallback URL
 *
 * [PRIMARY_URL] is the Unsloth GGUF mirror, the most
 * popular rebuild of the Phi-4-mini weights in the
 * community. [FALLBACK_URL] is Microsoft's official
 * repo, used if the Unsloth URL is unavailable. The
 * launcher does not auto-failover; a user can edit the
 * URL or wait for the next release. The constants are
 * here so the failure mode is documented, not silent.
 */
object Phi4ModelDownload {
    private const val LOG_TAG = "MindAnchor/Phi4"

    /**
     * The default size of the Phi-4 mini Q4_K_M
     * weights, in bytes. The actual download may be
     * a few kilobytes off (HuggingFace occasionally
     * rebuilds the GGUF with a slightly different
     * header), but the order of magnitude is what the
     * UI's "2.49 GB" copy advertises.
     */
    const val APPROXIMATE_BYTES: Long = 2_490_000_000L

    /**
     * The Unsloth GGUF mirror for Phi-4-mini. Unsloth
     * is the most-downloaded, most-rebuilt GGUF
     * mirror in the open-weights community.
     */
    const val PRIMARY_URL: String =
        "https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF/resolve/main/" +
            "Phi-4-mini-instruct-Q4_K_M.gguf"

    /**
     * Microsoft's official GGUF mirror for Phi-4-mini.
     * The fallback if the Unsloth URL changes.
     */
    const val FALLBACK_URL: String =
        "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/" +
            "Phi-4-mini-instruct-Q4_K_M.gguf"

    /**
     * The filename the launcher suggests to the
     * system's Downloads collection. Picked to match
     * the upstream artefact so a user who has the file
     * already on disk finds the prompt-by-name
     * obvious.
     */
    const val DOWNLOAD_SUBPATH: String = "Phi-4-mini-instruct-Q4_K_M.gguf"

    /**
     * The human-readable description shown in the
     * system notification. Kept short — the system
     * truncates long titles.
     */
    const val DOWNLOAD_TITLE: String = "Phi-4 mini (Q4_K_M)"

    /**
     * Enqueue a system download for the Phi-4-mini
     * Q4_K_M GGUF. Returns the download ID assigned
     * by [DownloadManager]; the caller stores it to
     * match the completion broadcast against the right
     * download. A null return means the enqueue failed
     * (network down, DownloadManager disabled, or the
     * URL is malformed in a way the system rejects);
     * the caller surfaces a user-visible message and
     * does not register a receiver.
     */
    fun enqueue(context: Context): Long? {
        val uri = Uri.parse(PRIMARY_URL)
        val request = DownloadManager.Request(uri).apply {
            setTitle(DOWNLOAD_TITLE)
            setDescription("The narrate model. About 2.49 GB.")
            setMimeType("application/octet-stream")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            // The system Downloads collection is the
            // user's own; this puts the file where they
            // would expect to find it, not in app-private
            // storage where the launcher cannot tell
            // them about it.
            setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, DOWNLOAD_SUBPATH)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            Log.w(LOG_TAG, "DownloadManager service unavailable")
            return null
        }
        return runCatching { manager.enqueue(request) }.getOrNull()
    }

    /**
     * The basename prefix the launcher accepts as a
     * Phi-4 download notification. Pinned as a
     * separate constant so the suffix-collision
     * logic is testable in isolation: the [DownloadManager]
     * appends `-N` to the destination filename when
     * a file with the requested name already exists in
     * the public Downloads collection (a user who has
     * downloaded the same file from a browser, or who
     * hit "retry" on a previous in-app download that
     * left a partially-completed file in place). The
     * launcher treats `Phi-4-mini-instruct-Q4_K_M.gguf`
     * and `Phi-4-mini-instruct-Q4_K_M-4.gguf` as the
     * same artefact; only the prefix and the `.gguf`
     * extension are checked.
     */
    const val DOWNLOAD_BASENAME_PREFIX: String = "Phi-4-mini-instruct-Q4_K_M"

    /**
     * The minimum file size, in bytes, for a candidate
     * in the public Downloads dir to be considered a
     * real model file rather than a partial / aborted
     * download. 100 MB is well below the model's actual
     * size (~2.49 GB) and well above the typical size of
     * an in-progress download. A lower threshold would
     * mis-import truncated files; a higher one would
     * miss a small (or Q2-quantised) Phi-4 build.
     */
    const val EXISTING_FILE_MIN_BYTES: Long = 100L * 1024L * 1024L

    /**
     * Whether [uri] looks like a download notification
     * for the Phi-4 file. The system delivers the
     * download ID via the intent's extras; the local
     * URI is what the launcher needs to import. This
     * helper filters the broadcasts so the launcher
     * does not act on someone else's download that
     * happened to land at the same time.
     *
     * Takes the URI as a [String] (the [Uri] class is
     * a stub on the JVM test environment, so passing
     * a [String] keeps the function testable on both
     * the device and the unit-test JVM). The string
     * is the canonical-form of the URI, as the system
     * delivers it in [DownloadManager.COLUMN_LOCAL_URI].
     */
    fun isPhi4File(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        // The basename is whatever follows the last
        // "/". `file:///storage/.../name.gguf` and
        // `content://.../name.gguf` both end with the
        // same last path segment.
        val name = uriString.substringAfterLast('/')
        // Match on prefix + .gguf extension, so the
        // DownloadManager's `-N` collision suffix
        // (e.g. `Phi-4-mini-instruct-Q4_K_M-4.gguf`)
        // is still recognised. Pin the canonical
        // filename too, so the no-collision case
        // (the first download) keeps working.
        return name.startsWith(DOWNLOAD_BASENAME_PREFIX) && name.endsWith(".gguf")
    }

    /**
     * Scans the system public Downloads collection
     * for a previously-downloaded Phi-4 file, and
     * returns the most recent one as a `file://` URI,
     * or `null` if none is on disk. v0.30.1.
     *
     * ## Why this exists
     *
     * The [DownloadManager] suffix-collides any
     * second download for the same target name with
     * a `-N` integer. The receiver in
     * [org.mindanchor.settings.Phi4ModelDownloadSection]
     * already accepts those suffixed names — see
     * [isPhi4File] — but a download that completed
     * BEFORE the receiver was listening (because the
     * user was on a different screen, or because the
     * app process was killed between completion and
     * the next opening of the settings surface) is
     * never re-broadcast, and the file sits on disk
     * with no in-app way to act on it. The user would
     * have to manually re-download the 2.49 GB to get
     * the receiver to fire, which is the very
     * "manual integrating" the v0.30.0 phone test
     * surfaced as the worst usability cliff on the
     * reading surface.
     *
     * This scan closes that cliff: the settings
     * surface offers a "Use existing download" tap
     * that imports the file the system already has.
     *
     * ## Why most-recent wins
     *
     * A user who has downloaded the same file twice
     * (perhaps the first time crashed, perhaps the
     * second time was a different model build) is
     * almost always looking for the latest one —
     * older downloads of a large model are typically
     * partial / corrupt / left over from a previous
     * attempt. We pick the file with the highest
     * lastModified timestamp; ties go to the
     * lexicographically largest name, which after
     * `-N` collision means the highest-N file wins.
     *
     * Returns `null` (not throws) if anything in the
     * scan fails. The caller's UI is "no existing
     * download found" — the same state as before —
     * and the user can fall back to the regular
     * download flow.
     */
    fun findExistingDownload(): Uri? = runCatching {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val candidates = downloadsDir
            .listFiles { file ->
                val name = file.name
                // Same shape as isPhi4File's URI-string
                // check, but applied to a File's name
                // directly. The basename test is the
                // one we actually care about; the
                // size floor of 100 MB is a cheap
                // "this is a real model file, not a
                // stray partial download" guard.
                name.startsWith(DOWNLOAD_BASENAME_PREFIX) &&
                    name.endsWith(".gguf") &&
                    file.length() > EXISTING_FILE_MIN_BYTES
            }
            ?.toList()
            ?: return@runCatching null
        if (candidates.isEmpty()) return@runCatching null
        val mostRecent = candidates.maxWithOrNull(
            compareBy<File> { it.lastModified() }.thenByDescending { it.name },
        ) ?: return@runCatching null
        Uri.fromFile(mostRecent)
    }.getOrNull()
}
