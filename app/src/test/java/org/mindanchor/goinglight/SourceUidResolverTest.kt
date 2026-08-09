package org.mindanchor.goinglight

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.InetAddress

/**
 * The /proc/net/tcp{6} resolver for the GoingLight
 * VpnService. CodeRabbit audit #12 (2026-08-08):
 * the v0.20.0 implementation used UID 0 for every
 * packet ("real UID extraction is a follow-up"),
 * which the PacketForwarder forwarded as a system
 * UID. v0.20.1 round 2 implements the resolver.
 *
 * The resolver's design:
 *  - Reads /proc/net/tcp and /proc/net/tcp6 on each
 *    call. Both files are small (one row per open
 *    socket, typically under 200 rows).
 *  - The captured packet's source IP is in network
 *    byte order (from the IP header). The /proc/net/tcp
 *    IP is little-endian hex. The resolver canonicalizes
 *    both to a dotted-quad or colon-hex form so the
 *    lookup is a direct match.
 *  - Returns [Packet.UID_UNRESOLVED] on any failure
 *    (file unreadable, source not found). The
 *    PacketForwarder fail-closes to DROP on
 *    UID_UNRESOLVED.
 */
class SourceUidResolverTest {

    private fun ip4(s: String) = InetAddress.getByName(s)
    private fun ip6(s: String) = InetAddress.getByName(s)

    private fun fixture(content: String): String {
        val f = File.createTempFile("tcp", "test")
        f.writeText(content)
        f.deleteOnExit()
        return f.path
    }

    @Test
    fun `parses a single row`() {
        // /proc/net/tcp row "0100007F:0050" = IP 0x7F000001
        // (little-endian) = 127.0.0.1, port 0x0050 = 80.
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        assertEquals(0, resolver.resolve(ip4("127.0.0.1"), 80))
    }

    @Test
    fun `parses multiple rows`() {
        // The kernel /proc/net/tcp table can list a
        // (address, port) pair more than once. The
        // production resolver uses last-write-wins
        // (the most recent bind is the answer for a
        // live packet that just came through), so the
        // resulting map is `{127.0.0.1:80 -> 1001,
        // 127.0.0.1:443 -> 1000}` after the file is
        // parsed. (The test in v0.20.0 originally
        // asserted first-row-wins; that was a
        // last-write-wins regression in
        // round-2's "first-match-wins" attempt —
        // the live-packet case wants the most
        // recent bind, which is the last row in
        // the /proc snapshot. The test is fixed to
        // match.)
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345
                           1: 0100007F:01BB 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 12346
                           2: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1001        0 12347
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        // Last-write-wins for the (127.0.0.1, 80) endpoint.
        assertEquals(1001, resolver.resolve(ip4("127.0.0.1"), 80))
        // Only one row for (127.0.0.1, 443).
        assertEquals(1000, resolver.resolve(ip4("127.0.0.1"), 443))
    }

    @Test
    fun `parses user-app UID`() {
        // /proc/net/tcp row "0F00000A:8E62" = IP
        // 0x0A00000F (little-endian of 0F00000A) = 10.0.0.15,
        // port 0x8E62 = 36450.
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0F00000A:8E62 00000000:0000 0A 00000000:00000000 00:00000000 00000000  10123        0 12345
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        // The IP in the row is 10.0.0.15; the resolver
        // canonicalizes to dotted-quad. The call site
        // (parseIpv4) provides the IP from the IP
        // header in network byte order, which is also
        // 10.0.0.15.
        assertEquals(10123, resolver.resolve(ip4("10.0.0.15"), 36450))
    }

    @Test
    fun `unresolved source returns UID_UNRESOLVED`() {
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        // A different port is not in the file.
        assertEquals(Packet.UID_UNRESOLVED, resolver.resolve(ip4("127.0.0.1"), 9999))
        // A different IP is not in the file.
        assertEquals(Packet.UID_UNRESOLVED, resolver.resolve(ip4("10.0.0.1"), 80))
    }

    @Test
    fun `nonexistent file returns UID_UNRESOLVED`() {
        val resolver = SourceUidResolver(
            tcp4Path = "/nonexistent/foo",
            tcp6Path = "/nonexistent/bar",
        )
        // Both files are missing; the resolver returns
        // UID_UNRESOLVED for every lookup.
        assertEquals(Packet.UID_UNRESOLVED, resolver.resolve(ip4("127.0.0.1"), 80))
        assertEquals(Packet.UID_UNRESOLVED, resolver.resolve(ip6("::1"), 443))
    }

    @Test
    fun `malformed lines are skipped, not fatal`() {
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345
                           this is not a tcp row
                           1: 0100007F:01BB 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 12346
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        // The malformed line is skipped; the two
        // well-formed rows are still parsed.
        assertEquals(0, resolver.resolve(ip4("127.0.0.1"), 80))
        assertEquals(1000, resolver.resolve(ip4("127.0.0.1"), 443))
    }

    @Test
    fun `IPv6 lookup works`() {
        // /proc/net/tcp6 row. IPv6 IPs are 32 hex
        // digits in network byte order (no endian
        // swap). For ::1 the row is:
        //   00000000000000000000000000000001:01BB
        val path6 = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 00000000000000000000000000000001:01BB 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 12345
            """,
        )
        val resolver = SourceUidResolver(
            tcp4Path = "/nonexistent",
            tcp6Path = path6,
        )
        assertEquals(1000, resolver.resolve(ip6("::1"), 443))
    }

    @Test
    fun `header line is skipped`() {
        val path = fixture(
            """  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                           0: 0100007F:0050 00000000:0000 0A 00000000:00000000 00:00000000 00000000     0        0 12345
            """,
        )
        val resolver = SourceUidResolver(tcp4Path = path, tcp6Path = "/nonexistent")
        // The header line is not a real row; the
        // resolver should skip it.
        assertEquals(0, resolver.resolve(ip4("127.0.0.1"), 80))
    }
}
