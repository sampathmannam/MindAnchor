package org.mindanchor.vitals.polar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client over the Polar AccessLink v3 API.
 *
 * The four endpoints the side-channel uses:
 *  - `/users/nightly-recharge/{date}` — HRV (RMSSD-derived) +
 *    overnight RHR (Polar's Nightly Recharge)
 *  - `/users/continuous-heart-rate/{date}` — 5-min RR samples
 *    (research-grade HRV if the user has a chest strap)
 *  - `/users/sleep/{date}` — sleep stages
 *  - `/users/activities/{date}` — steps + active minutes
 *
 * Auth: every authenticated request carries
 * `Authorization: Bearer <access_token>`. The token comes
 * from [PolarAuth] (OAuth2 Authorization Code against
 * `https://flow.polar.com/oauth2/authorization` →
 * `https://polarremote.com/v2/oauth/token`).
 *
 * ## Why Polar AccessLink and not BLE PMD
 *
 * The Polar OH1 / Verity Sense expose a proprietary
 * Polar Measurement Data (PMD) service over BLE
 * (`FB005C80-02E7-F387-1CAD-8ACD2D8DF0C8`) that carries
 * RR intervals and HRV. That path is research-grade
 * hardware: it requires a polar-verified firmware and
 * produces records the launcher's wellness card does not
 * know how to read. The web API path is the v0.35.0
 * shape; the PMD path is a v0.36.0 follow-up.
 *
 * @wording-reviewed — clinical-review-required. The
 * user-facing "Sync now" affordance and any "Polar"
 * labelling on the home card is a clinical-review surface.
 */
class PolarApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
) {

    /**
     * Trade an authorization code for an access token.
     * Called from [PolarAuth] after the OAuth2
     * authorization flow completes.
     *
     * The Polar token is documented as a 3-day TTL. There
     * is no refresh token — the user re-authorizes.
     */
    suspend fun exchangeCode(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
    ): PolarToken = withContext(Dispatchers.IO) {
        val url = TOKEN_URL.toHttpUrl()
        val body = buildString {
            append("grant_type=authorization_code")
            append("&code=").append(code)
            append("&redirect_uri=").append(redirectUri)
            append("&client_id=").append(clientId)
            append("&client_secret=").append(clientSecret)
        }.toRequestBody(FORM_MEDIA_TYPE)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw PolarApiException(
                    "Token exchange failed: HTTP ${resp.code}: ${text.take(ERROR_BODY_PREVIEW_CHARS)}",
                    httpCode = resp.code,
                )
            }
            json.decodeFromString(PolarToken.serializer(), text)
        }
    }

    /**
     * `/users/nightly-recharge/{date}` — HRV (RMSSD-derived)
     * + overnight RHR. The endpoint is one-shot per date;
     * the date is `yyyy-MM-dd` local.
     */
    suspend fun fetchNightlyRecharge(
        accessToken: String,
        userId: Long,
        date: String,
    ): NightlyRecharge = withContext(Dispatchers.IO) {
        val text = authenticatedGet(
            accessToken,
            "users/$userId/nightly-recharge/$date",
        )
        json.decodeFromString(NightlyRecharge.serializer(), text)
    }

    /**
     * `/users/continuous-heart-rate/{date}` — 5-min RR
     * samples. The response can be large (one entry per
     * 5 min of wear-time). Caller is responsible for
     * persistence.
     */
    suspend fun fetchContinuousHeartRate(
        accessToken: String,
        userId: Long,
        date: String,
    ): ContinuousHeartRate = withContext(Dispatchers.IO) {
        val text = authenticatedGet(
            accessToken,
            "users/$userId/continuous-heart-rate/$date",
        )
        json.decodeFromString(ContinuousHeartRate.serializer(), text)
    }

    /**
     * `/users/sleep/{date}` — sleep stages + total sleep.
     * The `polarSleepStartTime` / `polarSleepEndTime` are
     * UTC ISO-8601.
     */
    suspend fun fetchSleep(
        accessToken: String,
        userId: Long,
        date: String,
    ): SleepNight = withContext(Dispatchers.IO) {
        val text = authenticatedGet(accessToken, "users/$userId/sleep/$date")
        json.decodeFromString(SleepNight.serializer(), text)
    }

    /**
     * `/users/activities/{date}` — daily activity summary
     * (steps, active minutes, calories).
     */
    suspend fun fetchActivities(
        accessToken: String,
        userId: Long,
        date: String,
    ): ActivitySummary = withContext(Dispatchers.IO) {
        val text = authenticatedGet(accessToken, "users/$userId/activities/$date")
        json.decodeFromString(ActivitySummary.serializer(), text)
    }

    /**
     * Exchange a valid bearer token for the user id. The
     * `/users` endpoint is the canonical "who am I" call.
     * The id is needed for every other endpoint's path.
     */
    suspend fun fetchUserId(accessToken: String): Long = withContext(Dispatchers.IO) {
        val text = authenticatedGet(accessToken, "users")
        val parsed = json.decodeFromString(UserList.serializer(), text)
        parsed.polarUsers.firstOrNull()?.polarUserId
            ?: throw PolarApiException("Empty user list", httpCode = null)
    }

    private fun authenticatedGet(accessToken: String, path: String): String {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw PolarApiException(
                    "HTTP ${resp.code} on $path",
                    httpCode = resp.code,
                )
            }
            return text
        }
    }

    @Serializable
    data class PolarToken(
        @kotlinx.serialization.SerialName("access_token")
        val accessToken: String,
        @kotlinx.serialization.SerialName("token_type")
        val tokenType: String? = null,
        @kotlinx.serialization.SerialName("expires_in")
        val expiresIn: Long? = null,
        @kotlinx.serialization.SerialName("x_user_id")
        val userId: Long? = null,
    )

    @Serializable
    data class UserList(
        @kotlinx.serialization.SerialName("polar-users")
        val polarUsers: List<User> = emptyList(),
    ) {
        @Serializable
        data class User(
            @kotlinx.serialization.SerialName("polar-user-id")
            val polarUserId: Long,
        )
    }

    /**
     * Polar's Nightly Recharge response. The HRV is the
     * overnight RMSSD (milliseconds) and the RHR is the
     * overnight resting heart rate (bpm).
     */
    @Serializable
    data class NightlyRecharge(
        @kotlinx.serialization.SerialName("date")
        val date: String? = null,
        @kotlinx.serialization.SerialName("heart_rate_avg")
        val heartRateAvg: Long? = null,
        @kotlinx.serialization.SerialName("beat_to_beat_avg")
        val beatToBeatAvg: Double? = null,
        @kotlinx.serialization.SerialName("heart_rate_variability_avg")
        val heartRateVariabilityAvg: Double? = null,
    )

    @Serializable
    data class ContinuousHeartRate(
        @kotlinx.serialization.SerialName("date")
        val date: String? = null,
        @kotlinx.serialization.SerialName("continuous-heart-rate")
        val samples: List<HrSample> = emptyList(),
    ) {
        @Serializable
        data class HrSample(
            @kotlinx.serialization.SerialName("datetime")
            val datetime: String? = null,
            @kotlinx.serialization.SerialName("heart-rate")
            val heartRate: Long? = null,
        )
    }

    @Serializable
    data class SleepNight(
        @kotlinx.serialization.SerialName("date")
        val date: String? = null,
        @kotlinx.serialization.SerialName("sleep_start_time")
        val sleepStartTime: String? = null,
        @kotlinx.serialization.SerialName("sleep_end_time")
        val sleepEndTime: String? = null,
        @kotlinx.serialization.SerialName("total_sleep_time")
        val totalSleepSeconds: Long? = null,
        @kotlinx.serialization.SerialName("sleep_score")
        val sleepScore: Long? = null,
    )

    @Serializable
    data class ActivitySummary(
        @kotlinx.serialization.SerialName("date")
        val date: String? = null,
        @kotlinx.serialization.SerialName("steps")
        val steps: Long? = null,
        @kotlinx.serialization.SerialName("active-calories")
        val activeCalories: Long? = null,
    )

    companion object {
        private const val BASE_URL = "https://www.polaraccesslink.com/v3"
        private const val TOKEN_URL = "https://polarremote.com/v2/oauth/token"
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        private const val USER_AGENT = "MindAnchor/0.35"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private const val CONNECT_TIMEOUT_SECONDS: Long = 15
        private const val READ_TIMEOUT_SECONDS: Long = 30
        private const val CALL_TIMEOUT_SECONDS: Long = 45
        private const val ERROR_BODY_PREVIEW_CHARS: Int = 200
    }
}

/**
 * A single typed failure for any of [PolarApi]'s
 * operations. The settings UI turns [message] into the
 * "Failed" connection state; the structured [httpCode]
 * is kept for future rate-limit backoff and structured
 * logging.
 */
class PolarApiException(
    message: String,
    val httpCode: Int? = null,
) : IOException(message)
