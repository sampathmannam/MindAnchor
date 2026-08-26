package org.mindanchor.settings

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mindanchor.corpus.CorpusImport
import org.mindanchor.corpus.CorpusStore
import org.mindanchor.data.AppearancePrefs
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterStore
import org.mindanchor.letters.LetterWriter
import org.mindanchor.letters.WeekDataCollector
import org.mindanchor.narrate.ModelSlot
import org.mindanchor.narrate.ModelStore
import org.mindanchor.data.NotificationPrefs
import org.mindanchor.data.SunsetPrefs
import org.mindanchor.reader.ReaderPrefs
import org.mindanchor.reader.ReadingSize
import org.mindanchor.ui.NatureScene
import org.mindanchor.notifications.BatchAlarms
import org.mindanchor.notifications.BatchSchedule
import org.mindanchor.notifications.BatchReleaser
import org.mindanchor.report.Coverage
import org.mindanchor.report.CoverageLedger
import org.mindanchor.report.ReportStore
import org.mindanchor.report.ReportScheduler
import org.mindanchor.sleep.Deviation
import org.mindanchor.sleep.SleepRepository
import org.mindanchor.sleep.SleepSummary
import org.mindanchor.sleep.SleepWindowOptimizer
import org.mindanchor.sunset.Chronotype
import org.mindanchor.sunset.SunsetController
import org.mindanchor.vitals.DailyVitals
import org.mindanchor.vitals.HealthConnectSource
import org.mindanchor.vitals.coros.CorosAuth
import org.mindanchor.vitals.coros.CorosConnectionState
import org.mindanchor.vitals.coros.CorosSyncWorker
import org.mindanchor.vitals.coros.CorosVitalSource
import java.time.LocalDate

@androidx.annotation.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = NotificationPrefs(application)
    private val sunsetPrefs = SunsetPrefs(application)
    private val frictionPrefs = org.mindanchor.data.FrictionPrefs(application)
    private val sleepRepository = SleepRepository(application)
    private val appearancePrefs = AppearancePrefs(application)
    private val onboardingPrefs = org.mindanchor.onboarding.OnboardingPrefs(application)
    private val reportStore = ReportStore(application)
    private val backupPrefs = org.mindanchor.backup.BackupPrefs(application)

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

    /**
     * Pauses that have stopped being pauses — see
     * [org.mindanchor.friction.GateLedger].
     *
     * Only ever read here. This never becomes a notification: somebody
     * having a bad month does not need their phone volunteering that their
     * guards look pointless. They have to come and ask.
     */
    val stalePauses = combine(
        frictionPrefs.gateTallies,
        frictionPrefs.flaggedApps,
    ) { tallies, flagged ->
        val today = java.time.LocalDate.now()
        flagged.mapNotNull { pkg ->
            val tally = tallies[pkg] ?: return@mapNotNull null
            if (org.mindanchor.friction.GateLedger.worthMentioning(tally, today)) {
                pkg to tally
            } else {
                null
            }
        }.sortedBy { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The small things the person said help them — their words only, never
     * seeded with suggestions. See
     * [org.mindanchor.friction.SmallThings] for when they are offered and,
     * more importantly, when they are not.
     */
    val smallThings = frictionPrefs.smallThings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSmallThing(thing: String) {
        viewModelScope.launch { frictionPrefs.addSmallThing(thing) }
    }

    fun removeSmallThing(thing: String) {
        viewModelScope.launch { frictionPrefs.removeSmallThing(thing) }
    }

    /**
     * The user's own self-compassion phrases — see
     * [org.mindanchor.friction.CompassionMoment]. Their
     * words only; the launcher never seeds suggestions
     * (Neff 2003, Linardon 2020 meta).
     */
    val compassionMoments = frictionPrefs.compassionMoments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCompassionMoment(phrase: String) {
        viewModelScope.launch { frictionPrefs.addCompassionMoment(phrase) }
    }

    fun removeCompassionMoment(phrase: String) {
        viewModelScope.launch { frictionPrefs.removeCompassionMoment(phrase) }
    }

    /** Somebody looked at the numbers and kept the pause. Start again. */
    fun keepPause(packageName: String) {
        viewModelScope.launch { frictionPrefs.resetTally(packageName) }
    }

    /** Somebody looked at the numbers and let the pause go. */
    fun dropPause(packageName: String) {
        viewModelScope.launch {
            frictionPrefs.setFlagged(packageName, false)
            frictionPrefs.resetTally(packageName)
        }
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

    /** When the batches arrive. The person's own, defaulting to the studied dosage. */
    val releaseTimes = prefs.releaseTimes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatchSchedule.DEFAULT_TIMES)

    /**
     * Moves one release time by [byMinutes], or does nothing.
     *
     * Nothing is the right answer when the move would land on another
     * release: see BatchSchedule.nudged for why two batches at the same
     * minute is refused rather than stored. A refused nudge simply leaves
     * the times as they were, which is what the button not appearing to
     * do anything already means to somebody pressing it.
     */
    fun nudgeReleaseTime(slot: Int, byMinutes: Long) {
        viewModelScope.launch {
            val moved = BatchSchedule.nudged(prefs.currentReleaseTimes(), slot, byMinutes)
                ?: return@launch
            if (prefs.setReleaseTimes(moved)) BatchAlarms.ensureScheduled(getApplication())
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
     * Hands device ownership back, lifting every suspension first.
     *
     * A way out has to exist and has to be here. Ownership cannot be
     * removed by adb once granted, so if this did not exist the only route
     * back would be wiping the phone — and telling someone their way out
     * of a wellbeing app is a factory reset would be its own small
     * cruelty.
     *
     * [onDone] runs once the release has actually happened, so the screen
     * can re-read ownership rather than keep showing the state it had a
     * moment ago. Without it the section still reads "set up as its own
     * guardian" once it no longer is, which is the kind of lie that makes
     * a person tap the button again.
     */
    fun releaseDeviceOwner(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val chosen = frictionPrefs.flaggedApps.first()
            org.mindanchor.admin.DeviceOwner.release(getApplication(), chosen)
            onDone()
        }
    }

    /**
     * Whether the screen also goes grey through the quiet hours. Kept
     * separate from sunset itself: a quiet phone and a colourless one are
     * different wishes and neither should imply the other.
     */
    val grayscaleAtNight = sunsetPrefs.grayscaleAtNight

    fun setGrayscaleAtNight(enabled: Boolean) {
        viewModelScope.launch {
            sunsetPrefs.setGrayscaleAtNight(enabled)
            // Apply immediately if the quiet hours have already begun,
            // rather than leaving the switch looking broken until 22:00.
            val inWindow = sunsetPrefs.isQuietHour()
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

    val sunsetStart = sunsetPrefs.startTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SunsetPrefs.DEFAULT_START)

    val sunsetEnd = sunsetPrefs.endTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SunsetPrefs.DEFAULT_END)

    /**
     * Moves either end of the quiet hours by [startMinutes] / [endMinutes].
     *
     * Steppers rather than a clock dialog: the targets are large, which
     * matters for anyone with tremor or in distress, and nudging is what
     * people actually do to a bedtime — half an hour at a time, not by
     * typing an exact number.
     *
     * The alarms are re-armed afterwards. They are held by AlarmManager at
     * the old times, and nothing else would ever move them — the window
     * would look changed in settings and behave exactly as before.
     */
    fun nudgeSunset(startMinutes: Long, endMinutes: Long) {
        viewModelScope.launch {
            val (start, end) = sunsetPrefs.window()
            val moved = sunsetPrefs.setWindow(
                start.plusMinutes(startMinutes),
                end.plusMinutes(endMinutes),
            )
            if (moved) SunsetController.ensureScheduled(getApplication())
        }
    }

    // --- Phase 1 v0.26+ protective layer (G-22, G-21, G-19) ---

    /**
     * v0.26+ (G-22) — the behavioural-activation weekly-prompt
     * setting. When on, the Friday-evening PreHome surface
     * offers "Pick one mastery + one pleasure activity".
     * The Dimidjian 2006 (BA RCT, N=241) is the evidence
     * anchor. Default OFF — the project's opt-out-by-silence
     * rule.
     */
    val baPromptEnabled = frictionPrefs.baPromptEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setBaPromptEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setBaPromptEnabled(enabled) }
    }

    /**
     * v0.26+ (G-21) — the morning self-compassion break
     * setting. When on, the home surface shows the
     * 90-second ritual as the first thing the user sees
     * on PreHome cold-start. The Neff 2003 / Linardon 2020
     * meta (27 RCTs of smartphone-based self-compassion
     * apps, g=0.31 self-compassion) is the evidence anchor.
     * Default OFF.
     */
    val morningCompassionEnabled = frictionPrefs.morningCompassionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMorningCompassionEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setMorningCompassionEnabled(enabled) }
    }

    /**
     * v0.26+ (G-19) — the compassionate-wrap setting. When
     * on, AppWatchService posts a Snackbar offer
     * ("You were on Instagram for 32 minutes — note
     * anything?") when the user closes a doomscroll app
     * after 30+ minutes. The Snackbar is a 1-tap offer,
     * never a judgment. Default OFF.
     */
    val compassionateWrapEnabled = frictionPrefs.compassionateWrapEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCompassionateWrapEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setCompassionateWrapEnabled(enabled) }
    }

    /**
     * v0.28+ (Phase 3 G-8) — the expressive-writing prompt toggle.
     * Pennebaker 1997 (minimum-dosage 3-sentence entry point) is
     * the evidence anchor. Default OFF — the project's
     * opt-out-by-silence rule.
     */
    val expressiveWritingEnabled = frictionPrefs.expressiveWritingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setExpressiveWritingEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setExpressiveWritingEnabled(enabled) }
    }

    /**
     * v0.28+ (Phase 3 G-26) — the wind-down card toggle.
     * When on, the home surface shows the wind-down card after
     * the configured time (default 21:00). Default OFF.
     */
    val windDownEnabled = frictionPrefs.windDownEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWindDownEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setWindDownEnabled(enabled) }
    }

    /**
     * v0.28+ (Phase 3 G-29) — the gratitude card toggle.
     * Seligman 2005 (active-constructive response RCT) is the
     * evidence anchor. Default OFF.
     */
    val gratitudeEnabled = frictionPrefs.gratitudeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGratitudeEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setGratitudeEnabled(enabled) }
    }

    /**
     * v0.29+ (Phase 4 G-6) — the push-up mode toggle.
     * When on, opening a flagged app shows the push-up counter
     * (Hauck 2020 anchor). Default OFF.
     */
    val pushUpModeEnabled = frictionPrefs.pushUpModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPushUpModeEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setPushUpModeEnabled(enabled) }
    }

    /**
     * v0.29+ (Phase 4 G-28) — the voice journal toggle.
     * When on, the Anchor Note has a Record affordance. Default
     * OFF.
     */
    val voiceJournalEnabled = frictionPrefs.voiceJournalEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setVoiceJournalEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setVoiceJournalEnabled(enabled) }
    }

    /**
     * v0.30+ (spec Phase 1) — the PreHome
     * moment-of-pause opt-in. Default OFF. The
     * PreHomeActivity self-skips to HomeActivity
     * when this is false, so the launcher is
     * always-on for the always-on home unless the
     * user has explicitly asked for the pause.
     */
    val prehomeEnabled = frictionPrefs.prehomeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setPrehomeEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setPrehomeEnabled(enabled) }
    }

    /**
     * v0.70+ (Phase 1 T-1.5) — morning protection.
     * The user opts in; default is OFF. When ON, the
     * doomscroll apps (the existing
     * [org.mindanchor.prehome.DoomscrollList]) are
     * forced through the friction gate for the
     * user-set N minutes after the first
     * ACTION_USER_PRESENT of the local day. The
     * minutes field is the duration; 0 means off.
     */
    val morningProtectionEnabled = frictionPrefs.morningProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMorningProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { frictionPrefs.setMorningProtectionEnabled(enabled) }
    }

    val morningProtectionMinutes = frictionPrefs.morningProtectionMinutes
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            org.mindanchor.friction.MorningProtectionState.MAX_MINUTES / 2,
        )

    fun setMorningProtectionMinutes(minutes: Int) {
        viewModelScope.launch { frictionPrefs.setMorningProtectionMinutes(minutes) }
    }

    /**
     * v0.30+ (spec Phase 2) — the active-hours
     * window for notification curate. The
     * [AnchorNotificationListenerService] demotes
     * notifications from the doomscroll set only
     * inside this window; outside it, notifications
     * pass through unchanged. Default 21:00-07:00
     * (the spec's recommendation).
     */
    private val notificationPrefs = org.mindanchor.data.NotificationPrefs(
        application,
    )
    val activeHoursStart = notificationPrefs.activeHoursStart
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            org.mindanchor.data.NotificationPrefs.DEFAULT_ACTIVE_START,
        )
    val activeHoursEnd = notificationPrefs.activeHoursEnd
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            org.mindanchor.data.NotificationPrefs.DEFAULT_ACTIVE_END,
        )

    fun setActiveHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            notificationPrefs.setActiveHours(startMinutes, endMinutes)
        }
    }

    /**
     * v0.30+ (spec Phase 2) — the held-retention
     * window in days. Held notifications older than
     * this are pruned on listener connect. Default
     * 7 days (the spec's recommendation).
     */
    val heldRetentionDays = notificationPrefs.heldRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    fun setHeldRetentionDays(days: Int) {
        viewModelScope.launch {
            notificationPrefs.setHeldRetentionDays(days)
        }
    }

    /**
     * T-3.2 (v0.72+) — whether marketing notifications are demoted to
     * silent digest entries. Independent of batching being enabled: the
     * classifier holds marketing pings even from apps the person never
     * asked to batch, so its toggle stands on its own.
     */
    val marketingDemotionEnabled = notificationPrefs.marketingDemotionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setMarketingDemotionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPrefs.setMarketingDemotionEnabled(enabled)
        }
    }

    // --- Going Light ---

    /**
     * The user's Going Light schedule — see
     * [org.mindanchor.friction.GoingLightSchedule]. A surface read
     * of the FrictionPrefs-backed flow so the settings UI can show
     * the live state and so the toggle is round-trip stable.
     */
    val goingLightSchedule: StateFlow<org.mindanchor.friction.GoingLightSchedule> =
        frictionPrefs.goingLightSchedule
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                org.mindanchor.friction.GoingLightSchedule(),
            )

    /**
     * Whether the user has granted the OS-level VPN consent that
     * [org.mindanchor.goinglight.GoingLightVpnService] needs.
     * Re-checked on every call (the user can revoke from system
     * settings without this app being told).
     */
    fun hasGoingLightConsent(): Boolean =
        org.mindanchor.goinglight.GoingLightConsent.hasConsent(getApplication())

    /**
     * The Intent the OS expects the launching Activity to start
     * `forResult` for the consent dialog, or null when consent is
     * already in place. The settings Composable is the host — the
     * wrapper does not start activities.
     */
    fun prepareGoingLightConsent(): android.content.Intent? =
        org.mindanchor.goinglight.GoingLightConsent.prepareConsent(getApplication())

    /**
     * Persist the user's choice and arm the Going Light scheduler.
     *
     * If [enabled] is true, the schedule is widened to "every day"
     * the first time (the Castelo 2025 protocol is a daily window;
     * the user can narrow it later from a follow-up UI). The
     * VpnService start is *not* triggered here — that fires from
     * the scheduler at the next transition, by which time the
     * user has either granted the OS consent or the system
     * rejection will surface to them. This split keeps "turn it
     * on" decoupled from "the phone is now intercepting traffic",
     * which is the only ordering the consent dialog allows.
     */
    fun setGoingLightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = frictionPrefs.goingLightSchedule.first()
            val updated = if (enabled) {
                current.copy(
                    enabled = true,
                    activeDays = if (current.activeDays.isEmpty()) {
                        java.time.DayOfWeek.values().toSet()
                    } else {
                        current.activeDays
                    },
                )
            } else {
                current.copy(enabled = false)
            }
            frictionPrefs.setGoingLightSchedule(updated)
            val ctx = getApplication<Application>()
            if (enabled) {
                org.mindanchor.goinglight.GoingLightScheduler.enable(ctx, updated)
            } else {
                org.mindanchor.goinglight.GoingLightScheduler.disable(ctx)
            }
        }
    }

    // --- "Try it now" sunset trial ---

    /**
     * The wind-down trial the user can run from settings to see what
     * the quiet hours actually feel like, without waiting for 22:00.
     *
     * The flow: save the current interruption filter, apply the
     * priority-only filter SunsetController would apply at the start
     * of the window, hold for 60 seconds, then revert. Grayscale
     * is engaged only when the user has already opted into grey
     * nights *and* the permission is in place, so a one-off trial
     * never silently turns the screen grey for someone who never
     * asked for that.
     *
     * Idempotent: a second tap while a trial is running is a
     * no-op (the [sunsetTrialState] guard rejects the re-entry).
     * The state is observable so the UI can show "Trial running,
     * 45 s left" rather than a switch that just looks off.
     */
    private val _sunsetTrialState = MutableStateFlow<SunsetTrialState>(SunsetTrialState.Idle)
    val sunsetTrialState: StateFlow<SunsetTrialState> = _sunsetTrialState.asStateFlow()

    fun startSunsetTrial(durationSeconds: Int = 60) {
        if (_sunsetTrialState.value !is SunsetTrialState.Idle) return
        val ctx = getApplication<Application>()
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        // CodeRabbit review 2026-08-24 (PR #38): the
        // previous version reserved the Running
        // state *after* the first suspension
        // (manager.currentInterruptionFilter +
        // SunsetController.applyFilter). A second
        // call before that assignment passed the
        // guard at line 488; two countdown loops
        // would then run. The KDoc says the call is
        // idempotent, so the behaviour did not match
        // the contract. Reserve the Running state
        // with a provisional greyscaleOn = false
        // *before* the launch; copy() to the real
        // value once `greyOn` is known, and let the
        // second `_sunsetTrialState.value !is Idle`
        // guard reject the duplicate.
        val previous = manager.currentInterruptionFilter
        _sunsetTrialState.value = SunsetTrialState.Running(
            previousFilter = previous,
            greyscaleOn = false,
            remainingSeconds = durationSeconds,
        )
        viewModelScope.launch {
            // Apply the same filter SunsetController applies at start.
            SunsetController.applyFilter(ctx, priorityOnly = true)
            // Greyscale only when the user has opted into grey nights
            // and the OS permission is in place. Without both, the
            // trial still shows the interruption change, which is the
            // main thing the user is trying to feel.
            val greyNights = sunsetPrefs.isGrayscaleAtNight()
            val greyGranted = org.mindanchor.grayscale.Grayscale.isGranted(ctx)
            val greyOn = greyNights && greyGranted
            if (greyOn) org.mindanchor.grayscale.Grayscale.set(ctx, true)
            _sunsetTrialState.value = _sunsetTrialState.value.let {
                if (it is SunsetTrialState.Running) it.copy(greyscaleOn = greyOn) else it
            }
            var remaining = durationSeconds
            while (remaining > 0) {
                kotlinx.coroutines.delay(1_000)
                remaining -= 1
                val now = _sunsetTrialState.value
                if (now !is SunsetTrialState.Running) return@launch
                _sunsetTrialState.value = now.copy(remainingSeconds = remaining)
            }
            // Revert.
            SunsetController.applyFilter(ctx, priorityOnly = false)
            if (greyOn) org.mindanchor.grayscale.Grayscale.set(ctx, false)
            _sunsetTrialState.value = SunsetTrialState.Idle
        }
    }

    /**
     * The user aborted the trial early. Reverts immediately and
     * drops back to idle so a fresh "try it now" is possible.
     */
    fun cancelSunsetTrial() {
        val current = _sunsetTrialState.value as? SunsetTrialState.Running ?: return
        val ctx = getApplication<Application>()
        SunsetController.applyFilter(ctx, priorityOnly = current.previousFilter ==
            NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        if (current.greyscaleOn) {
            org.mindanchor.grayscale.Grayscale.set(ctx, false)
        }
        _sunsetTrialState.value = SunsetTrialState.Idle
    }

    /**
     * The user's chronotype, or [Chronotype.UNKNOWN] before the
     * onboarding question has been answered. Surfaced in settings so
     * the answer can be edited without re-running onboarding.
     *
     * Drives the default quiet-hours window: a new answer overwrites
     * the window unless the user has already picked one of their own
     * (see [SunsetPrefs.setChronotype]). The auto-update is the whole
     * point of the question.
     */
    val chronotype = sunsetPrefs.chronotype
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Chronotype.UNKNOWN)

    fun setChronotype(chronotype: Chronotype) {
        viewModelScope.launch {
            sunsetPrefs.setChronotype(chronotype)
            if (sunsetPrefs.isEnabled()) {
                SunsetController.ensureScheduled(getApplication())
            }
        }
    }

    // --- Sleep rhythm ---

    private val sleepState = MutableStateFlow<SleepSummary?>(null)
    val sleepSummary = sleepState.asStateFlow()

    init {
        refreshSleep()
        // The Health Connect status is read on the first UI
        // composition of the wearable section rather than here:
        // the underlying [MutableStateFlow] is declared further
        // down this class, and Kotlin initialises properties
        // top-to-bottom before running any [init] block, so an
        // init-time call to [refreshHealthConnectStatus] would
        // touch a [StateFlow.setValue] on a still-null field.
        // The settings UI calls [refreshHealthConnectStatus] from
        // a [LaunchedEffect] on first composition, which is
        // strictly after the ViewModel is fully built.
    }

    fun hasUsageAccess(): Boolean = sleepRepository.hasUsageAccess()

    val sleepMirror = sunsetPrefs.sleepMirror
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSleepMirror(enabled: Boolean) {
        viewModelScope.launch { sunsetPrefs.setSleepMirror(enabled) }
    }

    /**
     * How many of the recent nights ran later than this person's own
     * usual, or null when there is nothing honest to say.
     *
     * Null covers three separate cases and deliberately renders as
     * silence in all of them: the mirror is off, there are too few nights
     * to have a usual, or the week was steady. A screen that reported
     * "nothing unusual" every day would have taught somebody to check it.
     */
    val nightsLaterThanUsual: StateFlow<Int?> = combine(
        sunsetPrefs.sleepMirror,
        sleepState,
    ) { on, summary ->
        if (!on || summary == null) return@combine null
        val onsets = summary.windows.map {
            val time = java.time.Instant.ofEpochMilli(it.startMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
            Deviation.minutesAfterSixPm(time.hour * 60 + time.minute)
        }
        if (Deviation.worthShowing(onsets)) Deviation.laterThanUsual(onsets) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshSleep() {
        viewModelScope.launch(Dispatchers.IO) {
            sleepState.value = sleepRepository.estimate()
        }
    }

    /**
     * A wind-down window suggested from the user's own recent sleep
     * onsets, or null when there is not enough data to suggest
     * anything.
     *
     * Built on the regularity-not-duration finding (Windred et al.
     * 2024, *SLEEP* 47(1):zsad285). The suggestion is opt-in: the
     * settings panel renders it as a one-line "your nights cluster
     * around X" with a single button to apply it. Nothing is set
     * automatically.
     */
    val sleepSuggestion: StateFlow<SleepWindowOptimizer.Suggestion?> =
        sleepState.map { summary ->
            summary?.let {
                SleepWindowOptimizer.suggest(it.windows, java.time.ZoneId.systemDefault())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Applies the optimizer's suggested window. Sets the customised
     * flag, so a later chronotype change will not stomp on it.
     */
    fun applySleepSuggestion(suggestion: SleepWindowOptimizer.Suggestion) {
        viewModelScope.launch {
            sunsetPrefs.setWindow(suggestion.startTime, suggestion.endTime)
            if (sunsetPrefs.isEnabled()) {
                SunsetController.ensureScheduled(getApplication())
            }
        }
    }

    // --- Home-screen appearance ---

    val natureScene = appearancePrefs.scene
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NatureScene.ROTATE)

    fun setNatureScene(scene: NatureScene) {
        viewModelScope.launch { appearancePrefs.setScene(scene) }
    }

    // --- Check-ins (EMA) ---

    private val momentStore = org.mindanchor.model.MomentStore(application)

    val emaEnabled = momentStore.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** How many check-ins exist, for a plain count — never a streak. */
    val emaCount = momentStore.count
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The full [org.mindanchor.model.Moment] stream,
     * exposed for the "What your check-ins show"
     * insights section
     * ([org.mindanchor.insights.CheckInInsightsSection]).
     * The Composable collects this flow and passes
     * the live list to
     * [org.mindanchor.insights.CheckInPatterns.compute].
     */
    val moments = momentStore.moments

    fun setEmaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            momentStore.setEnabled(enabled)
            // Arms today's prompts when switched on; clears every armed
            // alarm when switched off. Same call either way — see
            // EmaScheduler.ensureScheduled.
            org.mindanchor.model.EmaScheduler.ensureScheduled(getApplication())
        }
    }

    // --- Last night's look (nightly report) ---

    val reportEnabled = reportStore.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setReportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            reportStore.setEnabled(enabled)
            // Arms the nightly alarm when switched on; cancels it when
            // switched off. Calling this again on an already-armed
            // schedule replaces the one alarm rather than stacking a
            // second — see ReportScheduler.ensureScheduled.
            if (enabled) {
                ReportScheduler.ensureScheduled(getApplication())
            } else {
                ReportScheduler.cancel(getApplication())
            }
        }
    }

    // --- Proving the pipeline on this phone ---
    //
    // The nightly build runs unattended, and "it will have worked" is an
    // assumption this project no longer makes anywhere. These exist so
    // the person can run the whole pipeline once, on demand, and read
    // per-signal facts about what is actually arriving — instead of
    // discovering in week three that a source was silent all along.

    val reportGeneratedDay = reportStore.generatedDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _reportRunning = MutableStateFlow(false)
    val reportRunning: StateFlow<Boolean> = _reportRunning.asStateFlow()

    fun runReportNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _reportRunning.value = true
            ReportScheduler.runNow(getApplication())
            _reportRunning.value = false
        }
    }

    /** Per-signal coverage from the last build, or null before any build. */
    val coverage: StateFlow<List<Coverage>?> = reportStore.coverage
        .map { encoded -> encoded?.let(CoverageLedger::decode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _probe = MutableStateFlow<DailyVitals?>(null)

    /** What Health Connect held for yesterday, read on demand. */
    val probe: StateFlow<DailyVitals?> = _probe.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    /**
     * What Health Connect looks like on this device, for the permission
     * grant flow. The launcher never reads a byte of wearable data
     * without the user having first seen the system dialog and tapped
     * allow — this state drives the "Connect to your watch" button in
     * the settings UI.
     */
    sealed interface HealthConnectStatus {
        data object Unknown : HealthConnectStatus
        data object Unavailable : HealthConnectStatus
        data class Available(val granted: Int, val total: Int) : HealthConnectStatus
    }

    private val _healthConnectStatus = MutableStateFlow<HealthConnectStatus>(HealthConnectStatus.Unknown)
    val healthConnectStatus: StateFlow<HealthConnectStatus> = _healthConnectStatus.asStateFlow()

    /**
     * Recompute the Health Connect permission state. Called on the
     * settings screen's first composition, after a permission flow
     * returns, and any time the launcher returns to the foreground.
     */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _healthConnectStatus.value = if (!HealthConnectSource.isAvailable(app)) {
                HealthConnectStatus.Unavailable
            } else {
                val granted = HealthConnectSource.grantedPermissions(app).size
                HealthConnectStatus.Available(
                    granted = granted,
                    total = HealthConnectSource.PERMISSIONS.size,
                )
            }
        }
    }

    /**
     * Reads yesterday straight from Health Connect, right now.
     *
     * The one honest way to learn what a particular watch actually
     * exports is to look — the vendor documentation for this project's
     * own watch turned out to describe less than the signal list hoped
     * for, and the next watch will differ again.
     */
    fun probeYesterday() {
        viewModelScope.launch(Dispatchers.IO) {
            _probing.value = true
            _probe.value = runCatching {
                HealthConnectSource.readDailyVitals(getApplication(), LocalDate.now().minusDays(1))
            }.getOrNull()
            _probing.value = false
        }
    }

    // --- Research on file (the corpus behind every report) ---

    private val _corpusSize = MutableStateFlow(0)

    /** How many passages the report has to draw on. */
    val corpusSize: StateFlow<Int> = _corpusSize.asStateFlow()

    private val _corpusImported = MutableStateFlow(false)

    /** Whether anything has been added on top of the bundled seed. */
    val corpusImported: StateFlow<Boolean> = _corpusImported.asStateFlow()

    private val _lastImport = MutableStateFlow<CorpusImportReport?>(null)

    /**
     * What the last import did, or null before one has happened this
     * session. Deliberately not persisted: it is a reply to a tap, and a
     * reply still sitting there a week later is not news, it is clutter.
     */
    val lastImport: StateFlow<CorpusImportReport?> = _lastImport.asStateFlow()

    fun refreshCorpus() {
        viewModelScope.launch(Dispatchers.IO) {
            _corpusSize.value = CorpusStore.load(getApplication()).size
            _corpusImported.value = CorpusStore.hasImported(getApplication())
        }
    }

    /**
     * Reads a picked file, merges it into what is already on file, and
     * stores the result.
     *
     * All of it on [Dispatchers.IO]: this reads a file of unknown size
     * off storage the app does not own, and doing that on the main thread
     * is how a settings screen freezes on somebody's slow SD card.
     *
     * A file that yields nothing usable is reported and **not** written.
     * Overwriting a working corpus with the result of a mis-tap would be
     * a destructive answer to a harmless mistake.
     */
    fun importCorpus(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val raw = CorpusStore.readPicked(context, uri)
            if (raw == null) {
                _lastImport.value = CorpusImportReport(unreadable = true)
                return@launch
            }
            val outcome = CorpusImport.merge(CorpusStore.load(context), raw)
            val stored = if (outcome.isEmpty) true else CorpusStore.saveImported(context, outcome.corpus)
            _lastImport.value = CorpusImportReport(
                added = outcome.added,
                replaced = outcome.replaced,
                skippedRows = outcome.skippedRows,
                truncated = outcome.truncated,
                unreadable = !stored,
            )
            _corpusSize.value = CorpusStore.load(context).size
            _corpusImported.value = CorpusStore.hasImported(context)
        }
    }

    /** Back to the bundled seed alone. */
    fun clearCorpus() {
        viewModelScope.launch(Dispatchers.IO) {
            CorpusStore.clearImported(getApplication())
            _lastImport.value = null
            _corpusSize.value = CorpusStore.load(getApplication()).size
            _corpusImported.value = CorpusStore.hasImported(getApplication())
        }
    }

    // --- Model (the small model a future writing engine would run) ---
    //
    // Mirrors the corpus section immediately above: a plain file import
    // into app-private storage, with the whole read and copy on
    // Dispatchers.IO because ModelStore is moving a multi-gigabyte file
    // off storage the app does not own. See ModelStore and Narrator for
    // why importing one does not yet make any writing happen.

    private val _modelPresent = MutableStateFlow(false)

    /** Whether a model is on file at all. */
    val modelPresent: StateFlow<Boolean> = _modelPresent.asStateFlow()

    private val _modelFit = MutableStateFlow(ModelSlot.Fit.TOO_LARGE)

    /**
     * Whether the model on file would actually run here. Meaningless
     * while [modelPresent] is false, where it defaults to the same
     * refuse-by-default value [ModelStore.fit] itself falls back to.
     */
    val modelFit: StateFlow<ModelSlot.Fit> = _modelFit.asStateFlow()

    private val _modelImportFailed = MutableStateFlow(false)

    /** Whether the most recent import attempt this session failed. */
    val modelImportFailed: StateFlow<Boolean> = _modelImportFailed.asStateFlow()

    fun refreshModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
            // Keep the new boolean StateFlow in sync with the
            // detailed fit enum, so a UI consuming [modelFits] sees
            // the same answer the model card does — both are read
            // off the same model file on the same disk.
            ModelStore.refreshFit(context)
        }
    }

    /**
     * Reads a picked file into app-private storage, replacing whatever
     * model was there before.
     *
     * A failed import leaves the previous model, if any, untouched — see
     * [ModelStore.importFrom] for why a failed copy never leaves a
     * partial file to be mistaken for a real one.
     */
    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val imported = ModelStore.importFrom(context, uri)
            _modelImportFailed.value = !imported
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
            ModelStore.refreshFit(context)
        }
    }

    /** Removes the model on file, if any. */
    fun clearModel() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            ModelStore.clear(context)
            _modelImportFailed.value = false
            _modelPresent.value = ModelStore.hasModel(context)
            _modelFit.value = ModelStore.fit(context)
            ModelStore.refreshFit(context)
        }
    }

    // --- Letters (the v0.25.2 morning letter) ---
    //
    // The fields here drive both the letter inbox (Task 6) and the
    // future Reading sub-section of the settings screen (Task 10).
    // They default to safe values so the screen renders even before
    // the first refresh completes: modelFits is "no, the model is
    // not on file yet", letterRunning is "no generation in flight",
    // and the size / count / enabled flags come from DataStore
    // flows that emit their persisted value the moment they are
    // collected.

    private val letterStore = LetterStore(application)

    private val readerPrefs = ReaderPrefs(application)

    /**
     * Whether the model on file would actually run on this phone,
     * exposed as a plain [Boolean] for the letter inbox's
     * "Generate now" enablement and the empty-state copy.
     *
     * Backed by the same probe as [modelFit], just rephrased — see
     * [ModelStore.fitFlow] for why the StateFlow is held on the
     * store rather than re-asked on every recomposition.
     */
    val modelFits: StateFlow<Boolean> = ModelStore.fitFlow()

    private val _letterRunning = MutableStateFlow(false)

    /**
     * True while a "Generate now" letter is in flight. Flipped back
     * to false in a [finally], so a generation that throws still
     * leaves the UI re-enabled. Mirrors the
     * [org.mindanchor.report.ReportScheduler] `runReportNow` shape
     * that this view model already uses for the nightly report.
     */
    val letterRunning: StateFlow<Boolean> = _letterRunning.asStateFlow()

    /**
     * The number of letters the user has not yet opened. v0.25.3-WP-C:
     * derived from the real per-letter [LetterStore.readDates] set
     * (replaces the v0.25.2 install-date stand-in). The [combine]
     * flow re-emits whenever either side changes — a new letter is
     * generated, or the user opens a letter — so the Settings
     * "Open inbox (N)" badge decrements the moment a row is tapped.
     * Empty `readDates` means a fresh install with no letters
     * opened, and the count then equals the total letter count.
     */
    val unreadLetterCount: StateFlow<Int> = combine(
        letterStore.letters,
        letterStore.readDates,
    ) { letters, readDates ->
        letters.count { it.date !in readDates }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * The user's chosen letter-reading text size, in sp.
     *
     * The consumer does the [StateFlow] conversion via [stateIn] so
     * the source of truth can stay a plain [kotlinx.coroutines.flow.Flow]
     * (Task 13's `ReaderPrefs` widens the read-side to a `Flow` to
     * cover the `DataStore` and SCALE-list paths). The initial value
     * is [ReadingSize.MEDIUM] — the same default [ReaderPrefs] falls
     * back to when no value has been persisted yet, so a user who
     * opens the screen before [ReaderPrefs] has emitted does not see
     * a value jump. [SharingStarted.Eagerly] matches the pattern
     * used by [unreadLetterCount] above: the upstream is a tiny
     * SharedPreferences read, the value is needed as soon as the
     * settings screen binds, and the cost of holding the latest
     * emission is one `ReadingSize` reference.
     */
    val letterSize: StateFlow<ReadingSize> = readerPrefs.size
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingSize.MEDIUM)

    /**
     * The hour-of-day the user chose to receive the daily letter,
     * exposed as a [StateFlow] so the Reading sub-section can render
     * the current value and survive recomposition without re-reading
     * the DataStore.
     *
     * Backed by [LetterStore.time] — same flow as the
     * [org.mindanchor.letters.LetterScheduler] reads. Initial value is
     * 08:00 (the spec's default — see [LetterStore]); a user who
     * opens the settings screen before the first DataStore emission
     * sees the default rather than a value jump, same posture as
     * [letterSize] above. [SharingStarted.Eagerly] matches the
     * pattern used by [unreadLetterCount] and [letterSize] because
     * the upstream is a small DataStore read and the value is needed
     * as soon as the settings screen binds.
     */
    val lettersTime: StateFlow<Pair<Int, Int>> = letterStore.time
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            LetterStore.DEFAULT_HOUR to LetterStore.DEFAULT_MINUTE,
        )

    /**
     * Whether the user has switched the daily letter on.
     *
     * The toggle on the Reading sub-section binds to this; the
     * [org.mindanchor.letters.LetterScheduler] reads the same
     * [LetterStore.enabled] source. Initial value is `false` — the
     * spec is "off by default; opt-in" — and matches the default
     * [LetterStore] falls back to when no value has been persisted
     * yet, so a user who opens the screen before the first
     * DataStore emission does not see a value jump.
     * [SharingStarted.Eagerly] matches the pattern used by
     * [unreadLetterCount], [letterSize], and [lettersTime] above.
     */
    val lettersEnabled: StateFlow<Boolean> = letterStore.enabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Pass-through to [LetterStore.setEnabled]. */
    fun setLettersEnabled(enabled: Boolean) {
        viewModelScope.launch { letterStore.setEnabled(enabled) }
    }

    /** Pass-through to [LetterStore.setTime]. */
    fun setLettersTime(hour: Int, minute: Int) {
        viewModelScope.launch { letterStore.setTime(hour, minute) }
    }

    /** Pass-through to [ReaderPrefs.setSize]. */
    fun setLetterSize(size: ReadingSize) {
        viewModelScope.launch { readerPrefs.setSize(size) }
    }

    /**
     * v0.25.4: per-type auto-sync toggles for the
     * Google Drive backup. The Settings sub-section
     * binds to these flows; the WP-D scheduler
     * reads the same [BackupPrefs] to decide
     * whether to fire on a new note / letter.
     *
     * The toggles are independent of the sign-in
     * state: a user can sign in with Google but
     * leave both toggles off (no auto-sync), or
     * flip a toggle before signing in (the sign-in
     * prompt fires when the first auto-sync
     * attempt finds no account). The default is
     * `false` on both — the v0.23.0
     * "off by default; opt-in" design that the
     * v0.25.4 plan explicitly extends.
     */
    val autoSyncNotes: StateFlow<Boolean> = backupPrefs.autoSyncNotes
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoSyncLetters: StateFlow<Boolean> = backupPrefs.autoSyncLetters
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoSyncNotes(enabled: Boolean) {
        viewModelScope.launch { backupPrefs.setAutoSyncNotes(enabled) }
    }

    fun setAutoSyncLetters(enabled: Boolean) {
        viewModelScope.launch { backupPrefs.setAutoSyncLetters(enabled) }
    }

    /**
     * Generates a letter on demand, using the same call shape as
     * the daily alarm ([org.mindanchor.letters.LetterScheduler.onFire])
     * but without the notification post and the re-arm.
     *
     * The notification belongs to the alarm — a person who pressed
     * "Generate now" is already looking at the result on the
     * settings screen, and a duplicate notification for the same
     * letter is the kind of small noise that trains people to
     * ignore the channel. The re-arm is also a no-op: the alarm is
     * already held by [org.mindanchor.letters.LetterScheduler] at
     * the user's chosen time, and re-arming it on every manual
     * generation would only move the trigger by the time the
     * function takes to return.
     *
     * Never throws. A missing model, a sparse week, a generation
     * the safety filter rejects — all of those are a quiet
     * "nothing today", same as the daily alarm.
     */
    fun runLetterNow() {
        viewModelScope.launch(Dispatchers.IO) {
            _letterRunning.value = true
            try {
                runCatching {
                    val week = WeekDataCollector(getApplication()).collectLastWeek()
                    val writer = LetterWriter(getApplication())
                    val body = writer.write(week) ?: return@runCatching
                    letterStore.save(Letter(date = LocalDate.now(), body = body))
                }
            } finally {
                _letterRunning.value = false
            }
        }
    }

    // --- Wellness signals (N-of-1, from Health Connect) ---
    //
    // The home card and the settings section both read the same flow,
    // so the two surfaces can never disagree about what is being
    // shown. The flow is null until the first refresh completes —
    // a "still loading" state that is not a "no data" state, so the
    // UI can render a quiet placeholder rather than the more loaded
    // "no data" state when the device is mid-startup.
    //
    // The refresh is launched on every ON_RESUME (via the
    // [permissionEpoch] pattern used elsewhere on this screen) so
    // the home card and the settings panel re-read the moment the
    // launcher comes back to the foreground after a Health Connect
    // permission grant.

    private val _wellnessReadings = MutableStateFlow<List<org.mindanchor.vitals.WellnessReading>?>(null)
    val wellnessReadings: StateFlow<List<org.mindanchor.vitals.WellnessReading>?> = _wellnessReadings.asStateFlow()

    /**
     * The in-flight wellness refresh, if any. Held so a fresh
     * [refreshWellness] call (e.g. when the settings screen returns
     * to the foreground, or a Health Connect permission grant
     * lands) cancels the previous run before starting the new one:
     * the readings pipeline reads and writes the wellness DataStore
     * on every call, and the launcher would otherwise run two
     * `readingsFor` operations in parallel and pick the one that
     * finished last, regardless of which one started last.
     */
    private var wellnessJob: kotlinx.coroutines.Job? = null

    fun refreshWellness() {
        wellnessJob?.cancel()
        wellnessJob = viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val readings = runCatching {
                org.mindanchor.vitals.WellnessRepository(app).readingsFor(LocalDate.now())
            }.getOrDefault(emptyList())
            _wellnessReadings.value = readings
        }
    }

    // --- COROS Training Hub bridge (opt-in side-channel) ---
    //
    // This is the third tier of the launcher's "wearable
    // story": Health Connect is the default and the wellness
    // card reads from it, the camera PPG is for HRV when no
    // watch is present, and the COROS bridge is the
    // opt-in-only escape hatch for the signals the watch does
    // not release to Health Connect. The state and the actions
    // are deliberately separate from the rest of the file —
    // there is no "always on" / "auto-reconnect" affordance,
    // the user has to come here and decide.
    //
    // The flow shape mirrors the rest of this ViewModel: a
    // [StateFlow] of a [CorosConnectionState] for the UI, and
    // suspend functions for the actions. The bridge's worker
    // is the only thing that calls the COROS API at runtime;
    // the UI never blocks on a network call (it sets the
    // state to [CorosConnectionState.AwaitingConsent] and lets
    // the worker complete in the background).

    private val _corosState = MutableStateFlow<CorosConnectionState>(CorosConnectionState.NotConnected)
    val corosState: StateFlow<CorosConnectionState> = _corosState.asStateFlow()

    /** The most recent successful sync, in epoch millis. Null when never synced on this device. */
    val corosLastSyncEpochMs: StateFlow<Long?> =
        CorosVitalSource(getApplication()).lastSyncEpochMs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The latest sync outcome, for the UI's "Last sync: X" line. */
    private val _corosSyncRunning = MutableStateFlow(false)
    val corosSyncRunning: StateFlow<Boolean> = _corosSyncRunning.asStateFlow()

    private val _corosSyncError = MutableStateFlow<String?>(null)
    val corosSyncError: StateFlow<String?> = _corosSyncError.asStateFlow()

    /**
     * Recomputes the COROS bridge state from the encrypted
     * credential store. Called when the user navigates back to
     * the measuring section, when the login completes, and on
     * any disconnect — same [permissionEpoch] pattern as the
     * Health Connect status above.
     */
    fun refreshCorosState() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val auth = CorosAuth(app)
            val lastSync = corosLastSyncEpochMs.value
            _corosState.value = auth.connectionState(lastSync)
        }
    }

    /**
     * Signs in with the supplied credentials, stores them in
     * EncryptedSharedPreferences, and arms the periodic
     * worker. Throws on a login failure (the caller surfaces
     * the message to the user); succeeds silently on a fresh
     * sign-in so the UI can flip to "Connected" without
     * showing a green checkmark that would invite the user
     * to read it as a confirmation.
     */
    @Suppress("detekt.TooGenericExceptionCaught")
    suspend fun connectCoros(
        email: String,
        password: String,
        region: String,
    ) {
        val app = getApplication<Application>()
        val auth = CorosAuth(app)
        _corosSyncError.value = null
        _corosState.value = CorosConnectionState.AwaitingConsent
        try {
            auth.loginWithCredentials(email = email, password = password, region = region)
            CorosSyncWorker.ensureScheduled(app)
            refreshCorosState()
        } catch (e: Exception) {
            // The catch is intentionally broad: the
            // sign-in path can throw a CorosApiException
            // (network failure, bad credentials) or a
            // SecurityException (Keystore-backed crypto
            // unavailable), and the UI surfaces the
            // message in both cases. A narrow catch
            // would force the user to retry on a
            // legitimately unrecoverable error.
            @Suppress("TooGenericExceptionCaught")
            val message = e.message
            _corosState.value = CorosConnectionState.Failed(message ?: "unknown")
            _corosSyncError.value = message
            throw e
        }
    }

    /**
     * Cancels the periodic schedule, wipes the encrypted
     * credential blob, and clears the cached data. The
     * disconnect path is the only way the bridge can leave
     * the device — disconnect = wipe, every time.
     */
    fun disconnectCoros() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            CorosAuth(app).disconnect()
            CorosVitalSource(app).clear()
            CorosSyncWorker.cancel(app)
            _corosState.value = CorosConnectionState.NotConnected
            _corosSyncError.value = null
        }
    }

    /**
     * Kicks an immediate one-shot sync. The [CorosSyncWorker.syncNow]
     * uses REPLACE policy so a second "Sync now" while the
     * first is still running joins the same work, rather than
     * stacking a second network call.
     */
    @Suppress("detekt.TooGenericExceptionCaught")
    fun corosSyncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _corosSyncRunning.value = true
            _corosSyncError.value = null
            try {
                CorosSyncWorker.syncNow(app)
            } catch (e: Exception) {
                // WorkManager.enqueueUniqueWork throws on
                // a mis-configured worker name; a SecurityException
                // surfaces when the app lacks the
                // FOREGROUND_SERVICE permission. The catch
                // is intentionally broad because both
                // lead to the same UI message — "sync
                // failed" with the exception text.
                @Suppress("TooGenericExceptionCaught")
                _corosSyncError.value = e.message
            } finally {
                _corosSyncRunning.value = false
            }
        }
    }
}

/** What an import did, in the terms the settings screen reports it. */
data class CorpusImportReport(
    val added: Int = 0,
    val replaced: Int = 0,
    val skippedRows: Int = 0,
    val truncated: Boolean = false,
    val unreadable: Boolean = false,
)

/**
 * The state of the "Try it now" sunset trial surfaced to the
 * settings UI. [Idle] is the default; [Running] carries the
 * previous interruption filter (so revert knows where to go
 * back to) and the remaining countdown so the user can see
 * the trial ticking down rather than staring at a switch that
 * looks off.
 */
sealed class SunsetTrialState {
    object Idle : SunsetTrialState()
    data class Running(
        val previousFilter: Int,
        val greyscaleOn: Boolean,
        val remainingSeconds: Int,
    ) : SunsetTrialState()
}
