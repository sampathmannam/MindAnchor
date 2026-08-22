package org.mindanchor.llm

/**
 * The LLM provider seam. v0.25.7 ships one impl
 * ([GroqClient]); v0.25.8+ will add an Anthropic impl.
 * The factory is the only place a new impl is wired in.
 *
 * The Result type carries either a successful
 * [LlmResponse] or a thrown [LetterError] — `Result` not
 * `Flow` because the launcher does not stream the
 * response (Groq is sub-second; the full body is shown
 * at once).
 *
 * The `suspend` keyword is the async seam: the OkHttp
 * call runs on `Dispatchers.IO` inside the impl; the
 * caller can `viewModelScope.launch { ... }` without
 * blocking the UI thread.
 */
interface LlmClient {

    /**
     * Issues a chat-completion request. Returns
     * `Result.success(LlmResponse)` on HTTP 2xx with a
     * parseable body; `Result.failure(LetterError)` for
     * any failure mode (401 → InvalidApiKey, 429 →
     * RateLimited, etc.).
     *
     * The Result is intentionally a `Result`, not a
     * sealed class — Kotlin's stdlib `Result` is what
     * coroutine-aware callers expect (`runCatching`,
     * `.getOrNull()`, etc.) and the type is already
     * familiar to every contributor.
     */
    suspend fun complete(req: LlmRequest): Result<LlmResponse>

    /**
     * One-token "OK" completion used by the Settings →
     * Daily letter (LLM) → "Test connection" button.
     * Confirms the key is valid, the model is available,
     * and the network reaches Groq — all three in one
     * round-trip.
     *
     * `Result.success(Unit)` on 2xx; `Result.failure(LetterError)`
     * otherwise. Same mapping as [complete].
     */
    suspend fun testConnection(): Result<Unit>
}
