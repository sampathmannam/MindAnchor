package org.mindanchor.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.30+ (Phase 4 G-5) — the [startLockTaskOn] /
 * [stopLockTaskOn] helpers resolve the host [Activity]
 * from the [Context] and call [Activity.startLockTask] /
 * [Activity.stopLockTask]. The tests pin the resolution
 * logic: the helpers must find the activity through any
 * number of [ContextWrapper] layers, and must not call
 * startLockTask when the [Context] is not an Activity
 * (e.g. an Application context).
 *
 * Robolectric's [Activity] supports [startLockTask] /
 * [stopLockTask] (the platform stubs are no-ops in the
 * unit-test runtime) so the assertion is a no-crash check;
 * what we verify is the resolution chain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SleepLockTaskTest {

    /** The simplest case: the context is the activity itself. */
    @Test
    fun findActivity_returnsTheContextWhenItIsTheActivity() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        assertEquals(activity, activity.findActivity())
    }

    /** The standard case: a ContextWrapper around the activity. */
    @Test
    fun findActivity_unwrapsContextWrapper() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val wrapper = object : ContextWrapper(activity) {}
        assertEquals(activity, wrapper.findActivity())
    }

    /** Two layers deep: launcher theme + window manager. */
    @Test
    fun findActivity_unwrapsMultipleContextWrappers() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val outer = object : ContextWrapper(activity) {}
        val inner = object : ContextWrapper(outer) {}
        assertEquals(activity, inner.findActivity())
    }

    /**
     * The Application context (the [ApplicationProvider.getApplicationContext]
     * the rest of the launcher uses for I/O) is NOT an
     * Activity and not a ContextWrapper around one. The
     * helpers must NOT call [startLockTask] on an
     * Application context — the call would throw
     * [IllegalStateException] on a real device (the
     * Application is not on a task stack).
     */
    @Test
    fun findActivity_returnsNullForApplicationContext() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appContext.findActivity())
    }
}
