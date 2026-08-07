package org.mindanchor.settings

import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.data.AppearancePrefs
import org.mindanchor.data.NotificationPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.ui.NatureScene
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.notifications.BatchReleaser
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.sleep.SleepSummary
import org.mindanchor.sunset.SunsetController

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = NotificationPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = org.mindanchor.data.FrictionPrefs(application)
    private val sleepRepository = SleepRepository(application)
    private val appearancePrefs = AppearancePrefs(application)
    private val onboardingPrefs = org.mindanchor.onboarding.OnboardingPrefs(application)

    /**
     * What the person said they were struggling with, at onboarding or
     * since. Used to mark the parts of this screen they came for — never
     * to switch anything on for them.
     */
    val goals = onboardingPrefs.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setGoals(goals: Set<org.mindanchor.onboarding.Goal>) {
        viewModelScope.launch { onboardingPrefs.setGoals(goals) }
    }

    val batchingEnabled = prefs.batchingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val batchedApps = prefs.batchedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(getApplication())
            .contains(getApplication<Application>().packageName)

    fun setBatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBatchingEnabled(enabled)
            if (enabled) BatchAlarms.ensureScheduled(getApplication())
        }
    }

    fun setAppBatched(packageName: String, batched: Boolean) {
        viewModelScope.launch { prefs.setAppBatched(packageName, batched) }
    }

    fun releaseNow() {
        viewModelScope.launch { BatchReleaser.releaseNow(getApplication()) }
    }

    // --- Sunset mode ---

    val sunsetEnabled = sunsetPrefs.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun hasDndAccess(): Boolean =
        getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true

    /**
     * Whether the screen also goes grey through the quiet hours. Kept
     * separate from sunset itself: a quiet phone and a colourless one are
     * different wishes and neither should imply the other.
     */
    /**
     * Hands device ownership back, lifting every suspension first.
     *
     * A way out has to exist and has to be here. Ownership cannot be
     * removed by adb once granted, so if this button did not exist the
     * only route back would be wiping the phone — and telling someone
     * their way out of a wellbeing app is a factory reset would be its own
     * small cruelty.
     */
    /**
     * Hands device ownership back, lifting every suspension first.
     *
     * [onDone] runs once the release has actually happened, so the screen
     * can re-read ownership rather than keep showing the state it had a
     * moment ago. Without it the section still reads "set up as its own
     * guardian" until the next resume, which is the kind of lie that makes
     * a person tap the button again.
     */
    fun releaseDeviceOwner(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val chosen = frictionPrefs.flaggedApps.first()
            org.mindanchor.admin.DeviceOwner.release(getApplication(), chosen)
            onDone()
        }
    }

    val grayscaleAtNight = sunsetPrefs.grayscaleAtNight

    fun setGrayscaleAtNight(enabled: Boolean) {
        viewModelScope.launch {
            sunsetPrefs.setGrayscaleAtNight(enabled)
            // Apply immediately if the quiet hours have already begun,
            // rather than leaving the switch looking broken until 22:00.
            val inWindow = SunsetPrefs.isQuietHour()
            if (inWindow || !enabled) {
                org.mindanchor.grayscale.Grayscale.set(getApplication(), enabled && inWindow)
            }
            SunsetController.onToggled(getApplication(), enabled || sunsetPrefs.isEnabled())
        }
    }

    fun setSunsetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            sunsetPrefs.setEnabled(enabled)
            SunsetController.onToggled(getApplication(), enabled)
        }
    }

    // --- Sleep rhythm ---

    private val sleepState = MutableStateFlow<SleepSummary?>(null)
    val sleepSummary = sleepState.asStateFlow()

    init {
        refreshSleep()
    }

    fun hasUsageAccess(): Boolean = sleepRepository.hasUsageAccess()

    fun refreshSleep() {
        viewModelScope.launch(Dispatchers.IO) {
            sleepState.value = sleepRepository.estimate()
        }
    }

    // --- Home-screen appearance ---

    val natureScene = appearancePrefs.scene
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NatureScene.ROTATE)

    fun setNatureScene(scene: NatureScene) {
        viewModelScope.launch { appearancePrefs.setScene(scene) }
    }
}
