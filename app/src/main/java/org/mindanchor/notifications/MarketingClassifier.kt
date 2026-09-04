package org.mindanchor.notifications

/**
 * Which kind of sender produced a held notification. Recorded on every
 * journal row so the weekly attention receipt can attribute interruptions
 * by tier (app x count x tier, master plan T-3.1).
 *
 * HUMAN exists as a tier even though humans almost never reach the journal:
 * conversations, calls and chosen people pass instantly. It is here so a
 * future hold rule that touches a human sender is represented honestly in
 * the receipt instead of being silently counted as an app.
 */
enum class SenderTier {
    HUMAN,
    MACHINE,
    MARKETING,

    ;

    companion object {
        /** Parses a stored journal value; anything unknown reads as [MACHINE]. */
        fun fromStored(value: String): SenderTier =
            entries.firstOrNull { it.name == value } ?: MACHINE
    }
}

/**
 * T-3.2 marketing classifier v1 — deterministic heuristics only
 * (master plan Phase 3). A notification is "marketing/engagement" when it
 * exists to pull the person back into the app rather than to tell them
 * something they asked for. Classified notifications are demoted: they land
 * silently in the digest and never buzz (CONCEPT.md 3.1B).
 *
 * Deliberately NOT a model. Every rule below is readable in one sitting and
 * testable without a device; the master plan defers any on-device model
 * until this heuristic's miss-rate has been measured (test-before-select).
 * Tuning bias is precision over recall: a promo email that slips through as
 * MACHINE costs a line in a digest; a real message classified MARKETING is
 * silenced, which is the one failure this class must not have.
 */
object MarketingClassifier {

    /**
     * Promo and engagement hooks. Lowercase; matched with word boundaries.
     * Two families on purpose:
     *  - commerce ("sale", "% off", …) — the app wants money;
     *  - re-engagement ("we miss you", "new feature", …) — the app wants
     *    attention. CONCEPT.md 3.1B names both: "marketing/engagement".
     */
    private val KEYWORDS = listOf(
        // Commerce
        "sale", "discount", "% off", "percent off", "coupon", "promo code",
        "flash sale", "buy now", "shop now", "order now", "back in stock",
        "price drop", "free shipping", "ends today", "last chance",
        "limited time", "offer expires", "exclusive offer",
        // Re-engagement
        "free trial", "upgrade to", "go premium", "subscribe now",
        "we miss you", "come back", "you left something behind",
        "don't miss", "you're invited", "sign up", "new feature",
        "update available",
    )

    /**
     * One precompiled pattern; tokens are regex-escaped, then given word
     * boundaries only where the edge character is a word character, so
     * "% off" still matches mid-string while "sale" does not match inside
     * "wholesale". Auditable by construction: this single pattern IS the
     * classifier's vocabulary.
     */
    private val KEYWORD_PATTERN = Regex(
        KEYWORDS.joinToString("|") { token ->
            val escaped = Regex.escape(token)
            val head = if (token.first().isLetterOrDigit()) "\\b" else ""
            val tail = if (token.last().isLetterOrDigit()) "\\b" else ""
            head + escaped + tail
        },
        RegexOption.IGNORE_CASE,
    )

    /** Categories Android itself marks as promotional. */
    private const val CATEGORY_PROMO = "promo"

    /** Social-category pings are engagement-class only when keyworded. */
    private const val CATEGORY_SOCIAL = "social"

    /** Categories that mean a human is talking; never demoted. */
    private val HUMAN_CATEGORIES = setOf("msg", "call")

    /**
     * True when [meta]/[title]/[text] look like marketing or an engagement
     * ping. Pure and Android-free so the whole rule set is unit-testable.
     *
     * Rules, in order:
     *  1. Anything naming a person, or in a human category, is never
     *     marketing — the hard no that keeps the precision promise.
     *  2. `CATEGORY_PROMO` alone is enough.
     *  3. Otherwise there must be a promo/engagement keyword AND a
     *     structural signal: either `CATEGORY_SOCIAL`, or the classic
     *     machine-ping shape where the notification has no distinct
     *     sender title ([title] blank or equal to the app's own label).
     *     An untitled "ShopShop: FLASH SALE ends today" is marketing;
     *     "Priya: the sale starts at 5" from a person is untouched at
     *     rule 1, which is exactly the intended asymmetry.
     */
    fun isMarketing(
        meta: NotificationMeta,
        title: String,
        text: String,
        appLabel: String,
    ): Boolean {
        // Rule 1 — the hard no that keeps the precision promise.
        val humanSender = meta.isConversation ||
            meta.people.isNotEmpty() ||
            (meta.category != null && meta.category in HUMAN_CATEGORIES)
        if (humanSender) return false

        // Rule 2 — Android already marked it promotional.
        if (meta.category == CATEGORY_PROMO) return true

        // Rule 3 — keyword AND a structural signal.
        val keyworded = KEYWORD_PATTERN.containsMatchIn(title) ||
            KEYWORD_PATTERN.containsMatchIn(text)
        if (!keyworded) return false

        // Either an engagement-class social ping, or the classic
        // machine-ping shape where the notification does not name a
        // sender but speaks AS the app ([title] blank or equal to the
        // app's label) — the shape of a broadcast, not a message.
        return meta.category == CATEGORY_SOCIAL ||
            title.isBlank() ||
            title.equals(appLabel.trim(), ignoreCase = true)
    }
}
