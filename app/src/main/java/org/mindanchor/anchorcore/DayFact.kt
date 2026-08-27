package org.mindanchor.anchorcore

import java.time.LocalDate

/**
 * The five deviations AnchorCore can name, each carrying its own numbers.
 *
 * A fact without its numbers is a label in disguise, so every [DayFact]
 * carries a `detail` payload the renderer unpacks. The renderers state a
 * count or a direction and stop — the same law as `Deviation.worthShowing`
 * ("states a fact about somebody's own screen and never interprets it")
 * and `GateLedger` ("reports a fact and declines to interpret it").
 */
enum class FactKind {
    /** N nights this week ran ≥90 min past the person's own median onset. */
    LATE_NIGHT_CLUSTER,

    /** The sleep-regularity score dropped by N points vs the prior week. */
    SLEEP_IRREGULAR,

    /** Steps robust-z below -2 against the person's own baseline. */
    MOVEMENT_LOW,

    /** HRV robust-z below -2 against the person's own baseline. */
    HRV_LOW,

    /** Resting heart rate robust-z above +2 against their own baseline. */
    RHR_HIGH,
}

/** One deviation, on one day, with the numbers that make it checkable. */
data class DayFact(
    val kind: FactKind,
    /** Pipe-separated numeric payload; each renderer documents its slots. */
    val detail: String,
    val day: LocalDate,
)

/**
 * Plain-sentence rendering, one line per kind.
 *
 * @wording-reviewed — every string this object emits reaches the person
 * (letter context, PreHome, the proposal card). Direction-only wording
 * ("below your usual"), never evaluative ("bad week") — the band
 * vocabulary the launcher already uses on the wellness card. Changing a
 * sentence here is a wording change and goes through the clinical-review
 * gate like any other user-facing copy.
 */
object DayFactRenderer {

    /**
     * Detail slots per kind:
     *  - LATE_NIGHT_CLUSTER: "nights|medianOnsetMinutesAfterSixPm" (the
     *    second slot is unused by this renderer but kept so the payload
     *    shape stays uniform for future surfaces)
     *  - SLEEP_IRREGULAR: "sriDropPoints"
     *  - MOVEMENT_LOW / HRV_LOW / RHR_HIGH: "robustZ"
     */
    fun render(kind: FactKind, detail: String): String = when (kind) {
        FactKind.LATE_NIGHT_CLUSTER -> {
            val nights = detail.substringBefore('|')
            "$nights nights this week ran well past your usual bedtime."
        }
        FactKind.SLEEP_IRREGULAR ->
            "Your sleep regularity dropped about $detail points from last week."
        FactKind.MOVEMENT_LOW ->
            "Steps have been below your usual range."
        FactKind.HRV_LOW ->
            "Resting heart-rate variability is below your usual range."
        FactKind.RHR_HIGH ->
            "Resting heart rate is above your usual range."
    }
}
