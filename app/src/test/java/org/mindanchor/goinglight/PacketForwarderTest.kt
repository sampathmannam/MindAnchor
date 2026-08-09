package org.mindanchor.goinglight

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * The pure-function decision for the VpnService packet
 * loop. Every Castelo 2025 mechanism case is pinned
 * here so a refactor cannot silently regress the block
 * to a forward or vice versa.
 *
 * @see docs/research/18 for the evidence base.
 */
class PacketForwarderTest {

    private fun packet(
        uid: Int = 10000,
        proto: Packet.Protocol = Packet.Protocol.TCP,
        host: String = "8.8.8.8",
        port: Int = 443,
    ) = Packet(uid, proto, InetAddress.getByName(host), port)

    @Test
    fun `communication channels (SIP) always forward`() {
        // TCP 5060/5061 is SIP — VoLTE/VoWiFi signaling.
        // The literature is unambiguous: communication must
        // remain functional during the block.
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(Verdict.FORWARD, fwd.decide(packet(port = 5060)))
        assertEquals(Verdict.FORWARD, fwd.decide(packet(port = 5061)))
    }

    @Test
    fun `carrier signaling port range 5000-5099 always forwards`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        // 5000, 5099, 5050 — all in range
        for (port in listOf(5000, 5050, 5099)) {
            assertEquals(
                "Port $port should forward as carrier signaling",
                Verdict.FORWARD,
                fwd.decide(packet(port = port)),
            )
        }
    }

    @Test
    fun `loopback always forwards`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "127.0.0.1")))
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "::1")))
    }

    @Test
    fun `link-local always forwards`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        // 169.254.0.0/16 — link-local
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "169.254.1.1")))
    }

    @Test
    fun `RFC1918 private addresses forward`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        for (host in listOf("10.0.0.1", "172.16.0.1", "192.168.1.1")) {
            assertEquals(
                "$host should forward as RFC1918",
                Verdict.FORWARD,
                fwd.decide(packet(host = host)),
            )
        }
    }

    @Test
    fun `local DNS (UDP 53 to private IP) forwards`() {
        // Apps need to resolve names. The resolution attempt
        // is what we drop, not the resolver itself.
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(proto = Packet.Protocol.UDP, host = "10.0.0.1", port = 53)),
        )
    }

    @Test
    fun `public DNS (UDP 53 to public IP) is a content packet and gets dropped`() {
        // The 8.8.8.8 DNS query is what the app uses to
        // find content servers. We want to drop it: forcing
        // the app to use a private resolver that ultimately
        // can't reach the content is the Castelo mechanism.
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(proto = Packet.Protocol.UDP, host = "8.8.8.8", port = 53)),
        )
    }

    @Test
    fun `content UID gets DROP when blockAll is true`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 10000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `non-content app UID with blockAll true gets DROP (conservative)`() {
        // The PacketForwarder conservatively DROPs any
        // app UID not in the content set when blockAll
        // is on. The intent: apps that the user has
        // not flagged as content are still app traffic,
        // and Going Light is *mobile internet* off,
        // not a per-app filter.
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 20000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `system UID (uid less than 1000) forwards even when blockAll is true`() {
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = true)
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 0, host = "8.8.8.8", port = 443)),
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 100, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `blockAll false means everything forwards (schedule is off)`() {
        // When the schedule is disabled, the VpnService
        // either isn't running, or if it is (e.g. mid-
        // teardown) it must let everything through.
        val fwd = PacketForwarder(contentUids = setOf(10000), blockAll = false)
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 10000, host = "8.8.8.8", port = 443)),
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 20000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `the Packet data class is value-equality`() {
        val a = Packet(0, Packet.Protocol.TCP, InetAddress.getByName("1.2.3.4"), 80)
        val b = Packet(0, Packet.Protocol.TCP, InetAddress.getByName("1.2.3.4"), 80)
        assertEquals(a, b)
    }
}
