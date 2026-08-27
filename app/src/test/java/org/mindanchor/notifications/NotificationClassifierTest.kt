package org.mindanchor.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.support.CrisisContactRef

class NotificationClassifierTest {

    private val own = "org.mindanchor"
    // v0.72+ — the curated let-through set. Phone, WhatsApp, messages
    // are inside by default; the test asserts they bypass batching.
    private val neverBatch = setOf(
        "com.android.dialer",
        "com.whatsapp",
        "com.google.android.apps.messaging",
    )

    private fun meta(
        pkg: String = "com.social.feed",
        category: String? = null,
        ongoing: Boolean = false,
        clearable: Boolean = true,
        summary: Boolean = false,
        conversation: Boolean = false,
        people: List<String> = emptyList(),
    ) = NotificationMeta(pkg, category, ongoing, clearable, summary, conversation, people)

    @Test
    fun `machine notification from a non-let-through app is held`() {
        assertTrue(NotificationClassifier.shouldHold(meta(), true, neverBatch, own))
    }

    @Test
    fun `nothing is held when batching is off`() {
        assertFalse(NotificationClassifier.shouldHold(meta(), false, neverBatch, own))
    }

    @Test
    fun `a let-through app is never held`() {
        assertFalse(
            NotificationClassifier.shouldHold(
                meta(pkg = "com.android.dialer"), true, neverBatch, own,
            ),
        )
        assertFalse(
            NotificationClassifier.shouldHold(
                meta(pkg = "com.whatsapp"), true, neverBatch, own,
            ),
        )
        assertFalse(
            NotificationClassifier.shouldHold(
                meta(pkg = "com.google.android.apps.messaging"), true, neverBatch, own,
            ),
        )
    }

    @Test
    fun `conversations always pass instantly`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(conversation = true), true, neverBatch, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "msg"), true, neverBatch, own),
        )
    }

    @Test
    fun `calls and alarms always pass`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "call"), true, neverBatch, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(category = "alarm"), true, neverBatch, own),
        )
    }

    @Test
    fun `ongoing and non-clearable notifications are never touched`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(ongoing = true), true, neverBatch, own),
        )
        assertFalse(
            NotificationClassifier.shouldHold(meta(clearable = false), true, neverBatch, own),
        )
    }

    @Test
    fun `group summaries are never touched`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(summary = true), true, neverBatch, own),
        )
    }

    @Test
    fun `our own notifications are never held`() {
        assertFalse(
            NotificationClassifier.shouldHold(meta(pkg = own), true, neverBatch + own, own),
        )
    }

    // --- Crisis contacts outrank every other rule ---

    private val ana = CrisisContactRef("Ana", "+91 98765 43210")

    @Test
    fun `a crisis contact is never held, even from an otherwise held app`() {
        assertFalse(
            NotificationClassifier.shouldHold(
                meta(people = listOf("tel:+919876543210")),
                batchingEnabled = true,
                neverBatchApps = neverBatch,
                ownPackage = own,
                crisisContacts = listOf(ana),
            ),
        )
    }

    @Test
    fun `a crisis contact named without a number still breaks through`() {
        assertFalse(
            NotificationClassifier.shouldHold(
                meta(people = listOf("Ana")),
                batchingEnabled = true,
                neverBatchApps = neverBatch,
                ownPackage = own,
                crisisContacts = listOf(ana),
            ),
        )
    }

    @Test
    fun `other senders from an otherwise held app are still held`() {
        assertTrue(
            NotificationClassifier.shouldHold(
                meta(people = listOf("tel:+911111111111")),
                batchingEnabled = true,
                neverBatchApps = neverBatch,
                ownPackage = own,
                crisisContacts = listOf(ana),
            ),
        )
    }

    @Test
    fun `with no crisis contacts configured behaviour is unchanged`() {
        assertTrue(
            NotificationClassifier.shouldHold(
                meta(people = listOf("Ana")),
                batchingEnabled = true,
                neverBatchApps = neverBatch,
                ownPackage = own,
                crisisContacts = emptyList(),
            ),
        )
    }

    @Test
    fun `an empty neverBatch set still holds the default app`() {
        // Sanity check: even with no curated bypasses, the default model
        // is "everything is held", so the common case must still hold.
        assertTrue(
            NotificationClassifier.shouldHold(
                meta(),
                batchingEnabled = true,
                neverBatchApps = emptySet(),
                ownPackage = own,
            ),
        )
    }
}
