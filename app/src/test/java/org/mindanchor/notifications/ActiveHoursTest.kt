package org.mindanchor.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.NotificationPrefs

/**
 * v0.30+ (spec Phase 2) — the active-hours
 * midnight-crossing rule. The spec calls for
 * 21:00 to 07:00 as the default; the rule
 * handles that without forcing the user to
 * express it as two windows. The companion
 * exposes the rule as `isWithinActiveHoursStatic`
 * for direct testing; the public member
 * `isWithinActiveHours` is the convenience for
 * the [org.mindanchor.notifications.AnchorNotificationListenerService].
 */
class ActiveHoursTest {

    private val helper: (Int, Int, Int) -> Boolean = { now, start, end ->
        NotificationPrefs.isWithinActiveHoursStatic(now, start, end)
    }

    @Test
    fun `start equals end means the full day is active`() {
        assertTrue(helper(0, 12 * 60, 12 * 60))
        assertTrue(helper(23 * 60 + 59, 12 * 60, 12 * 60))
    }

    @Test
    fun `non-crossing window 9-17 has 9am in and 18am out`() {
        // Work hours.
        assertTrue(helper(9 * 60, 9 * 60, 17 * 60))
        assertTrue(helper(16 * 60 + 59, 9 * 60, 17 * 60))
        assertFalse(helper(8 * 60 + 59, 9 * 60, 17 * 60))
        assertFalse(helper(17 * 60, 9 * 60, 17 * 60))
    }

    @Test
    fun `morning at 6am is within the spec 21-07 window`() {
        // 21:00-07:00 — the spec default.
        assertTrue(helper(6 * 60, 21 * 60, 7 * 60))
    }

    @Test
    fun `morning at 8am is outside the spec 21-07 window`() {
        assertFalse(helper(8 * 60, 21 * 60, 7 * 60))
    }

    @Test
    fun `afternoon at 3pm is outside the spec 21-07 window`() {
        assertFalse(helper(15 * 60, 21 * 60, 7 * 60))
    }

    @Test
    fun `evening at 10pm is within the spec 21-07 window`() {
        assertTrue(helper(22 * 60, 21 * 60, 7 * 60))
    }
}

