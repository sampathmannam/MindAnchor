package org.mindanchor.report

import java.time.LocalDate
import org.mindanchor.model.Anticipation
import org.mindanchor.model.LinkFinder
import org.mindanchor.model.Paired

/** The two things a person actually reports about themselves. */
enum class Label { VALENCE, AROUSAL }

/** One cell of the grid being tested: this signal against this label. */
data class SignalLabel(val signal: Signal, val label: Label) {
    // LinkFinder orders its work by the key's toString so that the same
    // history always maps to the same permutation seed. Spelling it out
    // rather than relying on the generated form means a future field on
    // this class cannot quietly change every p-value in the app.
    override fun toString(): String = "${signal.name}:${label.name}"
}

/**
 * Something this person's own record has tended to do, stated as a count
 * of their own days and nothing more.
 */
data class Pattern(
    val signal: Signal,
    val label: Label,
    val similarDays: Int,
    val medianWhenLikeToday: Double,
    val medianOverall: Double,
) {
    val lower: Boolean get() = medianWhenLikeToday < medianOverall
}

/**
 * Builds the lagged pairs, runs [LinkFinder] over the whole grid, and
 * turns whatever survives into something sayable.
 *
 * ## Why the pairing is lagged, and why that is the whole point
 *
 * Each day's label is paired with the **previous** day's signal. A
 * same-day pairing describes: you slept badly, you feel bad, and you
 * already knew both. A lagged one anticipates: on the mornings after
 * nights like last night, this is what you have tended to report. The lag
 * is one line of code and it is the difference between a diary and
 * something worth reading before the day starts.
 *
 * ## Why this can say nothing for months
 *
 * [LinkFinder] needs twenty-eight paired days before it will test at all,
 * and its threshold is set where the false-positive rate was measured
 * rather than where habit puts it — see [LinkFinder.ALPHA], where the
 * cost is spelled out. A strong link takes roughly eight weeks to become
 * findable and a weak one never does. That is intended. The alternative
 * measured out at up to a one-in-five chance of announcing a relationship
 * that does not exist, and this app's ordinary good outcome is silence.
 */
object PatternFinder {

    /**
     * How many patterns may be shown at once.
     *
     * Two. These are the strongest claims this app makes about somebody,
     * and a list of them reads as a diagnosis however each line is
     * worded — the same reasoning that caps [ReportComposer.MAX_SECTIONS]
     * at three.
     */
    const val MAX_PATTERNS = 2

    /**
     * Days of history the search runs over.
     *
     * Ninety rather than the thirty [ReportComposer] uses for its
     * baselines. A baseline only needs enough days to know what usual
     * looks like; a link needs enough *pairs* to survive a correction
     * across ten tests, and the power table in [LinkFinder.ALPHA] shows
     * detection improving steeply from twenty-eight days to eighty-four.
     * Reading ninety days of local health records costs a second or two
     * on a phone that is already awake and charging.
     */
    const val WINDOW_DAYS = 90

    /**
     * Pairs each day's label with the day before's signal.
     *
     * [days] is oldest first, and the result keeps that order —
     * [LinkFinder]'s block permutation depends on the sequence being the
     * real one, so this is the one place in this app where reordering a
     * list would silently change an answer.
     *
     * A day missing either half is skipped rather than filled in. A gap
     * is ordinary — a watch on the charger, a check-in not answered — and
     * inventing a value to close it would put a made-up number into the
     * only part of this system that claims to find relationships.
     */
    fun pairsFor(
        signalByDay: Map<LocalDate, Double>,
        labelByDay: Map<LocalDate, Double>,
        days: List<LocalDate>,
    ): List<Paired> = days.mapNotNull { day ->
        val signal = signalByDay[day.minusDays(1)] ?: return@mapNotNull null
        val label = labelByDay[day] ?: return@mapNotNull null
        // Monday is 0. The stratum feeds LinkFinder's weekday adjustment,
        // which exists because the weekly schedule is a common cause of
        // both series — see the measured numbers where the adjustment
        // lives.
        Paired(signal = signal, label = label, weekday = day.dayOfWeek.value - 1)
    }

    /**
     * The whole grid, tested at once and corrected for having been.
     *
     * [todaysSignals] holds the most recent night's value per signal —
     * what "a day like today" is measured against. A signal missing from
     * it can still contribute a link, but nothing can be said about today
     * from it, so it produces no pattern.
     */
    fun find(
        signalsByDay: Map<Signal, Map<LocalDate, Double>>,
        labelsByDay: Map<Label, Map<LocalDate, Double>>,
        days: List<LocalDate>,
        todaysSignals: Map<Signal, Double>,
        seed: Long,
    ): List<Pattern> {
        val grid = mutableMapOf<SignalLabel, List<Paired>>()
        signalsByDay.forEach { (signal, signalDays) ->
            labelsByDay.forEach { (label, labelDays) ->
                val pairs = pairsFor(signalDays, labelDays, days)
                if (pairs.isNotEmpty()) grid[SignalLabel(signal, label)] = pairs
            }
        }
        if (grid.isEmpty()) return emptyList()

        val links = LinkFinder.find(grid, seed)
        return links.entries
            .filter { it.value.holds }
            // Strongest first, then by key so an identical history never
            // reorders the report between two runs.
            .sortedWith(
                compareByDescending<Map.Entry<SignalLabel, org.mindanchor.model.Link>> {
                    kotlin.math.abs(it.value.rho)
                }.thenBy { it.key.toString() },
            )
            .mapNotNull { (key, link) ->
                val today = todaysSignals[key.signal] ?: return@mapNotNull null
                val history = grid.getValue(key)
                val neighbourhood = Anticipation.forToday(
                    link = link,
                    history = history,
                    todaysSignal = today,
                ) ?: return@mapNotNull null
                if (!agrees(link, history, today, neighbourhood)) return@mapNotNull null
                Pattern(
                    signal = key.signal,
                    label = key.label,
                    similarDays = neighbourhood.similarDays,
                    medianWhenLikeToday = neighbourhood.medianWhenLikeToday,
                    medianOverall = neighbourhood.medianOverall,
                )
            }
            .take(MAX_PATTERNS)
    }

    /**
     * Whether the neighbourhood around today points the same way the
     * whole record does.
     *
     * [LinkFinder] measures a **monotone** trend across every day on
     * file; [Anticipation] looks only at the handful of days nearest
     * today's value. Those can disagree, and where they do the
     * relationship is not monotone near today — a U-shape, most likely,
     * where both unusually high and unusually low signals sit next to the
     * same kind of day.
     *
     * When they disagree, the honest output is nothing. The sentence this
     * would otherwise produce says "came out lower than your usual" on
     * the strength of a link whose overall trend points the other way,
     * which is either confusing or wrong and is not worth being either.
     *
     * This is also what makes [Link.direction] load-bearing rather than
     * decorative. A rank correlation's sign is the one thing about it
     * that survives this sample size, and this is the place that uses it.
     *
     * Internal rather than private so it can be tested directly. Building
     * a history that both clears the significance bar and disagrees with
     * itself locally is fiddly enough that a test doing it would pass for
     * the wrong reason as easily as the right one.
     */
    internal fun agrees(
        link: org.mindanchor.model.Link,
        history: List<Paired>,
        todaysSignal: Double,
        neighbourhood: Anticipation.Neighbourhood,
    ): Boolean {
        val typicalSignal = Anticipation.median(history.map { it.signal }) ?: return false
        // A day sitting exactly on the person's own usual has no side to
        // be on, so there is nothing to predict and nothing to check.
        if (todaysSignal == typicalSignal) return false
        val belowUsual = todaysSignal < typicalSignal
        val expectLower = when (link.direction) {
            org.mindanchor.model.LinkDirection.SAME -> belowUsual
            org.mindanchor.model.LinkDirection.OPPOSITE -> !belowUsual
        }
        return expectLower == neighbourhood.lower
    }

    /**
     * Whether there is simply not enough paired history yet to have
     * looked.
     *
     * Distinct from finding nothing, and reported differently:
     * [org.mindanchor.report.ReportStore.StoredReport.patternsStillLearning]
     * carries the answer to the screen, which says so in as many words.
     * "Nothing stood out" from four days of data would be a lie of
     * omission.
     *
     * Asked only when [find] returned nothing — a search that produced a
     * pattern has self-evidently run.
     */
    fun stillLearning(
        signalsByDay: Map<Signal, Map<LocalDate, Double>>,
        labelsByDay: Map<Label, Map<LocalDate, Double>>,
        days: List<LocalDate>,
    ): Boolean {
        val longest = signalsByDay.maxOfOrNull { (_, signalDays) ->
            labelsByDay.maxOfOrNull { (_, labelDays) ->
                pairsFor(signalDays, labelDays, days).size
            } ?: 0
        } ?: 0
        return longest < LinkFinder.MIN_PAIRED_DAYS
    }
}
