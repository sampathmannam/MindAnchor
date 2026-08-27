package org.mindanchor

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mindanchor.digest.DigestActivity

/**
 * Every activity must survive being launched, rotated away and resumed.
 *
 * These would have caught the whole class of defects that only appear on a
 * real device: a theme that cannot inflate, a missing manifest entry, a
 * database that fails to open, a composable that throws on first frame.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun homeActivityReachesResumed() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun homeActivitySurvivesRecreation() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun homeActivitySurvivesBackgrounding() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun digestActivityOpens() {
        ActivityScenario.launch(DigestActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
