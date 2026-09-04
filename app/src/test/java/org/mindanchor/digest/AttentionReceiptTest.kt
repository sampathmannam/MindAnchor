package org.mindanchor.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mindanchor.data.db.AppInterruptCount
import org.mindanchor.notifications.SenderTier

/** T-3.1 — composition rules of the weekly attention receipt. */
class AttentionReceiptTest {

    private fun row(
        pkg: String,
        label: String = pkg,
        tier: String = "MACHINE",
        count: Int,
    ) = AppInterruptCount(packageName = pkg, appLabel = label, tier = tier, count = count)

    @Test
    fun `empty journal composes an empty receipt`() {
        assertTrue(AttentionReceipt.compose(emptyList()).isEmpty())
        assertEquals(0, AttentionReceipt.total(emptyList()))
    }

    @Test
    fun `entries are ordered heaviest first`() {
        val receipt = AttentionReceipt.compose(
            listOf(
                row("com.quiet", "Quiet", count = 2),
                row("com.loud", "Loud", count = 84),
                row("com.mid", "Mid", count = 12),
            ),
        )
        assertEquals(listOf(84, 12, 2), receipt.map { it.count })
    }

    @Test
    fun `equal counts break the tie alphabetically`() {
        val receipt = AttentionReceipt.compose(
            listOf(
                row("b", "Beta", count = 5),
                row("a", "Alpha", count = 5),
            ),
        )
        assertEquals(listOf("Alpha", "Beta"), receipt.map { it.appLabel })
    }

    @Test
    fun `unknown stored tier falls back to machine`() {
        val receipt = AttentionReceipt.compose(listOf(row("com.x", count = 1, tier = "SOMETHING_ELSE")))
        assertEquals(SenderTier.MACHINE, receipt.single().tier)
    }

    @Test
    fun `tiers survive the round trip`() {
        val receipt = AttentionReceipt.compose(
            listOf(
                row("com.a", count = 3, tier = "MARKETING"),
                row("com.b", count = 1, tier = "HUMAN"),
                row("com.c", count = 9),
            ),
        )
        // Compose orders heaviest first: 9, then 3, then 1.
        assertEquals(
            listOf(SenderTier.MACHINE, SenderTier.MARKETING, SenderTier.HUMAN),
            receipt.map { it.tier },
        )
    }

    @Test
    fun `totals and marketing counts are plain sums`() {
        val entries = AttentionReceipt.compose(
            listOf(
                row("com.a", count = 3, tier = "MARKETING"),
                row("com.b", count = 4, tier = "MARKETING"),
                row("com.c", count = 9),
            ),
        )
        assertEquals(16, AttentionReceipt.total(entries))
        assertEquals(7, AttentionReceipt.marketingCount(entries))
    }
}
