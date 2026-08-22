package org.mindanchor.llm

/**
 * The 9 failure modes for an LLM letter generation, plus
 * `userMessage` (the line the UI shows) and `isRetryable`
 * (whether the `Try again` button makes sense).
 *
 * The variants are deliberately disjoint: a 401 is
 * `InvalidApiKey`, never `Unknown`. The UI subscribes to a
 * `StateFlow<LetterError?>` and renders the error state when
 * the value is non-null.
 */
sealed class LetterError(
    val userMessage: String,
    val isRetryable: Boolean,
) : Throwable() {
    /** No API key set in Settings → Reading → Daily letter (LLM). */
    class NoApiKey : LetterError(
        "Add an API key in Settings.",
        isRetryable = false,
    )

    /** HTTP 401: the key is malformed or revoked. */
    class InvalidApiKey : LetterError(
        "API key not valid. Open settings to fix.",
        isRetryable = false,
    )

    /** HTTP 403: the key is valid but the account is rate-blocked or disabled. */
    class AccountUnauthorized : LetterError(
        "Account not authorized. Check your Groq console.",
        isRetryable = false,
    )

    /** HTTP 404: the chosen model ID is not (or no longer) on Groq. */
    class ModelNotFound : LetterError(
        "Model not available. Pick a different model in settings.",
        isRetryable = false,
    )

    /** HTTP 429: too many requests. Try again in a minute. */
    class RateLimited : LetterError(
        "Rate limit hit. Try again in a minute.",
        isRetryable = true,
    )

    /** HTTP 5xx: Groq is having trouble. */
    class ServerError : LetterError(
        "Groq is having trouble. Try again in a moment.",
        isRetryable = true,
    )

    /** Network unreachable (ConnectException, no DNS, no route). */
    class NetworkUnreachable : LetterError(
        "Network unreachable. Check your connection.",
        isRetryable = true,
    )

    /** OkHttp callTimeout (30s) elapsed. */
    class Timeout : LetterError(
        "The request timed out. Try again.",
        isRetryable = true,
    )

    /** Anything else (malformed JSON, etc.). */
    class Unknown : LetterError(
        "Something went wrong. Try again.",
        isRetryable = true,
    )
}
