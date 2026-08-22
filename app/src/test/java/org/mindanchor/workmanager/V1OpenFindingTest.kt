@file:Suppress(
    "SwallowedException", 
    "MaxLineLength", 
    "LoopWithTooManyJumpStatements", 
    "UnusedPrivateMember",
)

package org.mindanchor.workmanager

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1 bug-hunt report (`bug_hunt_backup.md`) found 20 backup
 * issues, of which v0.25.8 fixed 5. The remaining 15 are fair
 * game for the v2 sweep; this test class pins the *worker-
 * relevant* subset of those 15 with v2 analysis. The five
 * findings below are deepened in the v2 report, not duplicated
 * verbatim from v1 — the v2 framing is the WorkManager +
 * Concurrency surface specifically.
 */
class V1OpenFindingTest {

    /**
     * v1 #6 (GCM AAD missing) — FIXED in v0.25.9. The
     * v0.25.8 fix list did not include the AAD. The bug
     * pattern: `Cipher.init(ENCRYPT_MODE, key)` without a
     * `cipher.updateAAD(type.fileName.toByteArray())`.
     * For the v2 framing: the AAD binding is the WorkManager-
     * adjacent security property — a worker that drains a queue
     * of PendingBackups trusts the (type, payload) tuple from
     * DataStore, and the type binding is what stops a motivated
     * attacker from cross-appending Notes ciphertext into the
     * Letters file. The v0.25.9 fix shape: `updateAAD` is
     * called on both encrypt and decrypt, and both wrap and
     * unwrap take a `type: ContentType` argument. The
     * behavioural test is in
     * `EncryptedBackupCodecTest.wrap with Notes AAD then
     * unwrap as Letters returns null`.
     */
    @Test
    fun `EncryptedBackupCodec wrap calls updateAAD with type fileName and cross-file malleability is fixed`() {
        val source = readSource("backup/EncryptedBackupCodec.kt")
        assertNotNull(source)
        val callsUpdateAad = source!!.contains("updateAAD(type.fileName.toByteArray")
        val wrapTakesType = Regex(
            """fun\s+wrap\(plaintextJson:\s*String,\s*type:\s*ContentType""",
        ).containsMatchIn(source)
        val unwrapTakesType = Regex(
            """fun\s+unwrap\(blob:\s*ByteArray,\s*type:\s*ContentType""",
        ).containsMatchIn(source)
        assertTrue(
            "EncryptedBackupCodec v0.25.9 fix: wrap and unwrap must call " +
                "cipher.updateAAD with type.fileName and take a `type: ContentType` arg. " +
                "callsUpdateAad=$callsUpdateAad wrapTakesType=$wrapTakesType " +
                "unwrapTakesType=$unwrapTakesType.",
            callsUpdateAad && wrapTakesType && unwrapTakesType,
        )
    }

    /**
     * v1 #8 (Drive URLEncoder form-style) — OPEN. The v0.25.8
     * fix list did not include the URL-encoding change. The
     * bug pattern: `java.net.URLEncoder.encode(query, "UTF-8")`
     * encodes spaces as `+`, but Drive's REST `q` parameter
     * expects RFC 3986 `%20`. The current filenames
     * (`MindAnchor-Notes.txt`, `MindAnchor-Letters.txt`) have
     * no spaces, so the bug is latent. The worker triggers
     * the `find` call on every BackupRetryWorker.doWork run,
     * so the latent bug would fire on the first filename rename.
     *
     * The v2 framing: this is the **WorkManager-adjacent Drive
     * path** — the worker calls `find` on every drain. The
     * fix flips the assertion: `URLEncoder.encode` must be
     * replaced with `Uri.encode` (Android's `android.net.Uri`).
     */
    @Test
    fun `GoogleDriveBackupTarget findFileId uses URLEncoder which encodes spaces as + not %20 - latent Drive query bug`() {
        val source = readSource("backup/GoogleDriveBackupTarget.kt")
        assertNotNull(source)
        val usesUrlEncoder = source!!.contains("URLEncoder.encode(this, \"UTF-8\")") ||
            source.contains("URLEncoder.encode(")
        val doesNotReplacePlus = !source.contains(".replace(\"+\", \"%20\")")
        // The fix is either:
        // (a) replace URLEncoder with Uri.encode, or
        // (b) chain a .replace("+", "%20") to the URLEncoder output.
        assertTrue(
            "GoogleDriveBackupTarget.urlEncode uses java.net.URLEncoder.encode, which is " +
                "form-style (space → '+'). The Drive REST `q` parameter expects RFC 3986 " +
                "(space → '%20'). Current filenames have no spaces so the bug is latent, " +
                "but the worker calls findFileId on every BackupRetryWorker.doWork run, so " +
                "the first filename rename triggers a silent 400. " +
                "usesUrlEncoder=$usesUrlEncoder doesNotReplacePlus=$doesNotReplacePlus. " +
                "The fix is Uri.encode (Android) or a trailing .replace('+', '%20').",
            usesUrlEncoder && doesNotReplacePlus,
        )
    }

    /**
     * v1 #10 (two OkHttpClient instances) — OPEN. The
     * v0.25.8 fix list closed the *worker's* OkHttpClient
     * leak (v0.25.7+ WP-2) but not the *on-write trigger's*
     * client. `BackupScheduler.startIfNeeded` still creates
     * a fresh `val client = OkHttpClient()` and never closes
     * it. The fix flips the assertion: the on-write trigger's
     * client should be either (a) the same process-singleton
     * as the worker's, or (b) closed in a finally block.
     *
     * (This is the same finding as WorkManagerConcurrencyFindingTest's
     * "startIfNeeded creates an OkHttpClient that is never closed" —
     * the v2 framing of the v1 #10 finding, presented as a worker-
     * concurrency test for symmetry with the v1 backup-only tests.)
     */
    @Test
    fun `BackupScheduler startIfNeeded still creates a fresh OkHttpClient that is never closed (v1 number 10 persists in v0p25p8)`() {
        val source = readSource("backup/BackupScheduler.kt")
        assertNotNull(source)
        val createsClient = source!!.contains("fun startIfNeeded(context: Context)") &&
            source.contains("val client = OkHttpClient()")
        val doesNotClose = !source.contains("client.dispatcher.executorService.shutdown()") &&
            // The function body should not contain the close call.
            // The fix flips: close the client in a finally block
            // or use a process-singleton.
            !source.contains("client.shutdown()")
        assertTrue(
            "BackupScheduler.startIfNeeded creates `val client = OkHttpClient()` and never " +
                "closes it. v0.25.7+ WP-2 closed the same leak in BackupRetryWorker.doWork " +
                "but missed the on-write trigger path. createsClient=$createsClient " +
                "doesNotClose=$doesNotClose. The client outlives the process (the `appScope` " +
                "is a process-singleton companion field), so the leak is bounded to one " +
                "ExecutorService per process — not dozens like the pre-fix worker — but it " +
                "is still a leak.",
            createsClient && doesNotClose,
        )
    }

    /**
     * v1 #11 (`firstOrEmpty` swallows flow exceptions silently) — OPEN.
     * `BackupScheduler.backupAll` calls
     * `notesPrefs.notes.firstOrEmpty(NotesState())` and
     * `letterStore.letters.firstOrEmpty(emptyList())` (lines 140, 142).
     * The wrapper is `runCatching { this.first() }.getOrDefault(default)`,
     * which silently swallows any DataStore exception. A corrupt
     * prefs file or sealed-codec HMAC mismatch returns the default
     * (empty list), the "Back up now" button reports "0 notes / 0
     * letters backed up", and the user has no signal that something
     * went wrong.
     *
     * The v2 framing: this is the *user-facing* "Back up now" path
     * (the `backupAll` button in Settings). The worker is not
     * involved; the swallow is in the call before the worker is
     * ever scheduled. The fix flips: log the swallowed exception
     * so the failure is visible in logcat.
     */
    @Test
    fun `BackupScheduler firstOrEmpty swallows DataStore exceptions silently (v1 number 11 persists in v0p25p8)`() {
        val source = readSource("backup/BackupScheduler.kt")
        assertNotNull(source)
        // The bug is that the `firstOrEmpty` function's body does not
        // log the swallowed exception. The fix would add `Log.w(LOG_TAG, ..., e)`
        // in the runCatching onFailure path. The current shape is
        // `runCatching { this.first() }.getOrDefault(default)` with no
        // onFailure and no Log call in the function body.
        val definesFirstOrEmpty = source!!.contains("fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrEmpty(default: T)") &&
            source.contains("getOrDefault(default)")
        // Look for the specific `runCatching { this.first() }` pattern
        // *inside the firstOrEmpty function* and check that it does not
        // have a `.onFailure { Log.w(...) }` chain. The current shape is
        // just the bare runCatching + getOrDefault.
        // The function is a single-line body, so the surrounding
        // context is the `fun ... {` line and the `}` closer.
        val firstOrEmptyFunctionBody = Regex(
            """private suspend fun <T> kotlinx\.coroutines\.flow\.Flow<T>\.firstOrEmpty\(default: T\): T \{[^}]*\}""",
        ).find(source)?.value.orEmpty()
        val doesNotLogInBody = !firstOrEmptyFunctionBody.contains("Log.") &&
            !firstOrEmptyFunctionBody.contains("onFailure")
        assertTrue(
            "BackupScheduler.firstOrEmpty uses `runCatching { this.first() }.getOrDefault(default)` " +
                "which silently swallows DataStore exceptions. A corrupt notes prefs file " +
                "(sealed-codec HMAC mismatch) returns the default (empty list), the " +
                "'Back up now' button reports '0 notes / 0 letters backed up', and the user " +
                "has no signal. definesFirstOrEmpty=$definesFirstOrEmpty doesNotLogInBody=" +
                "$doesNotLogInBody. The fix: at least Log.w the swallowed exception in the wrapper.",
            definesFirstOrEmpty && doesNotLogInBody,
        )
    }

    /**
     * v1 #18 (default 10s backoff) — OPEN. The v0.25.8 fix list
     * did not adjust the backoff. `BackupRetryWorker.enqueueIfNeeded`
     * uses the default WorkManager backoff (`BackoffPolicy.EXPONENTIAL`,
     * 10s initial, 5h cap). A flaky Drive (intermittent 503s) sees
     * the worker retry at 10s, 20s, 40s, 80s, 160s, 320s — six
     * attempts in the first ~10 minutes.
     *
     * The v2 framing: the worker is constrained to CONNECTED, so
     * the backoff is the only thing that gates the retry rate on
     * a reachable-but-broken Drive. The fix flips: `setBackoffCriteria(
     * BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)` to start
     * at 30s and avoid the 10s hammer.
     */
    @Test
    fun `BackupRetryWorker uses default WorkManager backoff (10s) and first retry hammers a flaky Drive (v1 number 18 persists in v0p25p8)`() {
        val source = readSource("backup/BackupRetryWorker.kt")
        assertNotNull(source)
        val doesNotSetBackoff = !source!!.contains("setBackoffCriteria(")
        val usesDefault10s = // The default initial backoff is 10s. The
            // assertion fails if the explicit setBackoffCriteria call
            // is absent — i.e. the worker is on the default.
            doesNotSetBackoff
        assertTrue(
            "BackupRetryWorker does not call `setBackoffCriteria(...)` and is on the " +
                "WorkManager default of EXPONENTIAL/10s. A flaky Drive (intermittent 503s) " +
                "sees the worker retry at 10s, 20s, 40s, 80s, 160s, 320s — six attempts in " +
                "the first ~10 minutes. doesNotSetBackoff=$doesNotSetBackoff usesDefault10s=" +
                "$usesDefault10s. The fix flips: setBackoffCriteria(EXPONENTIAL, 30, SECONDS) " +
                "to start at 30s and avoid the 10s hammer on flaky backends.",
            usesDefault10s,
        )
    }

    private fun readSource(relative: String): String? = runCatching {
        val candidates = listOf(
            "app/src/main/java/org/mindanchor/$relative",
            "../app/src/main/java/org/mindanchor/$relative",
        )
        candidates.firstNotNullOfOrNull { path ->
            val file = java.io.File(path)
            if (file.exists()) file.readText() else null
        }
    }.getOrNull()
}
