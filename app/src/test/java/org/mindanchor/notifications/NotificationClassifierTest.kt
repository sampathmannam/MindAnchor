package org.mindanchor.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClassifierTest {

    private val own = "org.mindanchor"
    private val batched = setOf("com.social.feed", "com.shop.deals")

    private fun meta(
        pkg: String = "com.social.feed",
        category: String? = null,
        ongoing: Boolean = false,
        clearable: Boolean = true,
        summary: Boolean = false,
        conversation: Boolean = false,
    ) = NotificationMeta(pkg, category, ongoing, clearable, summary, conversation)

    @Test
    fun `machine notification from batched app is held`() {
        assertTrue(NotificationClassifier.shouldHold(meta(), true, batched, own))
    }

    @Test
    fun `nothing is held when batching is off`() {
        assertFalse(NotificationClassifier.shouldHold(meta(), false, batched, own))
    }

    @Test
    fun `apps not opted in are never held`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(pkg = "com.bank.app"), true, batched, own),
        )
    }

    @Test
    fun `conversations always pass instantly`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(conversation = true), true, batched, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "msg"), true, batched, own),
        )
    }

    @Test
    fun `calls and alarms always pass`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "call"), true, batched, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "alarm"), true, batched, own),
        )
    }

    @Test
    fun `ongoing and non-clearable notifications are never touched`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(ongoing = true), true, batched, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(clearable = false), true, batched, own),
        )
    }

    @Test
    fun `group summaries are never touched`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(summary = true), true, batched, own),
        )
    }

    @Test
    fun `our own notifications are never held`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(pkg = own), true, batched + own, own),
        )
    }
}
