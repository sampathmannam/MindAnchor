package org.mindanchor.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderPrefsRoundTripFindingTest {

    // DataStore is a process-wide singleton keyed on the preferences
    // name. Two tests in the same Robolectric class share state
    // unless explicitly reset. The "default" test, in particular,
    // requires an empty store.
    @Before fun resetStore() = runBlocking {
        ReaderPrefs(ApplicationProvider.getApplicationContext<Context>()).reset()
    }

    @Test fun `default size is MEDIUM`() = runBlocking {
        val prefs = ReaderPrefs(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(ReadingSize.MEDIUM, prefs.size.first())
    }

    @Test fun `setSize SMALL round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ReaderPrefs(ctx)
        prefs.setSize(ReadingSize.SMALL)
        assertEquals(ReadingSize.SMALL, prefs.size.first())
    }

    @Test fun `setSize LARGE round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ReaderPrefs(ctx)
        prefs.setSize(ReadingSize.LARGE)
        assertEquals(ReadingSize.LARGE, prefs.size.first())
    }

    @Test fun `setSize MEDIUM round-trips`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ReaderPrefs(ctx)
        prefs.setSize(ReadingSize.MEDIUM)
        assertEquals(ReadingSize.MEDIUM, prefs.size.first())
    }

    @Test fun `a fresh ReaderPrefs instance returns the last set value (DataStore persists)`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ReaderPrefs(ctx).setSize(ReadingSize.LARGE)
        // New instance, same context, same DataStore: should read LARGE.
        val fresh = ReaderPrefs(ctx)
        assertEquals(ReadingSize.LARGE, fresh.size.first())
    }
}
