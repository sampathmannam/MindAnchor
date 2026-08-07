package org.mindanchor.friction

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How a single app's pause has actually been going.
 *
 * [shown] counts the times the gate appeared, [abandoned] the times the
 * person chose "never mind" rather than going through. [sinceDay] is the
 * ISO date counting started, so a verdict is never reached on a week's
 * worth of data.
 */
data class GateTally(
    val shown: Int = 0,
    val abandoned: Int = 0,
    val sinceDay: String? = null,
)

/**
 * Notices when a pause has stopped being a pause.
 *
 * ## Why this exists
 *
 * Habituation is the most consistent finding in the whole literature —
 * HeartSteps decayed, Sense2Stop was null, Meinhardt (CHI 2025) found
 * people desensitise to prompts that ignore context, and Grüning's own
 * PNAS mechanism is habit decay. Yet no product in the landscape survey
 * ever removes a feature because it stopped working. docs/PLAN.md lists
 * "a de-escalation path when a user's own data shows a feature isn't
 * helping" as a do-no-harm *default*, and it had never been built.
 *
 * ## The problem this deliberately does not try to solve
 *
 * "Did this pause change behaviour" is a causal question, asked of
 * observational data, from one person. Answered naively — opens before
 * versus opens after — it calls random drift a success or a failure at
 * roughly the rate a coin would, and retiring a pause that was working is
 * a real cost to a real person.
 *
 * So this does not answer it. It reports a fact and declines to
 * interpret: *you have gone through this pause every single time it has
 * appeared, forty times, over three weeks.* Whether that means the pause
 * is useless or means it is quietly doing its job is not something a
 * launcher can know, and the person holding the phone has context no
 * amount of counting can supply.
 *
 * Grüning et al. (PNAS 2023, N=280) is the reference point that makes the
 * fact worth stating at all: a six-second pause led to **36% of open
 * attempts being abandoned**. Never abandoning once, across real volume,
 * is a long way from that.
 *
 * ## Where it is allowed to appear
 *
 * Settings, and nowhere else. Never a notification, never a card on the
 * home screen, never anything that arrives uninvited. A person having a
 * bad month does not need their phone volunteering that their guards look
 * pointless — they should have to go and ask.
 */
object GateLedger {

    /** Below this many appearances, the sample says nothing worth saying. */
    const val MIN_SHOWN = 20

    /** Below this many days, neither does the elapsed time. */
    const val MIN_DAYS = 14L

    /**
     * Abandonment at or under this counts as "never". Not zero, so that a
     * single mis-tap on "never mind" in three weeks does not hide a pause
     * that is otherwise pure ceremony.
     */
    const val ABANDON_RATE_FLOOR = 0.05

    /** Abandonment rate, or null when nothing has been shown yet. */
    fun abandonRate(tally: GateTally): Double? =
        if (tally.shown <= 0) null else tally.abandoned.toDouble() / tally.shown

    /**
     * True when this pause is worth mentioning to the person.
     *
     * Requires volume *and* elapsed time, both. Twenty appearances in a
     * single bad evening is not evidence about a habit; it is evidence
     * about one evening, and the tone system already softens for it.
     */
    fun worthMentioning(tally: GateTally, today: LocalDate): Boolean {
        if (tally.shown < MIN_SHOWN) return false
        val since = tally.sinceDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return false
        if (ChronoUnit.DAYS.between(since, today) < MIN_DAYS) return false
        val rate = abandonRate(tally) ?: return false
        return rate <= ABANDON_RATE_FLOOR
    }

    /** Records an appearance, starting the clock on the first one. */
    fun recordShown(tally: GateTally, today: LocalDate): GateTally = tally.copy(
        shown = tally.shown + 1,
        sinceDay = tally.sinceDay ?: today.toString(),
    )

    /** Records a "never mind". */
    fun recordAbandoned(tally: GateTally, today: LocalDate): GateTally =
        recordShown(tally, today).copy(abandoned = tally.abandoned + 1)

    /**
     * Starts the count again from today.
     *
     * Used when somebody keeps a pause after being shown its numbers.
     * Without this the same tally would trip the threshold forever and ask
     * them the same question every time they opened settings, which is its
     * own kind of nagging.
     */
    fun reset(today: LocalDate): GateTally = GateTally(sinceDay = today.toString())

    /**
     * One line per app: `package<TAB>shown<TAB>abandoned<TAB>sinceDay`.
     *
     * Text rather than JSON because this is two integers and a date, and
     * the file is read on every gate. Malformed lines are skipped rather
     * than thrown on — a corrupt tally must cost somebody a statistic, not
     * their home screen.
     */
    fun encode(tallies: Map<String, GateTally>): String =
        tallies.entries
            .filter { it.key.isNotBlank() }
            .joinToString("\n") { (pkg, t) ->
                "$pkg\t${t.shown}\t${t.abandoned}\t${t.sinceDay.orEmpty()}"
            }

    fun decode(raw: String): Map<String, GateTally> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4 || parts[0].isBlank()) return@mapNotNull null
            val shown = parts[1].toIntOrNull() ?: return@mapNotNull null
            val abandoned = parts[2].toIntOrNull() ?: return@mapNotNull null
            parts[0] to GateTally(
                shown = shown,
                abandoned = abandoned,
                sinceDay = parts[3].ifBlank { null },
            )
        }.toMap()
}
