package org.mindanchor.friction

/**
 * The DBT Module 4 (interpersonal effectiveness) helper.
 *
 * Linehan 1993's DBT skills training manual names three
 * sub-skills, one per priority:
 *
 *  - **DEAR MAN** — Describe, Express, Assert, Reinforce,
 *    (stay) Mindful, Appear confident, Negotiate — for
 *    *getting an objective met* (saying no, making a
 *    request, asking for something).
 *  - **GIVE** — Gentle, Interested, Validate, Easy
 *    Manner — for *preserving the relationship*.
 *  - **FAST** — Fair, (no) Apologies, Stick to values,
 *    Truthful — for *maintaining self-respect*.
 *
 * The user picks the priority before a hard conversation
 * and the [DearMan.scriptFor] function generates a
 * one-page script the user can save as a Letter.
 *
 * v0.26+ (Phase 1 G-23).
 */
object DearMan {

    /**
     * The three priorities Linehan 1993 names in
     * Module 4. The user picks one before generating
     * a script; the mapping to the right sub-skill
     * is in [DearMan.skillFor].
     */
    enum class Priority(val label: String) {
        OBJECTIVE("Get the objective met"),
        RELATIONSHIP("Preserve the relationship"),
        SELF_RESPECT("Maintain self-respect"),
    }

    /**
     * One of the three DBT sub-skills. The [skillName] is
     * the canonical Linehan label; the [headings] are
     * the letters the user writes into the script.
     */
    enum class Skill(val skillName: String, val headings: List<String>) {
        DEAR_MAN(
            "DEAR MAN",
            listOf("Describe", "Express", "Assert", "Reinforce", "Mindful", "Appear confident", "Negotiate"),
        ),
        GIVE(
            "GIVE",
            listOf("Gentle", "Interested", "Validate", "Easy manner"),
        ),
        FAST(
            "FAST",
            listOf("Fair", "No apologies", "Stick to values", "Truthful"),
        ),
    }

    /**
     * The mapping from priority to skill. Centralising
     * the mapping in one pure function is the right
     * place for the clinical-review test: a clinician
     * reading [DearMan.scriptFor] is also reading the
     * mapping.
     */
    fun skillFor(priority: Priority): Skill = when (priority) {
        Priority.OBJECTIVE -> Skill.DEAR_MAN
        Priority.RELATIONSHIP -> Skill.GIVE
        Priority.SELF_RESPECT -> Skill.FAST
    }

    /**
     * Generate the one-page script. The script is a
     * template: each heading from [Skill.headings] gets
     * a blank line the user fills in. The opening line
     * is the user's own situation, restated verbatim,
     * so the script's first line is what the user
     * actually wants to communicate.
     *
     * The output is plain text — no Markdown, no
     * emojis. The clinical-review pass prefers plain
     * text because the user is going to read the
     * script on their phone, then re-read it on the
     * way to the conversation, and the format should
     * not be the noise that prevents the second
     * re-read.
     */
    fun scriptFor(priority: Priority, situation: String): String {
        val skill = skillFor(priority)
        val head = "${skill.skillName} — ${priority.label}\n\n"
        val sit = if (situation.isBlank()) {
            "Situation: (one or two sentences)\n\n"
        } else {
            "Situation: $situation\n\n"
        }
        val body = skill.headings.joinToString("\n\n") { h ->
            "$h:\n"
        }
        return head + sit + body
    }
}
