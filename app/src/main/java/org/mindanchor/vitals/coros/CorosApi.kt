package org.mindanchor.vitals.coros

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client over the COROS Training Hub web API.
 *
 * The four endpoints the side-channel uses are listed
 * in [fetchDashboard], [fetchAnalyse], [fetchActivities],
 * and the constructor's [login] call. All calls go
 * over HTTPS, all bodies are JSON, and every
 * authenticated request carries two headers, exactly
 * as the Training Hub web UI sends them: the
 * `accesstoken: <token>` header (lowercase, no
 * underscore) and `yfheader: {"userId": ...}` echoing
 * the login response's userId. The server rejects
 * token-only requests with result=1019 ("Access token
 * is invalid") — see [yfHeader].
 *
 * The side-channel deliberately does *not* implement
 * the mobile API. Acquiring a mobile token would log
 * the user out of the COROS phone app (documented on
 * `cygnusb/coros-mcp`'s mobile-login KDoc) and we do
 * not have a use case that justifies that.
 */
class CorosApi(
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // v0.70.1: the regional data planes do not agree on
        // JSON types — the US host serialises day-key fields
        // the EU host quotes (a bare 20260828 where EU sends
        // "20260828"). Lenient parsing reads either form into
        // the String fields the wire schemas declare, instead
        // of failing the whole sync on the stricter host.
        isLenient = true
    },
    /**
     * A test-only override that replaces the regional
     * base URL. When non-null, every call uses this URL
     * regardless of the [region] argument. Production
     * callers leave it at the default (null) and the
     * [baseUrl] function maps the region to the right
     * Training Hub host.
     */
    internal val baseUrlOverride: String? = null,
) {

    /**
     * The base URLs are region-keyed. The side-channel
     * defaults to `eu` (the default the user sees in
     * COROS's app for accounts registered in Europe).
     * US accounts would set `region = "us"` at sign-in
     * time.
     */
    fun baseUrl(region: String): String = baseUrlOverride ?: when (region.lowercase()) {
        "us" -> "https://teamapi.coros.com"
        "asia", "cn" -> "https://teamcnapi.coros.com"
        else -> "https://teameuapi.coros.com"
    }

    /**
     * Authenticate against the Training Hub web API.
     * Returns a [CorosAuthPayload] on success.
     *
     * Throws [CorosApiException] for any non-`0000`
     * result code (most commonly `1001` for bad
     * credentials).
     */
    suspend fun login(
        email: String,
        passwordHashHex: String,
        region: String,
    ): CorosAuthPayload = withContext(Dispatchers.IO) {
        val url = baseUrl(region).toHttpUrl().newBuilder()
            .addPathSegments("account/login")
            .build()
        val body = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(account = email, accountType = 2, pwd = passwordHashHex),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CorosApiException(
                    "HTTP ${resp.code} on login: ${text.take(ERROR_BODY_PREVIEW_CHARS)}",
                    httpCode = resp.code,
                )
            }
            val parsed = decodeResponse(text, CorosLoginResponse.serializer(), "login")
            if (parsed.result != "0000" || parsed.data == null) {
                throw CorosApiException(
                    "Login failed: result=${parsed.result} ${parsed.message.orEmpty()}",
                    corosResult = parsed.result,
                )
            }
            CorosAuthPayload(
                accessToken = parsed.data.accessToken,
                userId = parsed.data.userId,
                region = region,
                timestampMs = System.currentTimeMillis(),
            )
        }
    }

    /**
     * `/dashboard/query` — last 7 days of nightly HRV
     * (RMSSD ms). The endpoint always returns the most
     * recent week; there is no date-range parameter.
     */
    suspend fun fetchDashboard(auth: CorosAuthPayload): List<CorosHrv> =
        withContext(Dispatchers.IO) {
            val text = authenticatedGet(auth, "dashboard/query")
            val parsed = decodeResponse(text, CorosDashboardResponse.serializer(), "dashboard")
            if (parsed.result != "0000") {
                throw CorosApiException(
                    "dashboard failed: result=${parsed.result}",
                    corosResult = parsed.result,
                )
            }
            val hrv = parsed.data?.summaryInfo?.sleepHrvData
            val items = hrv?.sleepHrvList.orEmpty().mapNotNull { item ->
                val day = item.happenDay ?: return@mapNotNull null
                CorosHrv(
                    date = day,
                    rmssd = item.avgSleepHrv,
                    baseline = item.sleepHrvBase,
                    standardDeviation = item.sleepHrvSd,
                )
            }
            val today = hrv?.happenDay
            if (today != null && items.none { it.date == today }) {
                items + CorosHrv(
                    date = today,
                    rmssd = hrv.avgSleepHrv,
                    baseline = hrv.sleepHrvBase,
                    standardDeviation = hrv.sleepHrvSd,
                )
            } else {
                items
            }
        }

    /**
     * `/analyse/query` — last ~28 days of daily
     * metrics with VO2max, RHR, training load. The
     * `t7dayList` field carries these summary values.
     */
    suspend fun fetchAnalyse(auth: CorosAuthPayload): List<CorosDaily> =
        withContext(Dispatchers.IO) {
            val text = authenticatedGet(auth, "analyse/query")
            val parsed = decodeResponse(text, CorosAnalyseResponse.serializer(), "analyse")
            if (parsed.result != "0000") {
                throw CorosApiException(
                    "analyse failed: result=${parsed.result}",
                    corosResult = parsed.result,
                )
            }
            parsed.data?.t7dayList.orEmpty().mapNotNull { item ->
                val day = item.happenDay ?: return@mapNotNull null
                CorosDaily(
                    date = day,
                    rhr = item.rhr,
                    trainingLoad = item.trainingLoad,
                    trainingLoadRatio = item.trainingLoadRatio,
                    vo2Max = item.vo2max,
                )
            }
        }

    /**
     * `/activity/query` — paginated list of activities
     * for the date range. The side-channel only reads
     * page 1 of 30; deeper paging is a future-work item
     * because the wellness card only needs "what did I
     * do this week" at a glance.
     */
    suspend fun fetchActivities(
        auth: CorosAuthPayload,
        startDay: String,
        endDay: String,
    ): List<CorosActivity> = withContext(Dispatchers.IO) {
        val url = baseUrl(auth.region).toHttpUrl().newBuilder()
            .addPathSegments("activity/query")
            .addQueryParameter("startDay", startDay)
            .addQueryParameter("endDay", endDay)
            .addQueryParameter("pageNumber", "1")
            .addQueryParameter("size", "30")
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("accessToken", auth.accessToken)
            // v0.70.1: see [authenticatedGet] — the Training
            // Hub requires the userId echoed in `yfheader`.
            .header("yfheader", yfHeader(auth))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CorosApiException(
                    "HTTP ${resp.code} on activities",
                    httpCode = resp.code,
                )
            }
            val parsed = decodeResponse(
                text,
                CorosActivityListResponse.serializer(),
                "activities",
            )
            if (parsed.result != "0000") {
                throw CorosApiException(
                    "activity list failed: result=${parsed.result}",
                    corosResult = parsed.result,
                )
            }
            parsed.data?.dataList.orEmpty().mapNotNull { item ->
                val id = item.labelId ?: return@mapNotNull null
                CorosActivity(
                    activityId = id,
                    name = item.name ?: item.remark,
                    sportType = item.sportType,
                    sportName = CorosSportNames.name(item.sportType),
                    startTime = item.startTime?.toString(),
                    durationSeconds = item.totalTime,
                    distanceMeters = item.distance,
                    avgHr = item.avgHr,
                    maxHr = item.maxHr,
                    caloriesRaw = item.calorie,
                    trainingLoad = item.trainingLoad,
                )
            }
        }
    }

    /**
     * The `yfheader` value every authenticated Training
     * Hub call must carry: the login response's userId,
     * echoed back as a one-field JSON object. Built with
     * the serializer rather than string concatenation so
     * an unexpected character in a future userId cannot
     * produce malformed JSON.
     */
    private fun yfHeader(auth: CorosAuthPayload): String =
        buildJsonObject { put("userId", auth.userId) }.toString()

    private fun authenticatedGet(auth: CorosAuthPayload, path: String): String {
        val url = baseUrl(auth.region).toHttpUrl().newBuilder()
            .addPathSegments(path.trimStart('/'))
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("accessToken", auth.accessToken)
            // v0.70.1: the Training Hub started rejecting
            // token-only requests with result=1019 ("Access
            // token is invalid") even for a token minted
            // seconds earlier. The maintained community
            // clients authenticate with the token header
            // spelled `accessToken`, a `Content-Type` set
            // even on GETs, and the login response's userId
            // echoed back as a JSON object in a `yfheader`
            // header. Match all three. The payload already
            // carries userId for exactly this; it was parsed
            // and never used until now.
            .header("yfheader", yfHeader(auth))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw CorosApiException(
                    "HTTP ${resp.code} on $path",
                    httpCode = resp.code,
                )
            }
            return text
        }
    }

    /**
     * Decode a JSON response body into [T], translating a
     * [SerializationException] (captive portal, proxy error
     * page, truncated response) into a [CorosApiException] so
     * the worker can retry on the next sync.
     */
    @Suppress("detekt.SwallowedException")
    private fun <T> decodeResponse(
        text: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        endpoint: String,
    ): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        // A non-JSON or malformed body is a transient error
        // — the server is reachable, the credentials are
        // accepted, but the response is unexpected. The
        // worker retries on the next periodic tick.
        throw CorosApiException(
            "Malformed response on $endpoint: ${e.message?.take(ERROR_BODY_PREVIEW_CHARS)}",
            corosResult = "MALFORMED",
        )
    }

    @kotlinx.serialization.Serializable
    internal data class LoginRequest(
        val account: String,
        val accountType: Int,
        val pwd: String,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * The COROS web UI sends this UA. A real browser UA
         * is more convincing than a generic OkHttp string.
         */
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/145.0.0.0 Safari/537.36"

        /**
         * Sensible defaults for a small handful of calls per
         * sync cycle. Timeouts are deliberately short so a
         * hung connection surfaces fast; the user is
         * waiting for the "last sync" timestamp to update.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // v0.70.1: HTTP/1.1 only. The Training Hub's data
            // plane started answering result=1019 ("Access
            // token is invalid") to authenticated reads made
            // over an OkHttp-negotiated HTTP/2 connection while
            // accepting the same headers over HTTP/1.1 — the
            // protocol the working community clients speak.
            // Pinning 1.1 removes the variable; the cost is
            // one connection per host, irrelevant at four
            // calls per six-hour sync.
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()

        private const val CONNECT_TIMEOUT_SECONDS: Long = 15
        private const val READ_TIMEOUT_SECONDS: Long = 30
        private const val CALL_TIMEOUT_SECONDS: Long = 45
        private const val ERROR_BODY_PREVIEW_CHARS: Int = 200
    }
}

/**
 * A single typed failure for any of [CorosApi]'s
 * operations. Carries the HTTP code (when the failure
 * was transport-level) and the COROS `result` string
 * (when the failure was API-level, like `1001` for
 * invalid credentials).
 *
 * The UI turns [reason] into the "Failed" connection
 * state; the *structured* fields are kept for future
 * work (rate-limit backoff, structured logging).
 */
class CorosApiException(
    message: String,
    val httpCode: Int? = null,
    val corosResult: String? = null,
) : IOException(message)
