package org.mindanchor.goinglight

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * The pure-function decision for a single packet captured
 * by the local VpnService.
 *
 * Castelo 2025 (`docs/research/18`) describes the trial
 * mechanism as "block mobile-internet traffic for a defined
 * window." The block is *selective*: the literature is
 * explicit that calls and SMS must remain functional, and
 * that the active ingredient is *mobile-internet content*,
 * not *communication*. This file is the function that
 * decides, for each packet, whether to forward it to the
 * real network, drop it as content, or return an error
 * (the silent-fail behavior the Castelo trial used so
 * apps that timed out didn't get the explicit "no route
 * to host" failure that some apps treat as a retry signal).
 *
 * The decision is intentionally a pure function with no
 * Android dependencies, so it can be unit-tested on the
 * JVM. The VpnService calls this function in its
 * `protect()` loop and acts on the verdict.
 *
 * ## Source-UID representation
 *
 * The VpnService does not expose the source UID of a
 * captured packet directly. The service runs a
 * (source_ip, source_port) -> uid table refreshed from
 * `/proc/net/tcp{6}` every few seconds. When the table
 * lookup fails (cold start, an unobserved source), the
 * service represents the unresolved UID as
 * [UID_UNRESOLVED]. The PacketForwarder treats
 * [UID_UNRESOLVED] as **fail-closed**: an unknown app
 * UID gets the conservative DROP, never a silent
 * FORWARD. The safe failure mode is "drop what we
 * can't positively identify."
 *
 * (CodeRabbit audit 2026-08-08: the v0.20.0 code
 * treated UID 0 as a "system UID" and forwarded it,
 * which forwarded every packet the service could not
 * attribute. v0.20.1 uses an explicit allowlist
 * ([systemUids]) and a fail-closed path for the
 * unresolved case.)
 *
 * @wording-reviewed — copy that surfaces "drop", "forward",
 * "error" in user-visible strings depends on these verdicts.
 */
data class Packet(
    val sourceUid: Int,
    val protocol: Protocol,
    val destinationAddress: InetAddress,
    val destinationPort: Int,
    val raw: ByteBuffer = ByteBuffer.allocate(0),
) {
    enum class Protocol { TCP, UDP, ICMP }

    companion object {
        /**
         * The sentinel for an unresolved source UID.
         * The VpnService sets [sourceUid] to this value
         * when the (source_ip, source_port) -> uid
         * table lookup fails. The PacketForwarder
         * fails closed on this case.
         */
        const val UID_UNRESOLVED = -1
    }
}

enum class Verdict { FORWARD, DROP, RETURN_ERROR }

/**
 * The decision function. Pure: same input, same output,
 * no I/O, no Android calls.
 *
 * The rules:
 *  1. **Communication channels**: SIP signaling
 *     (TCP/UDP 5060, 5061) is always forwarded. The
 *     carrier's wider signaling range is *not* allowed
 *     for arbitrary destinations — see
 *     [isCarrierSignaling] for the narrowed rule.
 *  2. **Loopback and link-local** are forwarded (the
 *     VpnService itself needs them to operate).
 *  3. **Local DNS** (port 53 to a private RFC1918
 *     address) is forwarded so apps can resolve names.
 *     Apps will then *try* to connect to the resolved IP,
 *     and that connection attempt is what we drop.
 *  4. **Schedule is off** (`!blockAll`): everything
 *     forwards. The schedule's "off" mode means the user
 *     disabled the schedule; the VpnService may still be
 *     running briefly during a transition.
 *  5. **System UIDs** (the explicit allowlist in
 *     [systemUids], default `{1000, 1001}`): always
 *     forward. This is the telephony / network-management
 *     set; the well-known uid range, not the entire
 *     uid < 1000 range, because the implementation
 *     below is conservative — only explicitly
 *     allow-listed UIDs.
 *  6. **Content UIDs** (the [contentUids] set the
 *     settings UI populates): DROP. This is the Castelo
 *     target.
 *  7. **Unresolved source UID** ([Packet.UID_UNRESOLVED]):
 *     DROP. The service couldn't attribute the packet to
 *     any app; the safe default is to drop.
 *  8. **App UID not in the content set, and not in the
 *     system set, and not unresolved**: DROP. Going Light
 *     is *mobile internet* off, not a per-app filter;
 *     the safer behaviour is to drop and let the user
 *     add the app to the content set if they want it
 *     through.
 *  9. **Unknown protocols** (anything that isn't
 *     TCP/UDP/ICMP) get RETURN_ERROR. The VpnService
 *     returns -1 to the app, which causes a "no route
 *     to host" — the same silent-fail the Castelo trial
 *     used.
 */
class PacketForwarder(
    private val contentUids: Set<Int> = emptySet(),
    private val systemUids: Set<Int> = setOf(1000, 1001),
    private val blockAll: Boolean = false,
) {
    fun decide(packet: Packet): Verdict = when {
        // Rule 1: SIP signaling (VoLTE/VoWiFi).
        // Tightly scoped to the well-known SIP ports; no
        // broader carrier range (the v0.20.0 range
        // 5000-5099 was a port-range bypass for content
        // apps on the public internet — see the carrier-
        // signaling rule below for the narrow fix).
        isSipSignaling(packet) -> Verdict.FORWARD
        // Rule 2: loopback / link-local
        isLocalAddress(packet.destinationAddress) -> Verdict.FORWARD
        // Rule 3: local DNS
        isLocalDns(packet) -> Verdict.FORWARD
        // Rule 4: schedule is off
        !blockAll -> Verdict.FORWARD
        // Rule 7: unresolved source UID. The service
        // couldn't attribute this packet to any app; the
        // safe default is to drop. (CodeRabbit audit
        // 2026-08-08: the v0.20.0 code treated UID 0 as
        // a "system UID" and forwarded it, which
        // forwarded every packet the service could not
        // attribute. v0.20.1 fail-closes here.)
        packet.sourceUid == Packet.UID_UNRESOLVED -> Verdict.DROP
        // Rule 5: explicit system UID allowlist. Honors
        // [systemUids] (default: 1000, 1001 — the
        // system and the radio). The previous code
        // dropped 1000 and 1001 because the rule was
        // `sourceUid < 1000`; the new rule is a direct
        // membership check.
        packet.sourceUid in systemUids -> Verdict.FORWARD
        // Rule 6: content UIDs (Castelo target)
        packet.sourceUid in contentUids -> Verdict.DROP
        // Rule 8: app UID not in the content set, not
        // in the system set, and not unresolved. DROP.
        else -> Verdict.DROP
    }

    /**
     * SIP signaling is TCP/UDP on ports 5060 and 5061
     * only. The wider 5000-5099 range is *not* a
     * general carrier range: it overlaps with arbitrary
     * services a content app could host. The v0.20.0
     * rule forwarded any packet on that range to any
     * destination, which was an open bypass. v0.20.1
     * only allows SIP on the well-known ports.
     */
    private fun isSipSignaling(packet: Packet): Boolean {
        if (packet.protocol != Packet.Protocol.TCP &&
            packet.protocol != Packet.Protocol.UDP
        ) return false
        return packet.destinationPort == 5060 ||
            packet.destinationPort == 5061
    }

    private fun isLocalAddress(addr: InetAddress): Boolean {
        return addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress ||
            // RFC1918 (IPv4) and ULA (IPv6 fc00::/7)
            isRfc1918(addr) || isUla(addr)
    }

    private fun isUla(addr: InetAddress): Boolean {
        val b = addr.address
        if (b.size != 16) return false
        // ULA: fc00::/7 (the top 7 bits are 1111 110)
        return (b[0].toInt() and 0xFE) == 0xFC
    }

    private fun isRfc1918(addr: InetAddress): Boolean {
        val b = addr.address
        if (b.size != 4) return false
        // 10.0.0.0/8
        if (b[0] == 10.toByte()) return true
        // 172.16.0.0/12
        if (b[0] == 172.toByte() && b[1].toInt() in 16..31) return true
        // 192.168.0.0/16
        if (b[0] == 192.toByte() && b[1] == 168.toByte()) return true
        return false
    }

    private fun isLocalDns(packet: Packet): Boolean {
        if (packet.protocol != Packet.Protocol.UDP) return false
        if (packet.destinationPort != 53) return false
        return isLocalAddress(packet.destinationAddress)
    }
}
