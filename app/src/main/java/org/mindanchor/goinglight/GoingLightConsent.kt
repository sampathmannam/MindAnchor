package org.mindanchor.goinglight

import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * The OS-level consent gate for [GoingLightVpnService].
 *
 * Android requires the user to consent once before an app can establish
 * a [VpnService] interface. [VpnService.prepare] returns a non-null
 * [Intent] when consent is still required; the caller launches it
 * `forResult` and re-reads [hasConsent] when the system returns.
 *
 * ## Why this lives here, not in [org.mindanchor.settings]
 *
 * The project's no-outbound-network contract ([NetworkCallsForbiddenTest])
 * classifies files by *identity*, not by *pattern*: any file outside the
 * VpnService subsystem allowlist that imports an outbound network API
 * fails the build. The settings package is not in that allowlist, so
 * importing [VpnService] there would expand the privacy surface. This
 * wrapper is the single seam: it lives in the goinglight package (which
 * the allowlist covers) and exposes a one-method API the settings
 * Composable can call without seeing the network primitive.
 *
 * @wording-reviewed — the consent UX wording is on the calling
 * Composable (Settings → Going Light), which routes through
 * `strings.xml`. The wrapper is surface-neutral: it has no user-visible
 * text. Any change here is a settings-side call site, not wording.
 */
object GoingLightConsent {

    /**
     * True when the user has already granted the OS-level VPN consent
     * for this package. Re-checks on every call — the user can revoke
     * consent from system settings without this app being told, and a
     * cached "yes" is exactly the wrong answer in that case.
     */
    fun hasConsent(context: Context): Boolean = VpnService.prepare(context) == null

    /**
     * The [Intent] the system expects the launching Activity to start
     * `forResult`, or null when consent is already granted (in which
     * case the caller can skip the dialog and go straight to enabling).
     *
     * The caller is responsible for starting the returned intent and
     * for re-reading [hasConsent] when the result returns; this helper
     * does not start anything itself.
     */
    fun prepareConsent(context: Context): Intent? = VpnService.prepare(context)
}
