package org.mindanchor.journal.skills

import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalTime

class SkillOfTheDayTest {
    @Test fun `morning 8am default to Breathing Space`() =
        assertEquals(SkillId.BREATHING_SPACE, SkillOfTheDay.suggest(LocalTime.of(8, 0), mood = null))
    @Test fun `midday 1pm default to Wise Mind`() =
        assertEquals(SkillId.WISE_MIND, SkillOfTheDay.suggest(LocalTime.of(13, 0), mood = null))
    @Test fun `evening 7pm default to DEAR MAN`() =
        assertEquals(SkillId.DEAR_MAN, SkillOfTheDay.suggest(LocalTime.of(19, 0), mood = null))
    @Test fun `late night 1am default to STOP`() =
        assertEquals(SkillId.STOP, SkillOfTheDay.suggest(LocalTime.of(1, 0), mood = null))
    @Test fun `mood Crushed overrides time → Tipp`() =
        assertEquals(SkillId.TIPP, SkillOfTheDay.suggest(LocalTime.of(8, 0), mood = 1))
    @Test fun `mood Heavy overrides time → Tipp`() =
        assertEquals(SkillId.TIPP, SkillOfTheDay.suggest(LocalTime.of(13, 0), mood = 2))
    @Test fun `mood Steady 8am → Breathing Space (no override)`() =
        assertEquals(SkillId.BREATHING_SPACE, SkillOfTheDay.suggest(LocalTime.of(8, 0), mood = 3))
}
