package org.mindanchor.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * v0.25.5 WP-E: the 14-day onboarding recap window.
 *
 * Kanfer & Goldstein 1991: a 14-day checkpoint is the earliest the
 * user can detect a habit pattern. The window is 7 days wide
 * (days 14-20, 28-34, 42-48, ...). The recap is shown when the user
 * is in a window AND has not already seen (or dismissed) the
 * recap for that window.
 *
 * The function is a pure `LocalDate -> Boolean` so it can be
 * tested without a Context, without a DataStore, without a clock.
 * A regression that hard-coded "show on day 14 only" or "show
 * every day" would let a user get a 14-day recap they never asked
 * for, or never get one at all. The five tests below pin the
 * boundary.
 */
class OnboardingRecapWindowFindingTest {

    private val installDay: LocalDate = LocalDate.of(2026, 3, 1)

    @Test
    fun `no install day means no recap`() {
        // installDay is the precondition for the whole feature. A
        // user who never completed onboarding has no install day,
        // and the recap must stay silent — otherwise a user who
        // skipped onboarding for two weeks would get a recap
        // they never asked for.
        assertFalse(inRecapWindowPure(installDay = null, recapSeenDay = null, today = installDay.plusDays(20)))
    }

    @Test
    fun `recap is silent in the first 14 days after install`() {
        // Day 0 through day 13: the user is still building the
        // habit. The recap would arrive before the habit is
        // measurable, and a recap that is too early is a survey.
        (0..13).forEach { offset ->
            assertFalse(
                "day $offset must not show a recap",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = null,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
    }

    @Test
    fun `recap shows in the 7-day window starting at day 14`() {
        // Days 14-20 are the first 14-day-checkpoint window. The
        // recap is the right thing to show here, and it stays
        // available for a week so a user who opens the app every
        // other day still sees it.
        (14..20).forEach { offset ->
            assertTrue(
                "day $offset must show a recap",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = null,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
    }

    @Test
    fun `recap is silent in the 7-day gap between windows`() {
        // Days 21-27 are between the first and second windows.
        // Showing the recap here would be nagging, not
        // checkpointing. A regression that turned the window
        // from 7 days wide into "every day from 14 onwards" would
        // produce a recap a day for the rest of the user's life.
        (21..27).forEach { offset ->
            assertFalse(
                "day $offset must not show a recap (between windows)",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = null,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
    }

    @Test
    fun `recapSeenDay suppresses the recap for that window only`() {
        // The user saw the day-14 recap on day 16. The next
        // window is day 28-34; the recap must come back then. A
        // regression that stored "user has seen the recap" as a
        // permanent flag would silence the day-28, day-42, ...
        // recaps for the rest of the install — turning a
        // checkpoint into a one-shot.
        val seenDay = installDay.plusDays(16)
        // Day 16-20: same window, suppressed.
        (16..20).forEach { offset ->
            assertFalse(
                "day $offset must not show a recap after the user already saw it on day 16",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = seenDay,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
        // Day 21-27: still between windows.
        (21..27).forEach { offset ->
            assertFalse(
                "day $offset must not show a recap (still between windows)",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = seenDay,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
        // Day 28-34: the next window, the recap comes back.
        (28..34).forEach { offset ->
            assertTrue(
                "day $offset must show a recap (next window)",
                inRecapWindowPure(
                    installDay = installDay,
                    recapSeenDay = seenDay,
                    today = installDay.plusDays(offset.toLong()),
                ),
            )
        }
    }
}
