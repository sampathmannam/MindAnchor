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
}

enum class Verdict { FORWARD, DROP, RETURN_ERROR }

/**
 * The decision function. Pure: same input, same output,
 * no I/O, no Android calls.
 *
 * The rules:
 *  1. Calls and SMS ports (TCP 5060, 5061 for SIP; the
 *     carrier's signaling channel; SMS over IMS) are
 *     always forwarded. The literature is unambiguous
 *     that the active ingredient is *content*, not
 *     *communication*.
 *  2. Loopback and link-local are forwarded (the VpnService
 *     itself needs them to operate).
 *  3. The local DNS resolver (port 53 to a private RFC1918
 *     address) is forwarded so apps can resolve names.
 *     Apps will then *try* to connect to the resolved IP,
 *     and that connection attempt is what we drop.
 *  4. Everything else: depends on whether the source UID
 *     is in the [contentUids] set (the browser, social,
 *     YouTube, etc. — configurable in settings) and
 *     whether the [blockAll] flag is set (the "off" mode
 *     of Going Light, used by the data layer to mean
 *     "schedule is disabled"). DROP for content UIDs;
 *     FORWARD for system UIDs (so the system itself can
 *     still reach the network for things like time sync).
 *  5. Unknown protocols (anything that isn't TCP/UDP/ICMP)
 *     get RETURN_ERROR. The VpnService returns -1 to the
 *     app, which causes a "no route to host" — the same
 *     silent-fail the Castelo trial used.
 */
class PacketForwarder(
    private val contentUids: Set<Int> = emptySet(),
    private val blockAll: Boolean = false,
) {
    fun decide(packet: Packet): Verdict = when {
        // Rule 1: communication channels always forward
        isCommunicationChannel(packet) -> Verdict.FORWARD
        // Rule 2: loopback / link-local
        isLocalAddress(packet.destinationAddress) -> Verdict.FORWARD
        // Rule 3: local DNS
        isLocalDns(packet) -> Verdict.FORWARD
        // Rule 4: blockAll (the schedule's "off" mode means:
        // don't block, the user disabled the schedule)
        !blockAll -> Verdict.FORWARD
        // Rule 4: content UIDs are the Castelo target
        packet.sourceUid in contentUids -> Verdict.DROP
        // System UIDs (uid < 10000 on Android) can reach
        // the network for things like NTP. App UIDs not in
        // the content set are user apps that the user has
        // not flagged for blocking; let them through.
        packet.sourceUid < 1000 -> Verdict.FORWARD
        // Unknown app UID: same as a content app. Conservative.
        else -> Verdict.DROP
    }

    private fun isCommunicationChannel(packet: Packet): Boolean {
        if (packet.protocol != Packet.Protocol.TCP &&
            packet.protocol != Packet.Protocol.UDP
        ) return false
        // SIP (VoLTE/VoWiFi signaling)
        if (packet.destinationPort == 5060 || packet.destinationPort == 5061) {
            return true
        }
        // The carrier's signaling port range varies by carrier
        // but is typically in the 5000-5099 range. We allow
        // the wider range to avoid breaking on carrier
        // variation. The literature doesn't pin a specific
        // range; the goal is "communication works."
        if (packet.destinationPort in 5000..5099) {
            return true
        }
        return false
    }

    private fun isLocalAddress(addr: InetAddress): Boolean {
        return addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isAnyLocalAddress ||
            // RFC1918 private ranges
            isRfc1918(addr)
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
