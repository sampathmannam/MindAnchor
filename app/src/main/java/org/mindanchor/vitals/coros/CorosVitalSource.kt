package org.mindanchor.vitals.coros

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.corosDataStore by preferencesDataStore(name = "coros_bridge_data")

/**
 * The local cache of COROS data the worker has fetched and the
 * wellness card reads from.
 *
 * ## What lives here
 *
 *  - Last 7 days of nightly HRV (RMSSD ms), serialised as a
 *    JSON array of `CorosHrv` records.
 *  - Last ~28 days of daily summaries (RHR, training load,
 *    VO2max), serialised as a JSON array of `CorosDaily`
 *    records.
 *  - Last page of activities (30 entries max), serialised as
 *    a JSON array of `CorosActivity` records.
 *  - The last successful sync timestamp, in epoch millis.
 *
 * The on-disk form is the same JSON shape the COROS API
 * returns, so re-writing it on every sync is a single
 * `prefs.edit { putString(...) }` call. The merge with Health
 * Connect data happens at *read* time, in [mergeWith], not
 * here — this class is the cache, the wellness card is the
 * merge point.
 */
class CorosVitalSource(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val hrvKey = stringPreferencesKey("hrv_json")
    private val dailyKey = stringPreferencesKey("daily_json")
    private val activitiesKey = stringPreferencesKey("activities_json")
    private val lastSyncKey = longPreferencesKey("last_sync_ms")

    /**
     * The most recent successful sync, in epoch millis, or
     * null when the bridge has never synced on this device.
     */
    val lastSyncEpochMs: Flow<Long?> = safeData().map { it[lastSyncKey] }

    /** The cached nightly HRV, oldest first. Empty when the bridge has never synced. */
    val hrv: Flow<List<CorosHrv>> = safeData().map { prefs ->
        decode(prefs[hrvKey], ListSerializer(CorosHrv.serializer()))
    }

    /** The cached daily summaries (RHR, training load, VO2max), oldest first. */
    val daily: Flow<List<CorosDaily>> = safeData().map { prefs ->
        decode(prefs[dailyKey], ListSerializer(CorosDaily.serializer()))
    }

    /** The cached activity page (max 30 entries). */
    val activities: Flow<List<CorosActivity>> = safeData().map { prefs ->
        decode(prefs[activitiesKey], ListSerializer(CorosActivity.serializer()))
    }

    /**
     * Replace the entire cache with the latest sync. Called
     * from [CorosSyncWorker.doWork] after the API calls
     * return. The [lastSyncEpochMs] is stamped even when
     * every list is empty — the worker has run, that is the
     * truth, and an empty list with a fresh timestamp is
     * more honest than a stale timestamp that says
     * "yesterday" when today is when the bridge was
     * re-connected.
     */
    suspend fun write(
        hrv: List<CorosHrv>,
        daily: List<CorosDaily>,
        activities: List<CorosActivity>,
    ) {
        val hrvJson = json.encodeToString(ListSerializer(CorosHrv.serializer()), hrv)
        val dailyJson = json.encodeToString(ListSerializer(CorosDaily.serializer()), daily)
        val activitiesJson = json.encodeToString(ListSerializer(CorosActivity.serializer()), activities)
        context.corosDataStore.edit { prefs ->
            prefs[hrvKey] = hrvJson
            prefs[dailyKey] = dailyJson
            prefs[activitiesKey] = activitiesJson
            prefs[lastSyncKey] = System.currentTimeMillis()
        }
    }

    /**
     * Wipes the cache. Called by the user-initiated
     * disconnect. The in-memory credential wipe is in
     * [CorosAuth.disconnect]; the two are deliberately
     * separate so a future "disconnect cache but keep
     * credentials" affordance has somewhere to live.
     */
    suspend fun clear() {
        context.corosDataStore.edit { it.clear() }
    }

    /**
     * Merge the COROS cache into a Health-Connect-derived
     * view, with the conflict rule **HC wins for what it
     * has, COROS fills the gaps**:
     *
     *  - When HC has an HRV reading for a date, that reading
     *    is kept (a PPG-measured HRV is more accurate than a
     *    wrist optical one — see
     *    [org.mindanchor.vitals.Sourcing.pick]).
     *  - When HC has no HRV, the COROS nightly HRV is used
     *    for that date.
     *  - The RHR rule is the same: HC wins if it has it,
     *    COROS's `/analyse/query` is the fallback.
     *
     * The merge is per-day; the function returns a list of
     * [MergedDay] for the dates that have data from at
     * least one source. The wellness card reads
     * [MergedDay.hrvRmssd] / [MergedDay.rhr] rather than
     * the raw [CorosDaily] / [CorosHrv], so the merge logic
     * does not leak into the UI.
     *
     * @param hcByDate the per-day Health Connect values
     *   keyed by ISO date, or null when HC is not available
     *   on this device.
     */
    suspend fun mergeWith(
        hcByDate: Map<String, HcDayVitals>?,
    ): List<MergedDay> {
        // The two flows are cheap — DataStore reads on the
        // same file — and merging them here keeps the cache
        // reads local to the merge.
        val hrvMap: Map<String, CorosHrv> = hrv.first().associateBy { it.date }
        val dailyMap: Map<String, CorosDaily> = daily.first().associateBy { it.date }
        val dates = (hrvMap.keys + dailyMap.keys + (hcByDate?.keys ?: emptySet()))
            .toSortedSet()
        return dates.map { date ->
            val corosHrv = hrvMap[date]
            val corosDaily = dailyMap[date]
            val hc = hcByDate?.get(date)
            MergedDay(
                date = date,
                hrvRmssd = hc?.hrvRmssd ?: corosHrv?.rmssd,
                hrvSource = when {
                    hc?.hrvRmssd != null -> Source.HEALTH_CONNECT
                    corosHrv?.rmssd != null -> Source.COROS
                    else -> Source.NONE
                },
                rhr = hc?.restingHeartRate ?: corosDaily?.rhr,
                rhrSource = when {
                    hc?.restingHeartRate != null -> Source.HEALTH_CONNECT
                    corosDaily?.rhr != null -> Source.COROS
                    else -> Source.NONE
                },
            )
        }
    }

    /**
     * One day of merged wearable data, with provenance on
     * each field. The wellness card can surface the
     * provenance in its detail view; the home card hides it.
     */
    data class MergedDay(
        val date: String,
        val hrvRmssd: Double?,
        val hrvSource: Source,
        val rhr: Double?,
        val rhrSource: Source,
    )

    /** Where a single value came from. NONE means "neither side has it today". */
    enum class Source { HEALTH_CONNECT, COROS, NONE }

    /**
     * The per-day Health-Connect read the merge step needs.
     * Mirrors the two fields the COROS side can fill so the
     * merge stays a small focused struct rather than
     * dragging in the full [org.mindanchor.vitals.DailyVitals]
     * shape.
     */
    data class HcDayVitals(
        val date: String,
        val hrvRmssd: Double?,
        val restingHeartRate: Double?,
    )

    /**
     * Decode a JSON array blob. Returns an empty list on any
     * failure: the cache is best-effort, a corrupted write
     * (e.g. a process kill between the data-store edit and
     * the OS flushing the file) is not grounds to take down
     * the wellness card.
     */
    private fun <T> decode(
        raw: String?,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
    ): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }

    /**
     * A read failure on the DataStore file (corrupt blob,
     * filesystem permission, an interrupted write) is the
     * same "no cache yet" state as a first run — empty
     * preferences, every key absent. The wellness card
     * surfaces that as a stale timestamp and a quiet
     * placeholder rather than crashing the compose tree.
     */
    private fun safeData(): Flow<androidx.datastore.preferences.core.Preferences> =
        context.corosDataStore.data.catch { emit(emptyPreferences()) }
}
