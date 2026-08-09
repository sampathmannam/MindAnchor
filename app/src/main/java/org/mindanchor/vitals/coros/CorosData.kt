package org.mindanchor.vitals.coros

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One night of HRV from the COROS Training Hub
 * dashboard endpoint (`/dashboard/query`).
 *
 * `summaryInfo.sleepHrvData.sleepHrvList` carries the
 * per-night list; the `happenDay` field on the
 * surrounding object carries today's value when
 * today's data is not yet in the list.
 *
 * `avgSleepHrv` is RMSSD milliseconds. COROS does not
 * expose any of the raw RR intervals — only the
 * summary statistic — so the wellness card cannot
 * recompute its own robust z-score from COROS data and
 * has to accept the watch's own number.
 */
@Serializable
data class CorosHrv(
    val date: String,
    val rmssd: Double? = null,
    val baseline: Double? = null,
    val standardDeviation: Double? = null,
)

/**
 * One day of the t7dayList / dayDetail summary returned
 * by `/analyse/query` and `/analyse/dayDetail/query`.
 *
 * `rhr` is the day's average resting heart rate
 * (beats per minute). `trainingLoad` is COROS's
 * proprietary 0–100 scale. `vo2Max` is ml/kg/min
 * (Copenhagen protocol, COROS's reported value).
 */
@Serializable
data class CorosDaily(
    val date: String,
    val rhr: Double? = null,
    val trainingLoad: Double? = null,
    val trainingLoadRatio: Double? = null,
    val vo2Max: Double? = null,
)

/**
 * One activity as returned by `/activity/query`. The
 * COROS activity list is paginated; the side-channel
 * only reads page 1 with `size=30`, which is enough
 * for a "what did I do this week" glance.
 *
 * `calories` is in *physical* calories (cal), not
 * kilocalories (kcal), per the COROS API contract.
 * Callers divide by 1000 to get kcal.
 */
@Serializable
data class CorosActivity(
    val activityId: String,
    val name: String? = null,
    val sportType: Int? = null,
    val sportName: String? = null,
    val startTime: String? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Long? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val caloriesRaw: Long? = null,
    val trainingLoad: Int? = null,
)

/**
 * The COROS API's auth payload, decoded from
 * `/account/login`. The web token is the only thing
 * the launcher needs; the mobile token is *not* used
 * because acquiring it would log the user out of the
 * COROS phone app (see [CorosAuth] for the rationale
 * on why we deliberately do not implement the mobile
 * API).
 */
@Serializable
data class CorosAuthPayload(
    val accessToken: String,
    val userId: String,
    val region: String,
    val timestampMs: Long,
)

/**
 * Sport name lookup for the COROS activity namespace.
 * The activity-side IDs are not the same as the
 * workout-API wire IDs: runs are 100/102/103, the
 * workout API collapses them all to 1. The side-channel
 * only deals with the activity side, so this map is
 * the only translation needed.
 */
object CorosSportNames {
    private val NAMES: Map<Int, String> = mapOf(
        SPORT_RUNNING to "Running",
        SPORT_TRAIL_RUNNING to "Trail Running",
        SPORT_TRACK_RUNNING to "Track Running",
        SPORT_HIKING to "Hiking",
        SPORT_ROAD_BIKE to "Road Bike",
        SPORT_INDOOR_CYCLING to "Indoor Cycling",
        SPORT_GRAVEL_BIKE to "Gravel Bike",
        SPORT_MTB to "MTB",
        SPORT_CARDIO to "Cardio",
        SPORT_STRENGTH to "Strength",
        SPORT_YOGA to "Yoga",
        SPORT_WALKING to "Walking",
        SPORT_BIKE_COMMUTE to "Bike Commute",
    )

    fun name(sportType: Int?): String? =
        sportType?.let { NAMES[it] ?: "Sport $it" }

    // The IDs are wire-format constants; a magic-number
    // rule on a static map would force this either way.
    // Naming them keeps the wire ↔ UI mapping obvious.
    private const val SPORT_RUNNING: Int = 100
    private const val SPORT_TRAIL_RUNNING: Int = 102
    private const val SPORT_TRACK_RUNNING: Int = 103
    private const val SPORT_HIKING: Int = 104
    private const val SPORT_ROAD_BIKE: Int = 200
    private const val SPORT_INDOOR_CYCLING: Int = 201
    private const val SPORT_GRAVEL_BIKE: Int = 203
    private const val SPORT_MTB: Int = 204
    private const val SPORT_CARDIO: Int = 400
    private const val SPORT_STRENGTH: Int = 402
    private const val SPORT_YOGA: Int = 403
    private const val SPORT_WALKING: Int = 900
    private const val SPORT_BIKE_COMMUTE: Int = 9807
}

/**
 * The signed-in state the UI cares about. `NotConnected`
 * is the default; `AwaitingConsent` is shown when the
 * user has typed credentials but the launcher has not
 * yet completed a successful login; `Connected` carries
 * the most recent sync timestamp.
 */
sealed interface CorosConnectionState {
    data object NotConnected : CorosConnectionState

    data object AwaitingConsent : CorosConnectionState

    data class Connected(
        val email: String,
        val region: String,
        val lastSyncEpochMs: Long,
    ) : CorosConnectionState

    data class Failed(val reason: String) : CorosConnectionState
}

/**
 * Wire schema for `/account/login`. The COROS API
 * returns `{"result": "0000", "data": {"accessToken": ...,
 * "userId": ..., ...}}` on success and a non-`0000`
 * result code on any failure.
 */
@Serializable
internal data class CorosLoginResponse(
    val result: String,
    val message: String? = null,
    val data: CorosLoginData? = null,
)

@Serializable
internal data class CorosLoginData(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("userId") val userId: String,
)

/**
 * Wire schema for `/dashboard/query`. Only the
 * `summaryInfo.sleepHrvData` subtree is read; the rest
 * of the dashboard payload is discarded.
 */
@Serializable
internal data class CorosDashboardResponse(
    val result: String,
    val data: CorosDashboardData? = null,
)

@Serializable
internal data class CorosDashboardData(
    val summaryInfo: CorosSummaryInfo? = null,
)

@Serializable
internal data class CorosSummaryInfo(
    val sleepHrvData: CorosSleepHrvData? = null,
)

@Serializable
internal data class CorosSleepHrvData(
    val happenDay: String? = null,
    val avgSleepHrv: Double? = null,
    val sleepHrvBase: Double? = null,
    val sleepHrvSd: Double? = null,
    val sleepHrvList: List<CorosHrvItem>? = null,
)

@Serializable
internal data class CorosHrvItem(
    val happenDay: String? = null,
    val avgSleepHrv: Double? = null,
    val sleepHrvBase: Double? = null,
    val sleepHrvSd: Double? = null,
)

/**
 * Wire schema for `/analyse/query`. The side-channel
 * reads `data.t7dayList` for the last-28-days window
 * with VO2max and RHR.
 */
@Serializable
internal data class CorosAnalyseResponse(
    val result: String,
    val data: CorosAnalyseData? = null,
)

@Serializable
internal data class CorosAnalyseData(
    val t7dayList: List<CorosT7DayItem>? = null,
)

@Serializable
internal data class CorosT7DayItem(
    val happenDay: String? = null,
    val rhr: Double? = null,
    val trainingLoad: Double? = null,
    val trainingLoadRatio: Double? = null,
    val vo2max: Double? = null,
)

/**
 * Wire schema for `/activity/query`. The side-channel
 * only reads `data.dataList` (the actual list) and
 * `data.totalCount` (for paging — currently unused
 * because the v0.20.7 side-channel only fetches one
 * page of 30).
 */
@Serializable
internal data class CorosActivityListResponse(
    val result: String,
    val data: CorosActivityListData? = null,
)

@Serializable
internal data class CorosActivityListData(
    val dataList: List<CorosActivityItem>? = null,
    val totalCount: Int? = null,
)

@Serializable
internal data class CorosActivityItem(
    val labelId: String? = null,
    val name: String? = null,
    val remark: String? = null,
    val sportType: Int? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val totalTime: Long? = null,
    val distance: Long? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val calorie: Long? = null,
    val trainingLoad: Int? = null,
)
