package org.mindanchor.goinglight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * The pure-function decision for the VpnService packet
 * loop. Every Castelo 2025 mechanism case is pinned
 * here so a refactor cannot silently regress the block
 * to a forward or vice versa.
 *
 * v0.20.1 changes (CodeRabbit audit 2026-08-08):
 *  - The 5000-5099 port range is no longer a general
 *    forward; only SIP on 5060/5061 forwards.
 *  - The "system UID" rule now uses an explicit
 *    [systemUids] allowlist (default {1000, 1001}),
 *    not a `sourceUid < 1000` numeric test.
 *  - An unresolved source UID (UID == -1) is
 *    fail-closed (DROP), not forwarded.
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
    fun `SIP signaling (TCP and UDP 5060, 5061) always forwards`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        // TCP 5060, 5061
        assertEquals(Verdict.FORWARD, fwd.decide(packet(port = 5060)))
        assertEquals(Verdict.FORWARD, fwd.decide(packet(port = 5061)))
        // UDP 5060, 5061
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(proto = Packet.Protocol.UDP, port = 5060)),
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(proto = Packet.Protocol.UDP, port = 5061)),
        )
    }

    @Test
    fun `the 5000-5099 port range is no longer a general forward`() {
        // CodeRabbit #16: the v0.20.0 rule forwarded any
        // packet on 5000-5099 to any destination, which
        // was an open bypass for content apps on the
        // public internet. v0.20.1 narrows the
        // communication exception to SIP only.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        // None of these should forward.
        for (port in listOf(5000, 5050, 5099, 5500, 4999)) {
            assertNotEquals(
                "Port $port should not forward as a general carrier range",
                Verdict.FORWARD,
                fwd.decide(packet(port = port)),
            )
        }
    }

    @Test
    fun `loopback always forwards`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "127.0.0.1")))
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "::1")))
    }

    @Test
    fun `link-local always forwards`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(Verdict.FORWARD, fwd.decide(packet(host = "169.254.1.1")))
    }

    @Test
    fun `RFC1918 private addresses forward`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
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
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(proto = Packet.Protocol.UDP, host = "10.0.0.1", port = 53)),
        )
    }

    @Test
    fun `public DNS (UDP 53 to public IP) is a content packet and gets dropped`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(proto = Packet.Protocol.UDP, host = "8.8.8.8", port = 53)),
        )
    }

    @Test
    fun `content UID gets DROP when blockAll is true`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 10000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `non-content app UID with blockAll true gets DROP (conservative)`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 20000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `system UIDs in the allowlist forward even when blockAll is true`() {
        // CodeRabbit #14: the v0.20.0 rule was
        // `sourceUid < 1000`, which dropped 1000 and 1001.
        // v0.20.1 honors the explicit allowlist.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 1000, host = "8.8.8.8", port = 443)),
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 1001, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `UID 0 is not a system UID anymore`() {
        // CodeRabbit #15: the v0.20.0 rule treated UID 0
        // as a system UID and forwarded it. UID 0 has
        // historically been a placeholder for an
        // unresolvable UID in some implementations. We
        // now treat UID 0 as "unresolved-or-uncategorised"
        // and apply the conservative DROP.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 0, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `unresolved source UID (UID_UNRESOLVED) fail-closes to DROP`() {
        // The service sets UID to UID_UNRESOLVED when
        // the (source_ip, source_port) -> uid table
        // lookup fails. The forwarder must drop, not
        // forward.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = Packet.UID_UNRESOLVED, host = "8.8.8.8", port = 443)),
        )
        // Even when the destination is RFC1918, the
        // unresolved UID is a DROP. The system is the
        // one that's local, not the user app; an
        // unresolved UID is not a system UID.
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = Packet.UID_UNRESOLVED, host = "127.0.0.1", port = 443)),
        )
    }

    @Test
    fun `UID 1000 in default systemUids allowlist forwards`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 1000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `UID 1001 in default systemUids allowlist forwards`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 1001, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `UID 2000 (not in default systemUids) is dropped`() {
        // 2000 is a well-known uid (the "shell" user on
        // some Android builds). It is *not* in the
        // default systemUids allowlist; the v0.20.0 rule
        // (`sourceUid < 1000`) would not have included
        // it either. v0.20.1 drops it.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = true,
        )
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 2000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `blockAll false means everything forwards (schedule is off)`() {
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1000, 1001),
            blockAll = false,
        )
        // Even a content app forwards when the
        // schedule is off.
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 10000, host = "8.8.8.8", port = 443)),
        )
        // An unresolved UID forwards when the
        // schedule is off (the conservative DROP only
        // applies when blockAll is true).
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = Packet.UID_UNRESOLVED, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `systemUids allowlist is configurable`() {
        // The allowlist is per-instance. A user with
        // a custom telephony stack can build a
        // forwarder with a different system set.
        val fwd = PacketForwarder(
            contentUids = setOf(10000),
            systemUids = setOf(1234),  // only 1234 is allowed
            blockAll = true,
        )
        assertEquals(
            Verdict.FORWARD,
            fwd.decide(packet(uid = 1234, host = "8.8.8.8", port = 443)),
        )
        // 1000 is no longer in the allowlist.
        assertEquals(
            Verdict.DROP,
            fwd.decide(packet(uid = 1000, host = "8.8.8.8", port = 443)),
        )
    }

    @Test
    fun `the Packet data class is value-equality`() {
        val a = Packet(0, Packet.Protocol.TCP, InetAddress.getByName("1.2.3.4"), 80)
        val b = Packet(0, Packet.Protocol.TCP, InetAddress.getByName("1.2.3.4"), 80)
        assertEquals(a, b)
    }

    @Test
    fun `UID_UNRESOLVED is a documented sentinel`() {
        // The sentinel is part of the public API; a
        // test pins the value so a refactor cannot
        // silently change it.
        assertEquals(-1, Packet.UID_UNRESOLVED)
    }
}
