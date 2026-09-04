package org.mindanchor.digest

import org.mindanchor.data.db.AppInterruptCount
import org.mindanchor.notifications.SenderTier

/**
 * One line of the weekly attention receipt: an app, its sender tier, and
 * how many times it interrupted this person inside the window.
 */
data class ReceiptEntry(
    val packageName: String,
    val appLabel: String,
    val tier: SenderTier,
    val count: Int,
)

/**
 * T-3.1 (v0.72+) — the composed weekly attention receipt
 * (CONCEPT.md 3.1D; Michie 2009 self-monitoring).
 *
 * Framing rules this composition enforces (master plan T-3.1, CONCEPT 6.3):
 * receipts are "demoted to supporting cast" — a descriptive record of what
 * already happened, never a score, streak, goal or ranking with winners.
 * The composer therefore only sorts and totals; every evaluative word is
 * absent by construction, and the UI strings carry that same constraint.
 */
object AttentionReceipt {

    /** Trailing window the receipt covers, in days. */
    const val WINDOW_DAYS = 7

    fun compose(counts: List<AppInterruptCount>): List<ReceiptEntry> {
        if (counts.isEmpty()) return emptyList()
        return counts
            .map { row ->
                ReceiptEntry(
                    packageName = row.packageName,
                    appLabel = row.appLabel,
                    tier = SenderTier.fromStored(row.tier),
                    count = row.count,
                )
            }
            // Same app can appear at two tiers after an upgrade; fold them
            // so the receipt shows one line per app per tier it actually
            // used, heaviest first, alphabetical as a deterministic tiebreak.
            .sortedWith(compareByDescending<ReceiptEntry> { it.count }.thenBy { it.appLabel })
    }

    /** Total interruptions across all entries — one number, no judgment. */
    fun total(entries: List<ReceiptEntry>): Int = entries.sumOf { it.count }

    /** How many of those came from marketing-classified senders. */
    fun marketingCount(entries: List<ReceiptEntry>): Int =
        entries.filter { it.tier == SenderTier.MARKETING }.sumOf { it.count }
}
