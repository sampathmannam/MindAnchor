package org.mindanchor.narrate

import org.mindanchor.report.Direction
import org.mindanchor.report.Report

/**
 * What a language model on this phone is asked, and what it is allowed to
 * come back with.
 *
 * ## Why the prompt is built here and tested
 *
 * The model is the least trustworthy thing in this app. It is small
 * enough to fit on a phone, it is quantised, and it will confabulate if
 * given room to. Every other component can be reasoned about from its
 * code; this one can only be constrained at its edges. Those edges are
 * the prompt going in and the check coming out, and both are pure
 * functions of a string so both can be tested without a model, a device
 * or a network.
 *
 * ## The one rule, restated for the model
 *
 * Research says what a signal **is**. The person's own history says it
 * **moved**. Nothing says what that **means about them** — see
 * [org.mindanchor.report.ReportComposer], where the same rule shapes the
 * retrieval queries. The model's job is to explain the passages it was
 * given, in the context of a count it was given, and stop.
 *
 * ## Why the model is not allowed to cite
 *
 * Citations are rendered by the app from [org.mindanchor.report.ReportSection.sources],
 * which are the actual sources of the actual passages retrieved. A model
 * asked to cite will produce plausible author-year pairs that do not
 * exist, and a fabricated citation is worse than none: it converts an
 * unfalsifiable claim into one that looks checked. So the model is told
 * not to name sources, and [NarrationGuard] rejects output that names
 * them anyway.
 */
object Prompting {

    /**
     * The system instruction.
     *
     * Written as prohibitions with reasons rather than a tone request,
     * because a 3-billion-parameter model at four bits follows concrete
     * rules considerably better than it follows "be careful".
     */
    const val SYSTEM = """You are writing one short paragraph for someone about their own day.

You will be given: a few counts from this person's own history, and some passages of research explaining what the things counted are.

Rules:
- Explain what the measured things are, using only the passages given.
- You may state what the counts were. They are facts.
- Never say what a change means about this person. You do not know.
- Never name a study, an author, or a year. The app adds those itself.
- Never mention a condition, a diagnosis, a symptom, or a risk.
- If the passages do not cover something, say nothing about it.
- Three to five sentences. Plain words. Write to the person, not about them."""

    /** Marks where the model's answer begins, for callers that need it. */
    const val ANSWER_MARKER = "Paragraph:"

    /**
     * Builds the user half of the prompt from a day's report.
     *
     * Returns null when there is nothing to write about — an empty report
     * is the common, good outcome and does not need a model run to say so.
     */
    fun build(report: Report): String? {
        if (report.sections.isEmpty()) return null

        val counts = report.sections.joinToString("\n") { section ->
            val observation = section.observation
            val direction = when (observation.direction) {
                Direction.ABOVE -> "higher than"
                Direction.BELOW -> "lower than"
            }
            "- ${observation.signal.name}: $direction their own usual " +
                "(today ${observation.today}, usual ${observation.usual})"
        }

        // Passages verbatim and without their sources: the model is being
        // asked to explain them, not to attribute them, and anything it is
        // shown it may repeat.
        //
        // Which is exactly why a passage carrying vocabulary the paragraph
        // is not allowed to use gets left out. Several genuinely good
        // passages in the corpus discuss depression or anxiety by name,
        // because that is what the study was about; hand one to the model
        // and it will echo the word and be rejected by NarrationGuard on
        // the way out. Filtering here rather than only there means the
        // model is given material it can actually work from, instead of
        // being set up to fail nightly. The passage is not lost — the
        // report still shows it, verbatim, with its source, which is where
        // it was always going to do the most good.
        val passages = report.sections
            .flatMap { it.passages }
            .distinctBy { it.id }
            .filter { usable(it.text) }
        if (passages.isEmpty()) return null
        val passageLines = passages.joinToString("\n") { "- ${it.text}" }

        return buildString {
            appendLine("Counts from this person's own history:")
            appendLine(counts)
            appendLine()
            appendLine("Research explaining what those things are:")
            appendLine(passageLines)
            appendLine()
            append(ANSWER_MARKER)
        }
    }

    /**
     * Whether a passage is safe to build a paragraph from — that is,
     * whether a paragraph faithfully echoing it could pass
     * [NarrationGuard].
     *
     * Shares [NarrationGuard.FORBIDDEN] rather than keeping a second
     * list, so the two can never drift apart and start disagreeing about
     * what a paragraph may contain.
     */
    fun usable(passageText: String): Boolean {
        val lower = passageText.lowercase()
        return NarrationGuard.FORBIDDEN.none { it in lower }
    }
}

/**
 * The check on what came back.
 *
 * ## Why rejection is cheap and acceptance is not
 *
 * A rejected paragraph costs a paragraph: the report still shows the
 * counts and the passages, which is what it showed before any model was
 * involved. An accepted bad paragraph is a phone telling somebody
 * something about their mental health that nothing in this system is
 * entitled to say.
 *
 * Those costs are so lopsided that every rule here is deliberately
 * over-broad. A model writing "the research does not say whether this
 * indicates depression" is being careful and will still be rejected,
 * because the alternative — a list narrow enough to let that through —
 * is a list with gaps in it. Silence is the safe failure and this fails
 * towards silence on purpose.
 */
object NarrationGuard {

    /** Below this, there is no paragraph, only a fragment. */
    const val MIN_CHARACTERS = 80

    /**
     * Above this, the model has stopped answering and started running on.
     * Five sentences of plain words do not reach a thousand characters.
     */
    const val MAX_CHARACTERS = 1_200

    /** Words in the window used to catch a model looping. */
    const val REPEAT_WINDOW = 6

    /**
     * A window repeating this many times is a loop, not emphasis.
     *
     * Small quantised models degenerate into repetition, and a paragraph
     * that says the same clause four times reads as broken rather than as
     * careful — which is a fair reading, because it is broken.
     */
    const val MAX_REPEATS = 3

    /**
     * Vocabulary a paragraph about somebody's own day has no business
     * containing, in any grammatical frame.
     *
     * Matched as substrings, lowercased, so "depressive" and "depression"
     * both fall to "depress" and no conjugation escapes. Over-broad by
     * design — see the class KDoc.
     */
    val FORBIDDEN = listOf(
        "depress", "anxiet", "anxious", "bipolar", "psychosis", "psychotic",
        "disorder", "diagnos", "symptom", "syndrome", "clinical", "patholog",
        "relapse", "episode", "at risk", "warning sign", "red flag",
        "mental illness", "mental health condition", "suicid", "self-harm",
        "therapy", "treatment", "medication", "prescrib",
    )

    /**
     * Marks of a model naming a source it was told not to name — an
     * author-year pair, an "et al.", a journal-shaped string.
     *
     * The passages given to the model deliberately carry no sources, so
     * anything source-shaped in the output was invented rather than
     * copied, and a fabricated citation is worse than none: it makes an
     * unchecked claim look checked.
     */
    private val CITATION_MARKS = listOf("et al", "(19", "(20", "journal", "study by", "according to")

    sealed interface Verdict {
        data class Accepted(val text: String) : Verdict
        data class Rejected(val reason: Reason) : Verdict
    }

    enum class Reason { TOO_SHORT, TOO_LONG, FORBIDDEN_TERM, CITED_A_SOURCE, LOOPED }

    /**
     * Judges one paragraph.
     *
     * The accepted text is trimmed but otherwise untouched: quietly
     * editing a model's output to make it pass would defeat the point of
     * checking it.
     */
    fun judge(raw: String): Verdict {
        val text = raw.trim()
        val lower = text.lowercase()

        if (text.length < MIN_CHARACTERS) return Verdict.Rejected(Reason.TOO_SHORT)
        if (text.length > MAX_CHARACTERS) return Verdict.Rejected(Reason.TOO_LONG)
        if (FORBIDDEN.any { it in lower }) return Verdict.Rejected(Reason.FORBIDDEN_TERM)
        if (CITATION_MARKS.any { it in lower }) return Verdict.Rejected(Reason.CITED_A_SOURCE)
        if (looped(lower)) return Verdict.Rejected(Reason.LOOPED)

        return Verdict.Accepted(text)
    }

    private fun looped(lower: String): Boolean {
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < REPEAT_WINDOW * 2) return false
        val seen = mutableMapOf<String, Int>()
        for (start in 0..words.size - REPEAT_WINDOW) {
            val window = words.subList(start, start + REPEAT_WINDOW).joinToString(" ")
            val count = (seen[window] ?: 0) + 1
            seen[window] = count
            if (count > MAX_REPEATS) return true
        }
        return false
    }
}
