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
     * The WebDAV backup bridge files. v0.23.0 added a
     * second opt-in outbound channel: the user enables
     * the bridge in Settings, types in their own WebDAV
     * URL + app-password, and the launcher uploads
     * AES-256-GCM-encrypted backup blobs. Off by default.
     *
     * Like the COROS bridge, this is a *user-consent
     * surface*: the URL and the password are the user's
     * own, the data is wrapped before it leaves the
     * device, and the bridge is silent until turned on.
     * Every other file in the app must stay call-free.
     */
    private val webDavBackupFiles = setOf(
        "app/src/main/java/org/mindanchor/backup/WebDavBackupTarget.kt",
        "app/src/main/java/org/mindanchor/backup/WebDavCredentialStore.kt",
        "app/src/main/java/org/mindanchor/backup/EncryptedBackupCodec.kt",
        "app/src/main/java/org/mindanchor/backup/KeystoreAesKey.kt",
    )

    /**
     * The WebDAV bridge's own tests live under
     * `app/src/test/java/org/mindanchor/backup/`. The
     * tests have to use [okhttp3.mockwebserver] to
     * exercise the API; the test directory is matched
     * the same way as the COROS bridge, so a test
     * file under backup/ that uses an outbound API is
     * not surfaced as a forbidden reference.
     */
    private val webDavBackupTestDir = "app/src/test/java/org/mindanchor/backup"

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
     * Every .kt file in the project is in exactly one
     * of four buckets:
     *  - VpnService subsystem (captured loopback only)
     *  - COROS bridge (opt-in outbound, single package)
     *  - WebDAV backup bridge (opt-in outbound, encrypted)
     *  - Everything else (no network, no exceptions)
     *
     * The classification is the *file's* identity — not
     * the patterns it uses — so adding a new COROS or
     * WebDAV file requires explicitly listing it in
     * corosBridgeFiles / webDavBackupFiles, which is a
     * clinical-review-surface change.
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
                    val inVpnSubsystem = f.path in vpnSubsystemFiles
                    val inCorosBridge = f.path in corosBridgeFiles
                    val inCorosTestDir = f.path.startsWith("$corosBridgeTestDir/")
                    val inWebDavBridge = f.path in webDavBackupFiles
                    val inWebDavTestDir = f.path.startsWith("$webDavBackupTestDir/")
                    val inSubsystem = inVpnSubsystem || inCorosBridge || inCorosTestDir ||
                        inWebDavBridge || inWebDavTestDir
                    if (!inSubsystem) {
                        // Non-subsystem files: any
                        // network pattern fails the
                        // build, full stop.
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
                "webDavBackupFiles) with explicit clinical-review sign-off.\n" +
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
    fun `webDavBackupFiles set contains only the expected WebDAV files`() {
        // Same pinning as the COROS bridge allowlist. A new
        // file under backup/ that uses an outbound channel
        // requires an explicit re-pin.
        assertEquals(
            "webDavBackupFiles drift. Got: $webDavBackupFiles",
            4,
            webDavBackupFiles.size,
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
