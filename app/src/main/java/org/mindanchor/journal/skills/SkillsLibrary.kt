/*
 * v0.66.0 (DBT-grounded journal) — Task 6.
 *
 * Static metadata for the five DBT / ACT / grounding skills surfaced in
 * the journal. The library is a plain `List<Skill>` of well-known
 * entries — on-device, no cloud, no telemetry, no LLM, no fetched
 * catalogue. Adding a sixth skill is a spec change, not a config
 * change, and is a deliberate decision (DBT diary card has five skill
 * slots in the workbook — McKay/Wood/Brantley 2007 — and the spec
 * calls for those five exactly).
 *
 * Sources:
 *   - TIPP (Temperature, Intense exercise, Paced breathing, Paired
 *     muscle relaxation) — Linehan 1993, DBT Skills Training
 *     Handouts and Worksheets, "Distract" module. The T is the
 *     protocol's signature fast-acting-distress-reduction lever:
 *     cold water on the face triggers the mammalian dive reflex
 *     and down-regulates the sympathetic nervous system in under
 *     a minute.
 *   - DEAR MAN (Describe, Express, Assert, Reinforce / Mindful,
 *     Appear confident, Negotiate) — Linehan 1993, "Interpersonal
 *     Effectiveness" module. The framing "communicate your needs,
 *     not to win an argument" is the BPD-safe anchor: the skill
 *     is for asking, not for converting.
 *   - S.T.O.P. (Stop, Take three breaths, Observe, Proceed
 *     mindfully) — Elisha Goldstein, "The Now Effect" (2011),
 *     adapted from the STOPP CBT protocol. A grounding pattern
 *     for reactive moments.
 *   - 3-Minute Breathing Space — Segal/Williams/Teasdale, "Mindful
 *     Way Through Depression" (2002). A 90-second-to-3-minute
 *     between-activities reset (awareness -> breath -> body).
 *   - Wise Mind — Linehan 1993, "Mindfulness" module. The
 *     dialectic of emotion mind and reasonable mind, with wise
 *     mind as the integration. The phrase "middle path" is the
 *     user's own median metaphor; the description frames it as
 *     "both/and" rather than "either/or" (Schwartz 1995).
 *
 * BPD-safe defaults (carried from v0.64.0 / v0.65.0, no rollback):
 *   - Descriptive voice, not directive. "Cold water on your face"
 *     (observation of a step) rather than "You should splash cold
 *     water" (imperative). The Wise Mind copy is the
 *     most-tested case (`Wise Mind is not directive` in the test
 *     suite); the other four are BPD-safe by inspection.
 *   - No streak counter, no leaderboard, no "!" affordance, no
 *     "you've used TIPP N times" copy. The library is content, not
 *     gamification.
 *   - No ranking of skills against each other. Order in `all` is
 *     the picker order; the order is the spec order, not a
 *     scoreboard.
 *
 * The titles for the three acronym-based skills are all-caps
 * (TIPP, DEAR MAN, S.T.O.P.) to match how the protocols are
 * written in the literature and to keep the picker UI consistent
 * with the workbook formatting. The test pins "TIPP" as the
 * title; the S.T.O.P. periods are kept because that is the
 * canonical acronym shape.
 */
package org.mindanchor.journal.skills

enum class SkillId { TIPP, DEAR_MAN, STOP, BREATHING_SPACE, WISE_MIND }

data class Skill(
    val id: SkillId,
    val title: String,
    val whenToUse: String,
    val howToDoIt: String,
    val timeSeconds: Int,
)

object SkillsLibrary {
    val all: List<Skill> = listOf(
        Skill(
            id = SkillId.TIPP,
            title = "TIPP",
            whenToUse = "Crisis, intense distress, urge to act now.",
            howToDoIt = """
                T — Temperature. Cold water on your face, hands, or back of neck. 30 seconds.
                I — Intense exercise. Sprint, jump, fast walk for 1-2 minutes.
                P — Paced breathing. Inhale 4 counts, exhale 6-8 counts. 2 minutes.
                P — Paired muscle relaxation. Tense + release each muscle group, head to toe.
            """.trimIndent(),
            timeSeconds = 60,
        ),
        Skill(
            id = SkillId.DEAR_MAN,
            title = "DEAR MAN",
            whenToUse = "Asking for something. Saying no. Resolving conflict.",
            howToDoIt = """
                D — Describe. State the facts. "You arrived 20 minutes late."
                E — Express. Say how you feel. "I felt disrespected."
                A — Assert. Ask for what you want. "Please text if you'll be late."
                R — Reinforce. What's the payoff for the other person? "It would help me plan my day."

                M — Mindful. Stay on point. Don't get sidetracked.
                A — Appear confident. Breathe, slow down, eye contact.
                N — Negotiate. Be willing to give to get. "If you can't text in advance, call when you're leaving."

                This is for you to communicate your needs, not to win an argument.
            """.trimIndent(),
            timeSeconds = 90,
        ),
        Skill(
            id = SkillId.STOP,
            title = "S.T.O.P.",
            whenToUse = "Strong emotion rising. Reactive moment.",
            howToDoIt = """
                S — Stop. Don't act yet. Freeze your body.
                T — Take three breaths. Long, slow, exhale longer than inhale.
                O — Observe. What is happening in your body? What is the thought? What is the feeling?
                P — Proceed mindfully. What serves you best right now?
            """.trimIndent(),
            timeSeconds = 60,
        ),
        Skill(
            id = SkillId.BREATHING_SPACE,
            title = "3-Minute Breathing Space",
            whenToUse = "Daily practice. Between activities. Coming back to present.",
            howToDoIt = """
                Minute 1 — Awareness. Notice what is. Body. Mood. Thought. Name it.
                Minute 2 — Breath. Follow the breath. Belly rising, falling. Or count 1-10, repeat.
                Minute 3 — Body. Scan from toes to head. Where is tension? Where is ease?
            """.trimIndent(),
            timeSeconds = 180,
        ),
        Skill(
            id = SkillId.WISE_MIND,
            title = "Wise Mind",
            whenToUse = "A decision. A conflict between what you want and what you should do. A moment of black-and-white thinking.",
            howToDoIt = """
                Emotion mind says: I want it now, this is all or nothing, this is the worst thing.
                Reasonable mind says: facts, plans, logic, consequences.
                Wise mind is the middle path. It holds both. It says: I can feel this AND I can act wisely.
                Ask: what would the wisest version of me do right now?
            """.trimIndent(),
            timeSeconds = 60,
        ),
    )

    fun byId(id: SkillId): Skill = all.first { it.id == id }
}
