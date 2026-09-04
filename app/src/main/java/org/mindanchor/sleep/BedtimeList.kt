package org.mindanchor.sleep

import java.time.LocalDate

/**
 * What the home screen should say about tonight's list, given the
 * clock and what is stored.
 *
 * Mirrors the shape of [org.mindanchor.friction.LoopPhase] on
 * purpose: the OpenLoop (single line, Zeigarnik) and the
 * BedtimeList (1–5 specific items, Scullin) are two
 * evidence-backed mechanisms for closing open cognitive loops at
 * night, and a home screen that always has *something* to say
 * is one people stop reading. Both are silent most of the time.
 */
enum class BedtimePhase {
    /** In the quiet hours, with nothing written down: offer to take a list. */
    CAPTURE,

    /** Out of the quiet hours, with a list from last night: hand it back. */
    RETURN,

    /** Say nothing. */
    NONE,
}

/**
 * The bedtime to-do list — Scullin-style.
 *
 * `docs/research/15` (the SOTA feature-gaps brief) named this as the
 * highest-ROI S-effort gap, citing:
 *
 * - **Scullin MK, Krueger ML, Ballard HK, Pruett N, Bliwise DL. *The
 *   effects of bedtime writing on difficulty falling asleep.* J Exp
 *   Psychol Gen 2018;147(1):139–146. doi:10.1037/xge0000374.** PSG
 *   study, N=57. The to-do-list group fell asleep significantly faster
 *   than the completed-activities group, with the *more specific* the
 *   list, the faster the onset — on average ~9 min gain, comparable
 *   to prescription sleep-aid effect sizes.
 *
 * @see Scullin MK, Krueger ML, Ballard HK, Pruett N, Bliwise DL. (2018)
 *      The effects of bedtime writing on difficulty falling asleep.
 *      *J Exp Psychol Gen* 147(1):139–146. DOI 10.1037/xge0000374
 * @see Masicampo EJ, Baumeister RF. (2011) Consider it done! Plan making
 *      can eliminate the cognitive effects of unfulfilled goals.
 *      *J Pers Soc Psychol* 101(4):667–683. DOI 10.1037/a0024192
 *      (mechanism: externalising the plan closes the Zeigarnik open loop)
 *
 * The mechanism is **Zeigarnik + Masicampo & Baumeister 2011**: an
 * unfinished task intrudes on cognition, but *writing a plan* for the
 * task removes the intrusion as effectively as finishing the task
 * does. A launcher that surfaces this prompt at the wind-down moment
 * — the exact place where a phone can see both the open loop and the
 * pre-sleep window — is the place the literature has not been able
 * to reach until now.
 *
 * ## What this is and is not
 *
 *  - It is a *brief, optional* bedtime prompt to write 1–5 specific
 *    things for tomorrow. A specificity heuristic below nudges the
 *    user toward concrete items ("call Mom at 6") rather than vague
 *    ones ("be better"), because that is what Scullin 2018 found
 *    *drove the effect*.
 *  - It is **not** a task manager. A line from three days ago is not
 *    a bedtime list; it is clutter. The list is per-night and is
 *    shown back the next morning (per the existing OpenLoop's
 *    "hand it back the morning after" rule) and then cleared.
 *  - It is **not** the same as [org.mindanchor.friction.OpenLoop],
 *    which is the Zeigarnik single-line "what's still open?" prompt
 *    for the 1am scroll. BedtimeList is the *plural* tomorrow-list
 *    at the wind-down moment, with a specificity rule and a
 *    per-night expiry. The two coexist.
 *
 * ## Specificity heuristic
 *
 * Scullin 2018 found that **specificity is the active ingredient**.
 * A vague entry ("be better at work") does not show the same effect.
 * [isSpecific] is a deliberately conservative heuristic — it does
 * not understand language, only counts the signals that correlate
 * with specificity in the wild: a *verb*, a *time or day token*,
 * and a length that fits a sentence rather than a single word. A
 * future pass can swap this for an on-device LLM judge; for v1 the
 * heuristic is what the project's "evidence or it doesn't ship"
 * rule is willing to ship.
 */
object BedtimeList {

    /** Enough items to capture a real tomorrow, few enough to stay a list. */
    const val MAX_ITEMS = 5

    /**
     * The longest a single line may be.
     *
     * Long enough to fit a real "call Mom at 6 about Saturday" —
     * short enough that a line cannot become a project.
     */
    const val MAX_LINE_LENGTH = 140

    /**
     * Whether a single line reads as specific.
     *
     * True when all three of: (a) it is long enough to carry a
     * subject and a verb (at least 12 characters), (b) it contains
     * at least one verb-shaped token, and (c) it contains a time or
     * day token that anchors the action to a moment. The first
     * two are necessary; the third is what Scullin 2018 found
     * *drove* the faster-sleep effect.
     */
    fun isSpecific(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 12) return false
        if (!hasVerb(trimmed)) return false
        if (!hasTimeOrDay(trimmed)) return false
        return true
    }

    /**
     * A small, deliberately-overcomplete list of verb-stems. Adding
     * to this list is a code review, not a runtime cost — the check
     * is case-insensitive substring matching against a 60-token
     * list, which is sub-microsecond per line.
     */
    private val VERB_STEMS = listOf(
        // common
        "call", "email", "text", "write", "send", "buy", "pick", "drop",
        "fix", "ask", "tell", "pay", "book", "sign", "file", "find",
        "meet", "walk", "run", "eat", "cook", "clean", "wash", "fold",
        "reply", "schedule", "cancel", "renew", "return", "submit",
        "review", "submit", "apply", "charge", "pack", "check",
        "make", "do", "finish", "start", "open", "close", "move",
        "drive", "ride", "take", "give", "bring", "put", "set",
        "renew", "cancel", "tell", "show",
    )

    private fun hasVerb(text: String): Boolean {
        val lower = text.lowercase()
        // Word-boundary matching so "mail" doesn't count as a verb
        // when the user wrote "gmail". The check is intentionally
        // a substring rather than a real morphology: a launcher
        // reading one line per night does not need NLP-grade
        // accuracy, and a strict check would over-reject.
        return VERB_STEMS.any { stem ->
            val padded = " $lower "
            " $stem " in padded || padded.contains(" $stem ")
        }
    }

    /**
     * Time or day tokens that anchor a tomorrow-list item to a
     * moment. Numbers (1–31) and explicit day words are the cheap
     * heuristic for "this is when" — Scullin 2018 found that an
     * item with a time component had the largest effect on
     * sleep-onset latency.
     */
    private val TIME_TOKENS = listOf(
        // days of the week
        "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday",
        "mon", "tue", "wed", "thu", "fri", "sat", "sun",
        // time of day
        "morning", "afternoon", "evening", "night", "noon",
        "am", "pm", "a.m.", "p.m.",
        // generic time anchors
        "before", "after", "by", "until", "at",
        // numbers 1–31 are the most common day-of-month tokens
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
        "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
        "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31",
    )

    private fun hasTimeOrDay(text: String): Boolean {
        val lower = text.lowercase()
        return TIME_TOKENS.any { it in lower }
    }

    /**
     * Trims and caps a single line, returns null for blank input.
     *
     * Same pattern as [org.mindanchor.friction.OpenLoop.clean]:
     * a blank in means nothing stored, so callers do not have to
     * branch on empty-string vs. null separately.
     */
    fun cleanLine(line: String): String? =
        line.trim().replace('\n', ' ').take(MAX_LINE_LENGTH).ifEmpty { null }

    /**
     * Encodes a list to a one-per-line string for storage.
     *
     * Empty lines are dropped on the way in. A stored file that
     * picks up stray newlines cannot produce an empty item in the
     * list.
     */
    fun encode(items: List<String>): String =
        items.joinToString("\n") { oneLine(it).trim() }.trimEnd()

    /**
     * One item per line is the whole format, so a line break inside an item
     * would split it into two on the next read. Items are the person's own
     * words, pasted as often as typed, so both writers normalise rather than
     * trusting the field to be single-line. A lone carriage return counts:
     * [lineSequence] treats it as a terminator too.
     */
    private fun oneLine(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ')


    /**
     * Decodes a stored file. Items are trimmed, blank items are
     * dropped, and the list is capped at [MAX_ITEMS]. The cap is
     * on the *output* rather than on the input so a corrupted or
     * stale file cannot produce an overflowing list.
     */
    fun decode(raw: String): List<String> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_ITEMS)
            .toList()

    /**
     * What the home screen should say, given the clock and what
     * is stored.
     *
     * Same shape as [org.mindanchor.friction.OpenLoop.phase]:
     *  - In the quiet hours, the prompt is **capture** (offer
     *    to take the list) only when nothing is stored.
     *  - Outside the quiet hours, the prompt is **return** (hand
     *    the list back from this morning) when a list was written
     *    last night.
     *  - A list older than yesterday is *not* a bedtime list, it
     *    is clutter, and being shown it is being reminded of
     *    something already let go. The phase is NONE.
     *
     * Distinct from [org.mindanchor.friction.OpenLoop.phase] on
     * one point: the bedtime list *captures* whether there is
     * actually anything to take. An empty stored list is
     * equivalent to no list — calling code does not have to
     * branch on "is the list empty" separately.
     */
    fun phase(
        quietHours: Boolean,
        items: List<String>,
        writtenDay: String?,
        today: LocalDate,
    ): BedtimePhase {
        // A list is only "on file" while it is from tonight
        // or last night. Anything older is clutter, and
        // treating it as on file would silence the capture
        // prompt forever — the trap a stale list creates is
        // a prompt that never fires again until the user
        // manually clears it, which is a worse outcome than
        // asking twice. An unparseable stored day is the
        // same trap; treat it as clutter too.
        val day = writtenDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val fresh = items.isNotEmpty() && day != null &&
            (day == today || day == today.minusDays(1))
        if (quietHours) {
            // In the quiet hours: only prompt to capture if
            // there is nothing on file. A fresh list does not
            // need to be re-asked for — the morning is when it
            // gets handed back.
            return if (fresh) BedtimePhase.NONE else BedtimePhase.CAPTURE
        }
        return if (fresh) BedtimePhase.RETURN else BedtimePhase.NONE
    }
}
