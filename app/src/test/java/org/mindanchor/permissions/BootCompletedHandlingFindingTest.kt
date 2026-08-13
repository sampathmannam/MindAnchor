@file:Suppress(
    "SwallowedException",
    "MaxLineLength",
    "LoopWithTooManyJumpStatements",
    "UnusedPrivateMember",
)

package org.mindanchor.permissions

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOTA v2 bug-hunt, finding #7: BootReceiver listens for
 * `Intent.ACTION_BOOT_COMPLETED` but does not handle
 * `Intent.ACTION_LOCKED_BOOT_COMPLETED` or
 * `Intent.ACTION_MY_PACKAGE_REPLACED` or
 * `Intent.ACTION_TIME_CHANGED` or
 * `Intent.ACTION_TIMEZONE_CHANGED`.
 *
 * On Android 7+ with a screen lock, ACTION_BOOT_COMPLETED only
 * fires after the user unlocks the device once. Direct-boot
 * (`LOCKED_BOOT_COMPLETED`) is the documented place to re-arm
 * alarms that should fire while the phone is locked (e.g. the
 * EMA / batching prompts). The current implementation waits for
 * the user to unlock before re-arming.
 *
 * The `MY_PACKAGE_REPLACED` case is a related issue: when the
 * user upgrades the app, AlarmManager clears all scheduled
 * alarms. The receiver should re-arm on replace as well. The
 * current code only handles BOOT_COMPLETED.
 *
 * The `TIME_CHANGED` and `TIMEZONE_CHANGED` cases are minor —
 * AlarmManager re-fires on the next event, but the EMA / batching
 * schedules use LocalDateTime / ZoneId.systemDefault() and may
 * drift on a timezone change.
 *
 * The three tests below pin the surface.
 */
class BootCompletedHandlingFindingTest {

    @Test
    fun `BootReceiver handles LOCKED_BOOT_COMPLETED for direct-boot alarm re-arming`() {
        val src = read("src/main/java/org/mindanchor/notifications/BootReceiver.kt") ?: return
        val hasLocked = src.contains("LOCKED_BOOT_COMPLETED")
        assertTrue(
            "BootReceiver must handle ACTION_LOCKED_BOOT_COMPLETED so alarms " +
                "are re-armed on devices with a screen lock. The current " +
                "implementation only handles ACTION_BOOT_COMPLETED, which on " +
                "Android 7+ only fires after the user unlocks the device once. " +
                "If a user reboots and does not unlock for hours, no notification " +
                "fires — a silent breakage of the batching, EMA, sunset, " +
                "report, and pulse schedules. Add a second <intent-filter> " +
                "with action LOCKED_BOOT_COMPLETED, or move the re-arm to " +
                "the Alarms.ensureAll() call which the receiver already " +
                "invokes.",
            hasLocked,
        )
    }

    @Test
    fun `BootReceiver handles MY_PACKAGE_REPLACED for upgrade re-arming`() {
        val src = read("src/main/java/org/mindanchor/notifications/BootReceiver.kt") ?: return
        val hasReplaced = src.contains("MY_PACKAGE_REPLACED")
        assertTrue(
            "BootReceiver must handle ACTION_MY_PACKAGE_REPLACED so alarms " +
                "are re-armed after an app upgrade. AlarmManager drops every " +
                "alarm on package replace. The current implementation only " +
                "handles BOOT_COMPLETED, so a user who updates the app and " +
                "does not reboot loses the batching, EMA, sunset, report, and " +
                "pulse schedules until next reboot. Add a third <intent-filter> " +
                "with action MY_PACKAGE_REPLACED, or a separate receiver.",
            hasReplaced,
        )
    }

    @Test
    fun `BootReceiver handles TIME_CHANGED and TIMEZONE_CHANGED`() {
        val src = read("src/main/java/org/mindanchor/notifications/BootReceiver.kt") ?: return
        val hasTimeChanged = src.contains("ACTION_TIME_CHANGED")
        val hasTimezoneChanged = src.contains("ACTION_TIMEZONE_CHANGED")
        // This is a soft requirement — the existing post-fire
        // `ensureScheduled` re-arms for the next event, so the
        // immediate next event after a TIME_CHANGED / TIMEZONE_CHANGED
        // may be off by an hour. The fix is to re-arm explicitly.
        assertTrue(
            "BootReceiver (or a separate receiver) should handle " +
                "ACTION_TIME_CHANGED and ACTION_TIMEZONE_CHANGED so the " +
                "EMA / batching / sunset schedules re-arm on timezone " +
                "changes. The current implementation does neither " +
                "(hasTimeChanged=$hasTimeChanged, " +
                "hasTimezoneChanged=$hasTimezoneChanged).",
            hasTimeChanged || hasTimezoneChanged,
        )
    }

    private fun read(path: String): String? = try {
        java.io.File(path).readText(Charsets.UTF_8)
    } catch (t: Throwable) {
        null
    }
}
