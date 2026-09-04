package org.mindanchor.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-3.2 marketing classifier v1 — deterministic heuristics
 * (master plan Phase 3). The precision-over-recall promise is the thing
 * under test here: a real human message must never be classified as
 * marketing, whatever the keywords around it.
 */
class MarketingClassifierTest {

    private fun meta(
        category: String? = null,
        conversation: Boolean = false,
        people: List<String> = emptyList(),
    ) = NotificationMeta(
        packageName = "com.shop.app",
        category = category,
        isOngoing = false,
        isClearable = true,
        isGroupSummary = false,
        isConversation = conversation,
        people = people,
    )

    @Test
    fun `promo category alone is marketing`() {
        assertTrue(
            MarketingClassifier.isMarketing(
                meta(category = "promo"), "ShopShop", "Autumn collection", "ShopShop",
            ),
        )
    }

    @Test
    fun `commerce keyword from the app itself is marketing`() {
        assertTrue(
            MarketingClassifier.isMarketing(
                meta(), "ShopShop", "FLASH SALE ends today — 40% off everything", "ShopShop",
            ),
        )
    }

    @Test
    fun `re-engagement keyword with blank title is marketing`() {
        assertTrue(
            MarketingClassifier.isMarketing(
                meta(), "", "We miss you! Come back for your free trial.", "ShopShop",
            ),
        )
    }

    @Test
    fun `social category with keyword is marketing`() {
        assertTrue(
            MarketingClassifier.isMarketing(
                meta(category = "social"), "", "Don't miss what you missed", "Feedly",
            ),
        )
    }

    @Test
    fun `no keyword means not marketing even from the app itself`() {
        assertFalse(
            MarketingClassifier.isMarketing(meta(), "ShopShop", "Your order shipped", "ShopShop"),
        )
    }

    @Test
    fun `keyword without a structural signal stays machine`() {
        // A distinct sender title and no promo/social category: reads like
        // an email subject line. Precision over recall — it waits in the
        // ordinary digest instead of being silenced.
        assertFalse(
            MarketingClassifier.isMarketing(
                meta(), "Gadget Weekly Newsletter", "The flash sale nobody expected", "Gadget Weekly",
            ),
        )
    }

    @Test
    fun `a person talking about a sale is never marketing`() {
        assertFalse(
            MarketingClassifier.isMarketing(
                meta(conversation = true), "Priya", "the sale starts at 5, come early", "Messages",
            ),
        )
        assertFalse(
            MarketingClassifier.isMarketing(
                meta(people = listOf("tel:+15551234567")),
                "",
                "limited time offer inside",
                "Messages",
            ),
        )
    }

    @Test
    fun `human categories are never marketing regardless of keywords`() {
        assertFalse(
            MarketingClassifier.isMarketing(
                meta(category = "msg"), "+1 555 123 4567", "buy now", "Dialer",
            ),
        )
        assertFalse(
            MarketingClassifier.isMarketing(
                meta(category = "call"), "", "last chance", "Phone",
            ),
        )
    }

    @Test
    fun `matching is case-insensitive and word-bounded`() {
        assertTrue(
            MarketingClassifier.isMarketing(meta(), "ShopShop", "Big DISCOUNT inside", "ShopShop"),
        )
        // "wholesale" contains "sale" but not at a word boundary.
        assertFalse(
            MarketingClassifier.isMarketing(meta(), "Hardware Co", "wholesale supplier portal", "Hardware Co"),
        )
    }
}
