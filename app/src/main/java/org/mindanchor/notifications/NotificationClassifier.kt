package org.mindanchor.notifications

/**
 * Android-free classification logic so it is fully unit-testable.
 *
 * Design (docs/research/05 §1, docs/research/02 §3): batching is opt-in per
 * app; conversations and calls always pass instantly ("humans break
 * through" — Fitz et al. 2019 batched *machine* interruptions, and muting
 * human contact is the documented backfire mode). Anything the OS marks
 * ongoing/non-clearable, and group summaries, must never be touched.
 */
data class NotificationMeta(
    val packageName: String,
    val category: String?,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean,
    val isConversation: Boolean,
)

object NotificationClassifier {

    /** Categories that always pass regardless of batching settings. */
    private val PASS_CATEGORIES = setOf(
        "call", "alarm", "reminder", "event", "navigation", "transport",
        "sys", "service", "err", "msg",
    )

    fun shouldHold(
        meta: NotificationMeta,
        batchingEnabled: Boolean,
        batchedApps: Set<String>,
        ownPackage: String,
    ): Boolean {
        if (!batchingEnabled) return false
        if (meta.packageName == ownPackage) return false
        if (meta.packageName !in batchedApps) return false
        if (meta.isOngoing || !meta.isClearable || meta.isGroupSummary) return false
        if (meta.isConversation) return false
        if (meta.category != null && meta.category in PASS_CATEGORIES) return false
        return true
    }
}
