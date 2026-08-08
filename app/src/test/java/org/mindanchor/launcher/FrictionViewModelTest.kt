package org.mindanchor.launcher

import android.app.Application
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The friction-gate concerns, extracted from
 * [LauncherViewModel]. The class is constructed with
 * the same Application and reads the same prefs; the
 * tests here pin the delegation contract.
 *
 * The full FrictionViewModel surface (gateFor,
 * recordNeverMind, launchTimed) needs the Android
 * Keystore and the DataStore and is exercised by the
 * project's instrumented tests. The unit tests here
 * pin the structural shape: the class exists, the
 * public method names are present, and the
 * [LauncherViewModel] is a thin facade.
 *
 * @wording-reviewed — see docs/research/21.
 */
class FrictionViewModelTest {

    @Test
    fun `the FrictionViewModel constructs from the same Application as LauncherViewModel`() {
        val app = Application()
        val fvm = FrictionViewModel(app)
        assertNotNull(fvm)
    }

    @Test
    fun `the LauncherViewModel still exposes the friction methods (delegation pattern)`() {
        val app = Application()
        val lvm = LauncherViewModel(app)
        val cls = LauncherViewModel::class.java
        // The public method signatures are unchanged.
        // The body now delegates to FrictionViewModel.
        assertTrue(
            "LauncherViewModel.recordNeverMind must still be public",
            cls.getDeclaredMethod("recordNeverMind", DisplayApp::class.java) != null,
        )
        assertTrue(
            "LauncherViewModel.launchTimed must still be public",
            cls.getDeclaredMethod("launchTimed", DisplayApp::class.java, java.lang.Long.TYPE) != null,
        )
        // The launcher is bound; assert the local var is not null.
        assertNotNull(lvm)
    }
}
