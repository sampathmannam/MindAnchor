package org.mindanchor.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

/**
 * Receiver Android requires before this app can hold any device policy.
 * It does nothing itself; its existence is the handle the system needs.
 */
class MindAnchorDeviceAdmin : DeviceAdminReceiver()

/**
 * Making chosen apps genuinely unopenable, for a phone set up to be one.
 *
 * Everything else in this launcher is advisory: a pause someone can walk
 * through, a hidden app still reachable by typing its name. That is right
 * for most people and useless for the hours when a person's own judgement
 * is the thing that has gone. Device Owner is the one Android mechanism
 * that makes a decision made while calm survive a decision made while not.
 *
 * ## Getting it
 *
 * Device Owner cannot be requested from inside an app. It is granted once,
 * over adb, on a phone with no accounts signed in — usually straight after
 * a factory reset:
 *
 * ```
 * adb shell dpm set-device-owner org.mindanchor/org.mindanchor.admin.MindAnchorDeviceAdmin
 * ```
 *
 * That is a deliberately high bar, and it should be. It also cannot be
 * undone by another adb command — only by this app releasing it or by
 * another factory reset — so [release] exists and the settings screen
 * offers it.
 *
 * ## The safety rule
 *
 * Nothing is suspended without passing [SuspensionGuard] first. A
 * suspended dialer is a person unable to call anyone, and this app is
 * aimed squarely at people for whom that matters most.
 */
object DeviceOwner {

    fun component(context: Context) =
        ComponentName(context.packageName, MindAnchorDeviceAdmin::class.java.name)

    private fun dpm(context: Context): DevicePolicyManager? =
        context.getSystemService(DevicePolicyManager::class.java)

    fun isDeviceOwner(context: Context): Boolean = runCatching {
        dpm(context)?.isDeviceOwnerApp(context.packageName) == true
    }.getOrDefault(false)

    /**
     * Suspends [chosen], minus anything that must stay reachable — the
     * dialer, messaging, this app, and whatever the person has marked
     * [alwaysOpen] because their work or their people run through it.
     *
     * Returns the packages actually suspended, which is what the caller
     * should show the person — never the list they asked for.
     */
    fun apply(
        context: Context,
        chosen: Set<String>,
        alwaysOpen: Set<String> = emptySet(),
    ): List<String> = runCatching {
        val manager = dpm(context) ?: return emptyList()
        if (!isDeviceOwner(context)) return emptyList()

        val safe = SuspensionGuard.suspendable(
            chosen = chosen,
            dialer = runCatching {
                context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            }.getOrNull(),
            sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
            self = context.packageName,
            alsoNeverSuspend = alwaysOpen,
        )
        if (safe.isEmpty()) {
            emptyList()
        } else {
            actuallySuspended(
                requested = safe,
                refused = manager.setPackagesSuspended(component(context), safe.toTypedArray(), true),
            )
        }
    }.getOrDefault(emptyList())

    /**
     * What the system actually closed, out of what was asked.
     *
     * `setPackagesSuspended` returns the packages whose suspended state it
     * could **not** set -- most often because they are not installed. That
     * return used to be discarded and the whole request recorded as
     * applied, which made the stored set claim more than had happened. The
     * default feed list is seven popular apps, so a phone missing some of
     * them is the ordinary case; and T-1.3 now shows this set to the
     * person, so it has to be true rather than merely intended.
     *
     * A null return is treated as complete success: the platform documents
     * an array, but this is an API boundary and a null must not erase a
     * result that did happen.
     */
    fun actuallySuspended(requested: List<String>, refused: Array<String>?): List<String> {
        val failed = refused?.toSet().orEmpty()
        return requested.filterNot { it in failed }
    }

    /** Lifts every suspension this app applied. */
    fun clear(context: Context, previously: Set<String>): Boolean = runCatching {
        val manager = dpm(context) ?: return false
        if (!isDeviceOwner(context)) return false
        if (previously.isNotEmpty()) {
            manager.setPackagesSuspended(component(context), previously.toTypedArray(), false)
        }
        true
    }.getOrDefault(false)

    /**
     * Hands device ownership back.
     *
     * Suspensions are lifted first. Releasing ownership while apps are
     * still suspended would leave them stuck with nothing left on the
     * phone able to free them short of a factory reset.
     *
     * `clearDeviceOwnerApp(String)` is deprecated in API 28; the
     * platform team marked it so because device-owner is now a
     * privileged-app-only grant, but no `ComponentName` replacement
     * has been published on the public API surface of API 35. The
     * deprecation is a signal of intent, not a call site to migrate.
     */
    fun release(context: Context, suspended: Set<String>): Boolean = runCatching {
        clear(context, suspended)
        dpm(context)?.clearDeviceOwnerApp(context.packageName)
        true
    }.getOrDefault(false)

    /** The line to paste into a terminal, shown verbatim in settings. */
    fun setupCommand(context: Context) =
        "adb shell dpm set-device-owner ${context.packageName}/" +
            MindAnchorDeviceAdmin::class.java.name
}
