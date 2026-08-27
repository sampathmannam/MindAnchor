package org.mindanchor.llm

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiCompatibleClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient = defaultClient(baseUrl),
) : LlmClient {

    override suspend fun complete(req: LlmRequest): Result<LlmResponse> = withContext(Dispatchers.IO) {
        org.mindanchor.diagnostics.CrashLog.breadcrumb("llm.complete.${req.model}")
        runCatching {
            val started = System.currentTimeMillis()
            val body = OpenAiRequestBody(
                model = model,
                messages = req.messages.map { it.toOpenAi() },
                temperature = req.temperature,
                max_tokens = req.maxTokens,
            ).toJson()
            val request = Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use { handleComplete(it, started) }
        }.recoverCatching { e -> throw mapToLetterError(e) }
    }

    override suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        org.mindanchor.diagnostics.CrashLog.breadcrumb("llm.test_connection.${model}")
        runCatching {
            val body = OpenAiRequestBody(
                model = model,
                messages = listOf(OpenAiMessage(role = "user", content = "OK")),
                temperature = 0.0,
                max_tokens = 1,
            ).toJson()
            val request = Request.Builder()
                .url(baseUrl + "chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw mapHttpStatusToLetterError(it.code, it.body?.string().orEmpty())
                }
            }
        }.recoverCatching { e -> throw mapToLetterError(e) }
    }

    private fun handleComplete(response: okhttp3.Response, started: Long): LlmResponse {
        if (!response.isSuccessful) {
            throw mapHttpStatusToLetterError(response.code, response.body?.string().orEmpty())
        }
        val raw = response.body?.string().orEmpty()
        val parsed = try {
            json.decodeFromString(OpenAiResponseBody.serializer(), raw)
        } catch (e: Exception) {
            throw LetterError.Unknown()
        }
        val content = parsed.choices.firstOrNull()?.message?.content ?: throw LetterError.Unknown()
        val promptTokens = parsed.usage?.prompt_tokens ?: 0
        val completionTokens = parsed.usage?.completion_tokens ?: 0
        val durationMs = System.currentTimeMillis() - started
        return LlmResponse(content = content, promptTokens = promptTokens, completionTokens = completionTokens, durationMs = durationMs)
    }

    private fun mapHttpStatusToLetterError(code: Int, body: String): LetterError = when (code) {
        401 -> LetterError.InvalidApiKey()
        403 -> LetterError.AccountUnauthorized()
        404 -> LetterError.ModelNotFound()
        429 -> LetterError.RateLimited()
        in 500..599 -> LetterError.ServerError()
        else -> LetterError.Unknown(body)
    }

    private fun mapToLetterError(e: Throwable): LetterError {
        // v0.72.x: an LLM call that fails with anything
        // other than a LetterError we threw ourselves is
        // worth a forensic trace. The user sees the
        // user-friendly letter error; the crash log
        // gets the real cause with the message and stack
        // trace so the next time the user files a bug
        // about "letter didn't write", we have something
        // to look at.
        val mapped = when (e) {
            is LetterError -> e
            is SocketTimeoutException -> LetterError.Timeout()
            is SSLException -> LetterError.TlsFailed()
            is ConnectException -> LetterError.NetworkUnreachable()
            is IOException -> LetterError.NetworkUnreachable()
            else -> LetterError.Unknown()
        }
        if (e !is LetterError) {
            android.util.Log.e("MindAnchor/Llm", "complete() mapped to $mapped", e)
        }
        return mapped
    }

    @Serializable
    private data class OpenAiRequestBody(
        val model: String,
        val messages: List<OpenAiMessage>,
        val temperature: Double,
        val max_tokens: Int,
        // v0.72.x: Groq's `gpt-oss-20b` and `-120b` are
        // reasoning models; with the default
        // `reasoning_effort: "medium"` they burn most of
        // their token budget on internal thinking and
        // return an empty `content` field. Set
        // `"low"` to force the model to spend most of
        // its budget on the visible response. Other
        // providers ignore this field (the
        // `ignoreUnknownKeys` setting on the Json
        // instance would also drop it if it did).
        val reasoning_effort: String = "low",
    )

    @Serializable
    private data class OpenAiMessage(val role: String, val content: String)

    @Serializable
    private data class OpenAiResponseBody(val choices: List<OpenAiChoice> = emptyList(), val usage: OpenAiUsage? = null)

    @Serializable
    private data class OpenAiChoice(val message: OpenAiMessage)

    @Serializable
    private data class OpenAiUsage(val prompt_tokens: Int = 0, val completion_tokens: Int = 0)

    private fun OpenAiRequestBody.toJson(): String = json.encodeToString(OpenAiRequestBody.serializer(), this)

    private fun LlmMessage.toOpenAi(): OpenAiMessage = when (this) {
        is LlmMessage.System -> OpenAiMessage(role = "system", content = content)
        is LlmMessage.User -> OpenAiMessage(role = "user", content = content)
        is LlmMessage.Assistant -> OpenAiMessage(role = "assistant", content = content)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(baseUrl: String): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .callTimeout(30, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            CertificatePinning.forBaseUrl(baseUrl)?.let { builder.certificatePinner(it) }
            return builder.build()
        }
    }
}

typealias GroqClient = OpenAiCompatibleClient
