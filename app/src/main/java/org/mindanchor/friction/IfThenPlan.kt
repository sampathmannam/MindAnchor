package org.mindanchor.friction

/**
 * The implementation-intention plan for one app.
 *
 * Gollwitzer 1999 *American Psychologist* 54(7):493–503 (and the
 * 2019 *Psychology of Action* consolidation) showed that
 * pre-committed if-then plans beat in-the-moment willpower on a
 * wide range of behaviour-change outcomes. The mechanism is
 * *cognitive automaticity*: specifying the cue ("if I'm about
 * to open Instagram"), the action ("then I'll check my email"),
 * and the duration ("for 5 minutes") in advance reduces the
 * load on the person at the moment of decision.
 *
 * `docs/research/15` §8 named the per-app if-then builder as
 * "the cheapest anti-habituation fix" — Adhikari PNAS 2023
 * found 36% of opens dismissed at first, but the effect *decays*
 * by week 6 unless the prompt style itself rotates. A
 * user-authored if-then plan rotates the prompt content (the
 * user wrote it) without rotating the prompt *shape* (still
 * the same breath, same time-box choice).
 *
 * The plan is *per app*, *free-text in three fields*, and
 * *optional*. A user who has not written one gets the existing
 * generic intention prompt and the existing time-box choices
 * (5, 10, 20 minutes). A user who has written one gets the
 * generic prompt pre-filled with their own words, which the
 * literature finds is the highest-yield form of the technique
 * (Wysa / Moodkit pattern).
 */
data class IfThenPlan(
    /** Free text. The "if I'm about to open X…" cue. */
    val cue: String = "",
    /** Free text. The "then I will…" action. */
    val action: String = "",
    /**
     * The time-box the user has chosen for this app, in
     * minutes. Null means "untimed" (the existing
     * "open_untimed" button). The user-chosen value is what
     * the launcher surfaces in the friction gate; the gate
     * still offers the 5/10/20 escape valves.
     */
    val defaultMinutes: Long? = null,
) {
    /**
     * A plan is "complete" when the user has written something
     * for both the cue and the action. Plans with only one
     * field filled are stored anyway (the user can come back
     * to them) but are not surfaced in the gate's pre-fill.
     */
    val isComplete: Boolean
        get() = cue.isNotBlank() && action.isNotBlank()

    /**
     * Sanitised for storage. Each field is trimmed and
     * capped. Blank fields are stored as empty strings so the
     * storage shape is stable.
     */
    fun sanitised(): IfThenPlan = copy(
        cue = cue.trim().take(MAX_FIELD),
        action = action.trim().take(MAX_FIELD),
        defaultMinutes = defaultMinutes?.coerceIn(MIN_MINUTES, MAX_MINUTES),
    )

    companion object {
        const val MAX_FIELD = 140
        const val MIN_MINUTES = 1L
        const val MAX_MINUTES = 120L
    }
}

/**
 * Storage codec for [IfThenPlan]s, per-app.
 *
 * One app per line. Tab-separated `package<TAB>cue<TAB>action<TAB>minutes`.
 * Same shape as [GateLedger.encode] and [OpenLoop.encode] —
 * a plain-text round trip, no JSON, no migration.
 */
object IfThenPlanStore {

    fun encode(plans: Map<String, IfThenPlan>): String =
        plans.entries
            .filter { it.key.isNotBlank() }
            .joinToString("\n") { (pkg, plan) ->
                val s = plan.sanitised()
                "$pkg\t${s.cue}\t${s.action}\t${s.defaultMinutes ?: ""}"
            }

    fun decode(raw: String): Map<String, IfThenPlan> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4 || parts[0].isBlank()) return@mapNotNull null
            val minutesRaw = parts[3]
            val minutes = if (minutesRaw.isBlank()) null
                else minutesRaw.toLongOrNull()?.coerceIn(
                    IfThenPlan.MIN_MINUTES,
                    IfThenPlan.MAX_MINUTES,
                )
            parts[0] to IfThenPlan(
                cue = parts[1].trim().take(IfThenPlan.MAX_FIELD),
                action = parts[2].trim().take(IfThenPlan.MAX_FIELD),
                defaultMinutes = minutes,
            )
        }.toMap()
}
