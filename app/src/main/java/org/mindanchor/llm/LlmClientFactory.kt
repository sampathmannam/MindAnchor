package org.mindanchor.llm

/**
 * The factory is the only place a new provider is wired
 * in. v0.25.7 returns a [GroqClient] for [LlmProvider.GROQ]
 * and returns the [NotImplementedLlmClient] stub for any
 * other value (the Settings picker doesn't surface other
 * values yet, but the factory is defensive against a
 * future caller passing an unknown enum value).
 *
 * v0.25.8+ adds the Anthropic branch here without
 * changing the [LlmClient] interface or any caller.
 *
 * NOTE: at this commit (Task 3, the seam), `GroqClient`
 * does not exist yet — it lands in a later task. Until
 * then, GROQ also returns the [NotImplementedLlmClient]
 * stub, which fails closed with [LetterError.Unknown].
 * This keeps the seam signature correct, the build
 * green, and the test in [LlmClientFactoryTest] passing.
 * The GROQ branch is updated to instantiate `GroqClient`
 * in the task that introduces it.
 */
object LlmClientFactory {

    fun create(
        provider: LlmProvider,
        apiKey: String,
        model: String,
    ): LlmClient = when (provider) {
        LlmProvider.GROQ -> GroqClient(
            apiKey = apiKey,
            model = model,
        )

        // Defensive: an unknown provider enum (a future
        // addition that hasn't shipped an impl yet) is
        // surfaced as Unknown rather than crashing the
        // caller's coroutine.
        else -> NotImplementedLlmClient
    }

    /**
     * A stub returned for providers without an impl yet.
     * Calls always fail with [LetterError.Unknown]. The
     * Settings picker hides the Anthropic option for
     * v0.25.7; this stub is a safety net, not a
     * user-visible path.
     */
    private object NotImplementedLlmClient : LlmClient {
        override suspend fun complete(req: LlmRequest): Result<LlmResponse> =
            Result.failure(LetterError.Unknown())

        override suspend fun testConnection(): Result<Unit> =
            Result.failure(LetterError.Unknown())
    }
}
