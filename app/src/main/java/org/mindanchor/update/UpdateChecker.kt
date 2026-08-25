package org.mindanchor.update

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * v0.30+ (security audit 2026-08-24) — the silent
 * GitHub Releases check that shipped in v0.25.9 was
 * a privacy contract violation: the launcher's
 * privacy promise is "no path to the network
 * exists", but [UpdateChecker.check] made an
 * outbound GET to api.github.com on every cold
 * start. The [NetworkCallsForbiddenTest] gate had
 * the [okhttp3] import listed as forbidden outside
 * the documented subsystem allowlists; the
 * UpdateChecker was not in any of them, so the
 * test was failing silently.
 *
 * The audit's recommended fix is to move the update
 * affordance off the device. The launcher no
 * longer checks for updates; the user opens the
 * release page in a browser. This keeps the
 * privacy promise clean: the launcher itself makes
 * no outbound network call. The "Check for
 * updates" button in Settings → About now opens
 * the [RELEASES_URL] in the user's default
 * browser.
 *
 * ## Why a browser hand-off
 *
 * A browser hand-off is the smaller change and
 * matches the rest of the launcher's posture
 * (Going Light is a local VPN, the LLM bridge is
 * OkHttp via the documented allowlist, the voice
 * journal is whisper.cpp on-device — all paths to
 * the network are either off-device, explicit,
 * and consent-gated). The UpdateChecker is the one
 * path that was implicit. Removing it makes the
 * privacy contract match the code.
 */
class UpdateChecker(private val appContext: Context) {

    /**
     * v0.30+ — open the [RELEASES_URL] in the user's
     * default browser. The launcher does not phone
     * home; the user does, with their own browser,
     * from their own network. This is a one-shot
     * Intent (no foreground service, no wake lock,
     * no telemetry).
     */
    fun openReleasesPage() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(RELEASES_URL),
        ).addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching {
            appContext.startActivity(intent)
        }
    }

    companion object {
        /**
         * The repo this app is published from.
         * Hard-coded because the project has a single
         * release channel. If the repo ever moves,
         * this constant moves with it.
         */
        const val REPO_OWNER = "sampathmannam"
        const val REPO_NAME = "MindAnchor"
        const val RELEASES_URL =
            "https://github.com/$REPO_OWNER/$REPO_NAME/releases"
    }
}

/**
 * v0.30+ (security audit 2026-08-24) — kept as a
 * marker type so the home-surface state-in signature
 * compiles. The previous [UpdateInfo] (with [version],
 * [url], [releaseName]) was the parsed GitHub
 * response; the new one is a no-op marker because
 * the launcher no longer makes an outbound call.
 * The [HomeSurface] snackbar that previously
 * rendered this value is now permanently suppressed
 * (the value is always null), so the type's
 * contents are never observed.
 */
data class UpdateInfo(
    val version: String = "",
    val url: String = "",
    val releaseName: String? = null,
)
