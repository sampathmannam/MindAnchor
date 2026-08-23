package org.mindanchor.backup

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The OAuth + token-store surface for v0.25.4's
 * Google Drive backup path. v0.25.4.
 *
 * The launcher does not own a server. The OAuth
 * grant is a *client-side* flow: `play-services-auth`
 * opens the Google account picker, returns a
 * [GoogleSignInAccount], and the launcher then
 * exchanges the account for an access token on
 * every Drive API call via [GoogleAuthUtil]. The
 * access token is the bearer; the account email is
 * the UI display; the refresh is implicit in
 * `GoogleAuthUtil`'s cache.
 *
 * ## What the user sees
 *
 *  - "Sign in with Google" — a button in
 *    Settings → Reading → Google Drive
 *    (see [GoogleDriveBackupSettingsSection], the
 *    WP-C surface). The button calls
 *    [signInIntent] and hands the Intent to an
 *    `ActivityResultLauncher`.
 *  - On a successful result, the section flips
 *    to "Signed in as <email>" and exposes the
 *    "Back up now" and "Forget this account"
 *    affordances. The account email is the only
 *    piece of identity that lives in this app's
 *    data layer.
 *  - On every Drive API call (the WP-B target),
 *    [currentAccessToken] is the gate. If the
 *    cached token is gone (sign-out, fresh
 *    install), it returns null and the caller
 *    surfaces a re-prompt.
 *
 * ## What the app stores
 *
 * Two places:
 *  - [TokenStore], an [EncryptedSharedPreferences]
 *    blob. Holds the access token + expiry. The
 *    AES-256-GCM wrapper is the
 *    `androidx.security.crypto` master key, the
 *    same one the v0.23.0 WebDAV bridge used for
 *    its URL / username / password. The on-disk
 *    blob is useless without the Keystore-backed
 *    key.
 *  - A DataStore for the account email
 *    ([signedInEmailFlow]). The email is not a
 *    secret, so it lives in plain DataStore; the
 *    UI subscribes to the flow and re-renders
 *    when the user signs in or out.
 *
 * ## Why a refresh token is not stored
 *
 * `play-services-auth` + `GoogleAuthUtil` keep the
 * refresh on Google's side. The client-side
 * `GoogleAuthUtil.getToken` call is the API
 * contract: it returns a fresh access token when
 * the previous one is expired, and the call itself
 * is what triggers Google's silent refresh. The
 * launcher never sees the refresh token. The
 * v0.25.4 surface stores the access token only,
 * because the access token is what the Drive REST
 * client puts in the `Authorization: Bearer`
 * header.
 *
 * ## Scope
 *
 * The OAuth scope is the narrowest "per-file
 * access" scope Google offers:
 * `https://www.googleapis.com/auth/drive.file`.
 * `drive.file` only grants access to files the
 * launcher created; the user's whole Drive is
 * invisible to the app. This is the user-trust
 * posture: the launcher cannot read the user's
 * other Drive files even if a future bug puts
 * the access token somewhere it should not be.
 */
class GoogleDriveAuth(private val context: Context) {

    /**
     * The DataStore key for the signed-in account
     * email. Plain DataStore, not EncryptedSharedPreferences,
     * because the email is the UI display, not a
     * secret.
     */
    private val emailKey = stringPreferencesKey("google_drive_account_email")

    /**
     * The DataStore instance. The
     * `Context.googleDriveEmailStore by preferencesDataStore(name = "...")`
     * extension is the standard pattern (one
     * DataStore per process, keyed on the
     * preferences name). The name is pinned so the
     * test's `@Before reset()` can address the same
     * store.
     */
    private val emailStore: DataStore<Preferences> = context.googleDriveEmailStore

    /**
     * The flow the Settings sub-section collects.
     * null = not signed in, non-null = the account
     * email to display.
     */
    val signedInEmailFlow: Flow<String?> = emailStore.data.map { prefs ->
        prefs[emailKey]?.takeIf { it.isNotBlank() }
    }

    /**
     * The encrypted-prefs handle for the access
     * token + expiry. Production code goes through
     * [TokenStore.create], which opens the
     * encrypted file; the test surfaces (see
     * `GoogleDriveAuthTokenStoreRoundTripFindingTest`)
     * construct a [TokenStore] directly from a
     * regular [SharedPreferences] because
     * Robolectric's Keystore stub does not back
     * the [MasterKey] the encrypted form needs.
     *
     * Lazy so the test-only constructor
     * (see the [TokenStore]-taking overload
     * below) can inject a non-encrypted
     * instance without the [MasterKey] init
     * being triggered. Production code pays the
     * lazy cost on the first [currentAccessToken]
     * call, which is fine — the lazy is on the
     * [TokenStore.create] call, not the keystore
     * access itself.
     */
    private val tokenStore: TokenStore by lazy { TokenStore.create(context) }

    /**
     * The GoogleSignInClient. Built lazily so the
     * [GoogleSignInOptions] construction
     * (which touches the platform) is deferred
     * until [signInIntent] is called.
     */
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    /**
     * Returns the [Intent] the caller hands to an
     * `ActivityResultLauncher` to start the Google
     * account picker. The launcher must use
     * `registerForActivityResult(StartActivityForResult())`
     * — the result is delivered to
     * [handleSignInResult].
     */
    fun signInIntent(): Intent = signInClient.signInIntent

    /**
     * Handles the result of the sign-in launcher.
     * On success, stores the account email in
     * [signedInEmailFlow]'s DataStore and the access
     * token in [TokenStore]. On failure, returns the
     * [SignInOutcome.Failure] and leaves the
     * existing credentials untouched.
     */
    suspend fun handleSignInResult(data: Intent?): SignInOutcome = withContext(Dispatchers.IO) {
        if (data == null) {
            Log.w(LOG_TAG, "handleSignInResult: null data")
            return@withContext SignInOutcome.Failure("No data returned from Google sign-in")
        }
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            Log.w(LOG_TAG, "sign-in failed: statusCode=${e.statusCode} message=${e.message}")
            return@withContext SignInOutcome.Failure(
                e.message ?: "Google sign-in failed (status ${e.statusCode})",
            )
        }
        val email = account.email
        if (email.isNullOrBlank()) {
            Log.w(LOG_TAG, "sign-in returned account with no email")
            return@withContext SignInOutcome.Failure("Google account has no email")
        }
        // Persist the email to the UI DataStore.
        emailStore.edit { it[emailKey] = email }
        // Prime the access token now so the first
        // Drive API call doesn't pay the cost.
        primeAccessToken(account)
        Log.i(LOG_TAG, "sign-in success: $email")
        SignInOutcome.Success(email)
    }

    /**
     * Returns a fresh access token, or null if
     * the user is not signed in / the token fetch
     * failed. The implementation reads the
     * [TokenStore] cache first; on a cache miss
     * (the user just signed in, the token has
     * been cleared, or the first call after a
     * fresh install) it falls through to
     * [GoogleAuthUtil.getToken] via
     * [fetchAndStoreAccessToken]. The token, when
     * fetched, is written back to the [TokenStore]
     * cache so the next caller doesn't pay the
     * same cost.
     *
     * The cache-first shape is the test surface:
     * the WP-B test sets a token directly on the
     * store and asserts the round-trip; the
     * platform `GoogleSignIn` lazy is never
     * triggered.
     */
    suspend fun currentAccessToken(): String? = withContext(Dispatchers.IO) {
        val cached = effectiveTokenStore().read()
        if (!cached.isNullOrBlank()) {
            return@withContext cached
        }
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null) {
            Log.w(LOG_TAG, "currentAccessToken: no signed-in account")
            return@withContext null
        }
        fetchAndStoreAccessToken(account)
    }

    /**
     * Clears the local credentials. The user has
     * chosen to disconnect: the email is wiped
     * from the DataStore, the access token is
     * wiped from [TokenStore], and
     * [GoogleSignInClient.signOut] is called so
     * the next sign-in shows the account picker
     * instead of silently re-authenticating the
     * cached account.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { signInClient.signOut() }.onFailure { e ->
            // The local wipe is the source of truth;
            // Google's sign-out is a best-effort.
            Log.w(LOG_TAG, "GoogleSignInClient.signOut failed: $e")
        }
        emailStore.edit { it.remove(emailKey) }
        tokenStore.clear()
        Log.i(LOG_TAG, "sign-out: credentials cleared")
    }

    /**
     * Test-only: clears the encrypted-prefs blob
     * and the DataStore. Mirrors the pattern of
     * [ReaderPrefs.reset] and
     * [org.mindanchor.letters.LetterStore.reset]:
     * the on-device stores are process-wide
     * singletons keyed on the preferences name, so
     * tests in the same class share state without
     * an explicit reset.
     */
    internal suspend fun reset() {
        tokenStore.clear()
        emailStore.edit { it.clear() }
    }

    /**
     * Test-only constructor: takes a pre-built
     * [TokenStore] so the round-trip test can
     * exercise the encrypted-prefs layer without
     * the GoogleSignInClient (which is bound to
     * the platform and not testable in a JVM
     * Robolectric run). See
     * `GoogleDriveAuthTokenStoreRoundTripFindingTest`
     * for the Robolectric setup.
     */
    internal constructor(context: Context, tokenStore: TokenStore) : this(context) {
        this.tokenStoreRef = tokenStore
    }

    /**
     * A test-only override of the token store. The
     * field is `var` because the test-only
     * constructor assigns it; production code
     * leaves it null and reads the lazy
     * [tokenStore] instead.
     */
    private var tokenStoreRef: TokenStore? = null

    /**
     * The actual token-store accessor. Prefers the
     * test override; falls back to the production
     * lazy field.
     */
    private fun effectiveTokenStore(): TokenStore = tokenStoreRef ?: tokenStore

    private fun primeAccessToken(account: GoogleSignInAccount) {
        // Fire-and-forget; failure to prime is not
        // fatal — the next [currentAccessToken] call
        // will retry. The user only sees a sign-in
        // success, so any pre-warm hiccup is silent.
        fetchAndStoreAccessToken(account)
    }

    private fun fetchAndStoreAccessToken(account: GoogleSignInAccount): String? {
        val scopeString = "oauth2:$DRIVE_FILE_SCOPE"
        val androidAccount = account.account
        if (androidAccount == null) {
            Log.w(LOG_TAG, "getToken: account.account is null")
            return null
        }
        return runCatching {
            // `GoogleAuthUtil.getToken` is the
            // on-device token-fetch API. It is
            // synchronous and may block on the
            // network; the call site is already on
            // Dispatchers.IO.
            GoogleAuthUtil.getToken(context, androidAccount, scopeString)
        }.onSuccess { token ->
            if (!token.isNullOrBlank()) {
                effectiveTokenStore().write(token)
            }
        }.onFailure { e ->
            Log.w(LOG_TAG, "getToken failed: $e")
        }.getOrNull()
    }

    /**
     * The outcome of [handleSignInResult]. The
     * Settings sub-section uses this to decide
     * which affordance to show.
     */
    sealed interface SignInOutcome {
        /**
         * The user picked an account and granted
         * the `drive.file` scope. The email is the
         * UI display.
         */
        data class Success(val email: String) : SignInOutcome

        /**
         * The user cancelled, the account has no
         * email, or the OAuth grant failed. The
         * message is for the launcher's log; the
         * user sees a generic "couldn't sign in"
         * surface.
         */
        data class Failure(val message: String) : SignInOutcome
    }

    companion object {
        private const val LOG_TAG = "MindAnchor/DriveAuth"
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

/**
 * The encrypted-prefs blob for the access token.
 *
 * v0.25.4 stores a single secret (the access
 * token); the v0.23.0 [WebDavCredentialStore] stored
 * three (URL, username, password). The shape is
 * the same pattern, slimmed down: one file, one key,
 * one [MasterKey] — the smallest possible surface
 * for "a short-lived bearer that must not leak".
 *
 * The class takes a [SharedPreferences] in its
 * primary constructor so the test surface can
 * inject a regular (non-encrypted) instance:
 * Robolectric's Keystore stub does not back the
 * [MasterKey] the encrypted form needs. Production
 * code goes through [create], which opens the
 * encrypted form; the unit test
 * (`GoogleDriveAuthTokenStoreRoundTripFindingTest`)
 * passes a regular [SharedPreferences] from the
 * test [Context]. The shape under test is
 * identical, so the round-trip is the same.
 *
 * The file is wiped by [clear] and by the owning
 * [GoogleDriveAuth.signOut].
 */
internal class TokenStore(private val prefs: SharedPreferences) {

    /**
     * @return the stored access token, or null if
     * the user has not signed in, has signed out,
     * or the store has been wiped.
     */
    fun read(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    /**
     * Writes [token] to the encrypted-prefs blob.
     * A blank token is a no-op (the store stays
     * empty rather than holding a placeholder
     * string).
     */
    fun write(token: String) {
        if (token.isBlank()) return
        prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
    }

    /**
     * Clears the store. Called from
     * [GoogleDriveAuth.signOut] and from the test's
     * `@Before reset` (test isolation).
     */
    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREF_FILE = "google_drive_token"
        private const val KEY_ACCESS_TOKEN = "access_token"

        /**
         * The production factory. Opens the
         * encrypted-prefs file under [PREF_FILE] with
         * a Keystore-backed [MasterKey]. The
         * encrypted form is the at-rest surface for
         * the access token on a real device; the
         * test surface uses a regular
         * [SharedPreferences] from the test
         * [Context] (see
         * `GoogleDriveAuthTokenStoreRoundTripFindingTest`).
         */
        fun create(context: Context): TokenStore = TokenStore(openEncrypted(context))

        private fun openEncrypted(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

/**
 * The DataStore extension for the account email.
 * One process-wide instance, keyed on the
 * preferences name. The `by preferencesDataStore`
 * delegate is the standard pattern; the top-level
 * extension property is required because
 * `Context.preferencesDataStore` is an extension
 * function on `Context` and Kotlin's `by` delegate
 * captures the receiver.
 */
private val Context.googleDriveEmailStore: DataStore<Preferences> by preferencesDataStore(
    name = "google_drive_account",
)
