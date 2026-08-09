package org.mindanchor.data

import android.content.Context
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

    /** The one unfinished thing — see [org.mindanchor.friction.OpenLoop]. */
    val openLoopNote: Flow<String?> = context.dataStore.data.map { it[loopNoteKey] }

    val openLoopDay: Flow<String?> = context.dataStore.data.map { it[loopDayKey] }

    suspend fun setOpenLoop(note: String, today: LocalDate = LocalDate.now()) {
        val cleaned = OpenLoop.clean(note) ?: return
        context.dataStore.edit {
            it[loopNoteKey] = cleaned
            it[loopDayKey] = today.toString()
        }
    }

    /** Clears it. One line, replaced each night — never a task list. */
    suspend fun clearOpenLoop() {
        context.dataStore.edit {
            it.remove(loopNoteKey)
            it.remove(loopDayKey)
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
     */
    val banditState: Flow<FrictionBandit.BanditState> =
        context.dataStore.data.map { decodeBandit(it[banditKey].orEmpty()) }

    suspend fun saveBanditState(state: FrictionBandit.BanditState) {
        context.dataStore.edit { it[banditKey] = encodeBandit(state) }
    }

    private fun encodeBandit(state: FrictionBandit.BanditState): String =
        "${state.full.alpha}\t${state.full.beta}\t${state.brief.alpha}\t${state.brief.beta}"

    private fun decodeBandit(raw: String): FrictionBandit.BanditState {
        if (raw.isBlank()) return FrictionBandit.BanditState()
        val parts = raw.split('\t')
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

    private val perAppSessionLengthKey = stringPreferencesKey("per_app_session_length")

    /**
     * The per-app *time-box* default — see
     * [org.mindanchor.friction.PerAppSessionLength]. A user
     * who picks "Open for 10 minutes" for Instagram can
     * have that choice remembered for the next reach.
     *
     * Stored as `package<TAB>minutes` per line, the
     * same shape as [IfThenPlanStore.encode]. The friction
     * gate reads this flow to highlight the user's last
     * choice in the 5/10/20 button row; the user can
     * still pick any of 5, 10, 20, or "open untimed"
     * with one tap.
     *
     * Evidence: `docs/research/22`. Lally 2010, Adhikari
     * 2023, Gollwitzer 1999, Wood & Neal 2007.
     */
    val perAppSessionLength: Flow<PerAppSessionLength> =
        context.dataStore.data.map {
            PerAppSessionLengthStore.decode(it[perAppSessionLengthKey].orEmpty())
        }

    /**
     * Record a per-app time-box choice. The minutes are
     * clamped to `[1, 120]` by [PerAppSessionLength.record].
     * A blank package name is a no-op.
     */
    suspend fun recordPerAppSessionLength(packageName: String, minutes: Long) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = PerAppSessionLengthStore.decode(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.record(packageName, minutes)
            prefs[perAppSessionLengthKey] = PerAppSessionLengthStore.encode(next)
        }
    }

    /**
     * Forget a per-app time-box choice. A blank or
     * non-existent package name is a no-op.
     */
    suspend fun clearPerAppSessionLength(packageName: String) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = PerAppSessionLengthStore.decode(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.forget(packageName)
            prefs[perAppSessionLengthKey] = PerAppSessionLengthStore.encode(next)
        }
    }

    private val perAppSessionLengthKey = stringPreferencesKey("per_app_session_length")

    /**
     * The per-app *time-box* default — see
     * [org.mindanchor.friction.PerAppSessionLength]. A user
     * who picks "Open for 10 minutes" for Instagram can
     * have that choice remembered for the next reach.
     *
     * Stored as `package<TAB>minutes` per line, the
     * same shape as [IfThenPlanStore.encode]. The friction
     * gate reads this flow to highlight the user's last
     * choice in the 5/10/20 button row; the user can
     * still pick any of 5, 10, 20, or "open untimed"
     * with one tap.
     *
     * Evidence: `docs/research/22`. Lally 2010, Adhikari
     * 2023, Gollwitzer 1999, Wood & Neal 2007.
     */
    val perAppSessionLength: Flow<PerAppSessionLength> =
        context.dataStore.data.map {
            PerAppSessionLengthStore.decode(it[perAppSessionLengthKey].orEmpty())
        }

    /**
     * Record a per-app time-box choice. The minutes are
     * clamped to `[1, 120]` by [PerAppSessionLength.record].
     * A blank package name is a no-op.
     */
    suspend fun recordPerAppSessionLength(packageName: String, minutes: Long) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = PerAppSessionLengthStore.decode(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.record(packageName, minutes)
            prefs[perAppSessionLengthKey] = PerAppSessionLengthStore.encode(next)
        }
    }

    /**
     * Forget a per-app time-box choice. A blank or
     * non-existent package name is a no-op.
     */
    suspend fun clearPerAppSessionLength(packageName: String) {
        if (packageName.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = PerAppSessionLengthStore.decode(prefs[perAppSessionLengthKey].orEmpty())
            val next = current.forget(packageName)
            prefs[perAppSessionLengthKey] = PerAppSessionLengthStore.encode(next)
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
