package org.mindanchor.vitals.coros

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Login orchestration and in-memory token caching for the COROS
 * Training Hub side-channel.
 *
 * Three properties this layer has to own at the same time:
 *
 *  1. **Re-login on expiry.** The web token is 24h TTL per the
 *     Training Hub contract; this class holds the token in memory
 *     and re-runs [CorosApi.login] with the stored credentials
 *     when the cached token is past its use-by time.
 *  2. **No double-login in one process.** When [login] is called
 *     concurrently, the second caller should not start a second
 *     `/account/login` request; instead it should wait for the
 *     first one. The [inFlight] [AtomicReference] does this
 *     without a mutex.
 *  3. **No token on disk.** The token is derived; only the
 *     credentials are persisted (in [CorosCredentialStore]).
 *     A process restart re-logs in on the next sync, which is
 *     cheap (one HTTPS round-trip) and keeps the disk
 *     footprint small.
 *
 * The class is the only place in the bridge that talks to
 * [CorosApi.login] directly. [CorosSyncWorker] calls
 * [ensureAuthed] before every dashboard / analyse / activity
 * fetch.
 *
 * @wording-reviewed — the user-facing "Connected" state is
 * the clinical-review surface: the user is told their COROS
 * password is held in Keystore-encrypted form, and the
 * disconnect path is the only way to clear it. Wording
 * changes here must be re-reviewed per docs/CLINICAL_REVIEW.md.
 *
 * @see docs/research/20 for the rationale on the side-channel.
 */
class CorosAuth(
    private val context: Context,
    private val api: CorosApi = CorosApi(),
    private val store: CorosCredentialStore = CorosCredentialStore(context),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    /**
     * The cached token, or null when there is no logged-in
     * session. Held in an [AtomicReference] because
     * [ensureAuthed] is called from a background worker and may
     * be re-entered across coroutines; the swap is the only
     * part that needs to be thread-safe.
     */
    private val cached = AtomicReference<CorosAuthPayload?>(null)

    /**
     * The in-flight login, when one is running. Held in an
     * [AtomicReference] so two concurrent [ensureAuthed] calls
     * do not each fire their own `/account/login` request —
     * the second caller joins the first's [kotlinx.coroutines.CompletableDeferred]
     * and gets the same payload back.
     */
    private val inFlight = AtomicReference<kotlinx.coroutines.CompletableDeferred<CorosAuthPayload>?>(null)

    /**
     * Returns the cached token if it is still within its TTL,
     * otherwise re-logs in. Returns null when there is no
     * stored credential, throws [CorosApiException] when the
     * re-login itself fails.
     *
     * Concurrent callers share a single in-flight login. A
     * successful login populates the cache; a failure clears
     * it (so the next call retries, rather than reusing a
     * known-bad token).
     */
    suspend fun ensureAuthed(): CorosAuthPayload? {
        val current = cached.get()
        if (current != null && (nowMs() - current.timestampMs) < TOKEN_TTL_MS) {
            return current
        }
        return loginFromStore()
    }

    /**
     * Reads the stored email + password, hashes the password,
     * and runs [CorosApi.login]. Updates the in-memory cache
     * on success. Throws when no credentials are present, or
     * when the API call returns a non-`0000` result code.
     */
    private suspend fun loginFromStore(): CorosAuthPayload? {
        val creds = store.read() ?: return null
        val region = store.region()
        return loginInternal(creds.first, creds.second, region)
    }

    /**
     * Logs in with the supplied credentials. The user's own
     * path (the Settings UI's "Connect" button) goes through
     * here rather than [loginFromStore] so the credentials can
     * be written to the store in the same call.
     */
    suspend fun loginWithCredentials(
        email: String,
        password: String,
        region: String,
    ): CorosAuthPayload {
        store.write(email, password, region)
        return loginInternal(email, password, region)
            ?: throw CorosApiException("login returned null", httpCode = null)
    }

    /**
     * Single-flight login. The [kotlinx.coroutines.CompletableDeferred]
     * is published to [inFlight] before the network call so a
     * concurrent caller sees it and joins.
     */
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun loginInternal(
        email: String,
        password: String,
        region: String,
    ): CorosAuthPayload? {
        val hash = CorosPasswordHasher.md5Hex(password)
        val existing = inFlight.get()
        if (existing != null) {
            return existing.await()
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<CorosAuthPayload>()
        if (!inFlight.compareAndSet(null, deferred)) {
            // Another caller raced past us; join theirs.
            return inFlight.get()?.await()
        }
        try {
            val payload = api.login(email, hash, region)
            cached.set(payload)
            deferred.complete(payload)
            return payload
        } catch (t: Throwable) {
            // A login failure must clear the cache so the
            // next call retries rather than reusing a
            // known-bad token. The deferred is completed
            // exceptionally so concurrent callers see the
            // same failure. The full exception propagates
            // up via `throw t`.
            cached.set(null)
            deferred.completeExceptionally(t)
            @Suppress("TooGenericExceptionCaught")
            throw t
        } finally {
            inFlight.set(null)
        }
    }

    /**
     * Wipes the in-memory token and the encrypted credential
     * store. The user invoked the disconnect button; the
     * launcher must not silently hold on to the password.
     */
    fun disconnect() {
        cached.set(null)
        store.clear()
    }

    /**
     * The [CorosConnectionState] the UI should render right now.
     *
     *  - [CorosConnectionState.NotConnected] when no credentials
     *    are on file.
     *  - [CorosConnectionState.Connected] when the in-memory
     *    token is still inside its TTL (the UI shows the email
     *    and region, the user is implicitly logged in).
     *  - [CorosConnectionState.AwaitingConsent] is only set by
     *    the UI between tapping "Connect" and the login
     *    round-trip returning. The settings screen flips the
     *    state itself; this method does not invent that state
     *    on the user's behalf.
     */
    fun connectionState(lastSyncEpochMs: Long?): CorosConnectionState {
        if (!store.isConnected()) return CorosConnectionState.NotConnected
        val email = store.read()?.first ?: return CorosConnectionState.NotConnected
        val region = store.region()
        val token = cached.get()
        // No in-memory token yet (process restart) is treated
        // as "connected but a fresh login is needed before
        // any fetch". The UI still shows the user as connected
        // — they did opt in — and the next sync re-logs in.
        if (token == null) {
            return CorosConnectionState.Connected(
                email = email,
                region = region,
                lastSyncEpochMs = lastSyncEpochMs ?: 0L,
            )
        }
        return CorosConnectionState.Connected(
            email = email,
            region = region,
            lastSyncEpochMs = lastSyncEpochMs ?: token.timestampMs,
        )
    }

    companion object {
        /**
         * The COROS Training Hub web token is documented as a
         * 24h TTL. We refresh at 23h as a soft margin so a
         * sync at 23:59 does not race a token that has
         * expired at 00:00.
         */
        private const val TOKEN_TTL_MS: Long = 23L * 60L * 60L * 1000L
    }
}
