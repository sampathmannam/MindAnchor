package org.mindanchor.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.mindanchor.friction.ExtensionLedger
import org.mindanchor.friction.GateLedger
import org.mindanchor.friction.GateTally
import org.mindanchor.friction.OpenLoop
import org.mindanchor.friction.SmallThings
import org.mindanchor.sleep.BedtimeList
import java.time.LocalDate

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
     */
    val smallThings: Flow<List<String>> =
        context.dataStore.data.map { SmallThings.decode(it[smallThingsKey].orEmpty()) }

    suspend fun addSmallThing(thing: String) {
        context.dataStore.edit {
            it[smallThingsKey] =
                SmallThings.encode(SmallThings.add(SmallThings.decode(it[smallThingsKey].orEmpty()), thing))
        }
    }

    suspend fun removeSmallThing(thing: String) {
        context.dataStore.edit {
            it[smallThingsKey] =
                SmallThings.encode(SmallThings.remove(SmallThings.decode(it[smallThingsKey].orEmpty()), thing))
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
     */
    val bedtimeList: Flow<List<String>> =
        context.dataStore.data.map { BedtimeList.decode(it[bedtimeItemsKey].orEmpty()) }

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
            it[bedtimeItemsKey] = BedtimeList.encode(cleaned)
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
     */
    val gateTallies: Flow<Map<String, GateTally>> =
        context.dataStore.data.map { GateLedger.decode(it[ledgerTallyKey].orEmpty()) }

    private suspend fun editTally(
        packageName: String,
        block: (GateTally) -> GateTally,
    ) {
        context.dataStore.edit { prefs ->
            val all = GateLedger.decode(prefs[ledgerTallyKey].orEmpty()).toMutableMap()
            all[packageName] = block(all[packageName] ?: GateTally())
            prefs[ledgerTallyKey] = GateLedger.encode(all)
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

    suspend fun extensionsToday(packageName: String, today: String): Int =
        ExtensionLedger.count(context.dataStore.data.first()[ledgerKey].orEmpty(), packageName, today)
}
