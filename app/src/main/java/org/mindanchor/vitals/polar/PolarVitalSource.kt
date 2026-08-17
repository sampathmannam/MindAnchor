package org.mindanchor.vitals.polar

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.polarDataStore by preferencesDataStore(name = "polar_bridge_data")

/**
 * The local cache of Polar AccessLink data the worker has
 * fetched and the wellness card reads from.
 *
 * ## What lives here
 *
 *  - Last 7 days of nightly HRV (RMSSD ms), serialised
 *    as a JSON array of `PolarHrv` records.
 *  - Last 7 days of overnight RHR, serialised as a JSON
 *    array of `PolarRhr` records.
 *  - Last 7 days of sleep (total seconds, score), as
 *    `PolarSleep` records.
 *  - The last successful sync timestamp, in epoch millis.
 *
 * The on-disk form is the same JSON shape the Polar API
 * returns, so re-writing it on every sync is a single
 * `prefs.edit { putString(...) }` call. The merge with
 * Health Connect data happens at *read* time, in
 * [mergeWith], not here — this class is the cache, the
 * wellness card is the merge point.
 *
 * The shape mirrors [org.mindanchor.vitals.coros.CorosVitalSource]
 * so a future v0.36.0 "add a new wearable connector"
 * pattern is a copy-paste. The merge rule is the same
 * too: HC wins for what it has, Polar fills the gaps.
 *
 * @wording-reviewed — clinical-review-required. The
 * user-facing "Last sync" timestamp on the home card is
 * the clinical-review surface; wording changes here must
 * be re-reviewed per docs/CLINICAL_REVIEW.md.
 */
class PolarVitalSource(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val hrvKey = stringPreferencesKey("hrv_json")
    private val rhrKey = stringPreferencesKey("rhr_json")
    private val sleepKey = stringPreferencesKey("sleep_json")
    private val lastSyncKey = longPreferencesKey("last_sync_ms")

    /**
     * The most recent successful sync, in epoch millis, or
     * null when the bridge has never synced on this device.
     */
    val lastSyncEpochMs: Flow<Long?> = safeData().map { it[lastSyncKey] }

    val hrv: Flow<List<PolarHrv>> = safeData().map { prefs ->
        decode(prefs[hrvKey], ListSerializer(PolarHrv.serializer()))
    }

    val rhr: Flow<List<PolarRhr>> = safeData().map { prefs ->
        decode(prefs[rhrKey], ListSerializer(PolarRhr.serializer()))
    }

    val sleep: Flow<List<PolarSleep>> = safeData().map { prefs ->
        decode(prefs[sleepKey], ListSerializer(PolarSleep.serializer()))
    }

    suspend fun write(
        hrv: List<PolarHrv>,
        rhr: List<PolarRhr>,
        sleep: List<PolarSleep>,
    ) {
        val hrvJson = json.encodeToString(ListSerializer(PolarHrv.serializer()), hrv)
        val rhrJson = json.encodeToString(ListSerializer(PolarRhr.serializer()), rhr)
        val sleepJson = json.encodeToString(ListSerializer(PolarSleep.serializer()), sleep)
        context.polarDataStore.edit { prefs ->
            prefs[hrvKey] = hrvJson
            prefs[rhrKey] = rhrJson
            prefs[sleepKey] = sleepJson
            prefs[lastSyncKey] = System.currentTimeMillis()
        }
    }

    suspend fun clear() {
        context.polarDataStore.edit { it.clear() }
    }

    /**
     * Merge the Polar cache into a Health-Connect-derived
     * view, with the conflict rule **HC wins for what it
     * has, Polar fills the gaps** (same rule as
     * [org.mindanchor.vitals.coros.CorosVitalSource.mergeWith]):
     *
     *  - When HC has an HRV reading for a date, that
     *    reading is kept.
     *  - When HC has no HRV, the Polar nightly HRV is
     *    used for that date.
     *  - The RHR rule is the same.
     */
    suspend fun mergeWith(
        hcByDate: Map<String, HcDayVitals>?,
    ): List<MergedDay> {
        val hrvMap: Map<String, PolarHrv> = hrv.first().associateBy { it.date }
        val rhrMap: Map<String, PolarRhr> = rhr.first().associateBy { it.date }
        val dates = (hrvMap.keys + rhrMap.keys + (hcByDate?.keys ?: emptySet()))
            .toSortedSet()
        return dates.map { date ->
            val polarHrv = hrvMap[date]
            val polarRhr = rhrMap[date]
            val hc = hcByDate?.get(date)
            MergedDay(
                date = date,
                hrvRmssd = hc?.hrvRmssd ?: polarHrv?.rmssd,
                hrvSource = when {
                    hc?.hrvRmssd != null -> Source.HEALTH_CONNECT
                    polarHrv?.rmssd != null -> Source.POLAR
                    else -> Source.NONE
                },
                rhr = hc?.restingHeartRate ?: polarRhr?.rhr,
                rhrSource = when {
                    hc?.restingHeartRate != null -> Source.HEALTH_CONNECT
                    polarRhr?.rhr != null -> Source.POLAR
                    else -> Source.NONE
                },
            )
        }
    }

    data class MergedDay(
        val date: String,
        val hrvRmssd: Double?,
        val hrvSource: Source,
        val rhr: Double?,
        val rhrSource: Source,
    )

    enum class Source { HEALTH_CONNECT, POLAR, NONE }

    data class HcDayVitals(
        val date: String,
        val hrvRmssd: Double?,
        val restingHeartRate: Double?,
    )

    private fun <T> decode(
        raw: String?,
        serializer: kotlinx.serialization.KSerializer<List<T>>,
    ): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }

    private fun safeData(): Flow<androidx.datastore.preferences.core.Preferences> =
        context.polarDataStore.data.catch { emit(emptyPreferences()) }
}

@Serializable
data class PolarHrv(
    val date: String,
    val rmssd: Double?,
    val baseline: Double? = null,
)

@Serializable
data class PolarRhr(
    val date: String,
    val rhr: Double?,
)

@Serializable
data class PolarSleep(
    val date: String,
    val totalSleepSeconds: Long?,
    val sleepScore: Long? = null,
)
