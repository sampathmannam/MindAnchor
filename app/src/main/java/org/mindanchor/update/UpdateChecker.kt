package org.mindanchor.update

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * v0.25.9: silent GitHub releases check.
 *
 * Called once at app start (and again when the user taps the
 * "Check for updates" button in Settings → About). One GET
 * request to the public GitHub Releases API; no auth, no
 * telemetry. The response is short (a few KB) and cached for
 * 24h in [UpdatePrefs] so a user who opens the app many times
 * a day does not generate one request per open.
 *
 * Privacy: the only thing this sends to GitHub is the standard
 * `User-Agent` of the OkHttp client. No user data, no device
 * id, no app id beyond the package version. Documented in
 * docs/PRIVACY.md.
 */
class UpdateChecker(private val appContext: Context) {

    private val httpClient = OkHttpClient.Builder().build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Returns [UpdateInfo] when a newer release exists on
     * GitHub, or `null` otherwise (including on network error
     * — the check is best-effort and never blocks the launcher).
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentVersionName() ?: return@withContext null
        val release = fetchLatestRelease() ?: return@withContext null
        val latest = release.tag_name.removePrefix("v").removePrefix("V")
        if (isNewer(current, latest)) UpdateInfo(latest, release.html_url, release.name)
        else null
    }

    private fun currentVersionName(): String? = try {
        @Suppress("DEPRECATION")
        appContext.packageManager.getPackageInfo(
            appContext.packageName,
            0,
        ).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                runCatching { json.decodeFromString<GitHubRelease>(body) }.getOrNull()
            }
        } catch (e: IOException) {
            // Network unavailable or GitHub down — silent no-op.
            // We never throw from `check`.
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Naive semver-ish compare. Treats dots as separators and
     * compares each segment numerically; falls back to string
     * compare when the segment is not a number (e.g. pre-release
     * tags like `0.25.9-rc1`).
     */
    private fun isNewer(current: String, latest: String): Boolean {
        val currentParts = current.split(".", "-")
        val latestParts = latest.split(".", "-")
        val n = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until n) {
            val c = currentParts.getOrNull(i) ?: "0"
            val l = latestParts.getOrNull(i) ?: "0"
            val cn = c.toIntOrNull()
            val ln = l.toIntOrNull()
            when {
                cn != null && ln != null -> {
                    if (ln != cn) return ln > cn
                }
                cn == null && ln != null -> {
                    // current is a pre-release suffix; latest is a number — current is older
                    return false
                }
                cn != null && ln == null -> {
                    // current is a number; latest is a pre-release suffix — current is newer (or equal)
                    return false
                }
                else -> {
                    val cmp = l.compareTo(c)
                    if (cmp != 0) return cmp > 0
                }
            }
        }
        return false
    }

    companion object {
        /**
         * The repo this app is published from. Hard-coded because
         * the project has a single release channel. If the repo
         * ever moves, this constant moves with it.
         */
        const val REPO_OWNER = "sampathmannam"
        const val REPO_NAME = "MindAnchor"
        private const val API_URL =
            "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    }
}

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String? = null,
    val html_url: String,
)

/**
 * Result of an update check. [version] is the latest version
 * string on GitHub (without the `v` prefix); [url] is the
 * release page on github.com that the user should be sent to.
 * [releaseName] is the human-readable title (e.g. "v0.25.10 —
 * Daily letter, auto-update") used in the snackbar copy.
 */
data class UpdateInfo(
    val version: String,
    val url: String,
    val releaseName: String?,
)
