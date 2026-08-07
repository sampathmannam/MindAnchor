package org.mindanchor.report

/**
 * Where a day's value actually came from.
 *
 * Carried so that "what is arriving" can be answered as a fact rather
 * than guessed at. On this project's own hardware the wearable exports
 * less than the signal list hopes for, and the difference between "the
 * watch sent nothing" and "the phone inferred it from the screen" is
 * exactly the kind of thing that must be visible rather than assumed.
 */
enum class MeasureSource {
    /** A deliberate measurement made in this app — the camera PPG, a check-in. */
    MEASURED_HERE,

    /** Read from Health Connect, whatever wrote it there. */
    WEARABLE,

    /** Inferred by this phone from its own screen rhythm. */
    PHONE_INFERRED,
}

/** A value and the provenance it arrived with. */
data class Sourced(val value: Double, val source: MeasureSource)

/**
 * Chooses one value per signal per day when more than one source has an
 * opinion.
 *
 * ## The precedence, and why
 *
 * A deliberate measurement beats a wearable's number: the person held
 * their finger on the lens for ninety seconds on purpose, and a system
 * that quietly prefers a gadget's figure over an act like that teaches
 * them the act was pointless. The wearable beats inference: a sensor
 * worn on the body beats a guess made from when the screen was dark.
 * Inference still beats nothing at all — with its provenance carried, so
 * a report built on it can be read for what it is.
 */
object Sourcing {

    fun pick(measuredHere: Double?, wearable: Double?, phoneInferred: Double?): Sourced? =
        when {
            measuredHere != null -> Sourced(measuredHere, MeasureSource.MEASURED_HERE)
            wearable != null -> Sourced(wearable, MeasureSource.WEARABLE)
            phoneInferred != null -> Sourced(phoneInferred, MeasureSource.PHONE_INFERRED)
            else -> null
        }
}

/**
 * One signal's coverage: how many days are on file across the report's
 * window, and where the latest value came from.
 */
data class Coverage(
    val signal: Signal,
    val daysOnFile: Int,
    /** ISO day of the most recent value, or null when none has ever arrived. */
    val lastDay: String?,
    val lastSource: MeasureSource?,
)

/**
 * Codec for the coverage summary the nightly build writes beside each
 * report. Same tab-separated, drop-the-bad-line discipline as every other
 * ledger here.
 */
object CoverageLedger {

    private const val NONE = "-"

    fun encode(entries: List<Coverage>): String =
        entries.joinToString("\n") {
            listOf(
                it.signal.name,
                it.daysOnFile.toString(),
                it.lastDay ?: NONE,
                it.lastSource?.name ?: NONE,
            ).joinToString("\t")
        }

    fun decode(raw: String): List<Coverage> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 4) return@mapNotNull null
            val signal = runCatching { Signal.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val days = parts[1].toIntOrNull() ?: return@mapNotNull null
            Coverage(
                signal = signal,
                daysOnFile = days,
                lastDay = parts[2].takeUnless { it == NONE },
                // An unrecognised source from some future version costs
                // the provenance word, not the whole row.
                lastSource = parts[3].takeUnless { it == NONE }
                    ?.let { name -> runCatching { MeasureSource.valueOf(name) }.getOrNull() },
            )
        }.toList()
}

/**
 * Codec for yesterday's plain facts — each signal's value and where it
 * came from, written beside the report that was built from them.
 *
 * ## Why facts exist at all
 *
 * For the first two weeks the baseline refuses to speak, and for around
 * eight the pattern search does; that silence is measured and right. But
 * a screen that says only "nothing yet" for six weeks teaches a person
 * to stop opening it before it ever has something to say. A diary of
 * measured facts — no interpretation, no comparison, just what arrived —
 * gives the screen an honest reason to exist from the second morning.
 */
object FactsLedger {

    fun encode(facts: Map<Signal, Sourced>): String =
        facts.entries.sortedBy { it.key.name }.joinToString("\n") { (signal, sourced) ->
            listOf(signal.name, sourced.value.toString(), sourced.source.name).joinToString("\t")
        }

    /** A bad line costs one fact, never the day. */
    fun decode(raw: String): Map<Signal, Sourced> =
        raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val signal = runCatching { Signal.valueOf(parts[0]) }.getOrNull()
                ?: return@mapNotNull null
            val value = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val source = runCatching { MeasureSource.valueOf(parts[2]) }.getOrNull()
                ?: return@mapNotNull null
            signal to Sourced(value, source)
        }.toMap()
}
