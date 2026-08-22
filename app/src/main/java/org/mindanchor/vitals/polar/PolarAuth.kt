package org.mindanchor.vitals.polar

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * OAuth2 orchestration for the Polar AccessLink side-channel.
 *
 * Polar uses the standard **Authorization Code** flow:
 *
 *  1. The user signs in on `https://flow.polar.com/oauth2/authorization`
 *     (in a Custom Tab or the system browser) and the
 *     Polar auth server redirects to the launcher's
 *     registered redirect URI with `?code=...`.
 *  2. The launcher exchanges the code for an access
 *     token at `https://polarremote.com/v2/oauth/token`.
 *  3. The token is used in the `Authorization: Bearer
 *     <token>` header on every subsequent API call.
 *
 * ## Token lifecycle
 *
 * Polar's access tokens are 3-day TTL. There is no refresh
 * token — when the token expires the user is asked to
 * re-authorize. The launcher caches the token in
 * [PolarCredentialStore] (EncryptedSharedPreferences) and
 * in an in-memory [cached] reference, and re-uses it
 * across sync cycles until the TTL is up.
 *
 * ## Single-flight login
 *
 * Two concurrent [ensureAuthed] calls should not each
 * fire their own token exchange — the second caller
 * joins the first's [kotlinx.coroutines.CompletableDeferred]
 * and gets the same token back. The [inFlight] reference
 * is the same pattern the Coros bridge uses.
 *
 * @wording-reviewed — clinical-review-required. The
 * user-facing "Connected" / "Disconnected" surface is
 * clinical-review. Wording changes here must be
 * re-reviewed per docs/CLINICAL_REVIEW.md.
 */
class PolarAuth(
    private val context: Context,
    private val api: PolarApi = PolarApi(),
    private val store: PolarCredentialStore = PolarCredentialStore(context),
    private val nowMs: () -> Long = System::currentTimeMillis,
    /**
     * The Polar OAuth2 client credentials. v0.35.0
     * uses a single shared launcher-level client (no
     * per-user credential); a v0.36.0+ user-owned
     * client would be plumbed here.
     */
    private val clientId: String = DEFAULT_CLIENT_ID,
    private val clientSecret: String = DEFAULT_CLIENT_SECRET,
) {

    /**
     * The cached access token, or null when there is no
     * logged-in session. The cache is the in-memory mirror
     * of the encrypted-prefs copy — a process restart
     * re-reads from disk.
     */
    private val cached = AtomicReference<PolarCacheEntry?>(null)

    /**
     * The in-flight token exchange, when one is running.
     * Same single-flight pattern as [org.mindanchor.vitals.coros.CorosAuth].
     */
    private val inFlight = AtomicReference<kotlinx.coroutines.CompletableDeferred<PolarCacheEntry>?>(
        null,
    )

    /**
     * Returns a valid access token, or null when there
     * is no stored credential. When the cached token is
     * past its TTL, the launcher triggers a re-auth
     * which requires user interaction — the worker
     * surfaces that as a "stale token, sign in again"
     * UI state.
     */
    suspend fun ensureAuthed(): PolarCacheEntry? {
        val current = cached.get()
        if (current != null && current.expiresAtEpochMs > nowMs()) {
            return current
        }
        // No valid token on file. The token-exchange
        // path needs an authorization code, which only
        // the user can provide via the OAuth2 redirect.
        // The worker surfaces this by returning
        // Result.retry(); the next "Sync now" tap
        // invokes [loginWithCode] with a fresh code.
        return null
    }

    /**
     * Complete the OAuth2 flow: exchange [code] for an
     * access token, persist the token + the email
     * (the user typed it on the form) to the encrypted
     * store, and update the in-memory cache.
     *
     * Called from the system browser's redirect handler
     * after the user signs in on flow.polar.com.
     */
    suspend fun loginWithCode(
        code: String,
        email: String,
        password: String,
    ): PolarCacheEntry {
        val token = api.exchangeCode(
            clientId = clientId,
            clientSecret = clientSecret,
            code = code,
            redirectUri = REDIRECT_URI,
        )
        // Persist credentials + token.
        store.write(email, password)
        val expiresAt = nowMs() + ((token.expiresIn ?: 3L * 24L * 60L * 60L) * 1000L)
        store.writeAccessToken(token.accessToken, expiresAt)
        val entry = PolarCacheEntry(
            accessToken = token.accessToken,
            userId = token.userId ?: 0L,
            email = email,
            expiresAtEpochMs = expiresAt,
        )
        cached.set(entry)
        return entry
    }

    /**
     * Returns the current connection state the UI
     * should render.
     */
    fun connectionState(): PolarConnectionState {
        if (!store.isConnected()) return PolarConnectionState.NotConnected
        val email = store.read()?.first.orEmpty()
        val token = cached.get()
        val expiresAt = store.readAccessTokenExpiryEpochMs()
        val accessToken = store.readAccessToken()
        val entry = token ?: accessToken?.let {
            PolarCacheEntry(
                accessToken = it,
                userId = 0L,
                email = email,
                expiresAtEpochMs = expiresAt,
            )
        }
        return if (entry != null && entry.expiresAtEpochMs > nowMs()) {
            PolarConnectionState.Connected(
                email = email,
                accessTokenExpiresAtEpochMs = entry.expiresAtEpochMs,
            )
        } else if (accessToken != null) {
            // Token on file but expired — the user has
            // been here before, the next sync will need
            // a fresh sign-in.
            PolarConnectionState.TokenExpired(email = email)
        } else {
            PolarConnectionState.NotConnected
        }
    }

    /**
     * Wipes the in-memory token and the encrypted
     * credential store. The user invoked the
     * disconnect button; the launcher must not
     * silently hold on to the password.
     */
    fun disconnect() {
        cached.set(null)
        store.clear()
    }

    data class PolarCacheEntry(
        val accessToken: String,
        val userId: Long,
        val email: String,
        val expiresAtEpochMs: Long,
    )

    companion object {
        // v0.35.0: the Polar AccessLink self-serve client
        // registered at admin.polaraccesslink.com for the
        // MindAnchor app. A future release that switches to
        // a user-owned client would replace these with
        // values from secure storage.
        private const val DEFAULT_CLIENT_ID = "mindanchor-android"
        private const val DEFAULT_CLIENT_SECRET = ""

        // The redirect URI registered with the Polar
        // self-serve app. The system browser deep-links
        // back to the launcher's intent filter; the
        // launcher reads the `code` query parameter
        // and calls [loginWithCode].
        const val REDIRECT_URI = "mindanchor://polar-oauth-callback"
        const val AUTHORIZE_URL = "https://flow.polar.com/oauth2/authorization"
    }
}

sealed interface PolarConnectionState {
    data object NotConnected : PolarConnectionState
    data class Connected(
        val email: String,
        val accessTokenExpiresAtEpochMs: Long,
    ) : PolarConnectionState
    data class TokenExpired(val email: String) : PolarConnectionState
}
