package org.mindanchor.errors

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * B14 (SOTA v2 bug-hunt, agent #5): the OnboardingPrefs.installDay
 * KDoc claims "set on the first read of `done` if the field is
 * missing"; the code sets it on the first `complete()` call. The
 * two behaviours differ for a user who installs the app, opens the
 * launcher without completing onboarding, uses it for 30 days,
 * and only then completes onboarding. The KDoc's wording is wrong;
 * the code's behaviour (documented inside `complete()`) is right.
 *
 * File-shape pin: the fix PR updates the `installDay` KDoc to match
 * the actual `complete()`-based behaviour. This is a wording fix;
 * the production code is correct.
 */
class InstallDayKdocMatchesBehaviourFindingTest {

    @Test
    fun `Onboarding installDay KDoc does not claim first-read-of-done (regression guard for B14)`() {
        val source = java.io.File(
            "src/main/java/org/mindanchor/onboarding/Onboarding.kt",
        ).readText()
        // The KDoc on installDay is the block immediately above
        // `val installDay: Flow<LocalDate?> = ...`. The pre-fix
        // literal is "set on the first read of [done]".
        val installDayBlock = source.substringAfter("v0.25.5 WP-E: the day the user first ran the app.")
            .substringBefore("val installDay:")
        assertFalse(
            "Onboarding.installDay KDoc must not claim \"set on the first read of [done]\" — " +
                "the code sets the field on the first `complete()` call. The " +
                "KDoc inside `complete()` is the correct one. This is a " +
                "wording fix; the production code is right.",
            installDayBlock.contains("first read"),
        )
    }
}
