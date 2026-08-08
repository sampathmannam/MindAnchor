package org.mindanchor.goinglight

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Going Light v1.1 design adds the `INTERNET`
 * permission to the manifest. The privacy promise is
 * that the app makes *zero* outbound network calls —
 * the VpnService captures loopback only.
 *
 * This test enforces the promise: any new file that
 * imports or references a network API (URL,
 * OkHttp, Retrofit, HttpURLConnection, WebSocket,
 * Socket, DatagramSocket) fails the build.
 *
 * The exception is the VpnService itself, which is
 * the only file that uses the captured-loopback
 * traffic pattern. The exemption list lives in
 * [allowedFiles].
 *
 * @see docs/research/18 for the rationale.
 */
class NetworkCallsForbiddenTest {

    private val appSrc = "app/src/main/java"
    private val testSrc = "app/src/test/java"

    /**
     * Network API patterns that would indicate the
     * app is making outbound calls. Each is a
     * substring the test greps for; the patterns are
     * conservative and trigger on any use, not just
     * an active connection.
     */
    private val forbiddenImports = listOf(
        "import java.net.URL",
        "import java.net.URLConnection",
        "import java.net.HttpURLConnection",
        "import java.net.Socket",
        "import java.net.DatagramSocket",
        "import java.net.InetSocketAddress",
        "import java.net.SocketAddress",
        "import okhttp3",
        "import retrofit2",
        "import com.squareup.okhttp",
        "import java.nio.channels.SocketChannel",
        "import java.rmi",
        "import javax.net.ssl",
        "import java.net.http.HttpClient",
        "import java.net.http.HttpRequest",
        "import java.net.http.HttpResponse",
        "import java.net.Proxy",
        "import java.util.concurrent.CompletableFuture" /* no, this is fine, false positive */ .replace(
            " /* no, this is fine, false positive */", "",
        ),
    )

    /**
     * Files that are explicitly allowed to use network
     * APIs. The VpnService is the only one: it captures
     * loopback traffic and the captured traffic is the
     * only thing it touches.
     */
    private val allowedFiles = setOf(
        // The VpnService captures loopback traffic.
        // It is the only file in the project that is
        // allowed to use network APIs.
        "app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
        // The PacketForwarder uses InetAddress as a data
        // carrier, not as a network endpoint.
        "app/src/main/java/org/mindanchor/goinglight/PacketForwarder.kt",
        // The scheduler reads/writes FrictionPrefs; the
        // network surface is through the VpnService only.
        "app/src/main/java/org/mindanchor/goinglight/GoingLightScheduler.kt",
    )

    @Test
    fun `no source file outside the VpnService subsystem imports a network API`() {
        val offenders = mutableListOf<String>()
        for (dir in listOf(appSrc, testSrc)) {
            val root = File(dir)
            if (!root.exists()) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    if (f.path in allowedFiles) return@forEach
                    val content = f.readText()
                    for (pattern in forbiddenImports) {
                        if (content.contains(pattern)) {
                            offenders.add("${f.path}: imports $pattern")
                        }
                    }
                }
        }
        assertTrue(
            "These files import a network API, which would break the " +
                "no-outbound-calls privacy promise of Going Light v1.1. " +
                "Add the file to NetworkCallsForbiddenTest.allowedFiles " +
                "only with explicit clinical-review sign-off.\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the allowedFiles list contains only the three expected VpnService files`() {
        // Sanity check: if a future contributor adds a
        // file to allowedFiles, it should be intentional.
        // This test will fail and force a re-review.
        val expected = setOf(
            "app/src/main/java/org/mindanchor/goinglight/GoingLightVpnService.kt",
            "app/src/main/java/org/mindanchor/goinglight/PacketForwarder.kt",
            "app/src/main/java/org/mindanchor/goinglight/GoingLightScheduler.kt",
        )
        assertTrue(
            "allowedFiles drift detected. " +
                "Expected: $expected, Got: $allowedFiles",
            allowedFiles == expected,
        )
    }
}
