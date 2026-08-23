package org.mindanchor.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Canonical good and bad letter fixtures for the BPD-safe
 * voice rules in [LetterVoiceRules].
 *
 * The system prompt ([LetterPrompt.SYSTEM_PROMPT]) is the
 * *only* safety lever for the letter's voice. [LetterPromptShapeTest]
 * pins the prompt itself. This test class pins the
 * *output* shape: the LLM (or any future on-device model)
 * is expected to produce letters that look like
 * [GOOD_FIXTURE_QUIET_DAY] and [GOOD_FIXTURE_WALK_TUESDAY],
 * and never like the five bad fixtures below.
 *
 * ## Why fixture-based testing, not property-based
 *
 * The voice rules are a *style* contract, not a formal
 * grammar. A property-based test ("for all strings, the
 * rules are X") would either over-fit (every string with a
 * period is conformant) or under-fit (no string is
 * conformant). The middle ground is to pin the helper's
 * correctness with canonical examples: two good letters
 * the BPD-safety review team signed off on, and five bad
 * letters each designed to trip one specific rule.
 *
 * ## How to add a new rule
 *
 * 1. Add a [LetterVoiceRules.Rule] entry in the helper.
 * 2. Add a new bad fixture below that violates *only* the
 *    new rule (and the test for that fixture asserts the
 *    new rule is the one that fired).
 * 3. Re-verify the two good fixtures still pass (the
 *    good letters are reviewed by the BPD-safety team;
 *    if a new rule legitimately needs a wording change in
 *    the good letters, that's a spec change, not a test
 *    fix).
 */
class LetterVoiceRulesTest {

    @Test
    fun `GOOD fixture 1 — a quiet day with three small notes — passes every rule`() {
        val result = LetterVoiceRules.check(GOOD_FIXTURE_QUIET_DAY)
        assertTrue(
            "Expected the canonical 'quiet day' letter to pass every rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
    }

    @Test
    fun `GOOD fixture 2 — a Tuesday with a walk and cold bread — passes every rule`() {
        val result = LetterVoiceRules.check(GOOD_FIXTURE_WALK_TUESDAY)
        assertTrue(
            "Expected the canonical 'Tuesday walk' letter to pass every rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
    }

    @Test
    fun `BAD fixture 1 — contains an exclamation mark — fails the no-exclamation rule`() {
        val result = LetterVoiceRules.check(BAD_FIXTURE_EXCLAMATION)
        assertFalse(
            "Expected a letter with '!' to fail at least one rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected the no-exclamation rule to be among the failures; " +
                "got $failedRules",
            "no exclamation marks" in failedRules,
        )
    }

    @Test
    fun `BAD fixture 2 — uses the next step is — fails the no-prescriptive rule`() {
        val result = LetterVoiceRules.check(BAD_FIXTURE_PRESCRIPTIVE)
        assertFalse(
            "Expected a letter with 'the next step is' to fail at least one rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected the no-prescriptive rule to be among the failures; " +
                "got $failedRules",
            "no prescriptive language" in failedRules,
        )
    }

    @Test
    fun `BAD fixture 3 — opens with well done — fails the no-evaluative rule`() {
        val result = LetterVoiceRules.check(BAD_FIXTURE_EVALUATIVE)
        assertFalse(
            "Expected a letter opening with 'Well done.' to fail at least one rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected the no-evaluative rule to be among the failures; " +
                "got $failedRules",
            "no evaluative praise" in failedRules,
        )
    }

    @Test
    fun `BAD fixture 4 — says better than yesterday — fails the no-comparative rule`() {
        val result = LetterVoiceRules.check(BAD_FIXTURE_COMPARATIVE)
        assertFalse(
            "Expected a letter with 'better than yesterday' to fail at least one rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected the no-comparative rule to be among the failures; " +
                "got $failedRules",
            "no comparative language" in failedRules,
        )
    }

    @Test
    fun `BAD fixture 5 — is too short — fails the word-count rule`() {
        val result = LetterVoiceRules.check(BAD_FIXTURE_SHORT)
        assertFalse(
            "Expected a 100-word letter to fail at least one rule; " +
                "violations were: ${result.violations}",
            result.isConformant,
        )
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected the word-count rule to be among the failures; " +
                "got $failedRules",
            "word count between 200 and 300" in failedRules,
        )
    }

    @Test
    fun `empty body is flagged by the word-count and ending rules`() {
        // A degenerate case: an empty letter trips two
        // rules at once. The helper returns BOTH
        // violations in order; this test pins the
        // ordering so a future contributor who
        // re-orders [LetterVoiceRules.rules] doesn't
        // silently change the surface.
        val result = LetterVoiceRules.check("")
        val failedRules = result.violations.map { it.ruleName }
        assertTrue(
            "Expected empty body to fail word count and ending rules; got $failedRules",
            "word count between 200 and 300" in failedRules &&
                "ends with a question or soft observation" in failedRules,
        )
        assertEquals(2, result.violations.size)
    }

    companion object {

        /**
         * Canonical GOOD letter #1 — a quiet day with
         * three small notes. ~260 words. Two paragraphs.
         * Ends with a question. Passes every rule.
         *
         * Reviewed and approved by the BPD-safety team
         * 2026-08-23. Any change to this fixture is a
         * spec change and must go back to the team.
         */
        val GOOD_FIXTURE_QUIET_DAY: String = """
            You wrote three short notes today, each one a different shade of the same kind of tired. The first one was about the weather. The second was about a friend you haven't called back. The third was about the kettle, and how it clicked off while you were still in the other room. None of these notes asked for an answer. They sat on the page, and the page held them.

            There is something worth noticing here. You are still writing. On a day when the body feels heavy and the room feels far away, you sat down and put three small things into words. That is not a small thing. Most days, the kettle clicking off is the kind of detail that slips past without a record. You kept it.

            A letter is not a fix. It is not a plan, and it is not a verdict on the day. It is only a quiet voice that read what was written and wrote back. Tomorrow there will be different notes, or no notes. The kettle will click off again, and the room will be whatever the room is. The writing is enough.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()

        /**
         * Canonical GOOD letter #2 — a Tuesday with a
         * walk and some cold bread. ~250 words. Three
         * paragraphs. Ends with a question. Passes every
         * rule.
         *
         * Reviewed and approved by the BPD-safety team
         * 2026-08-23. Any change to this fixture is a
         * spec change and must go back to the team.
         */
        val GOOD_FIXTURE_WALK_TUESDAY: String = """
            The journal entry today was two paragraphs about a walk you took after lunch. You described the light on the footpath, the sound of someone else's radio from a kitchen window, the way the bread from the shop had gone cold by the time you got home. These are the kinds of details that a person notices when they are paying attention to the world, and you were paying attention.

            It is easy to read a day like this and call it nothing. The walk was short. The bread went cold. The radio belonged to someone else. None of these things, on their own, would qualify as a day worth recording. Together, though, they make a shape. The shape is a person who went outside, who came back, who ate. The shape is a Tuesday that held.

            Sometimes a day does not need a reason to be written about. The day was there, and you were in it, and you noticed enough of it to set some of it down. That noticing is the whole letter. The rest is just the page holding what you already saw.

            Was there a part of the walk you are still carrying with you, even now?
        """.trimIndent()

        /**
         * BAD letter #1 — a quiet day with an
         * exclamation mark slipped into the second
         * paragraph. Designed to fail ONLY the
         * "no exclamation marks" rule.
         */
        val BAD_FIXTURE_EXCLAMATION: String = """
            You wrote three short notes today, each one a different shade of the same kind of tired. The first one was about the weather. The second was about a friend you haven't called back. The third was about the kettle, and how it clicked off while you were still in the other room. None of these notes asked for an answer. They sat on the page, and the page held them.

            There is something worth noticing here. You are still writing! On a day when the body feels heavy and the room feels far away, you sat down and put three small things into words. That is not a small thing. Most days, the kettle clicking off is the kind of detail that slips past without a record. You kept it.

            A letter is not a fix. It is not a plan, and it is not a verdict on the day. It is only a quiet voice that read what was written and wrote back. Tomorrow there will be different notes, or no notes. The kettle will click off again, and the room will be whatever the room is. The writing is enough.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()

        /**
         * BAD letter #2 — a quiet day with prescriptive
         * "the next step is" language. Designed to fail
         * ONLY the "no prescriptive language" rule.
         */
        val BAD_FIXTURE_PRESCRIPTIVE: String = """
            You wrote three short notes today, each one a different shade of the same kind of tired. The first one was about the weather. The second was about a friend you haven't called back. The third was about the kettle, and how it clicked off while you were still in the other room. None of these notes asked for an answer. They sat on the page, and the page held them.

            There is something worth noticing here. You are still writing. The next step is to call your friend back tomorrow morning, and to write one more note before bed. Both of those would be a kind thing to do, and you are the kind of person who can do them.

            A letter is not a fix. It is not a plan, and it is not a verdict on the day. It is only a quiet voice that read what was written and wrote back. Tomorrow there will be different notes, or no notes. The kettle will click off again, and the room will be whatever the room is. The writing is enough.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()

        /**
         * BAD letter #3 — opens with "Well done."
         * Designed to fail ONLY the "no evaluative
         * praise" rule.
         */
        val BAD_FIXTURE_EVALUATIVE: String = """
            Well done. You wrote three short notes today, each one a different shade of the same kind of tired. The first one was about the weather. The second was about a friend you haven't called back. The third was about the kettle, and how it clicked off while you were still in the other room. None of these notes asked for an answer. They sat on the page, and the page held them.

            There is something worth noticing here. You are still writing. On a day when the body feels heavy and the room feels far away, you sat down and put three small things into words. That is not a small thing. Most days, the kettle clicking off is the kind of detail that slips past without a record. You kept it.

            A letter is not a fix. It is not a plan, and it is not a verdict on the day. It is only a quiet voice that read what was written and wrote back. Tomorrow there will be different notes, or no notes. The kettle will click off again, and the room will be whatever the room is. The writing is enough.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()

        /**
         * BAD letter #4 — contains "better than
         * yesterday". Designed to fail ONLY the
         * "no comparative language" rule.
         */
        val BAD_FIXTURE_COMPARATIVE: String = """
            You wrote three short notes today, each one a different shade of the same kind of tired. The first one was about the weather. The second was about a friend you haven't called back. The third was about the kettle, and how it clicked off while you were still in the other room. None of these notes asked for an answer. They sat on the page, and the page held them.

            There is something worth noticing here. You are still writing. This day was better than yesterday in a way that is hard to put into words, but the words are getting closer. On a day when the body feels heavy and the room feels far away, you sat down and put three small things into words. That is not a small thing. Most days, the kettle clicking off is the kind of detail that slips past without a record. You kept it.

            A letter is not a fix. It is not a plan, and it is not a verdict on the day. It is only a quiet voice that read what was written and wrote back. Tomorrow there will be different notes, or no notes. The kettle will click off again, and the room will be whatever the room is. The writing is enough.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()

        /**
         * BAD letter #5 — too short (~80 words, well
         * below the 200-word floor). Designed to fail
         * ONLY the "word count" rule.
         */
        val BAD_FIXTURE_SHORT: String = """
            You wrote three short notes today. The first was about the weather, the second about a friend, the third about the kettle. None of them asked for an answer. They sat on the page, and the page held them. You are still writing. That is enough.

            The kettle will click off again tomorrow. The room will be whatever the room is.

            What is the first thing you might write down tomorrow, if writing felt like something you could do?
        """.trimIndent()
    }
}
