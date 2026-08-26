package org.mindanchor.anchorcore

import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

/**
 * Every string the loop can emit states a count or a direction and
 * stops. The ban list is LetterPrompt's voice-rules vocabulary — the
 * words that turn a fact into a verdict.
 */
class AnchorWordingTest {

    private val banned = listOf(
        "good", "bad", "well done", "great", "proud",
        "should", "must", "try to", "better than", "worse",
    )

    @Test
    fun `no renderer output carries a verdict word`() {
        val samples = FactKind.entries.map { DayFactRenderer.render(it, "3|300") } +
            LetterFactsSection.compose(
                AnchorState.Steady(
                    facts = listOf(DayFact(FactKind.LATE_NIGHT_CLUSTER, "3|300", LocalDate.of(2026, 8, 26))),
                    weekFlagged = true,
                    computedAtEpochMillis = 0L,
                ),
            )!!.let(::listOf)
        for (line in samples) {
            for (word in banned) {
                assertFalse(
                    "banned word '$word' in: $line",
                    line.contains(word, ignoreCase = true),
                )
            }
        }
    }
}
