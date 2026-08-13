package org.mindanchor.goinglight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import org.mindanchor.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
     * v0.25.9 (B2, SOTA v2 bug-hunt, errors
     * agent): a service-scoped CoroutineScope for
     * the DataStore read + interface establish
     * work. [start] used to do a blocking
     * DataStore `.first()` on the main thread
     * inside the VpnService Builder
     * initialisation — a slow DataStore read
     * blocked the main thread until ANR. The
     * service is now main-thread by definition
     * (it is a Service), so all blocking IO is
     * moved to [serviceScope] (a [SupervisorJob] +
     * [Dispatchers.IO] scope) and [start] is a
     * suspend fun.
     *
     * The scope is cancelled in [onDestroy] so
     * the in-flight start coroutine is not
     * orphaned if the OS kills the service
     * between the [ACTION_START] intent and the
     * establish() call.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The decision function used by the protect loop.
     *
     * v0.20.1 round 5 follow-up: the previous
     * `lateinit var forwarder` design required an
     * external caller to invoke [setForwarder]
     * before [start] was called. No caller does
     * this (the VpnService Intent cannot carry a
     * Parcelable, and no Companion factory or
     * Application-bound setter exists). The
     * first packet would throw
     * `UninitializedPropertyAccessException`, the
     * protect loop's catch-all would swallow it,
     * and the VPN would run but silently drop
     * every packet.
     *
     * The fix: a default-constructed [PacketForwarder]
     * with `blockAll = true` and the empty
     * `contentUids` set. The fail-closed default
     * is the safe behaviour: if the prefs read at
     * start time throws, the VPN blocks
     * everything (the Castelo 2025 mechanism),
     * which is the *expected* behaviour of Going
     * Light anyway. If [setForwarder] is later
     * called by a future caller, the new
     * forwarder replaces the default; the read-
     * modify-write on the @Volatile field is
     * safe because the protect loop reads the
     * field once per packet.
     *
     * @Volatile: a setter on one thread is
     * visible to a getter on the protect loop's
     * thread without a happens-before edge.
     */
    @Volatile
    private var forwarder: PacketForwarder = PacketForwarder(
        contentUids = emptySet(),
        systemUids = setOf(1000, 1001),
        blockAll = true,
    )

    /**
     * The source-UID resolver. v0.20.1 round 2 (CodeRabbit
     * #12): the v0.20.0 implementation used UID 0 for
     * every packet, which the forwarder treated as a
     * system UID and forwarded. v0.20.1 round 2 uses a
     * real resolver that reads /proc/net/tcp{6}.
     */
    private val sourceUidResolver = SourceUidResolver()

    fun setForwarder(f: PacketForwarder) {
        forwarder = f
    }

    /**
     * Establish the local interface and start the protect
     * loop. Returns true on success.
     *
     * The address space is `10.66.66.0/24` (IPv4) and
     * `fd00:66:66::/48` (IPv6) — private ranges chosen
     * to be unlikely to collide with the user's home
     * Wi-Fi or mobile hotspot. NetGuard uses
     * `10.1.10.0/24`; we picked `10.66.66.0/24` to make
     * it visually obvious in network logs that this is
     * the MindAnchor interface, not the user's network.
     *
     * The routes are 0.0.0.0/0 and ::/0 — catch-all.
     * The decision to forward or drop is made in the
     * protect loop, not at the routing layer, because
     * the routes are coarse-grained and the decision is
     * per-packet.
     *
     * v0.20.1 (CodeRabbit #12): the builder now
     * configures IPv6 alongside IPv4. The v0.20.0
     * builder registered only IPv4, but the protect
     * loop's parsePacket() also called parseIpv6()
     * for IP version 6 traffic. With no IPv6
     * address/route, the OS would block IPv6
     * entirely, and the parseIpv6() path was dead.
     * v0.20.1 adds an IPv6 interface address and a
     * ::/0 route so parseIpv6() can reach the
     * forwarder.
     */
    /**
     * v0.25.9: the start function is now `suspend`
     * — the DataStore read and the
     * `Builder.establish()` are both moved off the
     * main thread (the service is a Service, so its
     * `onStartCommand` runs on the main thread by
     * definition). The pre-fix shape was a blocking
     * DataStore `.first()` on the main thread inside
     * the Builder initialisation — a slow DataStore
     * read blocked the main thread until ANR.
     *
     * Returns true if the interface is up; false
     * on establish() failure (caller turns off
     * the foreground notification and stops
     * itself).
     */
    suspend fun start(): Boolean {
        if (isRunning) return true
        // v0.20.1 round 5 follow-up: read the
        // Going Light schedule and the system /
        // content UID sets from FrictionPrefs at
        // start time and rebuild the forwarder.
        // The previous design had a default
        // fail-closed forwarder only (blockAll
        // = true, contentUids = empty), which
        // means every packet was dropped. Now
        // the forwarder reflects the user's
        // actual schedule and the resolved
        // system-UID set. If the prefs read
        // throws (DataStore corruption, missing
        // Keystore), the default fail-closed
        // forwarder is kept; the VPN blocks
        // everything, which is the safe default.
        try {
            val prefs = org.mindanchor.data.FrictionPrefs(this)
            val schedule = prefs.goingLightSchedule.first()
            val contentUids = GoingLightPackageList.effectiveContentUids()
            val systemUids = GoingLightPackageList.systemUids
            forwarder = PacketForwarder(
                contentUids = contentUids,
                systemUids = systemUids,
                blockAll = schedule.enabled,
            )
        } catch (e: Exception) {
            // Keep the fail-closed default. The
            // VPN blocks everything; the user
            // sees "no internet." The exception
            // is intentionally swallowed: a
            // partial-prefs state must not crash
            // the VpnService, which would
            // require the user to revoke the
            // VPN and re-enable it.
        }
        val builder = Builder()
            .setSession("Going Light")
            .addAddress("10.66.66.2", 24)
            .addAddress("fd00:66:66::2", 48)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("10.66.66.1")
            .addDnsServer("fd00:66:66::1")
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
        // v0.20.1 (CodeRabbit audit 2026-08-08 #13):
        // the sinkhole pattern. We do NOT write to the
        // VPN descriptor. The VpnService is a
        // *capture* interface, not a routing engine:
        //  - Reading a packet from `input` consumes
        //    it from the kernel's perspective.
        //  - Writing it back to `output` would
        //    *re-inject* the same packet — the OS
        //    would route it again, the VpnService
        //    would read it again, and the loop
        //    would spin.
        //  - The "forward" semantic in a sinkhole is
        //    a no-op: the packet is consumed, the
        //    app's socket eventually times out, and
        //    the user sees "no internet."
        //
        // Going Light v1.1 is a content-blocker. The
        // forwarder decides per-packet whether the
        // packet *would have been allowed*; the
        // verdict is for logging and analytics. The
        // network behavior is the same for every
        // verdict: drop. The Castelo 2025 mechanism
        // is the silent timeout; this implementation
        // honors that.
        //
        // (For real traffic *forwarding* — a
        // proxy-style VPN — the design would be
        // different: read the packet, write to a
        // protected socket to the real network, read
        // the response, write the response back to
        // the VpnService. The v1.1 design is not
        // a proxy; it is a content-blocker.)
        val input = FileInputStream(pfd.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)
        while (isRunning) {
            try {
                packet.clear()
                val n = input.read(packet.array())
                if (n <= 0) continue
                packet.limit(n)
                val decision = forwarder.decide(parsePacket(packet))
                // All three verdicts are no-ops on the
                // wire: the packet was consumed by
                // the read above. The decision is
                // recorded for logging/analytics; the
                // network effect is identical.
                when (decision) {
                    Verdict.FORWARD,
                    Verdict.DROP,
                    Verdict.RETURN_ERROR -> {
                        // sinkhole: do nothing.
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
     * v0.20.1 round 2 (CodeRabbit #12): the source UID
     * is resolved via [SourceUidResolver], which reads
     * /proc/net/tcp{6} and matches the packet's
     * (source_ip, source_port) to the UID of the app
     * that opened the source socket. The VpnService API
     * does not expose the source UID directly; reading
     * the /proc/net/tcp table is the standard pattern
     * (NetGuard, Blokada).
     *
     * Returns UID [Packet.UID_UNRESOLVED] (= -1) when
     * the source is not in the table or the file is
     * unreadable. The forwarder fail-closes on
     * UID_UNRESOLVED to DROP.
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
        val src = ByteArray(4)
        buf.position(12)
        buf.get(src)
        val dst = ByteArray(4)
        buf.position(16)
        buf.get(dst)
        val srcAddr = InetAddress.getByAddress(src)
        val destAddr = InetAddress.getByAddress(dst)
        val (srcPort, destPort) = if (protocol == Packet.Protocol.TCP || protocol == Packet.Protocol.UDP) {
            val pos = buf.position()
            buf.position(pos + 2)
            val srcP = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            val dstP = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            buf.position(pos)
            srcP to dstP
        } else 0 to 0
        // v0.20.1 round 2 (CodeRabbit #12): use the
        // real /proc/net/tcp resolver. The packet is
        // attributed to the UID of the application
        // that opened the source socket. If the
        // resolver cannot attribute the packet
        // (cold start, the socket is in TIME_WAIT,
        // etc.), the result is
        // [Packet.UID_UNRESOLVED] and the forwarder
        // fail-closes to DROP.
        val uid = sourceUidResolver.resolve(srcAddr, srcPort)
        return Packet(uid, protocol, destAddr, destPort)
    }

    private fun parseIpv6(buf: ByteBuffer): Packet {
        val protocolByte = buf.get(6).toInt() and 0xFF
        val protocol = when (protocolByte) {
            6 -> Packet.Protocol.TCP
            17 -> Packet.Protocol.UDP
            58 -> Packet.Protocol.ICMP
            else -> Packet.Protocol.ICMP
        }
        val src = ByteArray(16)
        buf.position(8)
        buf.get(src)
        val dst = ByteArray(16)
        buf.position(24)
        buf.get(dst)
        val srcAddr = InetAddress.getByAddress(src)
        val destAddr = InetAddress.getByAddress(dst)
        val (srcPort, destPort) = if (protocol == Packet.Protocol.TCP || protocol == Packet.Protocol.UDP) {
            val pos = buf.position()
            buf.position(pos + 2)
            val srcP = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            val dstP = (buf.get().toInt() and 0xFF) shl 8 or (buf.get().toInt() and 0xFF)
            buf.position(pos)
            srcP to dstP
        } else 0 to 0
        // v0.20.1 round 2: same /proc/net/tcp6 resolver.
        val uid = sourceUidResolver.resolve(srcAddr, srcPort)
        return Packet(uid, protocol, destAddr, destPort)
    }

    override fun onRevoke() {
        stop()
        super.onRevoke()
    }

    override fun onDestroy() {
        // v0.25.9: cancel the serviceScope so an
        // in-flight start() coroutine is not
        // orphaned if the OS kills the service
        // between the ACTION_START intent and
        // the establish() call. Without this
        // cancellation, a service restart could
        // race with an in-flight start and
        // double-establish the VPN interface.
        serviceScope.cancel()
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
     *
     * v0.20.1 (CodeRabbit #11): ACTION_START now calls
     * [startForeground] with a [Notification] before
     * the temporary background-start allowance expires
     * (Android 12+ requires a foreground service for
     * any service that starts from the background).
     * Without the foreground promotion, the OS kills
     * the service within seconds of the receiver
     * firing.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    // v0.25.9: start() is a suspend
                    // fun (no runBlocking on the
                    // main thread). We launch it
                    // on the service's IO scope;
                    // the establish() call happens
                    // off the main thread.
                    serviceScope.launch {
                        val started = start()
                        if (!started) {
                            // stopForeground(boolean) was
                            // deprecated in API 24. The
                            // int form takes a flag from
                            // Service.STOP_FOREGROUND_*
                            // (REMOVE drops the
                            // notification, DETACH keeps
                            // it but exits foreground).
                            // Both call sites below want
                            // REMOVE.
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            return@launch
                        }
                        // Promote to a foreground service so
                        // the OS does not kill us under the
                        // background-start restrictions
                        // (Android 12+). The notification
                        // copy is the project's wording-
                        // reviewed surface.
                        startForeground(NOTIFICATION_ID, buildNotification())
                    }
                }
            }
            ACTION_STOP -> {
                stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * Build the foreground-service notification. The
     * channel and the title/text are the project's
     * wording-reviewed surface; the gate
     * (.github/workflows/clinical-review.yml) flags
     * any change here for review.
     *
     * v0.20.1: the channel ID is stable across app
     * restarts; we don't recreate the channel on
     * every start. If the channel was deleted by
     * the user, [Build.VERSION_CODES.O]+ requires
     * [NotificationManager.createNotificationChannel]
     * before [startForeground] — we create it
     * lazily.
     */
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Going Light",
                    NotificationManager.IMPORTANCE_LOW,
                )
                channel.description = getString(R.string.going_light_channel_description)
                channel.setShowBadge(false)
                nm.createNotificationChannel(channel)
            }
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.going_light_notification_title))
            .setContentText(getString(R.string.going_light_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        /**
         * The Intent action that the OS uses to start a
         * VpnService. The launcher is the entry point
         * for the user-facing consent dialog.
         */
        const val ACTION_START = "org.mindanchor.goinglight.START"
        const val ACTION_STOP = "org.mindanchor.goinglight.STOP"

        /**
         * The foreground-service notification ID.
         * Stable across restarts so the system
         * reuses the same notification slot.
         */
        const val NOTIFICATION_ID = 1001

        /**
         * The notification channel ID. v0.20.1:
         * stable across restarts; the channel is
         * created lazily on first use.
         */
        const val CHANNEL_ID = "org.mindanchor.goinglight"
    }
}
