package org.mindanchor.advisory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * Program 3 Task 5 — a person who has never touched this store gets the
 * fully closed state, each switch is independent of the other, and a
 * restored backup returns to exactly that same closed state rather than
 * reopening whatever the backup happened to capture.
 */
@RunWith(AndroidJUnit4::class)
class AdvisoryPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun reset(prefs: AdvisoryPrefs) {
        prefs.setMasterAdvisoryEnabled(false)
        prefs.setDeliveryAllowed(false)
        prefs.setCurrentEpisodeId(null)
    }

    @Test
    fun aNewStoreIsFalseFalseNull() = runBlocking {
        val prefs = AdvisoryPrefs(context)
        reset(prefs)
        val settings = prefs.settings.first()
        assertEquals(AdvisorySettings(), settings)
        assertEquals(false, settings.masterAdvisoryEnabled)
        assertEquals(false, settings.deliveryAllowed)
        assertNull(settings.currentEpisodeId)
    }

    @Test
    fun theTwoSwitchesAreIndependent() = runBlocking {
        val prefs = AdvisoryPrefs(context)
        reset(prefs)

        prefs.setMasterAdvisoryEnabled(true)
        var settings = prefs.settings.first()
        assertEquals(true, settings.masterAdvisoryEnabled)
        assertEquals(false, settings.deliveryAllowed)

        prefs.setDeliveryAllowed(true)
        settings = prefs.settings.first()
        assertEquals(true, settings.masterAdvisoryEnabled)
        assertEquals(true, settings.deliveryAllowed)

        prefs.setMasterAdvisoryEnabled(false)
        settings = prefs.settings.first()
        assertEquals(false, settings.masterAdvisoryEnabled)
        assertEquals(true, settings.deliveryAllowed)

        reset(prefs)
    }

    @Test
    fun theRecoveryKeyRoundTripsIndependently() = runBlocking {
        val prefs = AdvisoryPrefs(context)
        reset(prefs)

        prefs.setCurrentEpisodeId("episode-1")
        assertEquals("episode-1", prefs.settings.first().currentEpisodeId)
        assertEquals(false, prefs.settings.first().masterAdvisoryEnabled)

        prefs.setCurrentEpisodeId(null)
        assertNull(prefs.settings.first().currentEpisodeId)

        reset(prefs)
    }

    @Test
    fun disableAfterRestoreReturnsToFalseFalseNull() = runBlocking {
        val prefs = AdvisoryPrefs(context)
        prefs.setMasterAdvisoryEnabled(true)
        prefs.setDeliveryAllowed(true)
        prefs.setCurrentEpisodeId("episode-from-backup")

        prefs.disableAfterRestore()

        val settings = prefs.settings.first()
        assertEquals(AdvisorySettings(), settings)
        assertEquals(false, settings.masterAdvisoryEnabled)
        assertEquals(false, settings.deliveryAllowed)
        assertNull(settings.currentEpisodeId)
    }
}
