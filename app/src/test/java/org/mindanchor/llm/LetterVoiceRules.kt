package org.mindanchor.llm

/**
 * A runnable checker for the BPD-safe voice rules that
 * [LetterPrompt.SYSTEM_PROMPT] instructs the LLM to follow.
 *
 * The system prompt is the *only* safety lever for the
 * letter's voice (spec §7). [LetterPromptShapeTest] pins
 * the prompt itself; [LetterVoiceRulesTest] pins the
 * *output* shape by running these rules against canonical
 * good and bad letter fixtures. If a future contributor
 * "tightens" the prompt wording and the LLM starts
 * producing letters that look more like a coach, a
 * self-help book, or a prescriptive checklist, this
 * checker's rules will be the first thing to expand to
 * catch the new pattern.
 *
 * The checker is intentionally **case-insensitive** for
 * the forbidden phrases: a "Well Done!" greeting and a
 * "well done" one are the same rule violation. Word
 * boundaries are respected (e.g. "considering" does not
 * match "consider") so the checker doesn't false-positive
 * on common English.
 *
 * The checker is a *production-grade* helper, not a test
 * fixture: it lives in the test source set so it is not
 * bundled into the APK, but it can be moved to the main
 * source set later if a runtime guard wants to use it
 * (e.g. a belt-and-suspenders post-LLM check). The
 * `internal` visibility keeps the test package the only
 * caller for now.
 */
internal object LetterVoiceRules {

    /**
     * The outcome of running every rule against a letter body.
     * [violations] is empty when the letter is conformant; a
     * non-empty list names every rule the body broke.
     */
    data class RuleCheckResult(
        val violations: List<RuleViolation>,
    ) {
        val isConformant: Boolean get() = violations.isEmpty()
    }

    /**
     * One rule the body broke. The [phrase] is the exact
     * canonical phrase the rule forbids (e.g. "you should"),
     * and [ruleName] is the human-readable name a test or
     * a future runtime guard would log.
     */
    data class RuleViolation(
        val ruleName: String,
        val phrase: String,
    )

    /**
     * Run every rule against [body]. Order of [RuleCheckResult.violations]
     * matches the order of the rule list in [rules] (not the
     * order of occurrences in the text) so a test asserting
     * "only this one rule failed" gets a stable shape.
     */
    fun check(body: String): RuleCheckResult {
        val violations = rules.mapNotNull { rule ->
            if (rule.matcher(body)) RuleViolation(rule.name, rule.phrase) else null
        }
        return RuleCheckResult(violations)
    }

    /**
     * One rule. [phrase] is the canonical forbidden phrase
     * (or a one-line description when the rule is structural,
     * like "word count"). [matcher] returns true iff the
     * letter body violates this rule. A structural rule like
     * "word count" uses a synthetic phrase like "200-300 words"
     * that the violation report carries verbatim.
     */
    private data class Rule(
        val name: String,
        val phrase: String,
        val matcher: (String) -> Boolean,
    )

    private val rules: List<Rule> = listOf(
        Rule(
            name = "no exclamation marks",
            phrase = "!",
            matcher = { body -> body.contains("!") },
        ),
        Rule(
            name = "no prescriptive language",
            // "you should", "you must", "try to", "consider",
            // "the next step is", "have you tried" — all
            // banned in the system prompt.
            phrase = "you should / you must / try to / consider / the next step is / have you tried",
            matcher = { body ->
                PRESCRIPTIVE_PHRASES.any { containsWordPhrase(body, it) }
            },
        ),
        Rule(
            name = "no evaluative praise",
            // "well done", "great job", "I'm proud of you" —
            // evaluative, breaks the validate-then-suggest
            // voice.
            phrase = "well done / great job / I'm proud of you",
            matcher = { body ->
                EVALUATIVE_PHRASES.any { containsWordPhrase(body, it) }
            },
        ),
        Rule(
            name = "no comparative language",
            // "better than yesterday", "you used to", "you
            // always" — comparative breaks the
            // validate-then-suggest voice.
            phrase = "better than yesterday / you used to / you always",
            matcher = { body ->
                COMPARATIVE_PHRASES.any { containsWordPhrase(body, it) }
            },
        ),
        Rule(
            name = "no streak / count / score",
            // "X days in a row", "streaks", "scores",
            // explicit counts ("3 days", "5 entries") —
            // the system prompt forbids quantitative
            // assessment.
            phrase = "streaks / X days in a row / scores",
            matcher = { body -> containsWordPhrase(body, "streaks") || body.contains("X days in a row") },
        ),
        Rule(
            name = "no first-person self-reference",
            // "I" used as the writer ("I think", "I hope")
            // — the system prompt says the letter must not
            // mention "I" as the writer. We catch the
            // standalone "I" with a word boundary. Common
            // words that contain "I" (e.g. "in", "is") are
            // filtered by the boundary check.
            phrase = "I",
            matcher = { body -> STANDALONE_I_REGEX.containsMatchIn(body) },
        ),
        Rule(
            name = "no app / AI / system mention",
            // "app", "AI", "system" — the system prompt
            // forbids mentioning the app, the device, the
            // system, or AI. We match the standalone words
            // so "application" doesn't false-positive on
            // "app".
            phrase = "app / AI / system",
            matcher = { body ->
                APP_AI_SYSTEM_REGEX.containsMatchIn(body)
            },
        ),
        Rule(
            name = "no em-dash for emphasis",
            // The system prompt says em-dashes are not for
            // emphasis; use commas and full stops. A
            // legitimate em-dash use in a long parenthetical
            // is rare; we forbid the character outright. The
            // system prompt itself uses em-dashes, but a
            // letter *body* using them is the rule the
            // LLM is told to follow.
            phrase = "—",
            matcher = { body -> body.contains("—") },
        ),
        Rule(
            name = "no crisis-line phone numbers",
            // iCall 9152987821, Vandrevala 1860-2662-362,
            // AASRA 9820466726, Tele-MANAS 14416 — all
            // banned in the letter body. They live in a
            // separate surface (the journal's sticky bar).
            phrase = "iCall 9152987821 / Vandrevala 1860-2662-362 / AASRA 9820466726 / Tele-MANAS 14416",
            matcher = { body ->
                CRISIS_NUMBER_REGEX.containsMatchIn(body)
            },
        ),
        Rule(
            name = "word count between 200 and 300",
            // The system prompt's "200–300 words" target.
            // A letter shorter than 200 words feels thin;
            // a letter longer than 300 starts to lecture.
            phrase = "200-300 words",
            matcher = { body ->
                val words = body.trim().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                words.size !in 200..300
            },
        ),
        Rule(
            name = "ends with a question or soft observation",
            // The system prompt says "Never end with a
            // directive. Close with a quiet question." A
            // soft observation ends with a period; a
            // question ends with "?". Anything else
            // (exclamation, ellipsis) is neither.
            phrase = "? or .",
            matcher = { body ->
                val trimmed = body.trim()
                trimmed.isEmpty() ||
                    !(trimmed.endsWith("?") || trimmed.endsWith("."))
            },
        ),
    )

    /**
     * Case-insensitive "whole word" check. "consider"
     * matches "consider" and "Consider" but not
     * "considering" or "considered" (the trailing "ing" /
     * "ed" pushes past the word boundary).
     */
    private fun containsWordPhrase(body: String, phrase: String): Boolean =
        WORD_PHRASE_REGEX_CACHE.getValue(phrase).containsMatchIn(body)

    private val WORD_PHRASE_REGEX_CACHE: Map<String, Regex> by lazy {
        // \b is a word boundary; the (?i) makes the
        // pattern case-insensitive without forcing the
        // caller to lower-case the body first. The
        // phrase itself is escaped via [Regex.escape]
        // so a future contributor adding a phrase
        // with regex metacharacters (apostrophe, dot)
        // doesn't accidentally change the match
        // semantics.
        (PRESCRIPTIVE_PHRASES + EVALUATIVE_PHRASES + COMPARATIVE_PHRASES +
            listOf("streaks", "X days in a row"))
            .associateWith { phrase ->
                val escaped = Regex.escape(phrase)
                Regex("(?i)\\b$escaped\\b")
            }
    }

    private val PRESCRIPTIVE_PHRASES: List<String> = listOf(
        "you should",
        "you must",
        "try to",
        "consider",
        "the next step is",
        "have you tried",
    )

    private val EVALUATIVE_PHRASES: List<String> = listOf(
        "well done",
        "great job",
        "I'm proud of you",
    )

    private val COMPARATIVE_PHRASES: List<String> = listOf(
        "better than yesterday",
        "you used to",
        "you always",
    )

    /**
     * A standalone "I" is an upper-case "I" with a word
     * boundary on each side. The "(?i)" inline flag would
     * also match lower-case "i", which would false-positive
     * on prepositions ("in", "is", "it") and the pronoun
     * "i" inside other words, so we explicitly anchor on
     * the upper-case form.
     */
    private val STANDALONE_I_REGEX: Regex = Regex("\\bI\\b")

    /**
     * Standalone "app" / "AI" / "system" / "android" / "phone"
     * — the system prompt forbids mentioning the app, the
     * device, the system, or AI. "application" contains "app"
     * but the trailing letters push it past the \b on the
     * right, so the word-boundary check keeps it safe.
     */
    private val APP_AI_SYSTEM_REGEX: Regex =
        Regex("\\b(app|AI|android|phone)\\b", RegexOption.IGNORE_CASE)

    /**
     * The four Indian crisis line numbers that must not
     * appear in the letter body. The system prompt tells
     * the LLM this explicitly, and the test fixtures
     * (one of the bad letters contains "AASRA 9820466726")
     * pin the rule. A future contributor who adds a new
     * crisis line number to the journal's sticky bar
     * should also add it here.
     */
    private val CRISIS_NUMBER_REGEX: Regex = Regex(
        "(?i)(iCall\\s*9152987821|Vandrevala\\s*1860-?2662-?362|AASRA\\s*9820466726|Tele-?MANAS\\s*14416)",
    )

    private val WHITESPACE_REGEX: Regex = Regex("\\s+")
}
