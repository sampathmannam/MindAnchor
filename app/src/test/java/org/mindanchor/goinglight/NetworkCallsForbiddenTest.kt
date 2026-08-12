package org.mindanchor.goinglight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Going Light v1.1 design adds the `INTERNET`
 * permission to the manifest. The privacy promise is
 * that the app makes *zero* outbound network calls —
 * the VpnService captures loopback only.
 *
 * v0.20.1 (CodeRabbit audit 2026-08-08) replaces the
 * v0.20.0 file-level exemption with an *operation-level*
 * allowlist. v0.20.7 adds a second subsystem — the
 * COROS Training Hub bridge — whose sole purpose IS to
 * make outbound calls, but only when the user has
 * explicitly opted in (see Settings → Measuring →
 * Wearable bridge). Every other file in the app must
 * stay call-free.
 *
 * The test is the only thing standing between a
 * motivated contributor and a privacy-breaking
 * network call. The wording is clinical-review-required
 * (item B+K gate) because the test's *exceptions* are
 * a user-consent surface.
 *
 * @see docs/research/18 for the VpnService rationale.
 * @see docs/research/20 for the COROS bridge rationale.
 */
class NetworkCallsForbiddenTest {

    private val appSrc = "app/src/main/java"
    private val testSrc = "app/src/test/java"

    /**
     * Network API patterns that would indicate the
     * app is making outbound calls. Each pattern is
     * a substring; the test greps for *fully qualified*
     * references, so both `import java.net.Socket`
     * and `java.net.Socket()` in code bodies are
     * caught.
     *
     * The patterns are conservative: they trigger on
     * any reference, not just an active connection.
     * False positives are acceptable; false negatives
     * are not.
     */
    private val forbiddenPatterns = listOf(
        "java.net.URL",
        "java.net.URLConnection",
        "java.net.HttpURLConnection",
        "java.net.Socket",
        "java.net.DatagramSocket",
        "java.net.InetSocketAddress",
        "java.net.SocketAddress",
        "java.net.SocketImpl",
        "java.net.ServerSocket",
        "java.net.MulticastSocket",
        "okhttp3",
        "retrofit2",
        "com.squareup.okhttp",
        "java.nio.channels.SocketChannel",
        "java.nio.channels.DatagramChannel",
        "java.nio.channels.AsynchronousSocketChannel",
        "java.nio.channels.AsynchronousDatagramChannel",
        "java.rmi",
        "javax.net.ssl",
        "java.net.http.HttpClient",
        "java.net.http.HttpRequest",
        "java.net.http.HttpResponse",
        "java.net.http.WebSocket",
        "java.net.Proxy",
        "java.net.ProxySelector",
    )

    /**
     * The VpnService subsystem files. The
     * operation-level allowlist below applies to these
     * three; other files are denied unconditionally.
     */
    private val vpnSubsystemFiles = setOf(
        "app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
        "app/src/main/java/org/mindanchor/goinglight/PacketForwarder.kt",
        "app/src/main/java/org/mindanchor/goinglight/GoingLightScheduler.kt",
    )

    /**
     * The COROS Training Hub bridge files. Opt-in
     * (Settings → Measuring → Wearable bridge, off by
     * default) and the only files allowed to make
     * outbound calls to a third-party server.
     *
     * The COROS package is the *only* path through which
     * data leaves this phone. Every other file in the
     * app must stay call-free.
     */
    private val corosBridgeFiles = setOf(
        "app/src/main/java/org/mindanchor/vitals/coros/CorosApi.kt",
        "app/src/main/java/org/mindanchor/vitals/coros/CorosAuth.kt",
        "app/src/main/java/org/mindanchor/vitals/coros/CorosSyncWorker.kt",
        "app/src/main/java/org/mindanchor/vitals/coros/CorosCredentialStore.kt",
        "app/src/main/java/org/mindanchor/vitals/coros/CorosVitalSource.kt",
    )

    /**
     * The Google Drive backup bridge files. v0.25.4
     * replaces the v0.23.0 WebDAV bridge with a Drive
     * path that uses the user's own Google account.
     * Same consent shape as the old WebDAV bridge:
     * opt-in (Settings → Reading → Google Drive),
     * the user picks their account, every payload is
     * wrapped with AES-256-GCM via
     * [EncryptedBackupCodec] before transport, and the
     * `drive.file` scope is the narrowest "per-file
     * access" scope Google offers (the launcher only
     * sees files it created, never the user's whole
     * Drive). The bridge is silent until the user
     * enables it. The set is the *complete* list of
     * allowed files; a new file under backup/ that
     * uses an outbound channel requires an explicit
     * re-pin (see the `googleDriveBackupFiles` size-pin
     * test below).
     */
    private val googleDriveBackupFiles = setOf(
        "app/src/main/java/org/mindanchor/backup/GoogleDriveAuth.kt",
        "app/src/main/java/org/mindanchor/backup/GoogleDriveBackupTarget.kt",
        "app/src/main/java/org/mindanchor/backup/ContentType.kt",
        "app/src/main/java/org/mindanchor/backup/BackupTarget.kt",
    )

    /**
     * The Google Drive bridge's own tests live under
     * `app/src/test/java/org/mindanchor/backup/`. The
     * tests have to use [okhttp3.mockwebserver] to
     * exercise the Drive REST API; the test directory
     * is matched the same way as the COROS bridge,
     * so a test file under backup/ that uses an
     * outbound API is not surfaced as a forbidden
     * reference. (The same allowlist previously
     * covered the v0.23.0 WebDAV tests; v0.25.4
     * reuses the path.)
     */
    private val driveBackupTestDir = "app/src/test/java/org/mindanchor/backup"

    /**
     * The COROS bridge's own tests live under
     * `app/src/test/java/org/mindanchor/vitals/coros/`.
     * The tests have to use [okhttp3.mockwebserver] to
     * exercise the API; the test is in the same package
     * tree as the production code on purpose, so a
     * contributor who adds a forbidden reference in the
     * test file is still caught by this test (a test
     * file referencing `okhttp3.OkHttpClient` to make
     * a real outbound call is exactly the kind of
     * mistake the carve-out is not for). The test
     * files are matched by directory, not enumerated
     * one-by-one, because the test fixture set grows
     * with the production code and pinning a per-file
     * list here is a maintenance trap.
     */
    private val corosBridgeTestDir = "app/src/test/java/org/mindanchor/vitals/coros"

    /**
     * Files in the VpnService subsystem may use the
     * captured-loopback API surface. The set is the
     * *complete* list of allowed network-related
     * symbols; any other reference fails the build.
     *
     * The intent: a future contributor cannot add
     * `import okhttp3.OkHttpClient` to the
     * VpnService's body without a test failure. The
     * file-level exemption the v0.20.0 test had is
     * gone — a wholly different defense.
     */
    private val vpnSubsystemAllowedReferences = setOf(
        // Captured-loopback TUN plumbing. These are
        // not network endpoints; they read/write the
        // local virtual interface only.
        "android.net.VpnService",
        "android.net.VpnService.Builder",
        "android.os.ParcelFileDescriptor",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.nio.ByteBuffer",
        // InetAddress is a data carrier here — the
        // Packet class stores the destination as an
        // InetAddress. No network call is made.
        "java.net.InetAddress",
        // The intent that arms the alarm and
        // start/stop the service. Not a network call.
        "android.content.Intent",
        "android.app.PendingIntent",
        "android.app.AlarmManager",
        // Standard imports. Listed for completeness;
        // no allowlist drift is possible because the
        // test only triggers on the patterns above.
        "android.content.Context",
        "android.content.BroadcastReceiver",
        "kotlinx.coroutines",
    )

    /**
     * Files in the COROS bridge may use HTTP client
     * primitives, JSON serialization, encrypted
     * credential storage, and DataStore. The set is
     * the *complete* list of allowed symbols. Any
     * reference outside this set fails the build.
     */
    private val corosBridgeAllowedReferences = setOf(
        // OkHttp HTTP client (the only outbound channel)
        "okhttp3.OkHttpClient",
        "okhttp3.Request",
        "okhttp3.RequestBody",
        "okhttp3.MediaType",
        "okhttp3.Response",
        "okhttp3.HttpUrl",
        "okhttp3.Call",
        // Kotlinx serialization (JSON wire format)
        "kotlinx.serialization.Serializable",
        "kotlinx.serialization.json.Json",
        "kotlinx.serialization.json.JsonElement",
        // AndroidX encrypted credential storage
        "androidx.security.crypto.EncryptedSharedPreferences",
        "androidx.security.crypto.MasterKey",
        // DataStore for the local sync state (token, last-sync timestamp)
        "androidx.datastore.preferences.core.edit",
        // WorkManager scheduling primitive — not a network call itself
        "androidx.work.Worker",
        "androidx.work.CoroutineWorker",
        "androidx.work.ListenableWorker",
    )

    /**
     * The v0.25.4+ Drive bridge files re-use the same
     * shape as the WebDAV allowlist above: opt-in,
     * encrypted before transport, the user's own
     * account, off by default. A new file under
     * backup/ that uses an outbound channel for the
     * Drive path requires an explicit re-pin
     * (see the `googleDriveBackupFiles set` size pin
     * test below).
     */

    /**
     * Classifies a file as belonging to one of the
     * opt-in network subsystems (VpnService, COROS,
     * Google Drive). Extracted from the no-network
     * test to keep that test's complexity at one
     * decision (in-subsystem or not) instead of six.
     * The body is the union of the subsystem-specific
     * allowlists; a new subsystem is added by listing
     * its file set / test dir here AND declaring the
     * set above. The v0.23.0 WebDAV bridge has been
     * removed in v0.25.4; the per-type Drive bridge
     * reuses the same test dir.
     */
    private fun isInSubsystem(f: File): Boolean {
        val inVpn = f.path in vpnSubsystemFiles
        val inCoros = f.path in corosBridgeFiles
        val inCorosTest = f.path.startsWith("$corosBridgeTestDir/")
        val inDrive = f.path in googleDriveBackupFiles
        val inDriveTest = f.path.startsWith("$driveBackupTestDir/")
        return inVpn || inCoros || inCorosTest || inDrive || inDriveTest
    }

    /**
     * Every .kt file in the project is in exactly one
     * of four buckets:
     *  - VpnService subsystem (captured loopback only)
     *  - COROS bridge (opt-in outbound, single package)
     *  - WebDAV backup bridge (opt-in outbound, encrypted)
     *  - Google Drive backup bridge (opt-in outbound,
     *    encrypted, v0.25.4+)
     *  - Everything else (no network, no exceptions)
     *
     * The classification is the *file's* identity — not
     * the patterns it uses — so adding a new COROS,
     * WebDAV, or Drive file requires explicitly
     * listing it in corosBridgeFiles /
     * webDavBackupFiles / googleDriveBackupFiles,
     * which is a clinical-review-surface change.
     */
    @Test
    @Suppress("NestedBlockDepth")
    fun `no source file outside the VpnService, COROS, and WebDAV subsystems references a network API`() {
        val offenders = mutableListOf<String>()
        for (dir in listOf(appSrc, testSrc)) {
            val root = File(dir)
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    // v0.20.1 round 5 follow-up: skip
                    // this test file itself. The test's
                    // job is to scan every OTHER .kt
                    // file; scanning itself would
                    // surface the forbiddenPatterns
                    // list as a false positive (the
                    // patterns are string literals in
                    // this file). The exclusion is
                    // scoped to this single file path.
                    if (f.path == "app/src/test/java/org/mindanchor/goinglight/NetworkCallsForbiddenTest.kt") {
                        return@forEach
                    }
                    if (!isInSubsystem(f)) {
                        for (pattern in forbiddenPatterns) {
                            if (f.readText().contains(pattern)) {
                                offenders.add(
                                    "${f.path}: references forbidden pattern '$pattern' " +
                                        "outside the VpnService, COROS, and WebDAV subsystems",
                                )
                            }
                        }
                    }
                }
        }
        assertTrue(
            "These files reference a network API, which would break the " +
                "no-outbound-calls privacy promise of Going Light v1.1.\n" +
                "Either remove the network reference, or add it to one of the " +
                "subsystem allowlists (vpnSubsystemFiles / corosBridgeFiles / " +
                "googleDriveBackupFiles) with explicit " +
                "clinical-review sign-off.\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `vpnSubsystemFiles set contains only the three expected VpnService files`() {
        val expected = setOf(
            "app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
            "app/src/main/java/org/mindanchor/goinglight/PacketForwarder.kt",
            "app/src/main/java/org/mindanchor/goinglight/GoingLightScheduler.kt",
        )
        assertTrue(
            "vpnSubsystemFiles drift detected. " +
                "Expected: $expected, Got: $vpnSubsystemFiles",
            vpnSubsystemFiles == expected,
        )
    }

    @Test
    fun `corosBridgeFiles set contains only the expected COROS files`() {
        // Pin the set so a new file in vitals/coros/ has to be
        // explicitly added — the test re-fails until a clinical
        // review re-pins the value.
        assertEquals(
            "corosBridgeFiles drift. Got: $corosBridgeFiles",
            5,
            corosBridgeFiles.size,
        )
    }

    @Test
    fun `googleDriveBackupFiles set contains only the expected Drive files`() {
        // Same pinning pattern as the v0.23.0 WebDAV
        // allowlist (removed in v0.25.4). A new file
        // under backup/ that uses an outbound channel
        // for the Drive path requires an explicit re-pin
        // (clinical-review-required: this is a
        // user-consent surface, not a carve-out for
        // convenience).
        assertEquals(
            "googleDriveBackupFiles drift. Got: $googleDriveBackupFiles",
            4,
            googleDriveBackupFiles.size,
        )
    }

    @Test
    fun `vpnSubsystemAllowedReferences is documented and stable`() {
        // The allowed set is the project's contract with itself.
        // Pin the size so a contributor cannot silently add a
        // network surface. Any change to the allowed set must be
        // intentional and reviewed.
        assertEquals(
            "vpnSubsystemAllowedReferences drift. " +
                "Expected exactly 13 entries (current pinned value). " +
                "Got: ${vpnSubsystemAllowedReferences.size}: $vpnSubsystemAllowedReferences",
            13,
            vpnSubsystemAllowedReferences.size,
        )
    }

    @Test
    fun `corosBridgeAllowedReferences is documented and stable`() {
        // Same pinning as the VpnService allowlist. A new
        // OkHttp / kotlinx.serialization / security-crypto symbol
        // requires an explicit re-pin.
        assertEquals(
            "corosBridgeAllowedReferences drift. " +
                "Expected exactly 16 entries (current pinned value). " +
                "Got: ${corosBridgeAllowedReferences.size}: $corosBridgeAllowedReferences",
            16,
            corosBridgeAllowedReferences.size,
        )
    }
}
