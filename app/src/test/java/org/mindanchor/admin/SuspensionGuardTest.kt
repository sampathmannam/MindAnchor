package org.mindanchor.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These are the tests that matter most in this repository.
 *
 * Everything else here fails by being annoying. This fails by leaving
 * somebody unable to phone anyone at three in the morning, which is the
 * exact hour this app is built for.
 */
class SuspensionGuardTest {

    private val self = "org.mindanchor"

    @Test
    fun `the dialer is never suspendable, even when explicitly chosen`() {
        val out = SuspensionGuard.suspendable(
            chosen = setOf("com.example.dialer", "com.example.social"),
            dialer = "com.example.dialer",
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `the messaging app is never suspendable`() {
        val out = SuspensionGuard.suspendable(
            chosen = setOf("com.example.sms", "com.example.games"),
            dialer = null,
            sms = "com.example.sms",
            self = self,
        )
        assertEquals(listOf("com.example.games"), out)
    }

    @Test
    fun `this app can never suspend itself`() {
        // Suspending the launcher would leave a phone with no home screen
        // and no way to undo it from the phone.
        val out = SuspensionGuard.suspendable(
            chosen = setOf(self, "com.example.social"),
            dialer = null,
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `settings stays reachable so a mistake can always be undone`() {
        val out = SuspensionGuard.suspendable(
            chosen = setOf("com.android.settings", "com.example.social"),
            dialer = null,
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `telephony and emergency packages are protected without being named by the caller`() {
        val out = SuspensionGuard.suspendable(
            chosen = SuspensionGuard.ALWAYS_REACHABLE + "com.example.social",
            dialer = null,
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `a device with no dialer or sms still protects everything else`() {
        // Tablets have no telephony. That must not read as "nothing to
        // protect" and quietly widen what can be switched off.
        val out = SuspensionGuard.suspendable(
            chosen = setOf(self, "com.android.settings", "com.example.social"),
            dialer = null,
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `a contact's messaging app can be protected by the caller`() {
        val out = SuspensionGuard.suspendable(
            chosen = setOf("com.example.chat", "com.example.social"),
            dialer = null,
            sms = null,
            self = self,
            alsoNeverSuspend = setOf("com.example.chat"),
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `blank entries are dropped rather than passed to the system`() {
        val out = SuspensionGuard.suspendable(
            chosen = setOf("", "   ", "com.example.social"),
            dialer = null,
            sms = null,
            self = self,
        )
        assertEquals(listOf("com.example.social"), out)
    }

    @Test
    fun `choosing nothing suspends nothing`() {
        assertTrue(
            SuspensionGuard.suspendable(emptySet(), null, null, self).isEmpty(),
        )
    }

    @Test
    fun `the result is stable so repeated calls do not churn the system`() {
        val chosen = setOf("com.b", "com.a", "com.c")
        assertEquals(
            SuspensionGuard.suspendable(chosen, null, null, self),
            SuspensionGuard.suspendable(chosen, null, null, self),
        )
        assertEquals(listOf("com.a", "com.b", "com.c"), SuspensionGuard.suspendable(chosen, null, null, self))
    }
}
