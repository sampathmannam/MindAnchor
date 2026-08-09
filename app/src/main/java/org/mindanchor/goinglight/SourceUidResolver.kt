package org.mindanchor.goinglight

import java.io.File
import java.net.InetAddress

/**
 * Resolves a source UID from a (source_ip, source_port)
 * pair, by reading `/proc/net/tcp` and `/proc/net/tcp6`.
 *
 * CodeRabbit audit #12 (2026-08-08): the v0.20.0
 * GoingLightVpnService used UID 0 for every packet
 * ("real UID extraction is a follow-up"). The
 * PacketForwarder treated UID 0 as a system UID and
 * forwarded it. v0.20.1 round 2 implements the resolver.
 *
 * ## How it works
 *
 * Each row in `/proc/net/tcp` has the form:
 *
 *     sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
 *
 * The `local_address` is `IP:HEX-PORT`. The IP is
 * 8 hex digits in *little-endian byte order*: a row
 * showing `0100007F:0050` means IP `0x7F000001` =
 * 127.0.0.1 and port 0x0050 = 80. The `uid` is the
 * decimal Linux UID.
 *
 * The captured packet's source IP, in contrast, is in
 * the IP header in *network byte order* (big-endian).
 * The resolver normalizes the /proc/net/tcp form to
 * network byte order so the lookup is a direct
 * (ip, port) match.
 *
 * ## Failure modes
 *
 * The resolver returns [Packet.UID_UNRESOLVED] (= -1)
 * when:
 *  - The (source_ip, source_port) pair is not in the
 *    map (the source opened the socket before the
 *    resolver was started, or the source is the
 *    system itself).
 *  - The /proc/net/tcp* files cannot be read
 *    (permission denied, kernel bug).
 *
 * The PacketForwarder fail-closes on UID_UNRESOLVED
 * (CodeRabbit audit #15). The safe default is to
 * drop, not forward.
 */
class SourceUidResolver(
    /**
     * The path to the IPv4 TCP table. Tests can
     * substitute a fixture file.
     */
    private val tcp4Path: String = "/proc/net/tcp",
    /**
     * The path to the IPv6 TCP table. Tests can
     * substitute a fixture file.
     */
    private val tcp6Path: String = "/proc/net/tcp6",
) {

    /**
     * Resolve the source UID for a packet. The
     * (sourceIp, sourcePort) is matched against the
     * current contents of /proc/net/tcp and
     * /proc/net/tcp6.
     *
     * @param sourceIp the packet's source IP, in
     *   network byte order (the IP header's
     *   representation).
     * @param sourcePort the packet's source port.
     * @return the source UID, or [Packet.UID_UNRESOLVED]
     *   when the source is not found or the file is
     *   unreadable.
     */
    fun resolve(sourceIp: InetAddress, sourcePort: Int): Int {
        val ipv4 = resolveFromFile(tcp4Path, isIpv6 = false)
        // The /proc/net/tcp key is in little-endian
        // hex. The InetAddress bytes are in network
        // byte order. We canonicalize the lookup key
        // to a dotted-quad (IPv4) or colon-hex (IPv6)
        // form so the application-level call site
        // doesn't need to know the encoding.
        val canonicalKey = canonicalize(sourceIp, sourcePort)
        val found = ipv4[canonicalKey]
        if (found != null) return found
        val ipv6 = resolveFromFile(tcp6Path, isIpv6 = true)
        val found6 = ipv6[canonicalKey]
        return found6 ?: Packet.UID_UNRESOLVED
    }

    /**
     * Build the canonical lookup key for an
     * (ip, port) pair. The key format is
     * `IP-STRING:PORT-DECIMAL` so the application
     * call site (which holds the IP in network byte
     * order) can use the same format regardless of
     * the /proc/net/tcp encoding.
     *
     * Examples:
     *  - IPv4 10.0.0.15 port 80 -> "10.0.0.15:80"
     *  - IPv6 ::1 port 443    -> "0:0:0:0:0:0:0:1:443"
     *    (the canonical IPv6 form for "::1"; we
     *    suppress colons in the IP so the lookup
     *    matches the /proc/net/tcp6 key, which has
     *    no colons either).
     */
    private fun canonicalize(ip: InetAddress, port: Int): String {
        val b = ip.address
        return if (b.size == 4) {
            // IPv4: dotted-quad.
            "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}:$port"
        } else {
            // IPv6: 8 short hex values, no colons.
            val parts = (0 until 8).map { i ->
                val high = b[i * 2].toInt() and 0xFF
                val low = b[i * 2 + 1].toInt() and 0xFF
                ((high shl 8) or low).toString(16)
            }
            "${parts.joinToString(":")}:$port"
        }
    }

    /**
     * Build the lookup key from a /proc/net/tcp row.
     * The /proc/net/tcp IP is little-endian hex, so we
     * re-encode it as a canonical key.
     *
     * Example: row `0100007F:0050` -> canonical key
     * `127.0.0.1:80`.
     */
    private fun tcpKeyToCanonical(tcpKey: String, isIpv6: Boolean): String? {
        val parts = tcpKey.split(":")
        if (parts.size != 2) return null
        val ipHex = parts[0]
        // /proc/net/tcp encodes the port as hex
        // (e.g. 0050 = 80, 01BB = 443), not decimal.
        val port = parts[1].toIntOrNull(16) ?: return null
        val ipBytes = if (isIpv6) {
            // 32 hex digits = 16 bytes.
            if (ipHex.length != 32) return null
            val bytes = ByteArray(16)
            for (i in 0 until 16) {
                bytes[i] = ipHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            // /proc/net/tcp6 IPs are in network byte
            // order (no endian swap for IPv6).
            bytes
        } else {
            // 8 hex digits = 4 bytes. /proc/net/tcp
            // encodes the IP in little-endian byte
            // order; we re-encode in network byte order.
            if (ipHex.length != 8) return null
            ByteArray(4).also {
                it[0] = ipHex.substring(6, 8).toInt(16).toByte()
                it[1] = ipHex.substring(4, 6).toInt(16).toByte()
                it[2] = ipHex.substring(2, 4).toInt(16).toByte()
                it[3] = ipHex.substring(0, 2).toInt(16).toByte()
            }
        }
        return canonicalize(InetAddress.getByAddress(ipBytes), port)
    }

    /**
     * Parse one of the /proc/net/tcp{6} files into a
     * (canonical key) -> uid map. Returns an empty
     * map on any failure.
     *
     * The format of each line is whitespace-separated
     * columns; the columns we care about are:
     *   [1] local_address (IP:HEX-PORT, little-endian hex)
     *   [7] uid (decimal)
     */
    internal fun resolveFromFile(
        path: String,
        isIpv6: Boolean,
    ): Map<String, Int> {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                return emptyMap()
            }
            val result = HashMap<String, Int>()
            file.bufferedReader().useLines { lines ->
                // The first line is the header; skip.
                var first = true
                for (line in lines) {
                    if (first) {
                        first = false
                        continue
                    }
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 8) continue
                    val canonical = tcpKeyToCanonical(parts[1], isIpv6) ?: continue
                    // uid is decimal, port is hex
                    val uid = parts[7].toIntOrNull() ?: continue
                    // Last-write-wins: the kernel
                    // /proc/net/tcp table can list a
                    // (address, port) pair more than
                    // once (e.g. when a port has been
                    // re-bound across user ids); the
                    // last entry is the most recent
                    // bind, which is the answer we
                    // want for a source-uid check on a
                    // packet that just came through.
                    // (The first match would be the
                    // oldest bind, which is the wrong
                    // answer for a live packet.)
                    result[canonical] = uid
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
