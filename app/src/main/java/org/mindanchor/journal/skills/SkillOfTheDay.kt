package org.mindanchor.journal.skills

import java.time.LocalTime

object SkillOfTheDay {
    fun suggest(now: LocalTime, mood: Int?): SkillId = when {
        // Mood override (BPD-safety: when bad, TIPP is grounding)
        mood != null && mood <= 2 -> SkillId.TIPP

        // Time-based default
        now.hour in 5..11 -> SkillId.BREATHING_SPACE
        now.hour in 12..16 -> SkillId.WISE_MIND
        now.hour in 17..22 -> SkillId.DEAR_MAN
        else -> SkillId.STOP  // 23-04
    }
}
