package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.friction.CompassionMoment
import org.mindanchor.friction.CompassionStore
import org.mindanchor.friction.ExtensionLedger
import org.mindanchor.friction.FrictionBandit
import org.mindanchor.friction.GateLedger
import org.mindanchor.friction.GateTally
import org.mindanchor.friction.GoingLightSchedule
import org.mindanchor.friction.IfThenPlan
import org.mindanchor.friction.IfThenPlanStore
import org.mindanchor.friction.OpenLoop
import org.mindanchor.friction.PerAppSessionLength
import org.mindanchor.friction.SealedCodecs
import org.mindanchor.friction.SmallThings
import org.mindanchor.sleep.BedtimeList
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "friction")

/**
 * Friction configuration: which apps get the breathing gate, and the
 * per-day session-extension ledger that keeps "+5 minutes" honest.
 */
class FrictionPrefs(private val context: Context) {

    private val flaggedKey = stringSetPreferencesKey("flagged_packages")
    private val ledgerKey = stringPreferencesKey("extension_ledger")

    // v0.26+ (Phase 1 G-22): the behavioural-activation weekly
    // prompt. The user opts in; default is OFF (the project's
    // opt-out-by-silence rule). When on, the Friday-evening
    // PreHome surface offers "pick one mastery + one pleasure".
    // Dimidjian 2006 (BA RCT, N=241) is the evidence anchor.
    private val baPromptEnabledKey = booleanPreferencesKey("ba_prompt_enabled")
    val baPromptEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[baPromptEnabledKey] ?: false
    }
    suspend fun setBaPromptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[baPromptEnabledKey] = enabled }
    }

    // v0.26+ (Phase 1 G-21): the morning self-compassion
    // break. The user opts in; default is OFF. Neff 2003
    // and Linardon 2020 (27 RCTs of smartphone-based
    // self-compassion apps) are the evidence anchors.
    private val morningCompassionEnabledKey =
        booleanPreferencesKey("morning_compassion_enabled")
    val morningCompassionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[morningCompassionEnabledKey] ?: false
    }
    suspend fun setMorningCompassionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[morningCompassionEnabledKey] = enabled }
    }

    // v0.26+ (Phase 1 G-19): the compassionate wrap on
    // app-close after a long session. The user opts in;
    // default is OFF. The wrap fires a Snackbar that asks
    // "You were on %1$s for %2$s — note anything?" — the
    // ask is a 1-tap offer, never a judgment.
    private val compassionateWrapEnabledKey =
        booleanPreferencesKey("compassionate_wrap_enabled")
    val compassionateWrapEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[compassionateWrapEnabledKey] ?: false
    }
    suspend fun setCompassionateWrapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[compassionateWrapEnabledKey] = enabled }
    }

    // v0.26+ (Phase 1 G-1) — the Going Light consent
    // card dismissal. Persisted so the card does not
    // re-appear on every home-surface open after the
    // user dismisses it once. The OS-level VpnService
    // consent is re-checked on every home-surface open
    // — the dismissal is a UI affordance, not a
    // permission grant.
    private val goingLightConsentDismissedKey =
        booleanPreferencesKey("going_light_consent_dismissed")
    val goingLightConsentDismissed: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[goingLightConsentDismissedKey] ?: false
    }
    suspend fun dismissGoingLightConsent() {
        context.dataStore.edit { it[goingLightConsentDismissedKey] = true }
    }

    // v0.28+ (Phase 3 G-8) — the expressive-writing
    // prompt. The user opts in; default is OFF.
    // Pennebaker 1997 (minimum-dosage 3-sentence
    // entry point) is the evidence anchor. The
    // home surface shows the card on low-mood
    // check-in days (the user explicitly asks for
    // it, the launcher does not schedule it).
    private val expressiveWritingEnabledKey =
        booleanPreferencesKey("expressive_writing_enabled")
    val expressiveWritingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[expressiveWritingEnabledKey] ?: false
    }
    suspend fun setExpressiveWritingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[expressiveWritingEnabledKey] = enabled }
    }

    // v0.28+ (Phase 3 G-26) — the wind-down card.
    // The user opts in; default is OFF. Shown on
    // the home surface after the configured
    // wind-down time (default 21:00, overridable
    // in Settings). The launcher applies the
    // changes when the user taps Begin.
    private val windDownEnabledKey =
        booleanPreferencesKey("wind_down_enabled")
    val windDownEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[windDownEnabledKey] ?: false
    }
    suspend fun setWindDownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[windDownEnabledKey] = enabled }
    }

    // v0.28+ (Phase 3 G-29) — the gratitude
    // card. The user opts in; default is OFF.
    // Seligman 2005 (active-constructive response
    // RCT) is the evidence anchor. The card
    // writes to the Letters store (same pipeline
    // as the DEAR MAN script).
    private val gratitudeEnabledKey =
        booleanPreferencesKey("gratitude_enabled")
    val gratitudeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[gratitudeEnabledKey] ?: false
    }
    suspend fun setGratitudeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[gratitudeEnabledKey] = enabled }
    }

    // v0.29+ (Phase 4 G-6) — the push-up mode.
    // The user opts in; default is OFF. When on,
    // opening a flagged app shows the push-up
    // counter and the user must complete N reps
    // before the launcher lets the app open.
    // Hauck 2020 (Sports Medicine, intense-exercise
    // craving-reduction 30-50 min) is the evidence
    // anchor.
    private val pushUpModeEnabledKey =
        booleanPreferencesKey("push_up_mode_enabled")
    val pushUpModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[pushUpModeEnabledKey] ?: false
    }
    suspend fun setPushUpModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[pushUpModeEnabledKey] = enabled }
    }

    // v0.29+ (Phase 4 G-28) — the voice journal.
    // The user opts in; default is OFF. Records
    // audio on-device; whisper.cpp transcribes
    // on-device. ~75 MB APK cost acknowledged in
    // the Composable KDoc.
    private val voiceJournalEnabledKey =
        booleanPreferencesKey("voice_journal_enabled")
    val voiceJournalEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[voiceJournalEnabledKey] ?: false
    }
    suspend fun setVoiceJournalEnabled(enabled: Boolean) {
        context.dataStore.edit { it[voiceJournalEnabledKey] = enabled }
    }

    // v0.30+ (spec Phase 1) — the PreHome
    // moment-of-pause activity. The user opts in;
    // default is OFF. The opt-in is per the
    // project's opt-out-by-silence rule: a
    // launcher that changes the cold-start
    // experience is a launcher fighting the user
    // when not asked. When the flag is OFF, the
    // system HOME intent still points to the
    // PreHomeActivity (the manifest wiring) but
    // the activity self-skips to HomeActivity on
    // first composition; the toggle gates the
    // moment-of-pause surface itself.
    private val prehomeEnabledKey =
        booleanPreferencesKey("prehome_enabled")
    val prehomeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[prehomeEnabledKey] ?: false
    }
    suspend fun setPrehomeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[prehomeEnabledKey] = enabled }
    }

    val flaggedApps: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[flaggedKey] ?: emptySet()
    }

    suspend fun setFlagged(packageName: String, flagged: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[flaggedKey] ?: emptySet()
            prefs[flaggedKey] = if (flagged) current + packageName else current - packageName
        }
    }

    /**
     * Apps that must never be suspended or delayed, whatever else is
     * switched on.
     *
     * This exists because "distracting" and "must reach me at 3am" are not
     * opposites. Someone on call for work marks a messaging app as
     * distracting for good reason, and then enforced quiet hours would
     * close the one channel their job runs through. The person knows which
     * apps those are; nothing here tries to guess.
     */
    private val alwaysOpenKey = stringSetPreferencesKey("always_open")

    val alwaysOpen: Flow<Set<String>> =
        context.dataStore.data.map { it[alwaysOpenKey] ?: emptySet() }

    suspend fun setAlwaysOpen(packageName: String, always: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[alwaysOpenKey] ?: emptySet()
            prefs[alwaysOpenKey] = if (always) current + packageName else current - packageName
        }
    }

    private val reachKey = stringPreferencesKey("recent_reaches")

    /**
     * Records a reach for [packageName] at [now], and returns how many
     * times it had *already* been reached for inside [windowMillis].
     *
     * This is the whole memory behind context-gated friction, and it is
     * deliberately tiny: one timestamp per app, nothing historical, nothing
     * about what was done inside the app. Entries older than the window are
     * dropped on every write, so the file cannot grow into a usage log by
     * accident.
     */
    suspend fun recordReach(packageName: String, now: Long, windowMillis: Long): Int {
        var priorReaches = 0
        context.dataStore.edit { prefs ->
            val entries = (prefs[reachKey] ?: "")
                .lineSequence()
                .mapNotNull { line ->
                    val idx = line.lastIndexOf('\t')
                    val stamp = if (idx <= 0) null else line.substring(idx + 1).toLongOrNull()
                    if (stamp == null) null else line.substring(0, idx) to stamp
                }
                .filter { now - it.second < windowMillis }
                .toList()
            priorReaches = entries.count { it.first == packageName }
            prefs[reachKey] = (entries + (packageName to now))
                .joinToString("\n") { "${it.first}\t${it.second}" }
        }
        return priorReaches
    }

    private val smallThingsKey = stringPreferencesKey("small_things")

    /**
     * The small things the person said help them — see
     * [org.mindanchor.friction.SmallThings]. Their words only; nothing
     * here is ever seeded with suggestions.
     *
     * Persisted through [SealedCodecs.encodeSmallThings] /
     * [SealedCodecs.decodeSmallThings] so the data carries
     * an HMAC-SHA256 tag (Keystore-backed key). A v0.20.0
     * plaintext form on disk is rejected on read; the
     * first write seals the data.
     */
    val smallThings: Flow<List<String>> =
        context.dataStore.data.map { SealedCodecs.decodeSmallThings(it[smallThingsKey].orEmpty()) }

    suspend fun addSmallThing(thing: String) {
        context.dataStore.edit {
            val current = SealedCodecs.decodeSmallThings(it[smallThingsKey].orEmpty())
            it[smallThingsKey] = SealedCodecs.encodeSmallThings(SmallThings.add(current, thing))
        }
    }

    suspend fun removeSmallThing(thing: String) {
        context.dataStore.edit {
            val current = SealedCodecs.decodeSmallThings(it[smallThingsKey].orEmpty())
            it[smallThingsKey] = SealedCodecs.encodeSmallThings(SmallThings.remove(current, thing))
        }
    }

    private val loopNoteKey = stringPreferencesKey("open_loop_note")
    private val loopDayKey = stringPreferencesKey("open_loop_day")
    private val loopPostponedAtKey = stringPreferencesKey("open_loop_postponed_at")

    /** The one unfinished thing — see [org.mindanchor.friction.OpenLoop]. */
    val openLoopNote: Flow<String?> = context.dataStore.data.map { it[loopNoteKey] }

    val openLoopDay: Flow<String?> = context.dataStore.data.map { it[loopDayKey] }

    /**
     * The user-chosen revisit time for the open loop, if any. Stored as
     * the [Instant.toString] form (ISO-8601 UTC, e.g. `2026-03-10T15:00:00Z`)
     * so the round-trip survives DST shifts and timezone changes.
     */
    val openLoopPostponedAt: Flow<Instant?> =
        context.dataStore.data.map { prefs ->
            prefs[loopPostponedAtKey]?.let { stored ->
                runCatching { Instant.parse(stored) }.getOrNull()
            }
        }

    suspend fun setOpenLoop(
        note: String,
        today: LocalDate = LocalDate.now(),
        postponedAt: Instant? = null,
    ) {
        val cleaned = OpenLoop.clean(note) ?: return
        context.dataStore.edit {
            it[loopNoteKey] = cleaned
            it[loopDayKey] = today.toString()
            if (postponedAt != null) it[loopPostponedAtKey] = postponedAt.toString()
            else it.remove(loopPostponedAtKey)
        }
    }

    /**
     * Updates just the postponed-at field without re-writing the note or
     * the day. Used when the user changes their mind about when to
     * revisit a worry ("Later today" → "Tomorrow morning") without
     * changing what the worry is.
     */
    suspend fun setOpenLoopPostponedAt(at: Instant?) {
        context.dataStore.edit { prefs ->
            if (at == null) prefs.remove(loopPostponedAtKey)
            else prefs[loopPostponedAtKey] = at.toString()
        }
    }

    /** Clears it. One line, replaced each night — never a task list. */
    suspend fun clearOpenLoop() {
        context.dataStore.edit {
            it.remove(loopNoteKey)
            it.remove(loopDayKey)
            it.remove(loopPostponedAtKey)
        }
    }

    private val bedtimeItemsKey = stringPreferencesKey("bedtime_list_items")
    private val bedtimeDayKey = stringPreferencesKey("bedtime_list_day")

    /**
     * The bedtime to-do list — see [org.mindanchor.sleep.BedtimeList].
     *
     * Like [openLoopNote] above, this is *one-night* data. Stored as a
     * newline-separated string for parity with the existing
     * [SmallThings] / [OpenLoop] pattern (one DataStore key per
     * concept, no JSON, no migration). Decoded with
     * [BedtimeList.decode] on the way in; the cap is on the *output*
     * so a corrupted or hand-edited file cannot produce an
     * overflowing list.
     *
     * The brief is explicit: **not a task list, must never grow into
     * one** (Scullin 2018 — see docs/research/15 §2). A list from a
     * previous night is handed back in the morning and then cleared
     * the next time the prompt fires.
     *
     * Persisted through [SealedCodecs.encodeBedtimeList] /
     * [SealedCodecs.decodeBedtimeList] (HMAC-SHA256 tag).
     */
    val bedtimeList: Flow<List<String>> =
        context.dataStore.data.map { SealedCodecs.decodeBedtimeList(it[bedtimeItemsKey].orEmpty()) }

    val bedtimeListDay: Flow<String?> =
        context.dataStore.data.map { it[bedtimeDayKey] }

    suspend fun setBedtimeList(
        items: List<String>,
        today: LocalDate = LocalDate.now(),
    ) {
        // One bedtime list per night. Empty input clears the store
        // outright so a stale entry from three days ago never gets
        // handed back as if it were today's.
        val cleaned = items.mapNotNull { BedtimeList.cleanLine(it) }
        if (cleaned.isEmpty()) {
            clearBedtimeList()
            return
        }
        context.dataStore.edit {
            it[bedtimeItemsKey] = SealedCodecs.encodeBedtimeList(cleaned)
            it[bedtimeDayKey] = today.toString()
        }
    }

    suspend fun clearBedtimeList() {
        context.dataStore.edit {
            it.remove(bedtimeItemsKey)
            it.remove(bedtimeDayKey)
        }
    }

    private val ledgerTallyKey = stringPreferencesKey("gate_tallies")

    /**
     * How each app's pause has actually been going — see [GateLedger].
     *
     * Two integers and a date per app. Nothing about when, nothing about
     * what was done inside the app, nothing that could reconstruct a day.
     *
     * Persisted through [SealedCodecs.encodeGateTallies] /
     * [SealedCodecs.decodeGateTallies] (HMAC-SHA256 tag).
     * CodeRabbit audit #20 (2026-08-08): the v0.20.1
     * round 1 documentation claimed GateLedger was
     * wrapped, but the production path still used the
     * raw plaintext codec. v0.20.1 round 2 wires the
     * gate-tally codec and uses it for every
     * read/write of the gate-tally data.
     */
    val gateTallies: Flow<Map<String, GateTally>> =
        context.dataStore.data.map { SealedCodecs.decodeGateTallies(it[ledgerTallyKey].orEmpty()) }

    private suspend fun editTally(
        packageName: String,
        block: (GateTally) -> GateTally,
    ) {
        context.dataStore.edit { prefs ->
            val all = SealedCodecs.decodeGateTallies(prefs[ledgerTallyKey].orEmpty()).toMutableMap()
            all[packageName] = block(all[packageName] ?: GateTally())
            prefs[ledgerTallyKey] = SealedCodecs.encodeGateTallies(all)
        }
    }

    /** The gate appeared for [packageName]. */
    suspend fun recordGateShown(packageName: String, today: LocalDate = LocalDate.now()) {
        editTally(packageName) { GateLedger.recordShown(it, today) }
    }

    /** The gate appeared and the person chose not to go through. */
    suspend fun recordGateAbandoned(packageName: String, today: LocalDate = LocalDate.now()) {
        editTally(packageName) { GateLedger.recordAbandoned(it, today) }
    }

    /** Starts [packageName]'s count again, after somebody keeps the pause. */
    suspend fun resetTally(packageName: String, today: LocalDate = LocalDate.now()) {
        editTally(packageName) { GateLedger.reset(today) }
    }

    /** Increments and returns today's extension count for [packageName]. */
    suspend fun recordExtension(packageName: String, today: String): Int {
        var count = 0
        context.dataStore.edit { prefs ->
            val updated = ExtensionLedger.increment(prefs[ledgerKey].orEmpty(), packageName, today)
            prefs[ledgerKey] = updated
            count = ExtensionLedger.count(updated, packageName, today)
        }
        return count
    }

    private val banditKey = stringPreferencesKey("friction_bandit_state")

    /**
     * The v1.2 adaptive-friction policy state — see
     * [org.mindanchor.friction.FrictionBandit]. Persisted as a
     * tab-separated pair of `(alpha, beta)` per arm so the data
     * store stays text-only, in keeping with the
     * [GateLedger.encode] / [OpenLoop.encode] pattern.
     *
     * Sealed with the [SealedCodecs.frictionBandit] HMAC-SHA256
     * layer (v0.26+, Phase 1 G-2). The bandit state is the
     * launcher deciding *how hard to push*; a motivated user
     * with root could otherwise rewrite the on-disk posteriors
     * to pin the bandit to whichever arm biases the gate toward
     * their preferred friction level, defeating the §5
     * "intervention expiry" reset (which only resets one arm
     * at a time, and only when called by the nightly deviation
     * trigger). Sealing makes the on-disk form tamper-evident:
     * a forged state fails the MAC and the bandit resets to
     * the prior. The next legit `observe` produces a sealed
     * record and the bandit learns from there.
     */
    val banditState: Flow<FrictionBandit.BanditState> =
        context.dataStore.data.map { decodeBandit(it[banditKey].orEmpty()) }

    suspend fun saveBanditState(state: FrictionBandit.BanditState) {
        context.dataStore.edit { it[banditKey] = encodeBandit(state) }
    }

    /**
     * The §5 "intervention expiry" reset — see
     * [FrictionBandit.resetDominant]. The reset is conservative
     * (only the dominant arm is reset, the other arm's
     * history is preserved) and is fired by the nightly
     * deviation trigger when the dominant arm has not been
     * doing its job for the configured threshold. Idempotent:
     * calling it twice in a row is safe.
     */
    suspend fun resetBanditDominant() {
        context.dataStore.edit { prefs ->
            val current = decodeBandit(prefs[banditKey].orEmpty())
            val reset = FrictionBandit.resetDominant(current)
            prefs[banditKey] = encodeBandit(reset)
        }
    }

    private fun encodeBandit(state: FrictionBandit.BanditState): String =
        SealedCodecs.encodeBandit(
            listOf(
                state.full.alpha.toString(),
                state.full.beta.toString(),
                state.brief.alpha.toString(),
                state.brief.beta.toString(),
            ).joinToString("\t"),
        )

    private fun decodeBandit(raw: String): FrictionBandit.BanditState {
        if (raw.isBlank()) return FrictionBandit.BanditState()
        val sealed = runCatching { SealedCodecs.decodeBandit(raw) }.getOrNull()
            ?: return FrictionBandit.BanditState()
        val parts = sealed.split('\t')
        if (parts.size < 4) return FrictionBandit.BanditState()
        val (fa, fb, ba, bb) = parts
        val fullAlpha = fa.toDoubleOrNull() ?: return FrictionBandit.BanditState()
        val fullBeta = fb.toDoubleOrNull() ?: return FrictionBandit.BanditState()
        val briefAlpha = ba.toDoubleOrNull() ?: return FrictionBandit.BanditState()
        val briefBeta = bb.toDoubleOrNull() ?: return FrictionBandit.BanditState()
        return FrictionBandit.BanditState(
            full = FrictionBandit.Arm(alpha = fullAlpha, beta = fullBeta),
            brief = FrictionBandit.Arm(alpha = briefAlpha, beta = briefBeta),
        )
    }

    private val ifThenPlansKey = stringPreferencesKey("if_then_plans")

    /**
     * Per-app Gollwitzer if-then plans — see
     * [org.mindanchor.friction.IfThenPlan]. Stored as a
     * tab-separated `package<TAB>cue<TAB>action<TAB>minutes`
     * per line, following the same text-only pattern as
     * [GateLedger.encode] / [IfThenPlanStore.encode].
     *
     * A plan is per-app, optional, and may be incomplete
     * (cue filled, action empty, or vice versa). The friction
     * gate pre-fills the intention prompt with a complete
     * plan and falls back to the generic prompt when no plan
     * is on file.
     *
     * Persisted through [SealedCodecs.encodeIfThenPlans] /
     * [SealedCodecs.decodeIfThenPlans] (HMAC-SHA256 tag).
     */
    val ifThenPlans: Flow<Map<String, IfThenPlan>> =
        context.dataStore.data.map { SealedCodecs.decodeIfThenPlans(it[ifThenPlansKey].orEmpty()) }

    suspend fun setIfThenPlan(packageName: String, plan: IfThenPlan) {
        context.dataStore.edit { prefs ->
            val all = SealedCodecs.decodeIfThenPlans(prefs[ifThenPlansKey].orEmpty()).toMutableMap()
            if (plan.cue.isBlank() && plan.action.isBlank() && plan.defaultMinutes == null) {
                all.remove(packageName)
            } else {
                all[packageName] = plan
            }
            prefs[ifThenPlansKey] = SealedCodecs.encodeIfThenPlans(all)
        }
    }

    suspend fun clearIfThenPlan(packageName: String) {
        context.dataStore.edit { prefs ->
            val all = SealedCodecs.decodeIfThenPlans(prefs[ifThenPlansKey].orEmpty()).toMutableMap()
            all.remove(packageName)
            prefs[ifThenPlansKey] = SealedCodecs.encodeIfThenPlans(all)
        }
    }

    private val perAppSessionLengthKey = stringPreferencesKey("per_app_session_length")

    /**
     * The per-app *time-box* default — see
     * [org.mindanchor.friction.PerAppSessionLength]. A user
     * who picks "Open for 10 minutes" for Instagram can
     * have that choice remembered for the next reach.
     *
     * Persisted through
     * [SealedCodecs.encodePerAppSessionLength] /
     * [SealedCodecs.decodePerAppSessionLength]
     * (HMAC-SHA256 tag).
     *
     * Evidence: `docs/research/22`. Lally 2010,
     * Adhikari 2023, Gollwitzer 1999, Wood & Neal 2007.
     */
    val perAppSessionLength: Flow<PerAppSessionLength> =
        context.dataStore.data.map {
            SealedCodecs.decodePerAppSessionLength(it[perAppSessionLengthKey].orEmpty())
        }

    /**
     * Record a per-app time-box choice. The minutes are
     * clamped to `[1, 120]` by [PerAppSessionLength.record].
     * A blank package name is a no-op.
     */
    suspend fun recordPerAppSessionLength(packageName: String, minutes: Long) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = SealedCodecs.decodePerAppSessionLength(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.record(packageName, minutes)
            prefs[perAppSessionLengthKey] = SealedCodecs.encodePerAppSessionLength(next)
        }
    }

    /**
     * Forget a per-app time-box choice. A blank or
     * non-existent package name is a no-op.
     */
    suspend fun clearPerAppSessionLength(packageName: String) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = SealedCodecs.decodePerAppSessionLength(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.forget(packageName)
            prefs[perAppSessionLengthKey] = SealedCodecs.encodePerAppSessionLength(next)
        }
    }

    private val compassionKey = stringPreferencesKey("compassion_moments")

    /**
     * The user's own set of self-compassion phrases — see
     * [org.mindanchor.friction.CompassionMoment]. Stored as
     * one phrase per line, following the [SmallThings.encode]
     * / [OpenLoop.encode] pattern.
     *
     * Persisted through [SealedCodecs.encodeCompassion] /
     * [SealedCodecs.decodeCompassion] (HMAC-SHA256 tag).
     */
    val compassionMoments: Flow<List<CompassionMoment>> =
        context.dataStore.data.map { SealedCodecs.decodeCompassion(it[compassionKey].orEmpty()) }

    suspend fun setCompassionMoments(moments: List<CompassionMoment>) {
        context.dataStore.edit { it[compassionKey] = SealedCodecs.encodeCompassion(moments) }
    }

    /**
     * Add a new self-compassion phrase the user has just
     * typed. Pure list op through
     * [org.mindanchor.friction.CompassionList.add] so the
     * storage layer never has to know about MAX / dedup /
     * trim rules.
     */
    suspend fun addCompassionMoment(phrase: String) {
        context.dataStore.edit {
            val current = SealedCodecs.decodeCompassion(it[compassionKey].orEmpty())
            val updated = org.mindanchor.friction.CompassionList.add(current, phrase)
            if (updated !== current) {
                it[compassionKey] = SealedCodecs.encodeCompassion(updated)
            }
        }
    }

    /**
     * Drop the first trim-equal match for [phrase]. No-op
     * when the phrase is not in the list.
     */
    suspend fun removeCompassionMoment(phrase: String) {
        context.dataStore.edit {
            val current = SealedCodecs.decodeCompassion(it[compassionKey].orEmpty())
            val updated = org.mindanchor.friction.CompassionList.remove(current, phrase)
            if (updated !== current) {
                it[compassionKey] = SealedCodecs.encodeCompassion(updated)
            }
        }
    }

    private val goingLightKey = stringPreferencesKey("going_light_schedule")

    /**
     * The "Going Light" v1.1 schedule — see
     * [org.mindanchor.friction.GoingLightSchedule]. The
     * actual blocking mechanism (VpnService or
     * AccessibilityService) is a separate commit that
     * reads this flow; the data layer is the part that
     * ships now.
     */
    val goingLightSchedule: Flow<GoingLightSchedule> =
        context.dataStore.data.map { decodeGoingLight(it[goingLightKey].orEmpty()) }

    suspend fun setGoingLightSchedule(schedule: GoingLightSchedule) {
        context.dataStore.edit { it[goingLightKey] = encodeGoingLight(schedule) }
    }

    /**
     * Encodes the schedule as 8 tab-separated fields: enabled
     * (0/1), then 7 day-of-week booleans (Mon..Sun), then
     * start-minute-of-day, then end-minute-of-day. The
     * day-of-week set is encoded as 7 booleans for
     * human-readability on inspection; everything else is
     * integer-valued for cheap equality checks.
     */
    private fun encodeGoingLight(s: GoingLightSchedule): String {
        val dayBooleans = (1..7).joinToString("\t") { dow ->
            if (DayOfWeek.of(dow) in s.activeDays) "1" else "0"
        }
        return listOf(
            if (s.enabled) "1" else "0",
            dayBooleans,
            s.startTime.hour * 60 + s.startTime.minute,
            s.endTime.hour * 60 + s.endTime.minute,
        ).joinToString("\t")
    }

    private fun decodeGoingLight(raw: String): GoingLightSchedule {
        if (raw.isBlank()) return GoingLightSchedule()
        val parts = raw.split('\t')
        if (parts.size < 10) return GoingLightSchedule()
        val enabled = parts[0] == "1"
        val days = (1..7).mapNotNull { dow ->
            if (parts[dow] == "1") DayOfWeek.of(dow) else null
        }.toSet()
        val startMin = parts[8].toIntOrNull() ?: return GoingLightSchedule()
        val endMin = parts[9].toIntOrNull() ?: return GoingLightSchedule()
        return GoingLightSchedule(
            enabled = enabled,
            activeDays = days,
            startTime = LocalTime.of(startMin / 60, startMin % 60),
            endTime = LocalTime.of(endMin / 60, endMin % 60),
        )
    }

    suspend fun extensionsToday(packageName: String, today: String): Int =
        ExtensionLedger.count(context.dataStore.data.first()[ledgerKey].orEmpty(), packageName, today)
}
