package org.mindanchor.notifications

import org.mindanchor.support.CrisisContactRef
import org.mindanchor.support.PhoneMatch

/**
 * Android-free classification logic so it is fully unit-testable.
 *
 * v0.72+ (Master plan T-3.x): the model flipped to "default batched, opt-out
 * per app". [shouldHold] now returns `true` for the common case and only
 * `false` for hard pass-throughs — apps the user has explicitly marked to let
 * through, OS-marked bypass categories (calls, alarms, navigation, …), the
 * device's own package, a crisis contact's message, and ongoing / non-clearable
 * / group-summary notifications that must never be touched.
 */
data class NotificationMeta(
    val packageName: String,
    val category: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val isConversation: Boolean,
    /** Anyone the notification names: `tel:` URIs, numbers or display names. */
    val people: List<String> = emptyList(),
)

object NotificationClassifier {

    /** Categories that always pass regardless of batching settings. */
    private val PASS_CATEGORIES = setOf(
        "call", "alarm", "reminder", "event", "navigation", "transport",
        "sys", "service", "err", "msg",
    )

    /**
     * Decides whether the notification should be held (batched) or passed
     * through immediately.
     *
     * Order is significant: the hard pass-throughs short-circuit first so a
     * let-through app (phone, WhatsApp, …) never has a conversation-shaped
     * notification delayed even by a frame.
     */
    fun shouldHold(
        meta: NotificationMeta,
        batchingEnabled: Boolean,
        /**
         * v0.72+ — apps the user has explicitly chosen to let through
         * immediately. Membership here is a hard pass-through that
         * outranks every other rule (including marketing demotion; the
         * listener checks shouldHold before applying the marketing tier).
         */
        neverBatchApps: Set<String>,
        ownPackage: String,
        /** Chosen people who must never be delayed, whatever the settings. */
        crisisContacts: Collection<CrisisContactRef> = emptyList(),
    ): Boolean {
        if (!batchingEnabled) return false
        if (meta.packageName == ownPackage) return false
        // A crisis contact outranks every other rule, including an app the
        // user asked to let through: a delayed message from the person you
        // reach for is the one delay that can do real harm.
        if (PhoneMatch.mentionsAny(meta.people, crisisContacts)) return false
        if (meta.packageName in neverBatchApps) return false
        if (meta.isOngoing || !meta.isClearable || meta.isGroupSummary) return false
        if (meta.isConversation) return false
        if (meta.category != null && meta.category in PASS_CATEGORIES) return false
        return true
    }
}
