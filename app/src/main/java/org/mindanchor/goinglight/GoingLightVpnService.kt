package org.mindanchor.goinglight

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * The local VpnService that drops mobile-internet traffic
 * during a Going Light window. The mechanism is the same
 * pattern NetGuard, Blokada, and FocusMe use: a local
 * virtual interface that captures *all* app traffic, with
 * a packet-by-packet decision in a pure function.
 *
 * Castelo 2025 (`docs/research/18`) is the evidence base.
 * The decision function is in [PacketForwarder]. This
 * class is the Android-side plumbing: establish the
 * interface, run the protect loop, teardown.
 *
 * The user grants consent once, via the OS-level VPN
 * dialog. The first-time UX copy that introduces the
 * dialog is in the home screen (a follow-up commit);
 * the clinical-review gate (item B+K) will block the
 * Composable until the wording is reviewed.
 *
 * @wording-reviewed — the protect() loop, the
 * isRunning() flag, and the VpnService.Builder configuration
 * are all surface-touching in the sense that they are the
 * things the user is consenting to. Any change here must
 * be reviewed.
 */
class GoingLightVpnService : VpnService() {

    private var interfaceDescriptor: ParcelFileDescriptor? = null
    private var protectThread: Thread? = null
    @Volatile var isRunning: Boolean = false
        private set

    /**
     * The decision function used by the protect loop.
     * Set via [setForwarder] before [start] is called; the
     * VpnService lifecycle is owned by the OS so the
     * forwarder cannot be passed in the Intent.
     */
    private lateinit var forwarder: PacketForwarder

    fun setForwarder(f: PacketForwarder) {
        forwarder = f
    }

    /**
     * Establish the local interface and start the protect
     * loop. Returns true on success.
     *
     * The address space is `10.66.66.0/24` — a private
     * range chosen to be unlikely to collide with the
     * user's home Wi-Fi or mobile hotspot. NetGuard uses
     * `10.1.10.0/24`; we picked `10.66.66.0/24` to make
     * it visually obvious in network logs that this is
     * the MindAnchor interface, not the user's network.
     *
     * The routes are 0.0.0.0/0 — catch-all. The decision
     * to forward or drop is made in the protect loop,
     * not at the routing layer, because the routes are
     * coarse-grained and the decision is per-packet.
     */
    fun start(): Boolean {
        if (isRunning) return true
        val builder = Builder()
            .setSession("Going Light")
            .addAddress("10.66.66.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.66.66.1")
            .setMtu(1500)
            .setBlocking(true)
        val pfd = builder.establish() ?: return false
        interfaceDescriptor = pfd
        isRunning = true
        protectThread = Thread({ runProtectLoop(pfd) }, "GoingLight-Protect").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /**
     * Stop the protect loop and tear down the interface.
     * Idempotent.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        protectThread?.join(1000)
        protectThread = null
        try {
            interfaceDescriptor?.close()
        } catch (_: Exception) {
            // best-effort; the OS will reclaim the interface
            // when the service is destroyed.
        }
        interfaceDescriptor = null
    }

    private fun runProtectLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        while (isRunning) {
            try {
                packet.clear()
                val n = input.read(packet.array())
                if (n <= 0) continue
                packet.limit(n)
                val decision = forwarder.decide(parsePacket(packet))
                when (decision) {
                    Verdict.FORWARD -> {
                        // Write the packet back; the OS will
                        // forward it to the real network.
                        output.write(packet.array(), 0, n)
                    }
                    Verdict.DROP -> {
                        // Silently drop. No write to output.
                        // The app sees a slow connection.
                    }
                    Verdict.RETURN_ERROR -> {
                        // Don't write; the OS closes the
                        // socket on the next keepalive
                        // timeout. Castelo's mechanism.
                    }
                }
            } catch (_: Exception) {
                // Loop termination is via isRunning = false.
            }
        }
    }

    /**
     * Minimal IP-header parse to extract the source UID,
     * protocol, destination address, and destination port.
     * Returns a [Packet] for [PacketForwarder.decide].
     *
     * Real UID extraction requires the /proc/net/tcp
     * lookup keyed on the source port; the VpnService API
     * doesn't expose it directly. The implementation here
     * reads the source IP from the IP header and uses it
     * as a stand-in. A full implementation would maintain
     * a (source_ip, source_port) -> uid table refreshed
     * every few seconds from /proc/net/tcp{6}.
     */
    private fun parsePacket(buf: ByteBuffer): Packet {
        if (buf.remaining() < 20) {
            return Packet(Packet.UID_UNRESOLVED, Packet.Protocol.ICMP, InetAddress.getByName("0.0.0.0"), 0)
        }
        val version = (buf.get(0).toInt() shr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4(buf)
            6 -> parseIpv6(buf)
            else -> Packet(Packet.UID_UNRESOLVED, Packet.Protocol.ICMP, InetAddress.getByName("0.0.0.0"), 0)
        }
    }

    private fun parseIpv4(buf: ByteBuffer): Packet {
        val protocolByte = buf.get(9).toInt() and 0xFF
        val protocol = when (protocolByte) {
            6 -> Packet.Protocol.TCP
            17 -> Packet.Protocol.UDP
            1 -> Packet.Protocol.ICMP
            else -> Packet.Protocol.ICMP
        }
        val dst = ByteArray(4)
        buf.position(16)
        buf.get(dst)
        val destAddr = InetAddress.getByAddress(dst)
        val destPort = if (protocol == Packet.Protocol.TCP || protocol == Packet.Protocol.UDP) {
            val pos = buf.position()
            buf.position(pos + 2)
            val p = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            buf.position(pos)
            p
        } else 0
        // The (source_ip, source_port) -> uid table is
        // not implemented in v1.1. The VpnService
        // represents every unattributed packet as
        // [Packet.UID_UNRESOLVED], which the
        // PacketForwarder fail-closes to DROP. The safe
        // default is "drop what we can't positively
        // identify."
        return Packet(Packet.UID_UNRESOLVED, protocol, destAddr, destPort)
    }

    private fun parseIpv6(buf: ByteBuffer): Packet {
        val protocolByte = buf.get(6).toInt() and 0xFF
        val protocol = when (protocolByte) {
            6 -> Packet.Protocol.TCP
            17 -> Packet.Protocol.UDP
            58 -> Packet.Protocol.ICMP
            else -> Packet.Protocol.ICMP
        }
        val dst = ByteArray(16)
        buf.position(24)
        buf.get(dst)
        val destAddr = InetAddress.getByAddress(dst)
        val destPort = if (protocol == Packet.Protocol.TCP || protocol == Packet.Protocol.UDP) {
            val pos = buf.position()
            buf.position(pos + 2)
            val p = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            buf.position(pos)
            p
        } else 0
        return Packet(Packet.UID_UNRESOLVED, protocol, destAddr, destPort)
    }

    override fun onRevoke() {
        stop()
        super.onRevoke()
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    /**
     * Handle the start/stop intents the
     * [GoingLightScheduler] sends. ACTION_START opens
     * the VPN; ACTION_STOP tears it down and stops the
     * service. GoingLightScheduler.disable uses
     * ACTION_STOP so disabling the schedule during an
     * active window immediately stops the VPN (CodeRabbit
     * audit 2026-08-08: the previous behavior cancelled
     * only the alarm, leaving the VPN running until the
     * next transition).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    val started = start()
                    if (!started) {
                        stopForeground(true)
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }
            }
            ACTION_STOP -> {
                stop()
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    companion object {
        /**
         * The Intent action that the OS uses to start a
         * VpnService. The launcher is the entry point
         * for the user-facing consent dialog.
         */
        const val ACTION_START = "org.mindanchor.goinglight.START"
        const val ACTION_STOP = "org.mindanchor.goinglight.STOP"
    }
}
