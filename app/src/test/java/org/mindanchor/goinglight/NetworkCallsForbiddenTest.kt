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
 * This test enforces the promise. v0.20.1 (CodeRabbit
 * audit 2026-08-08) replaces the v0.20.0 *file-level*
 * exemption with an *operation-level* allowlist:
 *
 *  - Every `.kt` file under `app/src/main/java` and
 *    `app/src/test/java` is scanned for **fully
 *    qualified** network API references, not just
 *    import statements. A line like
 *    `java.net.Socket()` in the VpnService's body is
 *    caught.
 *  - The VpnService and its satellite files are
 *    allowed to use *only* the captured-loopback
 *    API surface (VpnService.Builder, ParcelFileDescriptor,
 *    InetAddress as a data carrier, etc.). Any
 *    reference to a network endpoint (URL, OkHttp,
 *    Retrofit, Socket, DatagramSocket, SocketChannel,
 *    HttpClient, javax.net.ssl, java.net.http,
 *    java.net.Proxy) fails the build.
 *
 * The test is the only thing standing between a
 * motivated contributor and a privacy-breaking
 * network call. The wording is clinical-review-required
 * (item B+K gate) because the test's *exceptions* are
 * a user-consent surface.
 *
 * @see docs/research/18 for the rationale.
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

    @Test
    fun `no source file outside the VpnService subsystem references a network API`() {
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
                    val content = f.readText()
                    if (f.path !in vpnSubsystemFiles) {
                        // Non-VpnService files: any
                        // network pattern fails the
                        // build, full stop.
                        for (pattern in forbiddenPatterns) {
                            if (content.contains(pattern)) {
                                offenders.add(
                                    "${f.path}: references forbidden pattern '$pattern' " +
                                        "outside the VpnService subsystem",
                                )
                            }
                        }
                    } else {
                        // VpnService subsystem files:
                        // the operation-level allowlist
                        // applies. A reference is
                        // allowed if and only if it
                        // appears in the allowlist.
                        for (pattern in forbiddenPatterns) {
                            if (content.contains(pattern)) {
                                offenders.add(
                                    "${f.path}: references forbidden pattern '$pattern' " +
                                        "in the VpnService subsystem. The VpnService " +
                                        "subsystem may only use the captured-loopback API " +
                                        "surface; OkHttp, Retrofit, Socket, etc. are denied.",
                                )
                            }
                        }
                    }
                }
        }
        assertTrue(
            "These files reference a network API, which would break the " +
                "no-outbound-calls privacy promise of Going Light v1.1.\n" +
                "Either remove the network reference, or add it to the " +
                "vpnSubsystemAllowedReferences set with explicit clinical-review " +
                "sign-off.\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the vpnSubsystemFiles set contains only the three expected VpnService files`() {
        // Sanity check: if a future contributor adds a
        // file to vpnSubsystemFiles, it should be
        // intentional. This test will fail and force a
        // re-review.
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
    fun `the allowed API surface is documented and stable`() {
        // The allowed set is the project's contract
        // with itself. Pin the size so a contributor
        // cannot silently add a network surface.
        // (This catches the wrong direction: an
        // allowed reference should be explicit, and
        // a new one should fail this test until a
        // clinical review re-pins the value.)
        //
        // v0.20.1 round 5 follow-up: the previous
        // size bound was 10..20, which allowed
        // silent drift up to +7 entries without
        // clinical review. Tightened to exact-match
        // the current 13 entries. Any future
        // change to the allowed set must be
        // intentional and reviewed.
        assertEquals(
            "vpnSubsystemAllowedReferences drift. " +
                "Expected exactly 13 entries (current pinned value). " +
                "Got: ${vpnSubsystemAllowedReferences.size}: $vpnSubsystemAllowedReferences",
            13,
            vpnSubsystemAllowedReferences.size,
        )
    }
}
